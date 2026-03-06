package com.example.arm64

import android.os.Handler
import android.os.Looper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : FlutterActivity() {
    private val CHANNEL = "magisk_manager/data"
    private val MAGISK_CHANNEL = "magisk_manager/magisk"
    private val LOGS_CHANNEL = "magisk_manager/logs"
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getModules" -> {
                    val modules = getModulesListSafe()
                    result.success(modules)
                }
                "getApps" -> {
                    val apps = getAppsListSafe()
                    result.success(apps)
                }
                "getMagiskVersion" -> {
                    result.success(getMagiskVersionSafe())
                }
                "isRooted" -> {
                    result.success(isRootedSafe())
                }
                "isZygiskEnabled" -> {
                    result.success(isZygiskEnabledSafe())
                }
                "isRamdiskLoaded" -> {
                    result.success(isRamdiskLoadedSafe())
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, MAGISK_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "installMagisk" -> {
                    val bootImage = call.argument<String>("bootImage")
                    val success = installMagisk(bootImage ?: "")
                    result.success(success)
                }
                "uninstallMagisk" -> {
                    val restoreImages = call.argument<Boolean>("restoreImages") ?: true
                    val success = uninstallMagisk(restoreImages)
                    result.success(success)
                }
                "patchBootImage" -> {
                    val bootImage = call.argument<String>("bootImage")
                    val patchedPath = patchBootImage(bootImage ?: "")
                    result.success(patchedPath)
                }
                "updateManager" -> {
                    result.success(updateMagiskManager())
                }
                "getLatestVersion" -> {
                    result.success(getLatestMagiskVersion())
                }
                "getDeviceInfo" -> {
                    result.success(getDeviceInfo())
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
                            val process = Runtime.getRuntime().exec("logcat -d -v time *:W -n 30")
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
                                Thread.sleep(300)
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

    private fun getLatestMagiskVersion(): String {
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
                "isRooted" to isRootedSafe(),
                "hasMagisk" to File("/data/adb/magisk").exists()
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun getModulesListSafe(): List<String> {
        return try {
            val modulesDir = File("/data/adb/modules")
            if (modulesDir.exists() && modulesDir.isDirectory) {
                modulesDir.listFiles()
                    ?.filter { it.isDirectory && it.name != ".core" }
                    ?.map { it.name }
                    ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getAppsListSafe(): List<String> {
        return try {
            val dbFile = File("/data/adb/magisk.db")
            if (dbFile.exists()) {
                listOf("SuperUser App", "Magisk Manager")
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getMagiskVersionSafe(): String {
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

    private fun isRootedSafe(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun isZygiskEnabledSafe(): Boolean {
        return try {
            val file = File("/data/adb/zygisk")
            file.exists() && file.readText().trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    private fun isRamdiskLoadedSafe(): Boolean {
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
}
