package com.magiskube.magisk

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import java.io.DataOutputStream
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

class MainActivity : FlutterActivity() {
    private var rootAccessGranted = false
    
    // Static reference to EventSink for sending logs from anywhere
    companion object {
        var logEventSink: EventChannel.EventSink? = null
            private set
        
        private var isLogStreamReady = false
        private var logcatProcess: Process? = null
        private var uiHandler: Handler? = null
        
        fun sendLog(log: String) {
            if (logEventSink != null && isLogStreamReady) {
                logEventSink?.success(log)
            } else {
                // Buffer the log for later sending when stream is ready
                bufferedLogs.add(log)
            }
        }
        
        // Buffer for logs sent before stream is ready
        private val bufferedLogs = mutableListOf<String>()
        
        fun flushBufferedLogs() {
            if (logEventSink != null && bufferedLogs.isNotEmpty()) {
                for (bufferedLog in bufferedLogs) {
                    logEventSink?.success(bufferedLog)
                }
                bufferedLogs.clear()
            }
        }
        
        fun startMagiskLogcat(handler: Handler) {
            uiHandler = handler
            try {
                // Start logcat process to capture Magisk-related logs
                logcatProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time", "*:V"))
                val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream))
                
                Thread {
                    try {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val log = line!!
                            // Filter and send logs
                            uiHandler?.post {
                                sendLog(log)
                            }
                        }
                    } catch (e: Exception) {
                        uiHandler?.post {
                            sendLog("[ERROR] Logcat reader error: ${e.message}")
                        }
                    }
                }.start()
            } catch (e: Exception) {
                sendLog("[ERROR] Failed to start logcat: ${e.message}")
            }
        }
        
        fun stopMagiskLogcat() {
            logcatProcess?.destroy()
            logcatProcess = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get root access status from MagiskApplication
        rootAccessGranted = MagiskApplication.isRootAvailable
    }
    private val CHANNEL = "magisk_manager/data"
    private val MAGISK_CHANNEL = "magisk_manager/magisk"
    private val DENYLIST_CHANNEL = "magisk_manager/denylist"
    private val LOGS_CHANNEL = "magisk_manager/logs"
    private val FILEPICKER_CHANNEL = "magisk_manager/filepicker"
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
                "setZygiskEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    result.success(setZygiskEnabled(enabled))
                }
                "setDenyListEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    result.success(setDenyListEnabled(enabled))
                }
                "isDenyListEnabled" -> result.success(isDenyListEnabled())
                "getAppActivities" -> {
                    val packageName = call.argument<String>("packageName") ?: ""
                    result.success(getAppActivities(packageName))
                }
                "addToDenyListActivity" -> {
                    val activityName = call.argument<String>("activityName") ?: ""
                    result.success(addToDenyListActivity(activityName))
                }
                "removeFromDenyListActivity" -> {
                    val activityName = call.argument<String>("activityName") ?: ""
                    result.success(removeFromDenyListActivity(activityName))
                }
                "isInDenyListActivity" -> {
                    val activityName = call.argument<String>("activityName") ?: ""
                    result.success(isInDenyListActivity(activityName))
                }
                "getMagiskConfig" -> result.success(getMagiskConfig())
                else -> result.notImplemented()
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, MAGISK_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "installMagisk" -> {
                    val bootImage = call.argument<String>("bootImage")
                    val isPatchMode = call.argument<Boolean>("isPatchMode") ?: false
                    result.success(installMagisk(bootImage ?: "", isPatchMode))
                }
                "uninstallMagisk" -> {
                    val restoreImages = call.argument<Boolean>("restoreImages") ?: true
                    result.success(uninstallMagisk(restoreImages))
                }
                "patchBootImage" -> {
                    val bootImage = call.argument<String>("bootImage")
                    result.success(patchBootImage(bootImage ?: ""))
                }
                "otaSlotSwitch" -> {
                    result.success(otaSlotSwitch())
                }
                "restoreMagiskAfterOta" -> {
                    result.success(restoreMagiskAfterOta())
                }
                "installAddonDScript" -> {
                    result.success(installAddonDScript())
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
                "grantRootAccess" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(grantRootAccess(packageName ?: ""))
                }
                "revokeRootAccess" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(revokeRootAccess(packageName ?: ""))
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, LOGS_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                private var eventSink: EventChannel.EventSink? = null

                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    // Set the static reference for use in other methods
                    logEventSink = events
                    // Mark stream as ready
                    isLogStreamReady = true
                    
                    // Start Magisk logcat stream
                    startMagiskLogcat(uiHandler)
                    
                    // Send initial log message
                    uiHandler.post {
                        events?.success("[INFO] Magisk log stream started")
                        // Flush any buffered logs
                        flushBufferedLogs()
                    }
                }

                override fun onCancel(arguments: Any?) {
                    // Stop Magisk logcat stream
                    stopMagiskLogcat()
                    eventSink = null
                    logEventSink = null
                    isLogStreamReady = false
                }
            }
        )

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, FILEPICKER_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "pickFile" -> {
                    pickFile(result)
                }
                "saveLogToFile" -> {
                    val logContent = call.argument<String>("logContent") ?: ""
                    val filename = call.argument<String>("filename") ?: "logs.txt"
                    result.success(saveLogToFile(logContent, filename))
                }
                else -> result.notImplemented()
                    }
                }
            }
        
            /**
             * Copy a file from assets to a destination path using root shell.
             * This function reads the asset from the app's assets directory and writes it to the specified location.
             */
            private fun copyAssetToFile(assetName: String, destPath: String): Boolean {
                return try {
                    // Read asset content
                    val inputStream = assets.open(assetName)
                    val content = inputStream.readBytes()
                    inputStream.close()
                    
                    // Write to destination using cat command
                    val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $destPath"))
                    val outputStream = writeProcess.outputStream
                    outputStream.write(content)
                    outputStream.close()
                    writeProcess.waitFor()
                    
                    if (writeProcess.exitValue() != 0) {
                        return false
                    }
                    
                    // Make file executable
                    val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $destPath"))
                    chmodProcess.waitFor()
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }
        
            /**
             * Copy multiple files from assets to a destination directory.
             */
            private fun copyAssetsToDirectory(assetFiles: List<String>, destDir: String): Boolean {
                return try {
                    // Create destination directory
                    val mkdirProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $destDir"))
                    mkdirProcess.waitFor()
                    if (mkdirProcess.exitValue() != 0) {
                        return false
                    }
                    
                    // Copy each file
                    for (assetName in assetFiles) {
                        val destPath = "$destDir/$assetName"
                        if (!copyAssetToFile(assetName, destPath)) {
                            return false
                        }
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }
        
            private fun getModulesList(): List<Map<String, Any>> {
        val modules = mutableListOf<Map<String, Any>>()
        try {
            // Use root shell to list modules directory
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /data/adb/modules"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            
            if (process.exitValue() == 0) {
                val lines = output.toString().split("\n").filter { it.trim().isNotEmpty() }
                
                for (moduleName in lines) {
                    val name = moduleName.trim()
                    if (name.isEmpty() || name == ".core" || name == ".") continue
                    
                    // Get module info using root shell
                    val moduleInfo = getModuleInfo(name)
                    modules.add(moduleInfo)
                }
            } else {
                // Fallback: try direct file access
                val modulesDir = File("/data/adb/modules")
                if (modulesDir.exists() && modulesDir.isDirectory) {
                    modulesDir.listFiles()?.filter { it.isDirectory && it.name != ".core" }?.forEach { moduleDir ->
                        val moduleInfo = getModuleInfoWithoutRoot(moduleDir)
                        modules.add(moduleInfo)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: try direct file access
            try {
                val modulesDir = File("/data/adb/modules")
                if (modulesDir.exists() && modulesDir.isDirectory) {
                    modulesDir.listFiles()?.filter { it.isDirectory && it.name != ".core" }?.forEach { moduleDir ->
                        val moduleInfo = getModuleInfoWithoutRoot(moduleDir)
                        modules.add(moduleInfo)
                    }
                }
            } catch (e2: Exception) {
                val errorMsg: String = e.message?.toString() ?: "Error accessing modules"
                val errorModule: Map<String, Any> = LinkedHashMap<String, Any>().apply {
                    put("name", "Error")
                    put("version", "Error")
                    put("author", errorMsg)
                    put("description", "")
                    put("isEnabled", false)
                    put("path", "")
                }
                modules.add(errorModule)
            }
        }
        return modules
    }

    private fun getModuleInfo(moduleName: String): Map<String, Any> {
        try {
            // Read module.prop using root shell (Magisk modules use .prop files, not .json)
            val propProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/adb/modules/$moduleName/module.prop"))
            val propReader = BufferedReader(InputStreamReader(propProcess.inputStream))
            val propOutput = StringBuilder()
            var line: String?
            
            while (propReader.readLine().also { line = it } != null) {
                propOutput.append(line).append("\n")
            }
            
            propProcess.waitFor()
            
            var name = moduleName
            var version = "Unknown"
            var author = "Unknown"
            var description = ""
            
            if (propProcess.exitValue() == 0 && propOutput.isNotEmpty()) {
                val propContent = propOutput.toString()
                // Parse module.prop key-value pairs
                val nameMatch = Regex("name\\s*=\\s*([^\\n]+)").find(propContent)
                val versionMatch = Regex("version\\s*=\\s*([^\\n]+)").find(propContent)
                val authorMatch = Regex("author\\s*=\\s*([^\\n]+)").find(propContent)
                val descMatch = Regex("description\\s*=\\s*([^\\n]+)").find(propContent)
                
                name = nameMatch?.groupValues?.get(1)?.trim() ?: moduleName
                version = versionMatch?.groupValues?.get(1)?.trim() ?: "Unknown"
                author = authorMatch?.groupValues?.get(1)?.trim() ?: "Unknown"
                description = descMatch?.groupValues?.get(1)?.trim() ?: ""
            }
            
            // Check if module is enabled using root shell
            val disableProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "[ -f /data/adb/modules/$moduleName/disable ] && echo 'disabled' || echo 'enabled'"))
            val disableReader = BufferedReader(InputStreamReader(disableProcess.inputStream))
            val disableStatus = disableReader.readLine()
            val isEnabled = disableStatus?.trim() != "disabled"
            
            return mapOf<String, Any>(
                "name" to name,
                "version" to version,
                "author" to author,
                "description" to description,
                "isEnabled" to isEnabled,
                "path" to "/data/adb/modules/$moduleName"
            )
        } catch (e: Exception) {
            val errorMsg: String = e.message?.toString() ?: "Error reading module info"
            return LinkedHashMap<String, Any>().apply {
                put("name", moduleName)
                put("version", "Error")
                put("author", "Error")
                put("description", errorMsg)
                put("isEnabled", false)
                put("path", "/data/adb/modules/$moduleName")
            }
        }
    }

    private fun getModuleInfoWithoutRoot(moduleDir: File): Map<String, Any> {
        var name = moduleDir.name
        var version = "Unknown"
        var author = "Unknown"
        var description = ""
        
        val moduleProp = File(moduleDir, "module.prop")
        if (moduleProp.exists()) {
            try {
                val propContent = moduleProp.readText()
                val nameMatch = Regex("name\\s*=\\s*([^\\n]+)").find(propContent)
                val versionMatch = Regex("version\\s*=\\s*([^\\n]+)").find(propContent)
                val authorMatch = Regex("author\\s*=\\s*([^\\n]+)").find(propContent)
                val descMatch = Regex("description\\s*=\\s*([^\\n]+)").find(propContent)
                
                name = nameMatch?.groupValues?.get(1)?.trim() ?: moduleDir.name
                version = versionMatch?.groupValues?.get(1)?.trim() ?: "Unknown"
                author = authorMatch?.groupValues?.get(1)?.trim() ?: "Unknown"
                description = descMatch?.groupValues?.get(1)?.trim() ?: ""
            } catch (e: Exception) {}
        }
        
        val isEnabled = File(moduleDir, "disable").exists() == false
        
        return mapOf<String, Any>(
            "name" to name,
            "version" to version,
            "author" to author,
            "description" to description,
            "isEnabled" to isEnabled,
            "path" to moduleDir.absolutePath
        )
    }

    private fun getInstalledApps(): List<Map<String, Any>> {
        val apps = mutableListOf<Map<String, Any>>()
        val pm = packageManager
        val denyList = getDenyList()
        val rootAllowedPackages = getRootAllowedPackages()
        
        try {
            // Get all installed packages using PackageManager
            val packages = pm.getInstalledPackages(0)
            
            for (packageInfo in packages) {
                val packageName = packageInfo.packageName
                
                // Skip system packages that we don't want to show
                if (packageName == "android" || packageName == "com.android.systemui") continue
                
                // Get app name from PackageManager
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                } catch (e: Exception) {
                    packageName
                }
                
                // Check if app is in denylist (isActive = false if in denylist)
                val isActive = !denyList.contains(packageName)
                
                // Check if app has root access granted
                val hasRootAccess = rootAllowedPackages.contains(packageName)
                
                apps.add(mapOf<String, Any>(
                    "name" to appName,
                    "packageName" to packageName,
                    "isActive" to isActive,
                    "hasRootAccess" to hasRootAccess
                ))
            }
        } catch (e: Exception) {
            // Fallback: try using pm list packages command
            try {
                val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-3"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val packageName = line?.replace("package:", "")?.trim() ?: continue
                    if (packageName.isNotEmpty()) {
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                        } catch (e2: Exception) {
                            packageName
                        }
                        val isActive = !denyList.contains(packageName)
                        val hasRootAccess = rootAllowedPackages.contains(packageName)
                        apps.add(mapOf<String, Any>(
                            "name" to appName,
                            "packageName" to packageName,
                            "isActive" to isActive,
                            "hasRootAccess" to hasRootAccess
                        ))
                    }
                }
            } catch (e2: Exception) {}
        }
        return apps.sortedBy { it["name"].toString().lowercase() }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            // Method 1: Check if Magisk is properly installed and active
            val magiskDir = File("/data/adb/magisk")
            if (magiskDir.exists() && magiskDir.isDirectory) {
                // Check if core files exist
                val stubFile = File("/data/adb/magisk/stub.apk")
                val utilFile = File("/data/adb/magisk/util_functions.sh")
                if (stubFile.exists() || utilFile.exists()) {
                    return true
                }
            }
            
            // Method 2: Check if su binary exists and works
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            
            if (output != null && output.contains("uid=0")) {
                return true
            }
            
            // Method 3: Check if Magisk binary exists and works
            val magiskCheck = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk -V"))
            val magiskReader = BufferedReader(InputStreamReader(magiskCheck.inputStream))
            val magiskVersion = magiskReader.readLine()
            magiskCheck.waitFor()
            
            if (magiskVersion != null && !magiskVersion.contains("not found") && !magiskVersion.isEmpty()) {
                return true
            }
            
            // Method 4: Check if /sbin/magisk exists (for system-as-root)
            if (File("/sbin/magisk").exists()) {
                return true
            }
            
            false
        } catch (e: Exception) {
            // Fallback: check if Magisk directory exists
            File("/data/adb/magisk").exists() || File("/sbin/magisk").exists()
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
            android.util.Log.d("MainActivity", "isZygiskEnabled: checking status")
            
            // Method 1: Use magisk --sqlite to query zygisk_enabled from settings table (most reliable)
            val magiskConfigFile = File("/data/adb/magisk.db")
            android.util.Log.d("MainActivity", "magisk.db exists: ${magiskConfigFile.exists()}")
            
            if (magiskConfigFile.exists()) {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key = 'zygisk_enabled'\""))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val result = reader.readLine()
                process.waitFor()
                val exitCode = process.exitValue()
                android.util.Log.d("MainActivity", "magisk --sqlite result: $result, exit code: $exitCode")
                
                if (result != null && result.trim() == "1") {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via magisk.db")
                    return true
                }
            }
            
            // Method 2: Check /data/adb/zygisk file (older Magisk versions)
            val zygiskFile = File("/data/adb/zygisk")
            android.util.Log.d("MainActivity", "zygisk file exists: ${zygiskFile.exists()}")
            
            if (zygiskFile.exists()) {
                val content = zygiskFile.readText().trim()
                android.util.Log.d("MainActivity", "zygisk file content: $content")
                if (content == "1") {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via zygisk file")
                    return true
                }
            }
            
            // Method 3: Check if Zygisk modules directory exists and has content
            val zygiskModulesDir = File("/data/adb/zygisk/modules")
            android.util.Log.d("MainActivity", "zygisk/modules dir exists: ${zygiskModulesDir.exists()}")
            
            if (zygiskModulesDir.exists() && zygiskModulesDir.isDirectory) {
                val modules = zygiskModulesDir.listFiles()
                android.util.Log.d("MainActivity", "zygisk modules count: ${modules?.size ?: 0}")
                if (modules != null && modules.isNotEmpty()) {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via modules directory")
                    return true
                }
            }
            
            android.util.Log.d("MainActivity", "isZygiskEnabled: false")
            false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking Zygisk status: ${e.message}", e)
            false
        }
    }

    private fun setZygiskEnabled(enabled: Boolean): Boolean {
        return try {
            android.util.Log.d("MainActivity", "setZygiskEnabled: enabled=$enabled")
            
            // Method 1: Use magisk --sqlite to update zygisk_enabled in settings table
            val magiskConfigFile = File("/data/adb/magisk.db")
            android.util.Log.d("MainActivity", "magisk.db exists: ${magiskConfigFile.exists()}")
            
            if (magiskConfigFile.exists()) {
                // Test if magisk command is available
                val testProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk -v"))
                testProcess.waitFor()
                val magiskVersion = testProcess.inputStream.bufferedReader().readText().trim()
                android.util.Log.d("MainActivity", "Magisk version: $magiskVersion, exit code: ${testProcess.exitValue()}")
                
                // Execute magisk --sqlite command with proper argument separation
                val sqliteCommand = if (enabled) {
                    "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '1')"
                } else {
                    "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '0')"
                }
                
                android.util.Log.d("MainActivity", "Executing: magisk --sqlite \"$sqliteCommand\"")
                
                // Use shell to execute the command properly
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$sqliteCommand\""))
                process.waitFor()
                val exitCode = process.exitValue()
                val errorOutput = process.errorStream.bufferedReader().readText().trim()
                android.util.Log.d("MainActivity", "magisk --sqlite exit code: $exitCode, error: $errorOutput")
                
                if (exitCode == 0) {
                    // Verify the setting was applied
                    val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk_enabled'\""))
                    verifyProcess.waitFor()
                    val verifyResult = verifyProcess.inputStream.bufferedReader().readText().trim()
                    android.util.Log.d("MainActivity", "Verification result: $verifyResult")
                    
                    // Restart Magisk daemon to apply changes
                    try {
                        android.util.Log.d("MainActivity", "Restarting Magisk daemon...")
                        val restartProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "killall magiskd"))
                        restartProcess.waitFor()
                        android.util.Log.d("MainActivity", "Magisk daemon restart exit code: ${restartProcess.exitValue()}")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to restart Magisk daemon: ${e.message}")
                    }
                    android.util.Log.d("MainActivity", "setZygiskEnabled: SUCCESS via magisk --sqlite")
                    return true
                } else {
                    android.util.Log.e("MainActivity", "magisk --sqlite failed with exit code $exitCode")
                }
            } else {
                android.util.Log.d("MainActivity", "magisk.db not found, trying fallback method")
            }
            
            // Method 2: Update /data/adb/zygisk file (older Magisk versions)
            val zygiskFile = File("/data/adb/zygisk")
            android.util.Log.d("MainActivity", "zygisk file exists: ${zygiskFile.exists()}")
            
            if (zygiskFile.exists()) {
                android.util.Log.d("MainActivity", "Writing ${if (enabled) "1" else "0"} to /data/adb/zygisk")
                val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ${if (enabled) "1" else "0"} > /data/adb/zygisk"))
                writeProcess.waitFor()
                val writeExitCode = writeProcess.exitValue()
                android.util.Log.d("MainActivity", "Write to zygisk file exit code: $writeExitCode")
                
                if (writeExitCode == 0) {
                    android.util.Log.d("MainActivity", "setZygiskEnabled: SUCCESS via zygisk file")
                    return true
                }
            } else {
                // Method 3: Create zygisk config file if it doesn't exist
                android.util.Log.d("MainActivity", "Creating /data/adb/zygisk config file")
                val createProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo ${if (enabled) "1" else "0"} > /data/adb/zygisk"))
                createProcess.waitFor()
                if (createProcess.exitValue() == 0) {
                    android.util.Log.d("MainActivity", "setZygiskEnabled: SUCCESS via creating zygisk file")
                    return true
                }
            }
            
            android.util.Log.e("MainActivity", "setZygiskEnabled: FAILED - all methods failed")
            false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting Zygisk: ${e.message}", e)
            false
        }
    }

    private fun isDenyListEnabled(): Boolean {
        return try {
            // Check if DenyList is enabled by checking if the denylist table exists or has entries
            val magiskConfigFile = File("/data/adb/magisk.db")
            if (magiskConfigFile.exists()) {
                // Use magisk --sqlite to check if denylist is enabled
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key = 'denylist'\""))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val result = reader.readLine()
                process.waitFor()
                if (result != null && result.trim() == "1") {
                    return true
                }
                
                // Fallback: check if denylist table has any entries
                val denylistProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT COUNT(*) FROM denylist\""))
                val denylistReader = BufferedReader(InputStreamReader(denylistProcess.inputStream))
                val countResult = denylistReader.readLine()
                denylistProcess.waitFor()
                if (countResult != null && countResult.trim().toIntOrNull() ?: 0 > 0) {
                    return true
                }
            }
            
            // Check if /data/adb/denylist file exists (older versions)
            val denylistFile = File("/data/adb/denylist")
            if (denylistFile.exists()) {
                return true
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun setDenyListEnabled(enabled: Boolean): Boolean {
        return try {
            // Method 1: Update Magisk config in database (newer Magisk versions)
            val magiskConfigFile = File("/data/adb/magisk.db")
            if (magiskConfigFile.exists()) {
                // Use magisk --sqlite to update denylist setting in settings table
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"INSERT OR REPLACE INTO settings (key, value) VALUES ('denylist', '${if (enabled) "1" else "0"}')\""))
                process.waitFor()
                if (process.exitValue() == 0) {
                    // Restart Magisk daemon to apply changes
                    val restartProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "killall magiskd"))
                    restartProcess.waitFor()
                    return true
                }
            }
            
            // Method 2: Create/remove /data/adb/denylist file (older versions)
            if (enabled) {
                val createProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "touch /data/adb/denylist"))
                createProcess.waitFor()
                return createProcess.exitValue() == 0
            } else {
                val removeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f /data/adb/denylist"))
                removeProcess.waitFor()
                return removeProcess.exitValue() == 0
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun getAppActivities(packageName: String): List<String> {
        return try {
            if (packageName.isEmpty()) return emptyList()
            
            // Get all activities for the package using pm command
            val process = Runtime.getRuntime().exec(arrayOf("pm", "dump", packageName))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val activities = mutableListOf<String>()
            var line: String?
            var inActivitiesSection = false
            
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("activities:") == true) {
                    inActivitiesSection = true
                    continue
                }
                if (inActivitiesSection && line?.contains("ActivityRecord") == true) {
                    // Extract activity name from ActivityRecord
                    val activityMatch = Regex("ActivityRecord\\{[^}]+ ([^\\s]+) [^}]+\\}").find(line)
                    if (activityMatch != null) {
                        activities.add(activityMatch.groupValues[1])
                    }
                }
                if (inActivitiesSection && line?.isBlank() == true) {
                    // End of activities section
                    break
                }
            }
            process.waitFor()
            
            if (activities.isNotEmpty()) {
                return activities
            }
            
            // Fallback: use dumpsys activity
            val dumpsysProcess = Runtime.getRuntime().exec(arrayOf("dumpsys", "activity", packageName))
            val dumpsysReader = BufferedReader(InputStreamReader(dumpsysProcess.inputStream))
            val dumpsysActivities = mutableListOf<String>()
            var dumpsysLine: String?
            
            while (dumpsysReader.readLine().also { dumpsysLine = it } != null) {
                if (dumpsysLine?.contains("ActivityRecord") == true) {
                    val activityMatch = Regex("ActivityRecord\\{[^}]+ ([^\\s]+) [^}]+\\}").find(dumpsysLine)
                    if (activityMatch != null) {
                        dumpsysActivities.add(activityMatch.groupValues[1])
                    }
                }
            }
            dumpsysProcess.waitFor()
            
            if (dumpsysActivities.isNotEmpty()) {
                return dumpsysActivities
            }
            
            // Fallback: get package info and extract main activity
            val pm = packageManager
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            if (packageInfo.activities != null) {
                for (activity in packageInfo.activities) {
                    activities.add(activity.name)
                }
            }
            
            activities
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun addToDenyListActivity(activityName: String): Boolean {
        if (activityName.isEmpty()) return false
        return try {
            // Add activity to denylist using magisk command
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist add $activityName"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun removeFromDenyListActivity(activityName: String): Boolean {
        if (activityName.isEmpty()) return false
        return try {
            // Remove activity from denylist using magisk command
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist rm $activityName"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isInDenyListActivity(activityName: String): Boolean {
        return try {
            val denyList = getDenyList()
            denyList.contains(activityName)
        } catch (e: Exception) {
            false
        }
    }

    private fun isRamdiskLoaded(): Boolean {
        return try {
            // Method 1: Check if Magisk daemon is running (most reliable indicator)
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A | grep magiskd"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            if (output.contains("magiskd")) {
                return true
            }
            
            // Method 2: Check if Magisk is properly installed and active
            val magiskDir = File("/data/adb/magisk")
            if (magiskDir.exists() && magiskDir.isDirectory) {
                // Check if core files exist
                val stubFile = File("/data/adb/magisk/stub.apk")
                val utilFile = File("/data/adb/magisk/util_functions.sh")
                if (stubFile.exists() || utilFile.exists()) {
                    return true
                }
            }
            
            // Method 3: Check /proc/cmdline for skip_initramfs (for older Magisk versions)
            val cmdlineFile = File("/proc/cmdline")
            if (cmdlineFile.exists()) {
                val cmdline = cmdlineFile.readText()
                if (!cmdline.contains("skip_initramfs")) {
                    return true
                }
            }
            
            // Method 4: Check for Magisk boot image backup directory
            val backupDir = File("/data/adb/boot-backup")
            if (backupDir.exists() && backupDir.isDirectory) {
                val backupFiles = backupDir.listFiles()
                if (backupFiles != null && backupFiles.isNotEmpty()) {
                    return true
                }
            }
            
            // Method 5: Check if /sbin/magisk exists (system-as-root with Magisk)
            if (File("/sbin/magisk").exists()) {
                return true
            }
            
            // Method 6: Check Magisk version command (if it returns a valid version, Magisk is loaded)
            val magiskVersionProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk -V"))
            val magiskVersionReader = BufferedReader(InputStreamReader(magiskVersionProcess.inputStream))
            val magiskVersion = magiskVersionReader.readLine()
            magiskVersionProcess.waitFor()
            if (magiskVersion != null && !magiskVersion.contains("not found") && !magiskVersion.isEmpty()) {
                return true
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun getMagiskConfig(): Map<String, Any> {
        return try {
            mapOf<String, Any>(
                "version" to getMagiskVersion(),
                "isRooted" to checkRootAccess(),
                "isZygiskEnabled" to isZygiskEnabled(),
                "isRamdiskLoaded" to isRamdiskLoaded(),
                "hasMagisk" to File("/data/adb/magisk").exists(),
                "isSuDaemonActive" to isSuDaemonActive()
            )
        } catch (e: Exception) {
            emptyMap<String, Any>() 
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

    private fun grantRootAccess(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            // Use Magisk's built-in SU policy management
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --su add $packageName"))
            process.waitFor()
            if (process.exitValue() == 0) {
                return true
            }
            
            // Fallback: Use magisk --sqlite command for newer Magisk versions
            val sqliteProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"INSERT OR REPLACE INTO policies (package_name, policy, until) VALUES ('${packageName}', 2, 0)\""))
            sqliteProcess.waitFor()
            if (sqliteProcess.exitValue() == 0) {
                return true
            }
            
            // Fallback: Direct database manipulation for older Magisk versions
            val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db 'INSERT OR REPLACE INTO policies (package_name, policy, until) VALUES (\"$packageName\", 2, 0)'"))
            dbProcess.waitFor()
            dbProcess.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun revokeRootAccess(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            // Use Magisk's built-in SU policy management
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --su remove $packageName"))
            process.waitFor()
            if (process.exitValue() == 0) {
                return true
            }
            
            // Fallback: Use magisk --sqlite command for newer Magisk versions
            val sqliteProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"DELETE FROM policies WHERE package_name = '${packageName}'\""))
            sqliteProcess.waitFor()
            if (sqliteProcess.exitValue() == 0) {
                return true
            }
            
            // Fallback: Direct database manipulation for older Magisk versions
            val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db 'DELETE FROM policies WHERE package_name = \"$packageName\"'"))
            dbProcess.waitFor()
            dbProcess.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getRootAllowedPackages(): List<String> {
        return try {
            // Method 1: Use magisk --sqlite to query package from policies table where policy > 0
            // This is the most reliable method for getting root-allowed packages
            val magiskDbFile = File("/data/adb/magisk.db")
            if (magiskDbFile.exists()) {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT package FROM policies WHERE policy > 0\""))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val allowedPackages = mutableListOf<String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val pkg = line?.trim()
                    if (!pkg.isNullOrEmpty() && !pkg.contains("package")) {
                        allowedPackages.add(pkg)
                    }
                }
                process.waitFor()
                
                if (allowedPackages.isNotEmpty()) {
                    android.util.Log.d("MainActivity", "Found ${allowedPackages.size} root-allowed packages via magisk --sqlite")
                    return allowedPackages
                }
            }
            
            // Method 2: Fallback to direct sqlite3 command
            val sqliteProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db 'SELECT package FROM policies WHERE policy > 0'"))
            val sqliteReader = BufferedReader(InputStreamReader(sqliteProcess.inputStream))
            val sqlitePackages = mutableListOf<String>()
            var sqliteLine: String?
            while (sqliteReader.readLine().also { sqliteLine = it } != null) {
                if (!sqliteLine.isNullOrEmpty()) {
                    sqlitePackages.add(sqliteLine.trim())
                }
            }
            sqliteProcess.waitFor()
            
            if (sqlitePackages.isNotEmpty()) {
                return sqlitePackages
            }
            
            // Method 3: Fallback to check magisk --su ls command (for newer Magisk versions)
            val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --su ls"))
            val suReader = BufferedReader(InputStreamReader(suProcess.inputStream))
            val suPackages = mutableListOf<String>()
            var suLine: String?
            while (suReader.readLine().also { suLine = it } != null) {
                if (!suLine.isNullOrEmpty() && !suLine.contains("allow") && !suLine.contains("deny")) {
                    // Parse the output format: "package_name allow/deny"
                    val parts = suLine.trim().split("\\s+".toRegex())
                    if (parts.size >= 2 && parts[1] == "allow") {
                        suPackages.add(parts[0])
                    }
                }
            }
            suProcess.waitFor()
            
            if (suPackages.isNotEmpty()) {
                return suPackages
            }
            
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting root allowed packages: ${e.message}")
            emptyList()
        }
    }

    private fun installMagisk(bootImage: String, isPatchMode: Boolean): Boolean {
        return try {
            // Send log at the start of operation
            sendLog("[INFO] Starting Magisk installation (patchMode=$isPatchMode, bootImage=$bootImage)")
            
            // Check if device is rooted and has root access
            if (!checkRootAccess()) {
                sendLog("[ERROR] Root access not available")
                return false
            }
            sendLog("[INFO] Root access confirmed")
            
            // Create temporary directory for Magisk files
            val tmpDir = "/data/local/tmp/magisk_install"
            sendLog("[INFO] Created temp directory: $tmpDir")
            val processMkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $tmpDir"))
            processMkdir.waitFor()
            
            var actualBootImage = bootImage
            
            if (actualBootImage.isEmpty()) {
                // Find boot image automatically (only for install mode, not patch mode)
                if (isPatchMode) {
                    // Patch mode requires a specific boot image file
                    sendLog("[ERROR] Patch mode requires a boot image file")
                    return false
                }
                
                actualBootImage = findBootImage()
                if (actualBootImage.isEmpty()) {
                    sendLog("[ERROR] Unable to find boot image")
                    return false
                }
                sendLog("[INFO] Found boot image: $actualBootImage")
            }
            
            // Copy boot image to tmp directory
            sendLog("[INFO] Copying boot image to temp directory")
            val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $actualBootImage $tmpDir/boot.img"))
            processCp.waitFor()
            if (processCp.exitValue() != 0) {
                sendLog("[ERROR] Failed to copy boot image")
                return false
            }
            
            // Copy necessary Magisk files from assets
            sendLog("[INFO] Copying Magisk files from assets")
            val magiskFiles = listOf("magiskinit", "magisk", "magiskboot", "stub.apk", "util_functions.sh", "boot_patch.sh")
            var copySuccess = true
            for (file in magiskFiles) {
                val destPath = "$tmpDir/$file"
                if (!copyAssetToFile(file, destPath)) {
                    sendLog("[ERROR] Failed to copy $file from assets")
                    copySuccess = false
                    break
                }
            }
            
            if (!copySuccess) {
                sendLog("[ERROR] Failed to copy required files from assets")
                return false
            }
            
            // Make all files executable
            sendLog("[INFO] Making files executable")
            val chmodAllProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpDir/*"))
            chmodAllProcess.waitFor()
            
            // Set up environment variables and run boot_patch.sh
            // The script uses BOOTMODE, KEEPVERITY, KEEPFORCEENCRYPT, etc.
            sendLog("[INFO] Executing boot_patch.sh script")
            
            // Create a wrapper script that sets up environment and sources boot_patch.sh
            val wrapperScript = """
                #!/system/bin/sh
                export BOOTMODE=true
                export TMPDIR="$tmpDir"
                export MAGISKBIN="$tmpDir"
                export KEEPVERITY=false
                export KEEPFORCEENCRYPT=false
                export PATCHVBMETAFLAG=false
                export RECOVERYMODE=false
                export LEGACYSAR=false
                
                cd "$tmpDir"
                
                # Redirect script output to log
                exec > >(while read line; do echo "[SCRIPT] ${'$'}line"; done) 2>&1
                
                . ./util_functions.sh
                . ./boot_patch.sh boot.img
                
                echo "[SCRIPT] Boot patch completed"
            """.trimIndent()
            
            // Write wrapper script
            val wrapperPath = "$tmpDir/install_wrapper.sh"
            val writeWrapper = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $wrapperPath"))
            val wrapperOutput = writeWrapper.outputStream
            wrapperOutput.write(wrapperScript.toByteArray())
            wrapperOutput.close()
            writeWrapper.waitFor()
            
            // Make wrapper executable and run it
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $wrapperPath")).waitFor()
            
            // Use ProcessBuilder for better control over process execution
            sendLog("[INFO] Executing wrapper script: $wrapperPath")
            val patchProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $wrapperPath"))
            
            // Read script output and send to log - read both stdout and stderr
            val scriptReader = BufferedReader(InputStreamReader(patchProcess.inputStream))
            val errorReader = BufferedReader(InputStreamReader(patchProcess.errorStream))
            
            // Read stdout
            var scriptLine: String?
            while (scriptReader.readLine().also { scriptLine = it } != null) {
                sendLog(scriptLine!!)
            }
            
            // Read stderr
            var errorLine: String?
            while (errorReader.readLine().also { errorLine = it } != null) {
                sendLog("[STDERR] $errorLine")
            }
            
            patchProcess.waitFor()
            
            if (patchProcess.exitValue() != 0) {
                sendLog("[ERROR] boot_patch.sh failed with exit code: ${patchProcess.exitValue()}")
                return false
            }
            
            sendLog("[INFO] Boot image patched successfully")
            
            // Verify that new-boot.img was created
            if (!File("$tmpDir/new-boot.img").exists()) {
                sendLog("[ERROR] new-boot.img was not created")
                return false
            }
            
            if (isPatchMode) {
                // For patch mode, just copy the patched image to a new location
                val outputFile = "/storage/emulated/0/Download/magisk_patched_${System.currentTimeMillis()}.img"
                sendLog("[INFO] Copying patched image to: $outputFile")
                val processCopyOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $tmpDir/new-boot.img $outputFile"))
                processCopyOut.waitFor()
                if (processCopyOut.exitValue() == 0) {
                    sendLog("[INFO] Patched image saved successfully")
                    true
                } else {
                    sendLog("[ERROR] Failed to copy patched image")
                    false
                }
            } else {
                // For install mode, flash the patched image
                sendLog("[INFO] Flashing patched image to: $actualBootImage")
                val processFlash = Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$tmpDir/new-boot.img of=$actualBootImage"))
                processFlash.waitFor()
                val flashSuccess = processFlash.exitValue() == 0
                
                if (flashSuccess) {
                    sendLog("[INFO] Boot image flashed successfully")
                    // Backup original image
                    val backupDir = "/data/adb/boot-backup"
                    val backupProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $backupDir"))
                    backupProcess.waitFor()
                    
                    val slotSuffix = getSlotSuffix()
                    val backupName = if (slotSuffix.isNotEmpty()) "boot$slotSuffix" else "boot"
                    val backupPath = "$backupDir/$backupName"
                    
                    val backupCopyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $actualBootImage $backupPath"))
                    backupCopyProcess.waitFor()
                    sendLog("[INFO] Boot image backed up to: $backupPath")
                } else {
                    sendLog("[ERROR] Failed to flash boot image")
                }
                
                flashSuccess
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Exception during installation: ${e.message}")
            false
        }
    }

    private fun findMagiskboot(): String {
        val possibleLocations = listOf(
            "/data/adb/magisk/magiskboot",
            "/sbin/magiskboot",
            "/system/bin/magiskboot",
            "/system/xbin/magiskboot",
            "/cache/magisk/magiskboot"
        )
        
        for (location in possibleLocations) {
            if (File(location).exists()) {
                return location
            }
        }
        return ""
    }

    private fun getSlotSuffix(): String {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim() ?: ""
            process.waitFor()
            return result
        } catch (e: Exception) {
            return ""
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
            mapOf<String, Any>(
                "androidVersion" to android.os.Build.VERSION.RELEASE,
                "sdkVersion" to android.os.Build.VERSION.SDK_INT,
                "device" to android.os.Build.DEVICE,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "model" to android.os.Build.MODEL,
                "isRooted" to checkRootAccess(),
                "hasMagisk" to File("/data/adb/magisk").exists()
            )
        } catch (e: Exception) {
            emptyMap<String, Any>()
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

    private fun findBootImage(): String {
        try {
            // Get current slot suffix
            val slotSuffixProcess = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix"))
            val slotReader = BufferedReader(InputStreamReader(slotSuffixProcess.inputStream))
            val slotSuffix = slotReader.readLine()?.trim() ?: ""
            
            // First, check if this is a GKI 13+ device by checking kernel version
            // GKI 13+ devices use init_boot partition for root
            val kernelVersionProcess = Runtime.getRuntime().exec(arrayOf("uname", "-r"))
            val kernelVersionReader = BufferedReader(InputStreamReader(kernelVersionProcess.inputStream))
            val kernelVersion = kernelVersionReader.readLine()?.trim() ?: ""
            
            var isGki13Plus = false
            if (kernelVersion.isNotEmpty()) {
                try {
                    // Parse kernel version (e.g., "5.10.107-android13-01234-g1234567890ab")
                    val majorVersion = kernelVersion.split(".")[0].toIntOrNull()
                    if (majorVersion != null && majorVersion >= 5) {
                        // Check for Android 13+ in kernel version string
                        if (kernelVersion.contains("android13") || kernelVersion.contains("android14") || 
                            kernelVersion.contains("android15") || kernelVersion.contains("gki-13") || 
                            kernelVersion.contains("gki-14") || kernelVersion.contains("gki-15")) {
                            isGki13Plus = true
                        } else if (majorVersion > 5 || (majorVersion == 5 && kernelVersion.split(".")[1].toIntOrNull() ?: 0 >= 10)) {
                            // Kernel 5.10+ is typically GKI 13+
                            isGki13Plus = true
                        }
                    }
                } catch (e: Exception) {
                    // If parsing fails, fall back to checking init_boot existence
                }
            }
            
            // Check for init_boot partition first (priority for GKI 13+ devices)
            if (slotSuffix.isNotEmpty()) {
                val initBootWithSlot = "/dev/block/by-name/init_boot$slotSuffix"
                if (File(initBootWithSlot).exists()) {
                    // If init_boot exists, always use it (this is the correct behavior for modern devices)
                    return initBootWithSlot
                }
            }
            val initBoot = "/dev/block/by-name/init_boot"
            if (File(initBoot).exists()) {
                // If init_boot exists, always use it (this is the correct behavior for modern devices)
                return initBoot
            }
            
            // If we're on a GKI 13+ device but init_boot doesn't exist, 
            // this might be an edge case, but we should still prefer boot partition
            // Check standard boot locations with slot support
            val bootLocations = mutableListOf<String>()
            if (slotSuffix.isNotEmpty()) {
                // Add slot-specific locations first
                bootLocations.add("/dev/block/by-name/boot$slotSuffix")
                bootLocations.add("/dev/block/platform/*/*/by-name/boot$slotSuffix")
                bootLocations.add("/dev/block/platform/*/*/*/by-name/boot$slotSuffix")
                bootLocations.add("/dev/block/bootdevice/by-name/boot$slotSuffix")
            }
            // Add non-slot locations as fallback
            bootLocations.add("/dev/block/by-name/boot")
            bootLocations.add("/dev/block/platform/*/*/by-name/boot")
            bootLocations.add("/dev/block/platform/*/*/*/by-name/boot")
            bootLocations.add("/dev/block/bootdevice/by-name/boot")
            
            for (location in bootLocations) {
                if (location.contains("*")) {
                    // Handle wildcard paths
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls $location 2>/dev/null"))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line?.isNotEmpty() == true && File(line).exists()) {
                            // Verify it's a valid boot image by checking if it's a block device
                            val statProcess = Runtime.getRuntime().exec(arrayOf("stat", "-c", "%F", line))
                            val statReader = BufferedReader(InputStreamReader(statProcess.inputStream))
                            val fileType = statReader.readLine()
                            if (fileType?.contains("block") == true) {
                                return line
                            }
                        }
                    }
                } else {
                    if (File(location).exists()) {
                        // Verify it's a valid boot image by checking if it's a block device
                        val statProcess = Runtime.getRuntime().exec(arrayOf("stat", "-c", "%F", location))
                        val statReader = BufferedReader(InputStreamReader(statProcess.inputStream))
                        val fileType = statReader.readLine()
                        if (fileType?.contains("block") == true) {
                            return location
                        }
                    }
                }
            }
            
            // Fallback: try to find using find_block logic
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "find /dev/block -name '*boot*' | grep -v 'recovery' | head -n 1"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            if (result?.isNotEmpty() == true) {
                // Verify it's a valid boot image
                val statProcess = Runtime.getRuntime().exec(arrayOf("stat", "-c", "%F", result))
                val statReader = BufferedReader(InputStreamReader(statProcess.inputStream))
                val fileType = statReader.readLine()
                if (fileType?.contains("block") == true) {
                    return result
                }
            }
            
        } catch (e: Exception) {
            // Ignore errors and return empty
        }
        return ""
    }

    private fun uninstallMagisk(restoreImages: Boolean): Boolean {
        return try {
            sendLog("[INFO] Starting Magisk uninstallation (restoreImages=$restoreImages)")
            
            // Check if Magisk is installed and available
            if (!File("/data/adb/magisk").exists()) {
                sendLog("[ERROR] Magisk directory not found")
                return false
            }
            sendLog("[INFO] Magisk directory found")
            
            // Create temporary directory for uninstaller
            val tmpDir = "/data/local/tmp/magisk_uninstall"
            sendLog("[INFO] Creating temp directory: $tmpDir")
            val processMkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $tmpDir"))
            processMkdir.waitFor()
            if (processMkdir.exitValue() != 0) {
                sendLog("[ERROR] Failed to create temp directory")
                return false
            }
            
            // Check if uninstaller.sh exists in /data/adb/magisk/, if not, copy from app assets
            var uninstallerPath = "/data/adb/magisk/uninstaller.sh"
            val uninstallerFile = File(uninstallerPath)
            if (!uninstallerFile.exists()) {
                // Copy from app assets to temporary location
                val assetUninstallerPath = "/data/local/tmp/uninstaller.sh"
                try {
                    val inputStream = assets.open("uninstaller.sh")
                    val content = inputStream.readBytes()
                    inputStream.close()
                    
                    val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $assetUninstallerPath"))
                    val outputStream = writeProcess.outputStream
                    outputStream.write(content)
                    outputStream.close()
                    writeProcess.waitFor()
                    
                    if (writeProcess.exitValue() != 0) {
                        sendLog("[ERROR] Failed to write uninstaller script")
                        return false
                    }
                    uninstallerPath = assetUninstallerPath
                    sendLog("[INFO] Copied uninstaller.sh from assets to $assetUninstallerPath")
                } catch (e: Exception) {
                    sendLog("[ERROR] Failed to copy uninstaller.sh from assets: ${e.message}")
                    // If uninstaller.sh doesn't exist in assets, use direct commands instead
                    uninstallerPath = ""
                }
            } else {
                sendLog("[INFO] Using uninstaller.sh from $uninstallerPath")
            }
            
            if (uninstallerPath.isNotEmpty()) {
                // Copy uninstaller script and necessary files
                sendLog("[INFO] Copying uninstaller script to temp directory")
                val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $uninstallerPath $tmpDir/"))
                processCp.waitFor()
                if (processCp.exitValue() != 0) {
                    sendLog("[ERROR] Failed to copy uninstaller script")
                    return false
                }
                
                // Copy Magisk binaries if they exist
                sendLog("[INFO] Copying Magisk binaries")
                val magiskFiles = listOf("magisk", "magiskboot", "util_functions.sh")
                for (file in magiskFiles) {
                    val sourceFile = "/data/adb/magisk/$file"
                    if (File(sourceFile).exists()) {
                        val processCopy = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $sourceFile $tmpDir/"))
                        processCopy.waitFor()
                        if (processCopy.exitValue() != 0) {
                            sendLog("[ERROR] Failed to copy $file")
                            return false
                        }
                    } else {
                        // Try to get from assets if not in /data/adb/magisk
                        try {
                            val inputStream = assets.open(file)
                            val content = inputStream.readBytes()
                            inputStream.close()
                            
                            // Write to temporary file
                            val tempFile = "/data/local/tmp/$file"
                            val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $tempFile"))
                            val outputStream = writeProcess.outputStream
                            outputStream.write(content)
                            outputStream.close()
                            writeProcess.waitFor()
                            
                            if (writeProcess.exitValue() != 0) {
                                sendLog("[ERROR] Failed to write $file from assets")
                                return false
                            }
                            
                            // Copy to tmpDir
                            val copyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $tempFile $tmpDir/"))
                            copyProcess.waitFor()
                            if (copyProcess.exitValue() != 0) {
                                sendLog("[ERROR] Failed to copy $file to tmpDir")
                                return false
                            }
                        } catch (e: Exception) {
                            sendLog("[WARN] Skipping $file - not found in assets or /data/adb/magisk")
                        }
                    }
                }
                
                // Make files executable
                sendLog("[INFO] Making files executable")
                val processChmod = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpDir/*"))
                processChmod.waitFor()
                
                // Set up environment and execute uninstaller script with real-time log
                sendLog("[INFO] Executing uninstaller script")
                
                // Create a wrapper script that sets up environment and runs uninstaller.sh
                val cmd = if (restoreImages) {
                    "$tmpDir/uninstaller.sh --restore-images"
                } else {
                    "$tmpDir/uninstaller.sh"
                }
                
                val wrapperScript = """
                    #!/system/bin/sh
                    export BOOTMODE=true
                    export TMPDIR="$tmpDir"
                    export MAGISKBIN="$tmpDir"
                    export INSTALLER="$tmpDir"
                    
                    cd "$tmpDir"
                    
                    # Redirect script output to log
                    exec > >(while read line; do echo "[SCRIPT] ${'$'}line"; done) 2>&1
                    
                    . ./util_functions.sh
                    $cmd
                    
                    echo "[SCRIPT] Uninstall completed"
                """.trimIndent()
                
                // Write wrapper script
                val wrapperPath = "$tmpDir/uninstall_wrapper.sh"
                val writeWrapper = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $wrapperPath"))
                val wrapperOutput = writeWrapper.outputStream
                wrapperOutput.write(wrapperScript.toByteArray())
                wrapperOutput.close()
                writeWrapper.waitFor()
                
                // Make wrapper executable and run it
                Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $wrapperPath")).waitFor()
                
                sendLog("[INFO] Executing uninstaller wrapper script")
                val uninstallProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $wrapperPath"))
                
                // Read script output and send to log - read both stdout and stderr
                val scriptReader = BufferedReader(InputStreamReader(uninstallProcess.inputStream))
                val errorReader = BufferedReader(InputStreamReader(uninstallProcess.errorStream))
                
                // Read stdout
                var scriptLine: String?
                while (scriptReader.readLine().also { scriptLine = it } != null) {
                    sendLog(scriptLine!!)
                }
                
                // Read stderr
                var errorLine: String?
                while (errorReader.readLine().also { errorLine = it } != null) {
                    sendLog("[STDERR] $errorLine")
                }
                
                uninstallProcess.waitFor()
                
                if (uninstallProcess.exitValue() == 0) {
                    sendLog("[INFO] Uninstallation completed successfully")
                    true
                } else {
                    sendLog("[ERROR] Uninstaller script failed with exit code: ${uninstallProcess.exitValue()}")
                    false
                }
            } else {
                // Direct uninstall without script
                sendLog("[WARN] Uninstaller script not available, using direct uninstall")
                try {
                    if (restoreImages) {
                        // Restore boot images if requested
                        sendLog("[INFO] Restoring boot images from backup")
                        val restoreProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "for img in /data/adb/boot-backup/*; do if [ -f \"\$img\" ]; then slot=\$(basename \"\$img\"); if [ -e \"/dev/block/by-name/boot_\$slot\" ]; then dd if=\"\$img\" of=\"/dev/block/by-name/boot_\$slot\"; elif [ -e \"/dev/block/by-name/boot\" ]; then dd if=\"\$img\" of=\"/dev/block/by-name/boot\"; fi; fi; done"))
                        restoreProcess.waitFor()
                        sendLog("[INFO] Boot images restored")
                    }
                    
                    // Remove Magisk files
                    sendLog("[INFO] Removing Magisk files")
                    val removeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -rf /data/adb/magisk /data/adb/modules /data/adb/.magisk && rm -f /system/bin/su /system/xbin/su /sbin/su"))
                    removeProcess.waitFor()
                    if (removeProcess.exitValue() == 0) {
                        sendLog("[INFO] Magisk files removed successfully")
                        true
                    } else {
                        sendLog("[ERROR] Failed to remove Magisk files")
                        false
                    }
                } catch (e: Exception) {
                    sendLog("[ERROR] Exception during uninstall: ${e.message}")
                    false
                }
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Exception during uninstallation: ${e.message}")
            false
        }
    }

    private fun patchBootImage(bootImage: String): String? {
        return try {
            if (bootImage.isEmpty()) {
                sendLog("[ERROR] Boot image path is empty")
                return null
            }
            
            sendLog("[INFO] Starting boot image patching: $bootImage")
            
            // Create temporary directory
            val tmpDir = "/data/local/tmp/magisk_patch"
            sendLog("[INFO] Creating temp directory: $tmpDir")
            val processMkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $tmpDir"))
            processMkdir.waitFor()
            if (processMkdir.exitValue() != 0) {
                sendLog("[ERROR] Failed to create temp directory")
                return null
            }
            
            // Copy boot image to tmp directory
            sendLog("[INFO] Copying boot image to temp directory")
            val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $bootImage $tmpDir/boot.img"))
            processCp.waitFor()
            if (processCp.exitValue() != 0) {
                sendLog("[ERROR] Failed to copy boot image")
                return null
            }
            
            // Copy necessary Magisk files from assets
            sendLog("[INFO] Copying Magisk files from assets")
            val magiskFiles = listOf("magiskinit", "magisk", "magiskboot", "stub.apk", "util_functions.sh", "boot_patch.sh")
            var copySuccess = true
            for (file in magiskFiles) {
                val destPath = "$tmpDir/$file"
                if (!copyAssetToFile(file, destPath)) {
                    sendLog("[ERROR] Failed to copy $file from assets")
                    // Skip init-ld as it's optional for some devices
                    if (file != "init-ld") {
                        copySuccess = false
                        break
                    }
                }
            }
            
            if (!copySuccess) {
                sendLog("[ERROR] Failed to copy required files from assets")
                return null
            }
            
            // Make files executable
            sendLog("[INFO] Making files executable")
            val processChmod = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpDir/*"))
            processChmod.waitFor()
            
            // Set up environment variables and run boot_patch.sh
            sendLog("[INFO] Executing boot_patch.sh script")
            
            // Create a wrapper script that sets up environment and sources boot_patch.sh
            val wrapperScript = """
                #!/system/bin/sh
                export BOOTMODE=true
                export TMPDIR="$tmpDir"
                export MAGISKBIN="$tmpDir"
                export KEEPVERITY=false
                export KEEPFORCEENCRYPT=false
                export PATCHVBMETAFLAG=false
                export RECOVERYMODE=false
                export LEGACYSAR=false
                
                cd "$tmpDir"
                
                # Redirect script output to log
                exec > >(while read line; do echo "[SCRIPT] ${'$'}line"; done) 2>&1
                
                . ./util_functions.sh
                . ./boot_patch.sh boot.img
                
                echo "[SCRIPT] Boot patch completed"
            """.trimIndent()
            
            // Write wrapper script
            val wrapperPath = "$tmpDir/patch_wrapper.sh"
            val writeWrapper = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $wrapperPath"))
            val wrapperOutput = writeWrapper.outputStream
            wrapperOutput.write(wrapperScript.toByteArray())
            wrapperOutput.close()
            writeWrapper.waitFor()
            
            // Make wrapper executable and run it
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $wrapperPath")).waitFor()
            
            // Use ProcessBuilder for better control over process execution
            sendLog("[INFO] Executing wrapper script: $wrapperPath")
            val patchProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $wrapperPath"))
            
            // Read script output and send to log - read both stdout and stderr
            val scriptReader = BufferedReader(InputStreamReader(patchProcess.inputStream))
            val errorReader = BufferedReader(InputStreamReader(patchProcess.errorStream))
            
            // Read stdout
            var scriptLine: String?
            while (scriptReader.readLine().also { scriptLine = it } != null) {
                sendLog(scriptLine!!)
            }
            
            // Read stderr
            var errorLine: String?
            while (errorReader.readLine().also { errorLine = it } != null) {
                sendLog("[STDERR] $errorLine")
            }
            
            patchProcess.waitFor()
            
            if (patchProcess.exitValue() != 0) {
                sendLog("[ERROR] boot_patch.sh failed with exit code: ${patchProcess.exitValue()}")
                return null
            }
            
            sendLog("[INFO] Boot image patched successfully")
            
            // Copy patched image to output location
            val outputFile = bootImage.replace(".img", "_patched.img")
            sendLog("[INFO] Copying patched image to: $outputFile")
            val processCopyOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $tmpDir/new-boot.img $outputFile"))
            processCopyOut.waitFor()
            if (processCopyOut.exitValue() == 0) {
                sendLog("[INFO] Patched image saved successfully: $outputFile")
                outputFile
            } else {
                sendLog("[ERROR] Failed to copy patched image")
                null
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Error patching boot image: ${e.message}")
            null
        }
    }

    private fun otaSlotSwitch(): Boolean {
        return try {
            sendLog("[INFO] Starting OTA slot switch using bootctl")
            
            // Check if bootctl exists
            val bootctlPath = "/tool/bootctl"
            val bootctlFile = File(bootctlPath)
            if (!bootctlFile.exists()) {
                sendLog("[ERROR] bootctl not found at $bootctlPath")
                return false
            }
            
            // Make bootctl executable
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $bootctlPath"))
            chmodProcess.waitFor()
            
            // Get current slot
            sendLog("[INFO] Getting current boot slot")
            val currentSlotProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath current-slot"))
            val currentSlotReader = BufferedReader(InputStreamReader(currentSlotProcess.inputStream))
            val currentSlot = currentSlotReader.readLine()?.trim() ?: "unknown"
            currentSlotProcess.waitFor()
            sendLog("[INFO] Current slot: $currentSlot")
            
            // Determine target slot
            val targetSlot = if (currentSlot == "_a") "_b" else "_a"
            sendLog("[INFO] Switching to slot: $targetSlot")
            
            // Set active boot slot
            sendLog("[INFO] Setting boot slot to $targetSlot")
            val setSlotProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath set-active-boot-slot $targetSlot"))
            setSlotProcess.waitFor()
            
            if (setSlotProcess.exitValue() == 0) {
                sendLog("[INFO] Boot slot switched successfully to $targetSlot")
                sendLog("[INFO] Please reboot device to boot from the new slot")
                true
            } else {
                sendLog("[ERROR] Failed to set boot slot")
                false
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Error during OTA slot switch: ${e.message}")
            false
        }
    }

    private fun restoreMagiskAfterOta(): Boolean {
        return try {
            sendLog("[INFO] Starting Magisk restoration after OTA")
            
            // Check if bootctl exists
            val bootctlPath = "/tool/bootctl"
            val bootctlFile = File(bootctlPath)
            if (!bootctlFile.exists()) {
                sendLog("[ERROR] bootctl not found at $bootctlPath")
                return false
            }
            
            // Make bootctl executable
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $bootctlPath"))
            chmodProcess.waitFor()
            
            // Get current slot
            sendLog("[INFO] Getting current boot slot")
            val currentSlotProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath current-slot"))
            val currentSlotReader = BufferedReader(InputStreamReader(currentSlotProcess.inputStream))
            val currentSlot = currentSlotReader.readLine()?.trim() ?: "unknown"
            currentSlotProcess.waitFor()
            sendLog("[INFO] Current slot: $currentSlot")
            
            // Check if Magisk backup exists
            val backupDir = File("/cache/magisk_backup")
            if (!backupDir.exists()) {
                sendLog("[WARN] No Magisk backup found in /cache/magisk_backup")
                // Try alternative backup location
                val altBackupDir = File("/data/adb/magisk_backup")
                if (!altBackupDir.exists()) {
                    sendLog("[ERROR] No Magisk backup found, please install addon.d script first")
                    return false
                }
            }
            
            // Restore Magisk files from backup
            sendLog("[INFO] Restoring Magisk files from backup")
            val restoreProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", """
                if [ -d /cache/magisk_backup/magisk ]; then
                    mkdir -p /data/adb/magisk
                    cp -r /cache/magisk_backup/magisk/* /data/adb/magisk/
                fi
                if [ -d /cache/magisk_backup/modules ]; then
                    mkdir -p /data/adb/modules
                    cp -r /cache/magisk_backup/modules/* /data/adb/modules/
                fi
                if [ -f /cache/magisk_backup/magisk.apk ]; then
                    cp /cache/magisk_backup/magisk.apk /data/adb/magisk.apk
                fi
                if [ -f /cache/magisk_backup/magisk.db ]; then
                    cp /cache/magisk_backup/magisk.db /data/adb/magisk.db
                fi
                chown -R root:root /data/adb/magisk
                chmod -R 755 /data/adb/magisk
                echo "Magisk restoration completed"
            """.trimIndent()))
            restoreProcess.waitFor()
            
            if (restoreProcess.exitValue() == 0) {
                sendLog("[INFO] Magisk restoration completed successfully")
                sendLog("[INFO] Please reboot device to apply changes")
                true
            } else {
                sendLog("[ERROR] Failed to restore Magisk")
                false
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Error during Magisk restoration: ${e.message}")
            false
        }
    }

    private fun installAddonDScript(): Boolean {
        return try {
            sendLog("[INFO] Installing addon.d script for OTA recovery")
            
            // Check if addon.d.sh exists in assets
            val addonDAsset = "addon.d.sh"
            try {
                assets.open(addonDAsset).close()
            } catch (e: Exception) {
                sendLog("[ERROR] addon.d.sh not found in assets")
                return false
            }
            
            // Create /system/addon.d directory if it doesn't exist
            val mkdirProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p /system/addon.d"))
            mkdirProcess.waitFor()
            
            // Copy addon.d.sh to /system/addon.d/
            val destPath = "/system/addon.d/99-magisk.sh"
            sendLog("[INFO] Copying addon.d script to $destPath")
            
            // Read asset content
            val inputStream = assets.open(addonDAsset)
            val content = inputStream.readBytes()
            inputStream.close()
            
            // Write to destination using cat command
            val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $destPath"))
            val outputStream = writeProcess.outputStream
            outputStream.write(content)
            outputStream.close()
            writeProcess.waitFor()
            
            if (writeProcess.exitValue() != 0) {
                sendLog("[ERROR] Failed to write addon.d script")
                return false
            }
            
            // Make script executable
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $destPath"))
            chmodProcess.waitFor()
            
            if (chmodProcess.exitValue() == 0) {
                sendLog("[INFO] addon.d script installed successfully")
                sendLog("[INFO] Magisk will be automatically restored after OTA updates")
                true
            } else {
                sendLog("[ERROR] Failed to set permissions on addon.d script")
                false
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Error installing addon.d script: ${e.message}")
            false
        }
    }

    private fun updateMagiskManager(): Boolean {
        return try {
            // Open SunRayEx's Magisk-Metro GitHub repository
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SunRayEx/Magisk-Metro"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
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

    private fun pickFile(result: Result) {
        pendingResult = result
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, 1001)
    }

    private fun saveLogToFile(logContent: String, filename: String): Boolean {
        return try {
            // Save to Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logFile = File(downloadsDir, filename)
            
            // Write log content to file
            val writer = logFile.bufferedWriter()
            writer.write(logContent)
            writer.close()
            
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null) {
                    // Get the file path from URI
                    val filePath = getFilePathFromUri(uri)
                    pendingResult?.success(filePath)
                } else {
                    pendingResult?.error("FILE_PICKER_ERROR", "No file selected", null)
                }
            } else {
                pendingResult?.error("FILE_PICKER_CANCELLED", "File picker cancelled", null)
            }
            pendingResult = null
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            when {
                uri.scheme == "file" -> uri.path
                uri.scheme == "content" -> {
                    val projection = arrayOf("_data")
                    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(cursor.getColumnIndexOrThrow("_data"))
                        } else {
                            null
                        }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
