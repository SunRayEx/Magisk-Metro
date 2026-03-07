package com.magiskube.magisk

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : FlutterActivity() {
    private val CHANNEL = "magisk_manager/data"
    private val MAGISK_CHANNEL = "magisk_manager/magisk"
    private val DENYLIST_CHANNEL = "magisk_manager/denylist"
    private val LOGS_CHANNEL = "magisk_manager/logs"
    private val uiHandler = Handler(Looper.getMainLooper())
    
    private var pendingResult: Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getModules" -> result.success(getModulesList())
                "getApps" -> result.success(getInstalledApps())
                "getMagiskVersion" -> result.success(getMagiskVersion())
                "isRooted" -> result.success(checkRootAccess())
                "isZygiskEnabled" -> result.success(isZygiskEnabled())
                "isRamdiskLoaded" -> result.success(isRamdiskLoaded())
                "getMagiskConfig" -> result.success(getMagiskConfig())
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, MAGISK_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "installMagisk" -> {
                    val bootImage = call.argument<String>("bootImage")
                    result.success(installMagisk(bootImage ?: ""))
                }
                "uninstallMagisk" -> {
                    val restoreImages = call.argument<Boolean>("restoreImages") ?: true
                    result.success(uninstallMagisk(restoreImages))
                }
                "patchBootImage" -> {
                    val bootImage = call.argument<String>("bootImage")
                    result.success(patchBootImage(bootImage ?: ""))
                }
                "updateManager" -> result.success(updateMagiskManager())
                "getLatestVersion" -> result.success(getLatestVersion())
                "getDeviceInfo" -> result.success(getDeviceInfo())
                "rebootDevice" -> {
                    rebootDevice()
                    result.success(true)
                }
                "openMagiskSettings" -> {
                    openMagiskSettings()
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DENYLIST_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getDenyList" -> result.success(getDenyList())
                "addToDenyList" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(addToDenyList(packageName ?: ""))
                }
                "removeFromDenyList" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(removeFromDenyList(packageName ?: ""))
                }
                "isInDenyList" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(isInDenyList(packageName ?: ""))
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, LOGS_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                private var isListening = false

                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    isListening = true
                    Thread {
                        try {
                            val process = Runtime.getRuntime().exec("logcat -d -v time *:W -n 50")
                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                            var line: String?
                            while (isListening) {
                                line = reader.readLine()
                                if (line == null || !isListening) break
                                uiHandler.post {
                                    if (isListening) {
                                        events?.success(line)
                                    }
                                }
                                Thread.sleep(200)
                            }
                        } catch (e: Exception) {
                            uiHandler.post {
                                events?.success("[E] Logcat error: ${e.message}")
                            }
                        }
                    }.start()
                }

                override fun onCancel(arguments: Any?) {
                    isListening = false
                }
            }
        )
    }

    private fun getModulesList(): List<Map<String, Any>> {
        val modules = mutableListOf<Map<String, Any>>()
        try {
            val modulesDir = File("/data/adb/modules")
            if (modulesDir.exists() && modulesDir.isDirectory) {
                modulesDir.listFiles()?.filter { it.isDirectory && it.name != ".core" }?.forEach { moduleDir ->
                    val moduleJson = File(moduleDir, "module.json")
                    val propsFile = File(moduleDir, "prop")
                    
                    var name = moduleDir.name
                    var version = "Unknown"
                    var author = "Unknown"
                    var description = ""
                    
                    if (moduleJson.exists()) {
                        try {
                            val json = moduleJson.readText()
                            val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(json)
                            val versionMatch = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(json)
                            val authorMatch = Regex("\"author\"\\s*:\\s*\"([^\"]+)\"").find(json)
                            val descMatch = Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").find(json)
                            
                            name = nameMatch?.groupValues?.get(1) ?: moduleDir.name
                            version = versionMatch?.groupValues?.get(1) ?: "Unknown"
                            author = authorMatch?.groupValues?.get(1) ?: "Unknown"
                            description = descMatch?.groupValues?.get(1) ?: ""
                        } catch (e: Exception) {}
                    }
                    
                    val isEnabled = File(moduleDir, "disable").exists() == false
                    
                    modules.add(mapOf(
                        "name" to name,
                        "version" to version,
                        "author" to author,
                        "description" to description,
                        "isEnabled" to isEnabled,
                        "path" to moduleDir.absolutePath
                    ))
                }
            }
        } catch (e: Exception) {
            modules.add(mapOf(
                "name" to "Error",
                "version" to "Error",
                "author" to (e.message ?: "Error"),
                "description" to "",
                "isEnabled" to false,
                "path" to ""
            ))
        }
        return modules
    }

    private fun getInstalledApps(): List<Map<String, Any>> {
        val apps = mutableListOf<Map<String, Any>>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm list packages -3"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    val packageName = it.replace("package:", "").trim()
                    if (packageName.isNotEmpty()) {
                        apps.add(mapOf(
                            "name" to packageName.substringAfterLast("."),
                            "packageName" to packageName,
                            "isActive" to !isInDenyList(packageName)
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            try {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(intent, 0).forEach { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    apps.add(mapOf(
                        "name" to resolveInfo.loadLabel(pm).toString(),
                        "packageName" to packageName,
                        "isActive" to !isInDenyList(packageName)
                    ))
                }
            } catch (e2: Exception) {}
        }
        return apps.sortedBy { it["name"].toString().lowercase() }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun getMagiskVersion(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk -V"))
            process.waitFor()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val version = reader.readLine() ?: "Unknown"
            if (version.contains("not found") || version.isEmpty()) "Unknown" else version
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun isZygiskEnabled(): Boolean {
        return try {
            val file = File("/data/adb/zygisk")
            file.exists() && file.readText().trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    private fun isRamdiskLoaded(): Boolean {
        return try {
            val file = File("/proc/cmdline")
            if (file.exists()) {
                val cmdline = file.readText()
                !cmdline.contains("skip_initramfs")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getMagiskConfig(): Map<String, Any> {
        return try {
            mapOf(
                "version" to getMagiskVersion(),
                "isRooted" to checkRootAccess(),
                "isZygiskEnabled" to isZygiskEnabled(),
                "isRamdiskLoaded" to isRamdiskLoaded(),
                "hasMagisk" to File("/data/adb/magisk").exists(),
                "isSuDaemonActive" to isSuDaemonActive()
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun isSuDaemonActive(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A | grep magiskd"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            output.contains("magiskd")
        } catch (e: Exception) {
            false
        }
    }

    private fun getDenyList(): List<String> {
        val denyList = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist ls"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    if (it.isNotEmpty() && !it.contains("denylist")) {
                        denyList.add(it.trim())
                    }
                }
            }
        } catch (e: Exception) {
            try {
                val dbFile = File("/data/adb/magisk.db")
                if (dbFile.exists()) {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db 'SELECT package_name FROM denylist'"))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { if (it.isNotEmpty()) denyList.add(it.trim()) }
                    }
                }
            } catch (e2: Exception) {}
        }
        return denyList
    }

    private fun addToDenyList(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist add $packageName"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun removeFromDenyList(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist rm $packageName"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isInDenyList(packageName: String): Boolean {
        return getDenyList().contains(packageName)
    }

    private fun installMagisk(bootImage: String): Boolean {
        return try {
            if (bootImage.isNotEmpty()) {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magiskboot unpack $bootImage"))
                process.waitFor()
                val process2 = Runtime.getRuntime().exec(arrayOf("su", "-c", "magiskboot patch ramdisk.cpio"))
                process2.waitFor()
                val process3 = Runtime.getRuntime().exec(arrayOf("su", "-c", "magiskboot repack $bootImage"))
                process3.waitFor()
                true
            } else {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --install"))
                process.waitFor()
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun uninstallMagisk(restoreImages: Boolean): Boolean {
        return try {
            val cmd = if (restoreImages) {
                arrayOf("su", "-c", "magisk --restore-images")
            } else {
                arrayOf("su", "-c", "magisk --uninstall")
            }
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun patchBootImage(bootImage: String): String? {
        return try {
            if (bootImage.isEmpty()) return null
            val outputFile = bootImage.replace(".img", "_patched.img")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magiskboot patch $bootImage $outputFile"))
            process.waitFor()
            if (process.exitValue() == 0) outputFile else null
        } catch (e: Exception) {
            null
        }
    }

    private fun updateMagiskManager(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r /data/local/tmp/MagiskManager.apk"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getLatestVersion(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("curl", "-s", "https://api.github.com/repos/topjohnwu/magisk/releases/latest"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val response = reader.readText()
            val versionMatch = Regex("\"tag_name\":\\s*\"([^\"]+)\"").find(response)
            versionMatch?.groupValues?.get(1) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getDeviceInfo(): Map<String, Any> {
        return try {
            mapOf(
                "androidVersion" to android.os.Build.VERSION.RELEASE,
                "sdkVersion" to android.os.Build.VERSION.SDK_INT,
                "device" to android.os.Build.DEVICE,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "model" to android.os.Build.MODEL,
                "isRooted" to checkRootAccess(),
                "hasMagisk" to File("/data/adb/magisk").exists()
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun rebootDevice() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
        } catch (e: Exception) {
            try {
                Runtime.getRuntime().exec("reboot")
            } catch (e2: Exception) {}
        }
    }

    private fun openMagiskSettings() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")
            if (intent != null) {
                startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.topjohnwu.magisk")
                }
                startActivity(intent)
            } catch (e2: Exception) {}
        }
    }
}
