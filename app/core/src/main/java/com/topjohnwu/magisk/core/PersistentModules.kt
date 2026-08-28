package com.topjohnwu.magisk.core

import com.topjohnwu.superuser.Shell
import org.json.JSONObject

/** One declared module in the persistent manifest. */
data class PersistentEntry(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val enabled: Boolean,
)

/** Parsed view of the declarative manifest maintained by metrolink. */
data class PersistentManifest(
    val updatedAt: Long,
    val modules: List<PersistentEntry>,
) {
    val appliedAt: java.util.Date? get() = if (updatedAt > 0) java.util.Date(updatedAt * 1000) else null
}

/** Outcome of a metrolink invocation; [message] carries the failure reason when not ok. */
data class PersistentResult(val ok: Boolean, val json: JSONObject?, val message: String? = null) {
    val restored: List<String> = json?.optJSONArray("restored")
        ?.let { array -> (0 until array.length()).map { array.optString(it) } }
        .orEmpty()
}

/**
 * Bridge to metrolink — the Zig + C persistent-module link manager shipped inside the APK.
 * The manifest (JSON, parsed by the Rust metroconf crate) and the immutable snapshot store
 * live under /data/adb/metromod (root-owned Magisk storage, never the app's own data
 * directory); a post-fs-data script enforces the declaration at boot.
 */
object PersistentModules {

    const val BASE_DIR = "/data/adb/metromod"
    const val BINARY = "$BASE_DIR/metrolink"
    const val GHOST_AUDIT_LOG = "$BASE_DIR/ghost_audit.log"
    private const val BOOT_SCRIPT = "/data/adb/post-fs-data.d/metromod.sh"

    /** Must match METROLINK_VERSION in the Zig source; older binaries are replaced on sight. */
    private const val TOOL_VERSION = 2

    fun supported(): Boolean = Info.env.isActive && !isRunningAsStub

    /** Whether the feature is configured by the user (Settings · Misc). */
    fun configured(): Boolean = Config.metroPersistentModules

    /** Copies the bundled metrolink binary into /data/adb/metromod. Every step is chained so a
     * failure anywhere (a busy old binary, a denied relabel) fails the whole install; the tree
     * is relabeled to magisk_file so exec and file access are permitted under enforcing policy. */
    fun install(): Boolean {
        if (!supported()) return false
        val src = "${AppContext.applicationInfo.nativeLibraryDir}/libmetrolink.so"
        val script = listOf(
            "mkdir -p $BASE_DIR/store",
            // Unlink first: overwriting an executing binary fails with ETXTBSY.
            "rm -f '$BINARY'",
            "cp -f '$src' '$BINARY'",
            "chmod 755 '$BINARY'",
            "chown 0:0 '$BINARY'",
            "chcon u:object_r:magisk_file:s0 '$BINARY'",
            "chcon -R u:object_r:magisk_file:s0 $BASE_DIR",
            "'$BINARY' version",
        ).joinToString(" && ")
        return Shell.cmd(script).exec().isSuccess
    }

    /** The version reported by the installed binary itself, or null when absent/broken. */
    private fun binaryVersion(): Int? = runCatching {
        val out = Shell.cmd("'$BINARY' version 2>/dev/null").exec().out.joinToString("\n").trim()
        JSONObject(out).takeIf { it.optBoolean("ok") }?.optInt("version")
    }.getOrNull()

    /**
     * Makes sure the native tool matches the running app before any operation. Presence alone
     * is not enough: a stale binary left by an older install must be replaced, so the check
     * asks the binary for its own version.
     */
    private fun ensureInstalled(): Boolean {
        if (!supported()) return false
        if (binaryVersion() == TOOL_VERSION) return true
        if (!install()) return false
        return binaryVersion() == TOOL_VERSION
    }

    /** Installs or removes the boot-time enforcement script. */
    fun ensureBootScript(enabled: Boolean): Boolean {
        val results = if (enabled) {
            Shell.cmd("mkdir -p /data/adb/post-fs-data.d", "'$BINARY' install-boot").exec()
        } else {
            Shell.cmd("rm -f $BOOT_SCRIPT").exec()
        }
        return results.isSuccess
    }


    private fun run(vararg cmds: String): PersistentResult {
        if (!ensureInstalled()) {
            return PersistentResult(
                false,
                null,
                "metrolink unavailable (binary v${binaryVersion()}, need v$TOOL_VERSION; root=${Info.env.isActive})",
            )
        }
        val result = Shell.cmd(*cmds).exec()
        val stdout = result.out.joinToString("\n").trim()
        val stderr = result.err.joinToString("\n").trim()
        val json = runCatching { JSONObject(stdout) }.getOrNull()
        val ok = result.isSuccess && json?.optBoolean("ok") == true
        // Surface everything we have: without this, a bare "exit=1" hides the actual cause
        // (missing binary, SELinux denial, shell banner text, ...).
        val message = if (ok) null else buildString {
            append(json?.optString("error")?.takeIf(String::isNotEmpty) ?: "")
            if (isEmpty() && stdout.isNotEmpty()) append(stdout.take(160))
            if (isEmpty() && stderr.isNotEmpty()) append(stderr.take(160))
            if (isEmpty()) append("exit=${result.code}")
        }
        return PersistentResult(ok, json, message)
    }

    fun apply() = run("'$BINARY' apply")

    fun snapshot(id: String) = run("'$BINARY' snapshot '$id'")

    fun unpin(id: String) = run("'$BINARY' unpin '$id'")

    fun toggle(id: String, enabled: Boolean) =
        run("'$BINARY' toggle '$id' ${if (enabled) 1 else 0}")

    fun prune() = run("'$BINARY' prune")

    /** Reads the current manifest; null when metrolink is not installed yet. */
    fun fetch(): PersistentManifest? {
        if (!supported()) return null
        if (Shell.cmd("test -f '$BASE_DIR/manifest.json'").exec().isSuccess.not()) return null
        val result = Shell.cmd("cat $BASE_DIR/manifest.json").exec()
        val text = result.out.joinToString("\n").trim()
        if (text.isEmpty()) return null
        return runCatching {
            val json = JSONObject(text)
            val modules = json.optJSONArray("modules")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val entry = array.optJSONObject(i) ?: return@mapNotNull null
                    PersistentEntry(
                        id = entry.optString("id"),
                        name = entry.optString("name").ifEmpty { entry.optString("id") },
                        version = entry.optString("version"),
                        versionCode = entry.optLong("version_code", -1),
                        enabled = entry.optBoolean("enabled", true),
                    )
                }
            }.orEmpty()
            PersistentManifest(json.optLong("updated_at", 0), modules)
        }.getOrNull()
    }
}
