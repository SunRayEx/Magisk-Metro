package com.topjohnwu.magisk.core.model.module

import com.squareup.moshi.JsonDataException
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.utils.RootUtils
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.Locale

data class LocalModule(
    val base: ExtendedFile,
) : Module() {
    private val svc get() = ServiceLocator.networkService

    override var id: String = ""
    override var name: String = ""
    override var version: String = ""
    override var versionCode: Int = -1
    var author: String = ""
    var description: String = ""
    var updateInfo: OnlineModule? = null
    var outdated = false
    private var updateUrl: String = ""

    private val removeFile = base.getChildFile("remove")
    private val disableFile = base.getChildFile("disable")
    private val updateFile = base.getChildFile("update")
    val zygiskFolder = base.getChildFile("zygisk")
    val webrootFolder = base.getChildFile("webroot")

    val updated get() = updateFile.exists()
    val isRiru = (id == "riru-core") || base.getChildFile("riru").exists()
    val isZygisk = zygiskFolder.exists()
    val zygiskUnloaded = zygiskFolder.getChildFile("unloaded").exists()
    val hasAction = base.getChildFile("action.sh").exists()
    val hasWebUi = webrootFolder.getChildFile("index.html").exists()

    var enable: Boolean
        get() = !disableFile.exists()
        set(enable) {
            if (enable) {
                disableFile.delete()
                Shell.cmd("copy_preinit_files").submit()
            } else {
                !disableFile.createNewFile()
                Shell.cmd("copy_preinit_files").submit()
            }
        }

    var remove: Boolean
        get() = removeFile.exists()
        set(remove) {
            if (remove) {
                if (updateFile.exists()) return
                removeFile.createNewFile()
                Shell.cmd("copy_preinit_files").submit()
            } else {
                removeFile.delete()
                Shell.cmd("copy_preinit_files").submit()
            }
        }

    @Throws(NumberFormatException::class)
    private fun parseProps(props: List<String>) {
        for (line in props) {
            val prop = line.split("=".toRegex(), 2).map { it.trim() }
            if (prop.size != 2)
                continue

            val key = prop[0]
            val value = prop[1]
            if (key.isEmpty() || key[0] == '#')
                continue

            when (key) {
                "id" -> id = value
                "name" -> name = value
                "version" -> version = value
                "versionCode" -> versionCode = value.toInt()
                "author" -> author = value
                "description" -> description = value
                "updateJson" -> updateUrl = value
            }
        }
    }

    init {
        runCatching {
            parseProps(Shell.cmd("dos2unix < $base/module.prop").exec().out)
        }

        if (id.isEmpty()) {
            id = base.name
        }

        if (name.isEmpty()) {
            name = id
        }
    }

    suspend fun fetch(): Boolean {
        if (updateUrl.isEmpty())
            return false

        try {
            val json = svc.fetchModuleJson(updateUrl)
            updateInfo = OnlineModule(this, json)
            outdated = json.versionCode > versionCode
            return true
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }

        return false
    }

    companion object {

        // The module filesystem is served through a root service that shares the lifetime of
        // the idle-timeout root shell, so a listing racing with that recycle can hit a dying
        // binder and come back empty even though /data/adb/modules is completely untouched.
        // Restoring the stock image only re-flashes the boot partition, but it churns the shell
        // exactly like this; without a retry it looks as if the restore deleted every module.
        private const val LIST_RETRY_MAX = 4
        private const val LIST_RETRY_BACKOFF_MS = 250L

        fun loaded() = RootUtils.fs.getFile(Const.MODULE_PATH).exists()

        suspend fun installed(): List<LocalModule> = withContext(Dispatchers.IO) {
            var modules: List<LocalModule> = emptyList()
            var attempt = 0
            while (modules.isEmpty() && attempt < LIST_RETRY_MAX) {
                modules = try {
                    listModulesInternal()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // The root service is recycling; let it settle and try again.
                    Timber.e(e)
                    emptyList()
                }
                attempt++
                // An empty listing is either genuine (nothing installed / Magisk inactive) or a
                // transient failure reaching the recycled root service. Only the genuine cases
                // stay empty across retries, so confirm before reporting every module gone.
                if (modules.isEmpty() && attempt < LIST_RETRY_MAX && loaded()) {
                    delay(LIST_RETRY_BACKOFF_MS)
                }
            }
            modules
        }

        private fun listModulesInternal(): List<LocalModule> =
            RootUtils.fs.getFile(Const.MODULE_PATH)
                .listFiles()
                .orEmpty()
                .filter { !it.isFile && !it.isHidden }
                .map { LocalModule(it) }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}
