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
import android.provider.OpenableColumns
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
    private val ROOT_ACCESS_CHANNEL = "magisk_manager/root_access"
    private val LOGS_CHANNEL = "magisk_manager/logs"
    private val FILEPICKER_CHANNEL = "magisk_manager/filepicker"
    private val uiHandler = Handler(Looper.getMainLooper())
    
    // Path to app_functions.sh script
    private val appFunctionsScriptPath = "/data/local/tmp/app_functions.sh"
    
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
                    // Use the new app_functions.sh method for better compatibility
                    val success = executeAppFunctionExitCode("set_zygisk_enabled", if (enabled) "1" else "0") == 0
                    result.success(success)
                }
                "setDenyListEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    // Use the new app_functions.sh method for better compatibility
                    val success = executeAppFunctionExitCode("set_denylist_enabled", if (enabled) "1" else "0") == 0
                    result.success(success)
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
                "installModule" -> {
                    val zipPath = call.argument<String>("zipPath")
                    result.success(installModule(zipPath ?: ""))
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

        // Root Access Channel - dedicated channel for root access app management
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, ROOT_ACCESS_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getRootAccessApps" -> result.success(getRootAccessAppsViaScript())
                "grantRootAccess" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(grantRootAccessViaScript(packageName ?: ""))
                }
                "revokeRootAccess" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(revokeRootAccessViaScript(packageName ?: ""))
                }
                "hasRootAccess" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(hasRootAccessViaScript(packageName ?: ""))
                }
                "getRootPolicy" -> {
                    val packageName = call.argument<String>("packageName")
                    result.success(getRootPolicyViaScript(packageName ?: ""))
                }
                "listRootPolicies" -> result.success(listRootPoliciesViaScript())
                "setupAppFunctionsScript" -> result.success(setupAppFunctionsScript())
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
        
        android.util.Log.d("MainActivity", "getInstalledApps: Starting...")
        android.util.Log.d("MainActivity", "getInstalledApps: denyList size: ${denyList.size}")
        
        val rootAllowedPackages = getRootAllowedPackages()
        android.util.Log.d("MainActivity", "getInstalledApps: rootAllowedPackages size: ${rootAllowedPackages.size}")
        android.util.Log.d("MainActivity", "getInstalledApps: rootAllowedPackages: $rootAllowedPackages")
        
        try {
            // Get all installed packages using PackageManager
            val packages = pm.getInstalledPackages(0)
            android.util.Log.d("MainActivity", "getInstalledApps: Total packages: ${packages.size}")
            
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
                // Check if core files exist (no stub.apk needed - Flutter app is standalone)
                val magiskFile = File("/data/adb/magisk/magisk")
                val utilFile = File("/data/adb/magisk/util_functions.sh")
                if (magiskFile.exists() || utilFile.exists()) {
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
            
            // Method 1: Check Magisk internal state using magisk --sqlite
            // Query the settings table for zygisk setting
            val magiskConfigFile = File("/data/adb/magisk.db")
            android.util.Log.d("MainActivity", "magisk.db exists: ${magiskConfigFile.exists()}")
            
            if (magiskConfigFile.exists()) {
                // Try using magisk --sqlite command first (most reliable)
                val sqliteResult = executeRootCommand("magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk'\"")
                android.util.Log.d("MainActivity", "SQLite query result for zygisk: '$sqliteResult'")
                
                if (sqliteResult.trim() == "1") {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via magisk --sqlite (zygisk=1)")
                    return true
                }
                
                // Also try zygisk_enabled key for older versions
                val sqliteResult2 = executeRootCommand("magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk_enabled'\"")
                android.util.Log.d("MainActivity", "SQLite query result for zygisk_enabled: '$sqliteResult2'")
                
                if (sqliteResult2.trim() == "1") {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via magisk --sqlite (zygisk_enabled=1)")
                    return true
                }
                
                // Fallback: Use sqlite3 directly
                val directSqliteResult = executeRootCommand("sqlite3 /data/adb/magisk.db \"SELECT value FROM settings WHERE key='zygisk'\"")
                android.util.Log.d("MainActivity", "Direct SQLite result: '$directSqliteResult'")
                
                if (directSqliteResult.trim() == "1") {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via direct sqlite3")
                    return true
                }
            }
            
            // Method 2: Check if Zygisk is loaded by checking for zygiskd process
            val zygiskdCheck = executeRootCommand("ps -A | grep zygiskd")
            if (zygiskdCheck.isNotEmpty() && zygiskdCheck.contains("zygiskd")) {
                android.util.Log.d("MainActivity", "isZygiskEnabled: true via zygiskd process")
                return true
            }
            
            // Method 3: Check /data/adb/zygisk directory structure
            val zygiskDir = File("/data/adb/zygisk")
            android.util.Log.d("MainActivity", "zygisk dir exists: ${zygiskDir.exists()}")
            
            if (zygiskDir.exists() && zygiskDir.isDirectory) {
                // Check if zygisk is active by looking for active files
                val zygiskActive = executeRootCommand("ls -la /data/adb/zygisk/")
                android.util.Log.d("MainActivity", "zygisk directory contents: $zygiskActive")
                
                // Check for uninstaller file which indicates zygisk is installed
                val uninstallerFile = File("/data/adb/zygisk/uninstaller.sh")
                if (uninstallerFile.exists()) {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via uninstaller.sh presence")
                    return true
                }
            }
            
            // Method 4: Check if any Zygisk modules are installed
            val zygiskModulesDir = File("/data/adb/modules")
            if (zygiskModulesDir.exists() && zygiskModulesDir.isDirectory) {
                zygiskModulesDir.listFiles()?.forEach { moduleDir ->
                    val zygiskDir = File(moduleDir, "zygisk")
                    if (zygiskDir.exists()) {
                        android.util.Log.d("MainActivity", "isZygiskEnabled: true via module ${moduleDir.name} having zygisk folder")
                        return true
                    }
                }
            }
            
            android.util.Log.d("MainActivity", "isZygiskEnabled: false")
            false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking Zygisk status: ${e.message}", e)
            false
        }
    }
    
    /**
     * Execute a root command and return the output
     */
    private fun executeRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error executing command: $command - ${e.message}")
            ""
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
            android.util.Log.d("MainActivity", "isDenyListEnabled: checking status")
            
            // Method 1: Use magisk --denylist status command
            val statusProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist status"))
            val statusReader = BufferedReader(InputStreamReader(statusProcess.inputStream))
            val statusOutput = StringBuilder()
            var statusLine: String?
            while (statusReader.readLine().also { statusLine = it } != null) {
                statusOutput.append(statusLine).append("\n")
            }
            statusProcess.waitFor()
            val statusResult = statusOutput.toString().trim()
            android.util.Log.d("MainActivity", "magisk --denylist status output: $statusResult")
            
            // Check if denylist is enabled (output contains "enabled" or "true")
            if (statusResult.contains("enabled", ignoreCase = true) || statusResult.contains("true", ignoreCase = true)) {
                android.util.Log.d("MainActivity", "isDenyListEnabled: true via magisk --denylist status")
                return true
            }
            
            // Method 2: Check settings table in magisk.db
            val magiskConfigFile = File("/data/adb/magisk.db")
            if (magiskConfigFile.exists()) {
                // Try multiple possible key names
                val keys = listOf("denylist", "magiskhide")
                for (key in keys) {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key = '$key'\""))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val result = reader.readLine()
                    process.waitFor()
                    android.util.Log.d("MainActivity", "SQLite query for key '$key': $result")
                    
                    // Parse output - handle format like "value|1" or just "1"
                    val value = result?.trim()?.split("|")?.lastOrNull()?.trim() ?: result?.trim()
                    if (value == "1") {
                        android.util.Log.d("MainActivity", "isDenyListEnabled: true via settings table key '$key'")
                        return true
                    }
                }
                
                // Fallback: check if denylist table has any entries
                val denylistProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT COUNT(*) FROM denylist\""))
                val denylistReader = BufferedReader(InputStreamReader(denylistProcess.inputStream))
                val countResult = denylistReader.readLine()
                denylistProcess.waitFor()
                android.util.Log.d("MainActivity", "denylist table count: $countResult")
                
                val count = countResult?.trim()?.split("|")?.lastOrNull()?.trim()?.toIntOrNull() ?: 0
                if (count > 0) {
                    android.util.Log.d("MainActivity", "isDenyListEnabled: true via denylist table entries ($count)")
                    return true
                }
            }
            
            android.util.Log.d("MainActivity", "isDenyListEnabled: false")
            false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking denylist status: ${e.message}")
            false
        }
    }

    private fun setDenyListEnabled(enabled: Boolean): Boolean {
        return try {
            android.util.Log.d("MainActivity", "setDenyListEnabled: enabled=$enabled")
            
            // Method 1: Use magisk --denylist command
            val cmd = if (enabled) "magisk --denylist enable" else "magisk --denylist disable"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor()
            val exitCode = process.exitValue()
            
            // Read output for debugging
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = outputReader.readText().trim()
            val error = errorReader.readText().trim()
            android.util.Log.d("MainActivity", "$cmd output: $output, error: $error, exitCode: $exitCode")
            
            if (exitCode == 0) {
                android.util.Log.d("MainActivity", "setDenyListEnabled: success via magisk --denylist command")
                return true
            }
            
            // Method 2: Update settings table directly
            val magiskConfigFile = File("/data/adb/magisk.db")
            if (magiskConfigFile.exists()) {
                // Try both key names for compatibility
                val keys = listOf("denylist", "magiskhide")
                for (key in keys) {
                    val sqliteCmd = "magisk --sqlite \"INSERT OR REPLACE INTO settings (key, value) VALUES ('$key', '${if (enabled) "1" else "0"}')\""
                    android.util.Log.d("MainActivity", "Executing: $sqliteCmd")
                    
                    val sqliteProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", sqliteCmd))
                    sqliteProcess.waitFor()
                    
                    if (sqliteProcess.exitValue() == 0) {
                        // Verify the setting was applied
                        val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key = '$key'\""))
                        val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
                        val verifyResult = verifyReader.readLine()
                        verifyProcess.waitFor()
                        android.util.Log.d("MainActivity", "Verification for key '$key': $verifyResult")
                        
                        val value = verifyResult?.trim()?.split("|")?.lastOrNull()?.trim()
                        if (value == if (enabled) "1" else "0") {
                            android.util.Log.d("MainActivity", "setDenyListEnabled: success via settings table key '$key'")
                            return true
                        }
                    }
                }
            }
            
            android.util.Log.e("MainActivity", "setDenyListEnabled: failed - all methods failed")
            false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting denylist: ${e.message}")
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
                // Check if core files exist (no stub.apk needed - Flutter app is standalone)
                val magiskFile = File("/data/adb/magisk/magisk")
                val utilFile = File("/data/adb/magisk/util_functions.sh")
                if (magiskFile.exists() || utilFile.exists()) {
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
            android.util.Log.d("MainActivity", "grantRootAccess: $packageName")
            
            // Step 1: Get UID for the package
            val uidProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys package $packageName | grep userId= | head -1"))
            val uidReader = BufferedReader(InputStreamReader(uidProcess.inputStream))
            val uidOutput = uidReader.readText().trim()
            uidProcess.waitFor()
            
            android.util.Log.d("MainActivity", "dumpsys output for $packageName: $uidOutput")
            
            // Parse UID from output like "userId=10123"
            val uidMatch = Regex("userId=(\\d+)").find(uidOutput)
            val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
            
            if (uid == null || uid < 10000) {
                android.util.Log.e("MainActivity", "Failed to get valid UID for $packageName (uid=$uid)")
                return false
            }
            
            android.util.Log.d("MainActivity", "Got UID $uid for package $packageName")
            
            // Step 2: Grant root access using magisk --sqlite with UID
            // policy values: 0=deny, 1=allow, 2=allow_forever
            val sqliteCmd = "INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)"
            android.util.Log.d("MainActivity", "Executing: magisk --sqlite \"$sqliteCmd\"")
            
            val grantProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$sqliteCmd\""))
            grantProcess.waitFor()
            
            val output = grantProcess.inputStream.bufferedReader().readText().trim()
            val error = grantProcess.errorStream.bufferedReader().readText().trim()
            android.util.Log.d("MainActivity", "Grant result: output=$output, error=$error, exitCode=${grantProcess.exitValue()}")
            
            if (grantProcess.exitValue() == 0) {
                android.util.Log.d("MainActivity", "Successfully granted root access to $packageName (uid=$uid)")
                return true
            }
            
            // Fallback: Use sqlite3 directly
            val dbCmd = "sqlite3 /data/adb/magisk.db \"INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)\""
            android.util.Log.d("MainActivity", "Fallback: $dbCmd")
            
            val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", dbCmd))
            dbProcess.waitFor()
            
            if (dbProcess.exitValue() == 0) {
                android.util.Log.d("MainActivity", "Successfully granted root access via sqlite3")
                return true
            }
            
            android.util.Log.e("MainActivity", "Failed to grant root access")
            false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error granting root access: ${e.message}")
            false
        }
    }

    private fun revokeRootAccess(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            android.util.Log.d("MainActivity", "revokeRootAccess: $packageName")
            
            // Step 1: Get UID for the package
            val uidProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys package $packageName | grep userId= | head -1"))
            val uidReader = BufferedReader(InputStreamReader(uidProcess.inputStream))
            val uidOutput = uidReader.readText().trim()
            uidProcess.waitFor()
            
            // Parse UID from output
            val uidMatch = Regex("userId=(\\d+)").find(uidOutput)
            val uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
            
            if (uid == null) {
                android.util.Log.e("MainActivity", "Failed to get UID for $packageName")
                return false
            }
            
            android.util.Log.d("MainActivity", "Got UID $uid for package $packageName")
            
            // Step 2: Revoke root access using magisk --sqlite with UID
            val sqliteCmd = "DELETE FROM policies WHERE uid=$uid"
            val revokeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$sqliteCmd\""))
            revokeProcess.waitFor()
            
            if (revokeProcess.exitValue() == 0) {
                android.util.Log.d("MainActivity", "Successfully revoked root access from $packageName (uid=$uid)")
                return true
            }
            
            // Fallback: Use sqlite3 directly
            val dbCmd = "sqlite3 /data/adb/magisk.db \"DELETE FROM policies WHERE uid=$uid\""
            val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", dbCmd))
            dbProcess.waitFor()
            
            dbProcess.exitValue() == 0
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error revoking root access: ${e.message}")
            false
        }
    }

    private fun getRootAllowedPackages(): List<String> {
        return try {
            android.util.Log.d("MainActivity", "getRootAllowedPackages: starting query")
            
            val allowedPackages = mutableListOf<String>()
            
            // Method 1: Use shell script to query magisk database
            // This is more reliable than direct command execution due to quote handling
            val script = """
                . /data/local/tmp/app_functions.sh 2>/dev/null || true
                get_root_access_apps
            """.trimIndent()
            
            // Write script to temp file
            val scriptPath = "/data/local/tmp/query_root_apps_$$.sh"
            val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $scriptPath"))
            writeProcess.outputStream.write(script.toByteArray())
            writeProcess.outputStream.close()
            writeProcess.waitFor()
            
            // Execute script
            val execProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $scriptPath"))
            val execReader = BufferedReader(InputStreamReader(execProcess.inputStream))
            var line: String?
            while (execReader.readLine().also { line = it } != null) {
                val pkg = line!!.trim()
                if (pkg.isNotEmpty() && pkg != "unknown") {
                    allowedPackages.add(pkg)
                    android.util.Log.d("MainActivity", "Found root app: $pkg")
                }
            }
            execProcess.waitFor()
            
            // Cleanup
            Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f $scriptPath")).waitFor()
            
            if (allowedPackages.isNotEmpty()) {
                android.util.Log.d("MainActivity", "getRootAllowedPackages: found ${allowedPackages.size} packages via script")
                return allowedPackages
            }
            
            // Method 2: Fallback - Query policies table directly using raw SQL via shell
            android.util.Log.d("MainActivity", "Trying fallback method: direct SQL query")
            
            // Use a shell script to properly handle the magisk --sqlite command
            val sqlScript = """
                magisk --sqlite 'SELECT uid FROM policies WHERE policy>0' 2>/dev/null || \
                sqlite3 /data/adb/magisk.db 'SELECT uid FROM policies WHERE policy>0' 2>/dev/null
            """.trimIndent()
            
            val sqlProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", sqlScript))
            val sqlReader = BufferedReader(InputStreamReader(sqlProcess.inputStream))
            val uidOutput = StringBuilder()
            var sqlLine: String?
            while (sqlReader.readLine().also { sqlLine = it } != null) {
                uidOutput.append(sqlLine).append("\n")
            }
            sqlProcess.waitFor()
            
            val rawOutput = uidOutput.toString().trim()
            android.util.Log.d("MainActivity", "SQL query output: '$rawOutput'")
            
            // Parse UIDs
            val uids = mutableListOf<Int>()
            for (uidLine in rawOutput.split("\n")) {
                val trimmed = uidLine.trim()
                if (trimmed.isEmpty()) continue
                
                // Handle format like "uid|12345" or just "12345"
                val uidValue = if (trimmed.contains("|")) {
                    trimmed.split("|").lastOrNull()?.trim()
                } else {
                    trimmed
                }
                
                val uid = uidValue?.toIntOrNull()
                if (uid != null && uid >= 10000) {
                    uids.add(uid)
                    android.util.Log.d("MainActivity", "Found root-granted UID: $uid")
                }
            }
            
            android.util.Log.d("MainActivity", "Found ${uids.size} UIDs with root access")
            
            // Convert UIDs to package names
            for (uid in uids) {
                try {
                    val pmProcess = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "--uid", uid.toString()))
                    val pmReader = BufferedReader(InputStreamReader(pmProcess.inputStream))
                    val pmOutput = pmReader.readText().trim()
                    pmProcess.waitFor()
                    
                    for (resultLine in pmOutput.split("\n")) {
                        val trimmedLine = resultLine.trim()
                        if (trimmedLine.startsWith("package:")) {
                            val packageName = trimmedLine.removePrefix("package:").split(",").first().trim()
                            if (packageName.isNotEmpty() && !allowedPackages.contains(packageName)) {
                                allowedPackages.add(packageName)
                                android.util.Log.d("MainActivity", "UID $uid -> package: $packageName")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to get package for UID $uid: ${e.message}")
                }
            }
            
            android.util.Log.d("MainActivity", "getRootAllowedPackages: returning ${allowedPackages.size} packages: $allowedPackages")
            allowedPackages
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting root allowed packages: ${e.message}", e)
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
            // Note: stub.apk is NOT included - Flutter app is standalone, no dynamic loading needed
            sendLog("[INFO] Copying Magisk files from assets")
            val magiskFiles = listOf("magiskinit", "magisk", "magiskboot", "util_functions.sh", "boot_patch.sh")
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
            // Note: Android shell (mksh/ash) doesn't support process substitution > >(...)
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
            sendLog("[INFO] Finding boot image partition...")
            
            // Get current slot suffix for A/B devices
            val slotSuffixProcess = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix"))
            val slotReader = BufferedReader(InputStreamReader(slotSuffixProcess.inputStream))
            val slotSuffix = slotReader.readLine()?.trim() ?: ""
            slotSuffixProcess.waitFor()
            sendLog("[INFO] Current slot suffix: $slotSuffix")
            
            // Get SDK version to check if this is Android 13+ (GKI device)
            val sdkVersion = android.os.Build.VERSION.SDK_INT
            val isAndroid13Plus = sdkVersion >= 33  // Android 13 is SDK 33
            sendLog("[INFO] SDK version: $sdkVersion, Android 13+: $isAndroid13Plus")
            
            // Check for init_boot partition first (GKI 13+ devices)
            // init_boot partition is used on devices with GKI (Generic Kernel Image)
            val initBootLocations = mutableListOf<String>()
            
            // With slot suffix first
            if (slotSuffix.isNotEmpty()) {
                initBootLocations.add("/dev/block/by-name/init_boot$slotSuffix")
            }
            // Without slot suffix
            initBootLocations.add("/dev/block/by-name/init_boot")
            
            for (location in initBootLocations) {
                val file = File(location)
                if (file.exists()) {
                    sendLog("[INFO] Found init_boot partition: $location")
                    // Verify it's a block device
                    val checkProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -b $location && echo 'block'"))
                    checkProcess.waitFor()
                    val result = checkProcess.inputStream.bufferedReader().readText().trim()
                    if (result == "block") {
                        sendLog("[INFO] Using init_boot partition: $location")
                        return location
                    }
                }
            }
            
            // If init_boot not found, check for boot partition
            sendLog("[INFO] init_boot not found, checking boot partition...")
            
            val bootLocations = mutableListOf<String>()
            
            // Add slot-specific locations first
            if (slotSuffix.isNotEmpty()) {
                bootLocations.add("/dev/block/by-name/boot$slotSuffix")
                bootLocations.add("/dev/block/bootdevice/by-name/boot$slotSuffix")
            }
            // Add non-slot locations
            bootLocations.add("/dev/block/by-name/boot")
            bootLocations.add("/dev/block/bootdevice/by-name/boot")
            
            for (location in bootLocations) {
                val file = File(location)
                if (file.exists()) {
                    sendLog("[INFO] Found boot partition: $location")
                    // Verify it's a block device
                    val checkProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -b $location && echo 'block'"))
                    checkProcess.waitFor()
                    val result = checkProcess.inputStream.bufferedReader().readText().trim()
                    if (result == "block") {
                        sendLog("[INFO] Using boot partition: $location")
                        return location
                    }
                }
            }
            
            // Use find command to search for boot partition
            sendLog("[INFO] Using find command to locate boot partition...")
            val findProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", """
                for p in /dev/block/by-name /dev/block/platform/*/*/by-name; do
                    if [ -d "${'$'}p" ]; then
                        # Check init_boot first
                        for n in init_boot init_boot_a init_boot_b; do
                            if [ -b "${'$'}p/${'$'}n" ]; then
                                echo "${'$'}p/${'$'}n"
                                exit 0
                            fi
                        done
                        # Then check boot
                        for n in boot boot_a boot_b; do
                            if [ -b "${'$'}p/${'$'}n" ]; then
                                echo "${'$'}p/${'$'}n"
                                exit 0
                            fi
                        done
                    fi
                done
                echo ""
            """.trimIndent()))
            findProcess.waitFor()
            val findResult = findProcess.inputStream.bufferedReader().readText().trim()
            
            if (findResult.isNotEmpty()) {
                sendLog("[INFO] Found partition via find: $findResult")
                return findResult
            }
            
            sendLog("[ERROR] Could not find boot or init_boot partition")
            
        } catch (e: Exception) {
            sendLog("[ERROR] Error finding boot image: ${e.message}")
        }
        return ""
    }

    private fun uninstallMagisk(restoreImages: Boolean): Boolean {
        return try {
            sendLog("[INFO] Starting Magisk uninstallation (restoreImages=$restoreImages)")
            
            // Check if Magisk is installed - check multiple possible locations
            val magiskDir = File("/data/adb/magisk")
            val magiskDb = File("/data/adb/magisk.db")
            val modulesDir = File("/data/adb/modules")
            
            // Check if any Magisk-related files/directories exist
            val hasMagisk = magiskDir.exists() || magiskDb.exists() || modulesDir.exists()
            
            if (!hasMagisk) {
                sendLog("[ERROR] No Magisk installation found")
                sendLog("[INFO] Checked: /data/adb/magisk, /data/adb/magisk.db, /data/adb/modules")
                return false
            }
            sendLog("[INFO] Magisk installation found")
            
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
            // Note: stub.apk is NOT included - Flutter app is standalone, no dynamic loading needed
            sendLog("[INFO] Copying Magisk files from assets")
            val magiskFiles = listOf("magiskinit", "magisk", "magiskboot", "util_functions.sh", "boot_patch.sh")
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
            sendLog("[INFO] Starting OTA slot switch with Magisk restoration")
            
            // Check if bootctl exists in assets first
            var bootctlPath = ""
            try {
                // Try to extract bootctl from assets
                val inputStream = assets.open("bootctl")
                val content = inputStream.readBytes()
                inputStream.close()
                
                // Write to temp location
                bootctlPath = "/data/local/tmp/bootctl"
                val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $bootctlPath"))
                val outputStream = writeProcess.outputStream
                outputStream.write(content)
                outputStream.close()
                writeProcess.waitFor()
                
                // Make executable
                val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $bootctlPath"))
                chmodProcess.waitFor()
                sendLog("[INFO] Extracted bootctl from assets to $bootctlPath")
            } catch (e: Exception) {
                // Try system bootctl
                bootctlPath = "/tool/bootctl"
                val bootctlFile = File(bootctlPath)
                if (!bootctlFile.exists()) {
                    sendLog("[ERROR] bootctl not found")
                    return false
                }
            }
            
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
            
            // === Step 1: Backup Magisk to cache before slot switch ===
            sendLog("[INFO] Step 1: Backing up Magisk files before slot switch")
            val backupDir = "/cache/magisk_backup"
            val backupProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", """
                rm -rf $backupDir
                mkdir -p $backupDir
                if [ -d /data/adb/magisk ]; then
                    mkdir -p $backupDir/magisk
                    cp -r /data/adb/magisk/* $backupDir/magisk/
                fi
                if [ -d /data/adb/modules ]; then
                    mkdir -p $backupDir/modules
                    cp -r /data/adb/modules/* $backupDir/modules/
                fi
                if [ -f /data/adb/magisk.db ]; then
                    cp /data/adb/magisk.db $backupDir/
                fi
                if [ -f /data/adb/magisk.apk ]; then
                    cp /data/adb/magisk.apk $backupDir/
                fi
                ls -la $backupDir
            """.trimIndent()))
            backupProcess.waitFor()
            sendLog("[INFO] Magisk backup completed")
            
            // === Step 2: Set active boot slot ===
            sendLog("[INFO] Step 2: Setting boot slot to $targetSlot")
            val setSlotProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath set-active-boot-slot $targetSlot"))
            setSlotProcess.waitFor()
            
            if (setSlotProcess.exitValue() != 0) {
                sendLog("[ERROR] Failed to set boot slot")
                return false
            }
            sendLog("[INFO] Boot slot switched successfully to $targetSlot")
            
            // === Step 3: Restore Magisk from backup ===
            sendLog("[INFO] Step 3: Restoring Magisk to the new slot")
            val restoreProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", """
                if [ -d $backupDir/magisk ]; then
                    mkdir -p /data/adb/magisk
                    cp -r $backupDir/magisk/* /data/adb/magisk/
                    chmod -R 755 /data/adb/magisk
                fi
                if [ -d $backupDir/modules ]; then
                    mkdir -p /data/adb/modules
                    cp -r $backupDir/modules/* /data/adb/modules/
                    chmod -R 755 /data/adb/modules
                fi
                if [ -f $backupDir/magisk.db ]; then
                    cp $backupDir/magisk.db /data/adb/
                    chmod 600 /data/adb/magisk.db
                fi
                if [ -f $backupDir/magisk.apk ]; then
                    cp $backupDir/magisk.apk /data/adb/
                fi
                echo "Magisk restoration completed"
            """.trimIndent()))
            restoreProcess.waitFor()
            
            if (restoreProcess.exitValue() == 0) {
                sendLog("[INFO] Magisk restored successfully to the new slot")
                sendLog("[INFO] Please reboot device to boot from the new slot with Magisk")
                true
            } else {
                sendLog("[ERROR] Failed to restore Magisk")
                false
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Error during OTA slot switch: ${e.message}")
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
            val intent = packageManager.getLaunchIntentForPackage("com.magiskube.magisk")
            if (intent != null) {
                startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.magiskube.magisk")
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
                    // For content URIs, copy the file to cache directory
                    // This is necessary because content:// URIs don't have real file paths
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        // Get file name from URI or use timestamp
                        val fileName = getFileNameFromUri(uri) ?: "selected_file_${System.currentTimeMillis()}"
                        
                        // Create cache file
                        val cacheFile = File(cacheDir, fileName)
                        
                        // Copy content to cache file
                        val outputStream = cacheFile.outputStream()
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        
                        sendLog("[INFO] File copied to cache: ${cacheFile.absolutePath}")
                        cacheFile.absolutePath
                    } else {
                        sendLog("[ERROR] Failed to open input stream for URI: $uri")
                        null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            sendLog("[ERROR] getFilePathFromUri failed: ${e.message}")
            null
        }
    }
    
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            var fileName: String? = null
            if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
            }
            if (fileName == null) {
                uri.path?.substringAfterLast('/')
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Root Access Management via app_functions.sh ====================
    
    /**
     * Setup app_functions.sh script from assets
     * This should be called once when the app starts
     */
    private fun setupAppFunctionsScript(): Boolean {
        return try {
            // Check if script already exists and is up to date
            val existingScript = File(appFunctionsScriptPath)
            
            // Copy script from assets to /data/local/tmp/
            val inputStream = assets.open("app_functions.sh")
            val content = inputStream.readBytes()
            inputStream.close()
            
            // Write script using root shell
            val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $appFunctionsScriptPath"))
            val outputStream = writeProcess.outputStream
            outputStream.write(content)
            outputStream.close()
            writeProcess.waitFor()
            
            if (writeProcess.exitValue() != 0) {
                android.util.Log.e("MainActivity", "Failed to write app_functions.sh")
                return false
            }
            
            // Make script executable
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $appFunctionsScriptPath"))
            chmodProcess.waitFor()
            
            android.util.Log.d("MainActivity", "app_functions.sh setup completed")
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up app_functions.sh: ${e.message}")
            false
        }
    }
    
    /**
     * Execute a function from app_functions.sh script
     * @param functionName The name of the function to call
     * @param args Arguments to pass to the function
     * @return The output of the function execution
     */
    private fun executeAppFunction(functionName: String, vararg args: String): String {
        return try {
            // Ensure script exists
            if (!File(appFunctionsScriptPath).exists()) {
                setupAppFunctionsScript()
            }
            
            // Build the command
            val argsStr = args.joinToString(" ")
            val command = ". $appFunctionsScriptPath && $functionName $argsStr"
            
            android.util.Log.d("MainActivity", "Executing: $command")
            
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            
            val result = output.toString().trim()
            android.util.Log.d("MainActivity", "Function $functionName output: $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error executing $functionName: ${e.message}")
            ""
        }
    }
    
    /**
     * Get list of apps with root access via app_functions.sh
     * @return List of package names that have root access granted
     */
    private fun getRootAccessAppsViaScript(): List<String> {
        return try {
            val output = executeAppFunction("get_root_access_apps")
            if (output.isNotEmpty()) {
                output.split("\n").filter { it.trim().isNotEmpty() }
            } else {
                // Fallback to direct method
                getRootAllowedPackages()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting root access apps: ${e.message}")
            // Fallback to direct method
            getRootAllowedPackages()
        }
    }
    
    /**
     * Grant root access to an app via app_functions.sh
     * @param packageName The package name of the app
     * @return true if successful, false otherwise
     */
    private fun grantRootAccessViaScript(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        
        return try {
            val output = executeAppFunction("grant_root_access", packageName)
            val exitCode = executeAppFunctionExitCode("grant_root_access", packageName)
            
            if (exitCode == 0) {
                android.util.Log.d("MainActivity", "Granted root access to $packageName via script")
                true
            } else {
                // Fallback to direct method
                grantRootAccess(packageName)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error granting root access: ${e.message}")
            // Fallback to direct method
            grantRootAccess(packageName)
        }
    }
    
    /**
     * Revoke root access from an app via app_functions.sh
     * @param packageName The package name of the app
     * @return true if successful, false otherwise
     */
    private fun revokeRootAccessViaScript(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        
        return try {
            val exitCode = executeAppFunctionExitCode("revoke_root_access", packageName)
            
            if (exitCode == 0) {
                android.util.Log.d("MainActivity", "Revoked root access from $packageName via script")
                true
            } else {
                // Fallback to direct method
                revokeRootAccess(packageName)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error revoking root access: ${e.message}")
            // Fallback to direct method
            revokeRootAccess(packageName)
        }
    }
    
    /**
     * Check if an app has root access via app_functions.sh
     * @param packageName The package name of the app
     * @return true if the app has root access, false otherwise
     */
    private fun hasRootAccessViaScript(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        
        return try {
            val exitCode = executeAppFunctionExitCode("has_root_access", packageName)
            exitCode == 0
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking root access: ${e.message}")
            false
        }
    }
    
    /**
     * Get root policy for an app via app_functions.sh
     * @param packageName The package name of the app
     * @return The policy value (0=deny, 1=allow, 2=allow_forever, 3=allow_session)
     */
    private fun getRootPolicyViaScript(packageName: String): Int {
        if (packageName.isEmpty()) return 0
        
        return try {
            val output = executeAppFunction("get_root_policy", packageName)
            output.toIntOrNull() ?: 0
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting root policy: ${e.message}")
            0
        }
    }
    
    /**
     * List all root policies via app_functions.sh
     * @return List of "package_name:policy" strings
     */
    private fun listRootPoliciesViaScript(): List<String> {
        return try {
            val output = executeAppFunction("list_root_policies")
            if (output.isNotEmpty()) {
                output.split("\n").filter { it.trim().isNotEmpty() && it.contains(":") }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error listing root policies: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Execute a function from app_functions.sh and return the exit code
     * @param functionName The name of the function to call
     * @param args Arguments to pass to the function
     * @return The exit code of the function
     */
    private fun executeAppFunctionExitCode(functionName: String, vararg args: String): Int {
        return try {
            // Ensure script exists
            if (!File(appFunctionsScriptPath).exists()) {
                setupAppFunctionsScript()
            }
            
            // Build the command
            val argsStr = args.joinToString(" ")
            val command = ". $appFunctionsScriptPath && $functionName $argsStr; echo \$?"
            
            android.util.Log.d("MainActivity", "Executing with exit code: $command")
            
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                lines.add(line ?: "")
            }
            
            process.waitFor()
            
            // The last line should be the exit code
            val exitCode = lines.lastOrNull()?.trim()?.toIntOrNull() ?: 1
            android.util.Log.d("MainActivity", "Function $functionName exit code: $exitCode")
            exitCode
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error executing $functionName: ${e.message}")
            1
        }
    }
    
    // ==================== Module Installation ====================
    
    /**
     * Install a Magisk module from a zip file
     * @param zipPath The path to the module zip file
     * @return true if installation was successful, false otherwise
     */
    private fun installModule(zipPath: String): Boolean {
        return try {
            sendLog("[INFO] Starting module installation from: $zipPath")
            
            // Validate zip path
            if (zipPath.isEmpty()) {
                sendLog("[ERROR] No zip file path provided")
                return false
            }
            
            // Check if zip file exists
            val zipFile = File(zipPath)
            if (!zipFile.exists()) {
                sendLog("[ERROR] Zip file not found: $zipPath")
                return false
            }
            sendLog("[INFO] Zip file found: $zipPath")
            
            // Check root access
            if (!checkRootAccess()) {
                sendLog("[ERROR] Root access not available")
                return false
            }
            sendLog("[INFO] Root access confirmed")
            
            // Create temporary directory for module installation
            val tmpDir = "/data/local/tmp/module_install_${System.currentTimeMillis()}"
            sendLog("[INFO] Creating temp directory: $tmpDir")
            val processMkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $tmpDir"))
            processMkdir.waitFor()
            if (processMkdir.exitValue() != 0) {
                sendLog("[ERROR] Failed to create temp directory")
                return false
            }
            
            // Copy zip file to temp directory with proper quoting
            sendLog("[INFO] Copying zip file to temp directory")
            val escapedZipPath = zipPath.replace("'", "'\"'\"'")
            val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '$escapedZipPath' '$tmpDir/module.zip'"))
            processCp.waitFor()
            if (processCp.exitValue() != 0) {
                sendLog("[ERROR] Failed to copy zip file")
                return false
            }
            
            // Verify the zip file was copied correctly
            val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -la '$tmpDir/module.zip' && unzip -l '$tmpDir/module.zip' | head -5"))
            val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
            var verifyLine: String?
            while (verifyReader.readLine().also { verifyLine = it } != null) {
                sendLog("[DEBUG] Zip verification: $verifyLine")
            }
            verifyProcess.waitFor()
            if (verifyProcess.exitValue() != 0) {
                sendLog("[ERROR] Zip file verification failed - file may be corrupted or not a valid zip")
                return false
            }
            
            // Copy module_installer.sh from assets
            sendLog("[INFO] Copying module_installer.sh from assets")
            val installerPath = "$tmpDir/module_installer.sh"
            try {
                val inputStream = assets.open("module_installer.sh")
                val content = inputStream.readBytes()
                inputStream.close()
                
                val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $installerPath"))
                val outputStream = writeProcess.outputStream
                outputStream.write(content)
                outputStream.close()
                writeProcess.waitFor()
                
                if (writeProcess.exitValue() != 0) {
                    sendLog("[ERROR] Failed to write module_installer.sh")
                    return false
                }
            } catch (e: Exception) {
                sendLog("[ERROR] Failed to copy module_installer.sh from assets: ${e.message}")
                return false
            }
            
            // Copy util_functions.sh from assets (required by module_installer.sh)
            sendLog("[INFO] Copying util_functions.sh from assets")
            val utilFunctionsPath = "$tmpDir/util_functions.sh"
            try {
                val inputStream = assets.open("util_functions.sh")
                val content = inputStream.readBytes()
                inputStream.close()
                
                val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $utilFunctionsPath"))
                val outputStream = writeProcess.outputStream
                outputStream.write(content)
                outputStream.close()
                writeProcess.waitFor()
                
                if (writeProcess.exitValue() != 0) {
                    sendLog("[ERROR] Failed to write util_functions.sh")
                    return false
                }
            } catch (e: Exception) {
                sendLog("[ERROR] Failed to copy util_functions.sh from assets: ${e.message}")
                return false
            }
            
            // Make scripts executable
            sendLog("[INFO] Making scripts executable")
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $installerPath $utilFunctionsPath"))
            chmodProcess.waitFor()
            
            // Create wrapper script to execute module installation
            // The module_installer.sh expects parameters: dummy <outfd> <zipfile>
            val wrapperScript = """
                #!/system/bin/sh
                export BOOTMODE=true
                export TMPDIR="$tmpDir"
                export MAGISKBIN="/data/adb/magisk"
                
                cd "$tmpDir"
                
                # Verify files exist
                if [ ! -f ./module_installer.sh ] || [ ! -f ./util_functions.sh ]; then
                    echo "[ERROR] Required scripts not found in $tmpDir"
                    ls -la "$tmpDir"
                    exit 1
                fi
                
                # Source util_functions.sh
                . ./util_functions.sh
                
                # Execute module installer with proper parameters
                # Parameters: dummy <outfd> <zipfile>
                sh "$tmpDir/module_installer.sh" dummy 1 "$tmpDir/module.zip"
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
            
            sendLog("[INFO] Executing module installer")
            val installProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $wrapperPath"))
            
            // Read script output and send to log
            val scriptReader = BufferedReader(InputStreamReader(installProcess.inputStream))
            val errorReader = BufferedReader(InputStreamReader(installProcess.errorStream))
            
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
            
            installProcess.waitFor()
            val exitCode = installProcess.exitValue()
            
            // Cleanup temp directory
            sendLog("[INFO] Cleaning up temp directory")
            val cleanupProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -rf $tmpDir"))
            cleanupProcess.waitFor()
            
            if (exitCode == 0) {
                sendLog("[INFO] Module installed successfully!")
                sendLog("[INFO] Reboot may be required for the module to take effect")
                true
            } else {
                sendLog("[ERROR] Module installation failed with exit code: $exitCode")
                false
            }
        } catch (e: Exception) {
            sendLog("[ERROR] Exception during module installation: ${e.message}")
            false
        }
    }
}
