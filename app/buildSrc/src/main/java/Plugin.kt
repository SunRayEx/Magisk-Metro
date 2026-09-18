
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File
import java.util.Properties
import java.util.Random

// Set non-zero value here to fix the random seed for reproducible builds
// CI builds are always reproducible
val RAND_SEED = if (System.getenv("CI") != null) 42 else 0
lateinit var RANDOM: Random
val ABI_SUPPORT_LIST = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64", "riscv64")

private val props = Properties()
private var commitHash = ""
private val supportAbis = setOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64", "riscv64")
private val defaultAbis = setOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64")

object Config {
    operator fun get(key: String): String? {
        val v = props[key] as? String ?: return null
        return v.ifBlank { null }
    }

    fun contains(key: String) = get(key) != null

    val version: String get() = get("version") ?: commitHash.ifBlank { "MagisKube-dev" }
    val versionCode: Int get() = get("magisk.versionCode")!!.toInt()
    val stubVersion: String get() = get("magisk.stubVersion")!!
    val abiList: Set<String> get() {
        val abiList = get("abiList") ?: return defaultAbis
        return abiList.split(Regex("\\s*,\\s*")).toSet() intersect supportAbis
    }
}

fun Project.rootFile(path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file
    else File(rootProject.file(".."), path)
}

class MagiskPlugin : Plugin<Project> {
    override fun apply(project: Project) = project.applyPlugin()

    private fun Project.applyPlugin() {
        initRandom(rootProject.file("dict.txt"))
        props.clear()

        // Get gradle properties relevant to Magisk
        props.putAll(providers.gradlePropertiesPrefixedBy("magisk.").get())

        // Load config.prop
        val configPath = findProperty("configPath") as String?
        val configFile = rootFile(configPath ?: "config.prop")
        if (configFile.exists()) {
            configFile.inputStream().use {
                val config = Properties()
                config.load(it)
                // Remove properties that should be passed by commandline
                config.remove("abiList")
                props.putAll(config)
            }
        }

        // Commandline override
        findProperty("abiList")?.let { props.put("abiList", it) }

        commitHash = runCatching {
            FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(rootFile("."))
                .build()
                .use { repo ->
                    repo.refDatabase.exactRef("HEAD")?.objectId?.let { refId ->
                        repo.newObjectReader().use { reader ->
                            reader.abbreviate(refId, 8).name()
                        }
                    }
                }
        }.getOrNull().orEmpty()
    }
}
