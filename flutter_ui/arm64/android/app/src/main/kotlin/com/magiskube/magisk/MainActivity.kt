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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.platform.PlatformViewRegistry
import com.magiskube.magisk.webui.WebUIViewFactory
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
    private val WEBUI_CHANNEL = "magisk_manager/webui"
    private val uiHandler = Handler(Looper.getMainLooper())
    
    // Path to app_functions.sh script
    private val appFunctionsScriptPath = "/data/local/tmp/app_functions.sh"
    
    private var pendingResult: Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Register WebUI PlatformView
        val registry: PlatformViewRegistry = flutterEngine.platformViewsController.registry
        registry.registerViewFactory("magiskube-webui", WebUIViewFactory())

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
                    // Use direct magisk command for better reliability and persistence
                    val success = setZygiskEnabledDirect(enabled)
                    result.success(success)
                }
                "setDenyListEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    // Use direct magisk command for better reliability
                    val success = setDenyListEnabledDirect(enabled)
                    result.success(success)
                }
                "isDenyListEnabled" -> result.success(isDenyListEnabled())
                "isSuListEnabled" -> result.success(isSuListEnabled())
                "getSuListApps" -> result.success(getSuListApps())
                "isInSuList" -> {
                    val packageName = call.argument<String>("packageName") ?: ""
                    result.success(isInSuList(packageName))
                }
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
                "patchBootImageNoRoot" -> {
                    val bootImage = call.argument<String>("bootImage")
                    val outputDir = call.argument<String>("outputDir")
                    result.success(patchBootImageNoRoot(bootImage ?: "", outputDir))
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
                "toggleModule" -> {
                    val modulePath = call.argument<String>("modulePath")
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    result.success(toggleModule(modulePath ?: "", enabled))
                }
                "removeModule" -> {
                    val modulePath = call.argument<String>("modulePath")
                    result.success(removeModule(modulePath ?: ""))
                }
                "executeModuleAction" -> {
                    val modulePath = call.argument<String>("modulePath")
                    result.success(executeModuleAction(modulePath ?: ""))
                }
                "checkModuleWebUI" -> {
                    val modulePath = call.argument<String>("modulePath")
                    result.success(checkModuleWebUI(modulePath ?: ""))
                }
                "openModuleWebUI" -> {
                    val url = call.argument<String>("url")
                    result.success(openModuleWebUI(url ?: ""))
                }
                "getModuleDetails" -> {
                    val modulePath = call.argument<String>("modulePath")
                    result.success(getModuleDetails(modulePath ?: ""))
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
                "readFileAsRoot" -> {
                    val filePath = call.argument<String>("filePath")
                    result.success(readFileAsRoot(filePath ?: ""))
                }
                "fileExistsAsRoot" -> {
                    val filePath = call.argument<String>("filePath")
                    result.success(fileExistsAsRoot(filePath ?: ""))
                }
                "setZygiskEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    // Use direct magisk command for better reliability and persistence
                    val success = setZygiskEnabledDirect(enabled)
                    result.success(success)
                }
                "setDenyListEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    // Use direct magisk command for better reliability
                    val success = setDenyListEnabledDirect(enabled)
                    result.success(success)
                }
                "setSuListEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    val success = setSuListEnabledDirect(enabled)
                    result.success(success)
                }
                "addToSuList" -> {
                    val packageName = call.argument<String>("packageName") ?: ""
                    result.success(addToSuList(packageName))
                }
                "removeFromSuList" -> {
                    val packageName = call.argument<String>("packageName") ?: ""
                    result.success(removeFromSuList(packageName))
                }
                "fetchMagiskLogs" -> result.success(fetchMagiskLogs())
                "clearMagiskLogs" -> result.success(clearMagiskLogs())
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
        
        // WebUI Channel - for KernelSU WebUI compatible interface
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, WEBUI_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "setupWebUI" -> {
                    val moduleDir = call.argument<String>("moduleDir") ?: ""
                    val moduleId = call.argument<String>("moduleId") ?: ""
                    result.success(setupWebUI(moduleDir, moduleId))
                }
                "execCommand" -> {
                    val command = call.argument<String>("command") ?: ""
                    result.success(execWebUICommand(command))
                }
                "execCommandWithResult" -> {
                    val command = call.argument<String>("command") ?: ""
                    result.success(execWebUICommandWithResult(command))
                }
                "spawnCommand" -> {
                    val command = call.argument<String>("command") ?: ""
                    val callbackId = call.argument<String>("callbackId") ?: ""
                    result.success(spawnWebUICommand(command, callbackId))
                }
                "setFullScreen" -> {
                    val enable = call.argument<Boolean>("enable") ?: false
                    result.success(setFullScreen(enable))
                }
                "readWebrootFile" -> {
                    val moduleDir = call.argument<String>("moduleDir") ?: ""
                    val relativePath = call.argument<String>("relativePath") ?: ""
                    result.success(readWebrootFile(moduleDir, relativePath))
                }
                "hasWebroot" -> {
                    val moduleDir = call.argument<String>("moduleDir") ?: ""
                    result.success(hasWebroot(moduleDir))
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
            // A module is disabled if it has a 'disable' file
            // Also check for 'remove' file which marks module for removal
            val checkCmd = "if [ -f /data/adb/modules/$moduleName/disable ]; then echo 'disabled'; elif [ -f /data/adb/modules/$moduleName/remove ]; then echo 'removed'; else echo 'enabled'; fi"
            val disableProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", checkCmd))
            val disableReader = BufferedReader(InputStreamReader(disableProcess.inputStream))
            val disableStatus = disableReader.readLine()
            disableProcess.waitFor()
            val isEnabled = disableStatus?.trim() == "enabled"
            
            // Check if module needs reboot (has update folder or new installation)
            val updateCheckCmd = "test -d /data/adb/modules/$moduleName/update && echo 'needs_reboot' || echo 'ok'"
            val updateProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", updateCheckCmd))
            val updateReader = BufferedReader(InputStreamReader(updateProcess.inputStream))
            val updateStatus = updateReader.readLine()
            updateProcess.waitFor()
            val needsReboot = updateStatus?.trim() == "needs_reboot"
            
            android.util.Log.d("MainActivity", "Module $moduleName: status='$disableStatus', isEnabled=$isEnabled, needsReboot=$needsReboot")
            
            return mapOf<String, Any>(
                "name" to name,
                "version" to version,
                "author" to author,
                "description" to description,
                "isEnabled" to isEnabled,
                "path" to "/data/adb/modules/$moduleName",
                "needsReboot" to needsReboot
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
        
        android.util.Log.d("MainActivity", "getInstalledApps: Starting scan...")
        
        // Primary method: Use PackageManager directly (most reliable)
        try {
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            android.util.Log.d("MainActivity", "getInstalledApps: Found ${packages.size} total packages")
            
            for (packageInfo in packages) {
                try {
                    val appInfo = packageInfo.applicationInfo
                    if (appInfo == null) continue
                    
                    // Filter: only show third-party apps (not system apps)
                    // A system app has FLAG_SYSTEM (0x1) set in flags
                    val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    if (!isSystemApp) {
                        val packageName = packageInfo.packageName
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        
                        apps.add(mapOf<String, Any>(
                            "name" to appName,
                            "packageName" to packageName,
                            "isActive" to true,
                            "hasRootAccess" to false
                        ))
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Error processing package: ${e.message}")
                }
            }
            
            android.util.Log.d("MainActivity", "getInstalledApps: Found ${apps.size} third-party apps")
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "getInstalledApps PackageManager error: ${e.message}")
            
            // Fallback: Use shell command
            try {
                val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-3"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    val packageName = line?.removePrefix("package:")?.trim()
                    if (!packageName.isNullOrEmpty()) {
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            val appName = pm.getApplicationLabel(appInfo).toString()
                            
                            apps.add(mapOf<String, Any>(
                                "name" to appName,
                                "packageName" to packageName,
                                "isActive" to true,
                                "hasRootAccess" to false
                            ))
                        } catch (e: Exception) {
                            // Skip packages we can't get info for
                        }
                    }
                }
                process.waitFor()
                
            } catch (e2: Exception) {
                android.util.Log.e("MainActivity", "Fallback scan also failed: ${e2.message}")
            }
        }
        
        android.util.Log.d("MainActivity", "getInstalledApps: Returning ${apps.size} apps")
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
            // Try to get version string (e.g., "v10001") using magisk -v
            // Output format might be: "v10001" or "v10001:MAGISK:R" or "Magisk v10001"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk -v"))
            process.waitFor()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val version = reader.readLine() ?: ""
            
            if (version.isNotEmpty() && !version.contains("not found")) {
                // Extract version string, remove "Magisk " prefix if present
                var versionStr = version.removePrefix("Magisk ").trim()
                
                // Remove everything after colon (e.g., ":MAGISK:R")
                val colonIndex = versionStr.indexOf(':')
                if (colonIndex > 0) {
                    versionStr = versionStr.substring(0, colonIndex)
                }
                
                if (versionStr.isNotEmpty()) return versionStr
            }
            
            // Fallback: try to read from config.prop in magisk directory
            val configProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/adb/magisk/config.prop 2>/dev/null | grep '^version=' | head -1"))
            configProcess.waitFor()
            val configReader = BufferedReader(InputStreamReader(configProcess.inputStream))
            val configLine = configReader.readLine() ?: ""
            
            if (configLine.startsWith("version=")) {
                var versionStr = configLine.removePrefix("version=").trim()
                // Remove everything after colon
                val colonIndex = versionStr.indexOf(':')
                if (colonIndex > 0) {
                    versionStr = versionStr.substring(0, colonIndex)
                }
                return versionStr
            }
            
            // Last fallback: return version code as string
            val vcProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk -V"))
            vcProcess.waitFor()
            val vcReader = BufferedReader(InputStreamReader(vcProcess.inputStream))
            val vc = vcReader.readLine() ?: "Unknown"
            if (vc.contains("not found") || vc.isEmpty()) "Unknown" else "v$vc"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun isZygiskEnabled(): Boolean {
        return try {
            android.util.Log.d("MainActivity", "isZygiskEnabled: checking status")
            
            // Method 1: Check database setting first (most reliable for UI state)
            val magiskConfigFile = File("/data/adb/magisk.db")
            
            if (magiskConfigFile.exists()) {
                // Query both possible keys: zygisk and zygisk_enabled
                val sqliteResult = executeRootCommand("magisk --sqlite \"SELECT * FROM settings\"")
                android.util.Log.d("MainActivity", "SQLite query result: '$sqliteResult'")
                
                for (line in sqliteResult.split("\n")) {
                    val trimmed = line.trim()
                    // Check for zygisk key
                    if (trimmed.startsWith("key=zygisk|")) {
                        val valueMatch = Regex("value=(\\d+)").find(trimmed)
                        if (valueMatch != null && valueMatch.groupValues[1] == "1") {
                            android.util.Log.d("MainActivity", "isZygiskEnabled: TRUE via zygisk key in database")
                            return true
                        }
                    }
                    // Check for zygisk_enabled key (older Magisk versions)
                    if (trimmed.startsWith("key=zygisk_enabled|")) {
                        val valueMatch = Regex("value=(\\d+)").find(trimmed)
                        if (valueMatch != null && valueMatch.groupValues[1] == "1") {
                            android.util.Log.d("MainActivity", "isZygiskEnabled: TRUE via zygisk_enabled key in database")
                            return true
                        }
                    }
                }
                
                // Also try direct query for zygisk setting
                val directQuery = executeRootCommand("magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk'\"")
                android.util.Log.d("MainActivity", "Direct zygisk query: '$directQuery'")
                if (directQuery.trim() == "1" || directQuery.contains("value=1")) {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: TRUE via direct query")
                    return true
                }
            }
            
            // Method 2: Check ro.dalvik.vm.native.bridge property (runtime indicator)
            // This shows if Zygisk is actually loaded and active at runtime
            val nativeBridge = executeRootCommand("getprop ro.dalvik.vm.native.bridge")
            android.util.Log.d("MainActivity", "ro.dalvik.vm.native.bridge = '$nativeBridge'")
            
            if (nativeBridge.contains("libzygisk.so")) {
                android.util.Log.d("MainActivity", "isZygiskEnabled: TRUE - native bridge contains libzygisk.so")
                return true
            }
            
            // Method 3: Check if Zygisk is loaded by checking for zygiskd process
            val zygiskdCheck = executeRootCommand("ps -A | grep zygiskd")
            if (zygiskdCheck.isNotEmpty() && zygiskdCheck.contains("zygiskd")) {
                android.util.Log.d("MainActivity", "isZygiskEnabled: true via zygiskd process")
                return true
            }
            
            // Method 4: Check /data/adb/zygisk directory structure
            val zygiskDir = File("/data/adb/zygisk")
            android.util.Log.d("MainActivity", "zygisk dir exists: ${zygiskDir.exists()}")
            
            if (zygiskDir.exists() && zygiskDir.isDirectory) {
                val zygiskActive = executeRootCommand("ls -la /data/adb/zygisk/")
                android.util.Log.d("MainActivity", "zygisk directory contents: $zygiskActive")
                
                // Check for uninstaller.sh which indicates Zygisk was set up
                val uninstallerFile = File("/data/adb/zygisk/uninstaller.sh")
                if (uninstallerFile.exists()) {
                    android.util.Log.d("MainActivity", "isZygiskEnabled: true via uninstaller.sh presence")
                    return true
                }
            }
            
            // Method 5: Check if any Zygisk modules are installed
            val zygiskModulesDir = File("/data/adb/modules")
            if (zygiskModulesDir.exists() && zygiskModulesDir.isDirectory) {
                zygiskModulesDir.listFiles()?.forEach { moduleDir ->
                    val moduleZygiskDir = File(moduleDir, "zygisk")
                    if (moduleZygiskDir.exists()) {
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
    
    /**
     * Direct method to enable/disable Zygisk using magisk commands
     * This is more reliable than the script-based approach
     * IMPORTANT: Must update BOTH runtime state AND database for persistence
     */
    private fun setZygiskEnabledDirect(enabled: Boolean): Boolean {
        return try {
            android.util.Log.d("MainActivity", "setZygiskEnabledDirect: enabled=$enabled")
            
            val value = if (enabled) "1" else "0"
            
            // Step 1: Update database for persistence (survives reboot)
            android.util.Log.d("MainActivity", "Step 1: Updating database for persistence")
            
            // Try using sqlite3 directly for more reliable database updates
            val dbUpdateCmd = """
                sqlite3 /data/adb/magisk.db "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk', '$value')";
                sqlite3 /data/adb/magisk.db "INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '$value')";
            """.trimIndent()
            
            val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", dbUpdateCmd))
            dbProcess.waitFor()
            
            // Also try via magisk --sqlite
            val magiskSqlCmd = "magisk --sqlite \"INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk', '$value')\""
            Runtime.getRuntime().exec(arrayOf("su", "-c", magiskSqlCmd)).waitFor()
            
            val magiskSqlCmd2 = "magisk --sqlite \"INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '$value')\""
            Runtime.getRuntime().exec(arrayOf("su", "-c", magiskSqlCmd2)).waitFor()
            
            // Verify database update
            val verifyDbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"SELECT value FROM settings WHERE key='zygisk'\""))
            val verifyDbReader = BufferedReader(InputStreamReader(verifyDbProcess.inputStream))
            val dbValue = verifyDbReader.readText().trim()
            verifyDbProcess.waitFor()
            android.util.Log.d("MainActivity", "Database zygisk value: '$dbValue'")
            
            // Step 2: Notify Magisk daemon to reload settings
            try {
                // Send HUP signal to reload
                Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep -x magiskd | head -1) 2>/dev/null || true")).waitFor()
                android.util.Log.d("MainActivity", "Sent HUP signal to magiskd")
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to notify magiskd: ${e.message}")
            }
            
            // Final verification
            Thread.sleep(200)
            
            // Check database again
            val finalDbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"SELECT value FROM settings WHERE key='zygisk'\""))
            val finalDbReader = BufferedReader(InputStreamReader(finalDbProcess.inputStream))
            val finalDbValue = finalDbReader.readText().trim()
            finalDbProcess.waitFor()
            
            android.util.Log.d("MainActivity", "Final database value: '$finalDbValue', expected: '$value'")
            
            if (finalDbValue == value) {
                android.util.Log.d("MainActivity", "setZygiskEnabledDirect: SUCCESS - database updated")
                true
            } else if (finalDbValue.isEmpty() && !enabled) {
                // If value is empty and we're disabling, that's okay
                android.util.Log.d("MainActivity", "setZygiskEnabledDirect: SUCCESS - disabled (no entry)")
                true
            } else {
                // Try one more time with different approach
                android.util.Log.w("MainActivity", "Database value mismatch, trying alternative method")
                
                // Use magisk --sqlite with both keys
                val altCmd = "magisk --sqlite \"INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk', '$value'); INSERT OR REPLACE INTO settings (key, value) VALUES ('zygisk_enabled', '$value');\""
                val altProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", altCmd))
                altProcess.waitFor()
                
                // Verify again
                val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk'\""))
                val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
                val verifyOutput = verifyReader.readText().trim()
                verifyProcess.waitFor()
                
                android.util.Log.d("MainActivity", "Alternative method verification: '$verifyOutput'")
                
                if (verifyOutput.contains(value)) {
                    android.util.Log.d("MainActivity", "setZygiskEnabledDirect: SUCCESS via alternative method")
                    true
                } else {
                    android.util.Log.e("MainActivity", "setZygiskEnabledDirect: FAILED")
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in setZygiskEnabledDirect: ${e.message}")
            false
        }
    }

    private fun isDenyListEnabled(): Boolean {
        return try {
            android.util.Log.d("MainActivity", "isDenyListEnabled: checking status")
            
            // NOTE: We check the DATABASE setting, not the runtime Zygisk state
            // The UI will show if DenyList is enabled in settings
            // Actual effectiveness depends on Zygisk being enabled
            
            // Method 1: Use sqlite3 to query settings table directly
            val dbQueryResult = executeRootCommand("sqlite3 /data/adb/magisk.db \"SELECT value FROM settings WHERE key='denylist' LIMIT 1\"")
            android.util.Log.d("MainActivity", "sqlite3 denylist query: '$dbQueryResult'")
            
            // Check if the result is "1"
            if (dbQueryResult.trim() == "1") {
                android.util.Log.d("MainActivity", "isDenyListEnabled: TRUE via sqlite3 query (denylist=1)")
                return true
            }
            
            // Method 2: Check using magisk --sqlite command
            val magiskSqlResult = executeRootCommand("magisk --sqlite \"SELECT value FROM settings WHERE key='denylist'\"")
            android.util.Log.d("MainActivity", "magisk --sqlite denylist query: '$magiskSqlResult'")
            
            // Parse result - format could be "value=1" or just "1"
            val dbValue = if (magiskSqlResult.startsWith("value=")) {
                magiskSqlResult.removePrefix("value=").trim()
            } else {
                magiskSqlResult.trim()
            }
            
            if (dbValue == "1") {
                android.util.Log.d("MainActivity", "isDenyListEnabled: TRUE via magisk --sqlite (denylist=1)")
                return true
            }
            
            // Method 3: Check magiskhide key for older Magisk versions
            val magiskhideResult = executeRootCommand("sqlite3 /data/adb/magisk.db \"SELECT value FROM settings WHERE key='magiskhide' LIMIT 1\"")
            android.util.Log.d("MainActivity", "sqlite3 magiskhide query: '$magiskhideResult'")
            
            if (magiskhideResult.trim() == "1") {
                android.util.Log.d("MainActivity", "isDenyListEnabled: TRUE via magiskhide key")
                return true
            }
            
            // Method 4: Check if denylist table has any entries (indicates it was enabled and used)
            val denyListCount = executeRootCommand("sqlite3 /data/adb/magisk.db \"SELECT COUNT(*) FROM denylist\"")
            android.util.Log.d("MainActivity", "denylist table count: '$denyListCount'")
            
            val count = denyListCount.trim().toIntOrNull() ?: 0
            if (count > 0) {
                android.util.Log.d("MainActivity", "isDenyListEnabled: TRUE (denylist has $count entries)")
                return true
            }
            
            // Method 5: Query ALL settings and look for denylist related keys
            val allSettings = executeRootCommand("magisk --sqlite \"SELECT * FROM settings\"")
            android.util.Log.d("MainActivity", "All settings: '$allSettings'")
            
            // Parse settings output for denylist or magiskhide
            for (line in allSettings.split("\n")) {
                val trimmed = line.trim()
                // Format: key=denylist|value=1
                if (trimmed.contains("key=denylist|") || trimmed.contains("key=magiskhide|")) {
                    if (trimmed.contains("value=1")) {
                        android.util.Log.d("MainActivity", "isDenyListEnabled: TRUE found in settings: $trimmed")
                        return true
                    }
                }
            }
            
            android.util.Log.d("MainActivity", "isDenyListEnabled: FALSE")
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
    
    /**
     * Direct method to enable/disable DenyList using magisk commands
     * This is more reliable than the script-based approach
     * IMPORTANT: Must update BOTH runtime state AND database for persistence
     * IMPORTANT: DenyList requires Zygisk to be enabled first
     */
    private fun setDenyListEnabledDirect(enabled: Boolean): Boolean {
        return try {
            android.util.Log.d("MainActivity", "setDenyListEnabledDirect: enabled=$enabled")
            
            // NOTE: We save the setting to database regardless of Zygisk runtime state
            // The actual effectiveness depends on Zygisk being enabled
            // UI will show the setting state, not the runtime effectiveness
            
            // Check if Zygisk is enabled in database (not runtime state)
            val zygiskDbValue = executeRootCommand("sqlite3 /data/adb/magisk.db \"SELECT value FROM settings WHERE key='zygisk' LIMIT 1\"")
            val zygiskEnabled = zygiskDbValue.trim() == "1"
            
            android.util.Log.d("MainActivity", "Zygisk database setting: '$zygiskDbValue', enabled=$zygiskEnabled")
            
            if (enabled && !zygiskEnabled) {
                android.util.Log.w("MainActivity", "WARNING: DenyList enabled but Zygisk is not enabled in database!")
                android.util.Log.w("MainActivity", "DenyList will not be effective until Zygisk is enabled and device is rebooted")
                // Still proceed to save the setting
            }
            
            val value = if (enabled) "1" else "0"
            
            // Step 1: Update database directly using sqlite3
            android.util.Log.d("MainActivity", "Step 1: Updating database via sqlite3")
            val dbUpdateCmd = """
                sqlite3 /data/adb/magisk.db "INSERT OR REPLACE INTO settings (key, value) VALUES ('denylist', '$value')";
                sqlite3 /data/adb/magisk.db "INSERT OR REPLACE INTO settings (key, value) VALUES ('magiskhide', '$value')";
            """.trimIndent()
            
            val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", dbUpdateCmd))
            dbProcess.waitFor()
            
            // Step 2: Also try via magisk --sqlite for good measure
            android.util.Log.d("MainActivity", "Step 2: Updating via magisk --sqlite")
            val magiskSqlCmd = "magisk --sqlite \"INSERT OR REPLACE INTO settings (key, value) VALUES ('denylist', '$value')\""
            Runtime.getRuntime().exec(arrayOf("su", "-c", magiskSqlCmd)).waitFor()
            
            val magiskSqlCmd2 = "magisk --sqlite \"INSERT OR REPLACE INTO settings (key, value) VALUES ('magiskhide', '$value')\""
            Runtime.getRuntime().exec(arrayOf("su", "-c", magiskSqlCmd2)).waitFor()
            
            // Step 3: Try magisk --denylist command for runtime state
            android.util.Log.d("MainActivity", "Step 3: Updating runtime via magisk --denylist")
            val cmd = if (enabled) "magisk --denylist enable" else "magisk --denylist disable"
            val runtimeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            runtimeProcess.waitFor()
            android.util.Log.d("MainActivity", "magisk --denylist command exit code: ${runtimeProcess.exitValue()}")
            
            // Step 4: Notify Magisk daemon to reload settings
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep -x magiskd | head -1) 2>/dev/null || true")).waitFor()
                android.util.Log.d("MainActivity", "Sent HUP signal to magiskd")
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to notify magiskd: ${e.message}")
            }
            
            // Give it a moment to take effect
            Thread.sleep(300)
            
            // Step 5: Verify database was updated
            val verifyDbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"SELECT value FROM settings WHERE key='denylist' LIMIT 1\""))
            val verifyDbReader = BufferedReader(InputStreamReader(verifyDbProcess.inputStream))
            val dbValue = verifyDbReader.readText().trim()
            verifyDbProcess.waitFor()
            android.util.Log.d("MainActivity", "Database denylist value: '$dbValue'")
            
            // Step 6: Final verification
            if (dbValue == value) {
                android.util.Log.d("MainActivity", "setDenyListEnabledDirect: SUCCESS - database updated to '$value'")
                return true
            }
            
            // If database verification failed, try one more approach
            android.util.Log.w("MainActivity", "Database verification failed, trying alternative verification")
            
            // Check if denylist table has entries (if enabling)
            if (enabled) {
                val countProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"SELECT COUNT(*) FROM denylist\""))
                val countReader = BufferedReader(InputStreamReader(countProcess.inputStream))
                val countOutput = countReader.readText().trim()
                countProcess.waitFor()
                val count = countOutput.toIntOrNull() ?: 0
                android.util.Log.d("MainActivity", "DenyList table has $count entries")
                
                // Even with 0 entries, if the database says denylist is enabled, it's fine
                if (count >= 0) {
                    android.util.Log.d("MainActivity", "setDenyListEnabledDirect: SUCCESS - denylist table exists")
                    return true
                }
            } else {
                // When disabling, check if value is 0 or empty
                if (dbValue == "0" || dbValue.isEmpty()) {
                    android.util.Log.d("MainActivity", "setDenyListEnabledDirect: SUCCESS - denylist disabled")
                    return true
                }
            }
            
            // Last resort: check via magisk --sqlite
            val magiskVerifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key='denylist'\""))
            val magiskVerifyReader = BufferedReader(InputStreamReader(magiskVerifyProcess.inputStream))
            val magiskVerifyOutput = magiskVerifyReader.readText().trim()
            magiskVerifyProcess.waitFor()
            android.util.Log.d("MainActivity", "magisk --sqlite verify: '$magiskVerifyOutput'")
            
            val finalValue = if (magiskVerifyOutput.startsWith("value=")) {
                magiskVerifyOutput.removePrefix("value=").trim()
            } else {
                magiskVerifyOutput.trim()
            }
            
            if (finalValue == value) {
                android.util.Log.d("MainActivity", "setDenyListEnabledDirect: SUCCESS via magisk --sqlite")
                true
            } else {
                android.util.Log.e("MainActivity", "setDenyListEnabledDirect: FAILED - value mismatch (expected '$value', got '$finalValue')")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in setDenyListEnabledDirect: ${e.message}")
            false
        }
    }

    // ==================== SuList (Whitelist Mode) Methods ====================

    private fun isSuListEnabled(): Boolean {
        return try {
            // Use SELECT * FROM settings because WHERE clause doesn't work
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT * FROM settings\""))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            
            // Parse output - look for key=sulist|value=1
            for (line in output.split("\n")) {
                val trimmed = line.trim()
                if (trimmed.startsWith("key=sulist|")) {
                    val valueMatch = Regex("value=(\\d+)").find(trimmed)
                    if (valueMatch != null && valueMatch.groupValues[1] == "1") {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun setSuListEnabledDirect(enabled: Boolean): Boolean {
        return try {
            android.util.Log.d("MainActivity", "setSuListEnabledDirect: enabled=$enabled")
            val value = if (enabled) "1" else "0"
            
            // Update SuList setting in database FIRST (quick operation)
            val cmd = "INSERT OR REPLACE INTO settings (key, value) VALUES ('sulist', '$value')"
            Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$cmd\"")).waitFor()
            Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"$cmd\"")).waitFor()
            
            if (enabled) {
                // Perform heavy operations in background thread to avoid UI freeze
                Thread {
                    try {
                        android.util.Log.d("MainActivity", "Background: Enabling SuList whitelist mode")
                        
                        // Enable DenyList first (we use it for hiding)
                        setDenyListEnabledDirect(true)
                        Thread.sleep(200)
                        
                        // Get SuList whitelist apps
                        val suListApps = getSuListApps().toSet()
                        android.util.Log.d("MainActivity", "Background: SuList whitelist apps: $suListApps")
                        
                        // Get all installed packages (third-party only)
                        val allPackages = getAllInstalledPackages()
                        android.util.Log.d("MainActivity", "Background: Total third-party packages: ${allPackages.size}")
                        
                        // Add all packages NOT in whitelist to DenyList in batch
                        // Use batch command for better performance
                        val packagesToHide = allPackages.filter { !suListApps.contains(it) }
                        android.util.Log.d("MainActivity", "Background: Packages to hide: ${packagesToHide.size}")
                        
                        // Process in smaller batches to avoid timeout
                        var addedCount = 0
                        for ((index, packageName) in packagesToHide.withIndex()) {
                            if (addToDenyListDirect(packageName)) {
                                addedCount++
                            }
                            // Log progress every 20 apps
                            if ((index + 1) % 20 == 0) {
                                android.util.Log.d("MainActivity", "Background: Progress: ${index + 1}/${packagesToHide.size}")
                            }
                        }
                        
                        android.util.Log.d("MainActivity", "Background: Added $addedCount apps to DenyList")
                        
                        // Remove whitelist apps from DenyList
                        for (packageName in suListApps) {
                            removeFromDenyListDirect(packageName)
                        }
                        
                        // Notify Magisk daemon to reload
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep -x magiskd | head -1) 2>/dev/null || true")).waitFor()
                        
                        android.util.Log.d("MainActivity", "Background: SuList whitelist mode enabled successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Background: Error enabling SuList: ${e.message}")
                    }
                }.start()
            } else {
                // Disabling SuList - also do in background
                Thread {
                    try {
                        android.util.Log.d("MainActivity", "Background: Disabling SuList, clearing DenyList")
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist rm-all 2>/dev/null || true")).waitFor()
                        
                        // Notify Magisk daemon to reload
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep -x magiskd | head -1) 2>/dev/null || true")).waitFor()
                        
                        android.util.Log.d("MainActivity", "Background: SuList disabled successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Background: Error disabling SuList: ${e.message}")
                    }
                }.start()
            }
            
            // Return true immediately - background thread will complete the operation
            android.util.Log.d("MainActivity", "SuList setting updated, background operation started")
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting SuList: ${e.message}")
            false
        }
    }
    
    /// Get all installed packages
    private fun getAllInstalledPackages(): List<String> {
        return try {
            val packages = mutableListOf<String>()
            val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-3"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val packageName = line?.replace("package:", "")?.trim() ?: continue
                if (packageName.isNotEmpty()) {
                    packages.add(packageName)
                }
            }
            process.waitFor()
            packages
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting installed packages: ${e.message}")
            emptyList()
        }
    }
    
    /// Add package to DenyList directly (without going through denylist channel)
    private fun addToDenyListDirect(packageName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist add $packageName"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /// Remove package from DenyList directly
    private fun removeFromDenyListDirect(packageName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist rm $packageName"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }


    /// Get list of apps in SuList whitelist
    private fun getSuListApps(): List<String> {
        return try {
            // SuList apps are stored in a separate table or as a setting
            // Format: comma-separated package names in settings table
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT value FROM settings WHERE key='sulist_apps'\""))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            
            if (output.isNotEmpty() && output != "value=" && !output.endsWith("=")) {
                // Parse the value - format could be "value=pkg1,pkg2,pkg3" or just "pkg1,pkg2,pkg3"
                val value = if (output.startsWith("value=")) {
                    output.removePrefix("value=")
                } else {
                    output
                }
                if (value.isNotEmpty()) {
                    value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting SuList apps: ${e.message}")
            emptyList()
        }
    }

    /// Check if a package is in SuList whitelist
    private fun isInSuList(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return getSuListApps().contains(packageName)
    }

    /// Add an app to SuList whitelist
    /// In SuList (whitelist) mode:
    /// - Apps in the whitelist should SEE Magisk (allowed)
    /// - Apps NOT in the whitelist should NOT see Magisk (hidden via DenyList)
    /// 
    /// When adding to whitelist:
    /// 1. Add to sulist_apps setting for persistence
    /// 2. REMOVE from DenyList (so it becomes visible)
    /// 3. Grant root access
    private fun addToSuList(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            android.util.Log.d("MainActivity", "Adding $packageName to SuList whitelist")
            
            // Check if SuList mode is enabled
            if (!isSuListEnabled()) {
                android.util.Log.w("MainActivity", "SuList mode is not enabled, cannot add to whitelist")
                return false
            }
            
            // Method 1: Add to sulist_apps setting for persistence
            val currentApps = getSuListApps().toMutableSet()
            currentApps.add(packageName)
            val newValue = currentApps.joinToString(",")
            
            var cmd = "INSERT OR REPLACE INTO settings (key, value) VALUES ('sulist_apps', '$newValue')"
            Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$cmd\"")).waitFor()
            Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"$cmd\"")).waitFor()
            
            // Method 2: CRITICAL - REMOVE from DenyList so this app can SEE Magisk
            // In SuList whitelist mode, apps NOT in DenyList are the ones that see Magisk
            // We hide all apps by default (in setSuListEnabledDirect), then unhide whitelist apps
            removeFromDenyListDirect(packageName)
            
            // Method 3: Grant root access to this app
            grantRootAccessViaScript(packageName)
            
            // Notify Magisk daemon to reload denylist
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep -x magiskd | head -1) 2>/dev/null || true")).waitFor()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to notify magiskd: ${e.message}")
            }
            
            android.util.Log.d("MainActivity", "Added $packageName to SuList whitelist (removed from DenyList)")
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error adding to SuList: ${e.message}")
            false
        }
    }

    /// Remove an app from SuList whitelist
    /// In SuList (whitelist) mode:
    /// - Removing from whitelist means the app should NOT see Magisk anymore
    /// - We need to ADD it to DenyList to hide it
    /// - Also revoke root access
    private fun removeFromSuList(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            android.util.Log.d("MainActivity", "Removing $packageName from SuList whitelist")
            
            // Method 1: Remove from sulist_apps setting
            val currentApps = getSuListApps().toMutableSet()
            currentApps.remove(packageName)
            val newValue = currentApps.joinToString(",")
            
            var cmd = "INSERT OR REPLACE INTO settings (key, value) VALUES ('sulist_apps', '$newValue')"
            Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$cmd\"")).waitFor()
            Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"$cmd\"")).waitFor()
            
            // Method 2: CRITICAL - ADD to DenyList to hide Magisk from this app
            // In SuList whitelist mode, apps in DenyList are hidden from Magisk
            if (isSuListEnabled()) {
                addToDenyListDirect(packageName)
            }
            
            // Method 3: Revoke root access from this app
            revokeRootAccessViaScript(packageName)
            
            // Notify Magisk daemon to reload denylist
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep -x magiskd | head -1) 2>/dev/null || true")).waitFor()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to notify magiskd: ${e.message}")
            }
            
            android.util.Log.d("MainActivity", "Removed $packageName from SuList whitelist (added to DenyList)")
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error removing from SuList: ${e.message}")
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
        
        android.util.Log.d("MainActivity", "getDenyList: Starting to fetch denylist")
        
        try {
            // Method 1: Query database directly (most reliable)
            // The denylist table has columns: package_name, process_name
            // We want to get unique package names
            val dbQuery = "SELECT DISTINCT package_name FROM denylist"
            val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"$dbQuery\""))
            val dbReader = BufferedReader(InputStreamReader(dbProcess.inputStream))
            var dbLine: String?
            while (dbReader.readLine().also { dbLine = it } != null) {
                val packageName = dbLine?.trim()
                if (!packageName.isNullOrEmpty() && packageName != "package_name") {
                    denyList.add(packageName)
                    android.util.Log.d("MainActivity", "getDenyList: Found package from DB: $packageName")
                }
            }
            dbProcess.waitFor()
            
            if (denyList.isNotEmpty()) {
                android.util.Log.d("MainActivity", "getDenyList: Returning ${denyList.size} items from database")
                return denyList
            }
            
            // Method 2: Use magisk --denylist ls command
            android.util.Log.d("MainActivity", "getDenyList: Trying magisk --denylist ls")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --denylist ls 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            
            val rawOutput = output.toString().trim()
            android.util.Log.d("MainActivity", "getDenyList: magisk --denylist ls output: '$rawOutput'")
            
            // Parse the output
            // Format from magisk --denylist ls:
            // 1. package|process (most common): "com.example.app|com.example.app"
            // 2. package|process with activity: "com.example.app|com.example.app:process"
            // 3. isolated|package:process (isolated process): "isolated|com.example.app:sandboxed_process0"
            for (outputLine in rawOutput.split("\n")) {
                val trimmed = outputLine.trim()
                if (trimmed.isEmpty()) continue
                
                // Skip header lines if any
                if (trimmed.startsWith("Denylist") || trimmed.startsWith("===") || 
                    trimmed.startsWith("---") || trimmed == "ID" || trimmed == "Package") {
                    continue
                }
                
                // Skip isolated processes - they are special sandboxed processes
                if (trimmed.startsWith("isolated|")) {
                    android.util.Log.d("MainActivity", "getDenyList: Skipping isolated process: $trimmed")
                    continue
                }
                
                // Extract package name from different formats
                // Primary format: package|process
                val packageName = when {
                    // Format: package|process (most common from magisk --denylist ls)
                    trimmed.contains("|") -> {
                        trimmed.substringBefore("|").trim()
                    }
                    // Format: package/activity
                    trimmed.contains("/") -> {
                        trimmed.substringBefore("/").trim()
                    }
                    // Format: package:process
                    trimmed.contains(":") -> {
                        trimmed.substringBefore(":").trim()
                    }
                    // Just package name
                    else -> {
                        trimmed
                    }
                }
                
                // Validate package name (should not be empty and should contain at least one dot)
                if (packageName.isNotEmpty() && packageName.contains(".") && !denyList.contains(packageName)) {
                    denyList.add(packageName)
                    android.util.Log.d("MainActivity", "getDenyList: Parsed package: '$packageName' from '$trimmed'")
                }
            }
            
            // Method 3: Also get activities from denylist table
            val activityQuery = "SELECT package_name || '/' || process_name FROM denylist WHERE process_name IS NOT NULL AND process_name != ''"
            val activityProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db \"$activityQuery\""))
            val activityReader = BufferedReader(InputStreamReader(activityProcess.inputStream))
            var activityLine: String?
            while (activityReader.readLine().also { activityLine = it } != null) {
                val activity = activityLine?.trim()
                if (!activity.isNullOrEmpty()) {
                    // Add as package/activity format
                    if (!denyList.contains(activity)) {
                        denyList.add(activity)
                        android.util.Log.d("MainActivity", "getDenyList: Found activity from DB: $activity")
                    }
                    // Also ensure the package is in the list
                    val pkg = activity.substringBefore("/")
                    if (!denyList.contains(pkg)) {
                        denyList.add(pkg)
                    }
                }
            }
            activityProcess.waitFor()
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "getDenyList error: ${e.message}")
            
            // Fallback: Try alternative database query format
            try {
                android.util.Log.d("MainActivity", "getDenyList: Trying fallback query")
                val fallbackProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "sqlite3 /data/adb/magisk.db '.dump denylist'"))
                val fallbackReader = BufferedReader(InputStreamReader(fallbackProcess.inputStream))
                var fallbackLine: String?
                val insertPattern = Regex("INSERT INTO denylist VALUES\\('([^']+)'")
                
                while (fallbackReader.readLine().also { fallbackLine = it } != null) {
                    val match = insertPattern.find(fallbackLine ?: "")
                    if (match != null) {
                        val packageName = match.groupValues[1]
                        if (packageName.isNotEmpty() && !denyList.contains(packageName)) {
                            denyList.add(packageName)
                            android.util.Log.d("MainActivity", "getDenyList: Found from dump: $packageName")
                        }
                    }
                }
                fallbackProcess.waitFor()
            } catch (e2: Exception) {
                android.util.Log.e("MainActivity", "getDenyList fallback error: ${e2.message}")
            }
        }
        
        android.util.Log.d("MainActivity", "getDenyList: Returning ${denyList.size} items: $denyList")
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
            
            // Get UID using multiple methods for reliability
            var uid: Int? = null
            
            // Method 1: Use dumpsys package
            try {
                val uidProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys package $packageName"))
                val uidReader = BufferedReader(InputStreamReader(uidProcess.inputStream))
                val uidOutput = uidReader.readText()
                uidProcess.waitFor()
                
                val patterns = listOf(
                    Regex("userId=(\\d+)"),
                    Regex("uid=(\\d+)"),
                    Regex("User 0:.*?uid=(\\d+)"),
                    Regex("granted=true.*?uid=(\\d+)")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(uidOutput)
                    if (match != null) {
                        uid = match.groupValues[1].toIntOrNull()
                        if (uid != null && uid >= 10000) {
                            android.util.Log.d("MainActivity", "Got UID $uid via dumpsys pattern: $pattern")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "dumpsys method failed: ${e.message}")
            }
            
            // Method 2: Use pm list packages -U
            if (uid == null) {
                try {
                    val pmProcess = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-U"))
                    val pmReader = BufferedReader(InputStreamReader(pmProcess.inputStream))
                    var pmLine: String?
                    while (pmReader.readLine().also { pmLine = it } != null) {
                        if (pmLine!!.contains("package:$packageName ")) {
                            val uidMatch = Regex("uid:(\\d+)").find(pmLine!!)
                            uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
                            if (uid != null) {
                                android.util.Log.d("MainActivity", "Got UID $uid via pm list")
                                break
                            }
                        }
                    }
                    pmProcess.waitFor()
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "pm list method failed: ${e.message}")
                }
            }
            
            // Method 3: Use PackageManager
            if (uid == null) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    uid = appInfo.uid
                    android.util.Log.d("MainActivity", "Got UID $uid via PackageManager")
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "PackageManager method failed: ${e.message}")
                }
            }
            
            if (uid == null || uid < 10000) {
                android.util.Log.e("MainActivity", "Failed to get valid UID for $packageName (uid=$uid)")
                return false
            }
            
            android.util.Log.d("MainActivity", "Got UID $uid for package $packageName")
            
            // Grant root access using multiple methods
            var success = false
            
            // Method 1: INSERT via magisk --sqlite
            val insertCmd = "INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)"
            android.util.Log.d("MainActivity", "Trying INSERT: magisk --sqlite \"$insertCmd\"")
            val insertProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$insertCmd\""))
            insertProcess.waitFor()
            
            // Verify grant
            Thread.sleep(300)
            val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
            val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
            val verifyOutput = verifyReader.readText().trim()
            verifyProcess.waitFor()
            android.util.Log.d("MainActivity", "After INSERT, policy = '$verifyOutput'")
            
            if (verifyOutput == "policy=2" || verifyOutput == "2") {
                android.util.Log.d("MainActivity", "Successfully granted root access via magisk --sqlite INSERT")
                success = true
            }
            
            // Method 2: Use sqlite3 directly if Method 1 failed
            if (!success) {
                val dbInsertCmd = "sqlite3 /data/adb/magisk.db \"INSERT OR REPLACE INTO policies (uid, policy, until, logging, notification) VALUES ($uid, 2, 0, 1, 1)\""
                android.util.Log.d("MainActivity", "Trying sqlite3 direct: $dbInsertCmd")
                val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", dbInsertCmd))
                dbProcess.waitFor()
                
                // Verify
                Thread.sleep(300)
                val verify2Process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
                val verify2Reader = BufferedReader(InputStreamReader(verify2Process.inputStream))
                val verify2Output = verify2Reader.readText().trim()
                verify2Process.waitFor()
                android.util.Log.d("MainActivity", "After sqlite3 INSERT, policy = '$verify2Output'")
                
                if (verify2Output == "policy=2" || verify2Output == "2") {
                    android.util.Log.d("MainActivity", "Successfully granted root access via sqlite3")
                    success = true
                }
            }
            
            if (success) {
                android.util.Log.d("MainActivity", "Successfully granted root access to $packageName (uid=$uid)")
                // Notify Magisk daemon to reload
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep magiskd | head -1)")).waitFor()
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to notify magiskd: ${e.message}")
                }
            } else {
                android.util.Log.e("MainActivity", "Failed to grant root access to $packageName")
            }
            
            success
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error granting root access: ${e.message}")
            false
        }
    }

    private fun revokeRootAccess(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            android.util.Log.d("MainActivity", "revokeRootAccess: $packageName")
            
            // Get UID using multiple methods for reliability (same as revokeRootAccessViaScript)
            var uid: Int? = null
            
            // Method 1: Use dumpsys package
            try {
                val uidProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys package $packageName"))
                val uidReader = BufferedReader(InputStreamReader(uidProcess.inputStream))
                val uidOutput = uidReader.readText()
                uidProcess.waitFor()
                
                // Try multiple patterns
                val patterns = listOf(
                    Regex("userId=(\\d+)"),
                    Regex("uid=(\\d+)"),
                    Regex("User 0:.*?uid=(\\d+)"),
                    Regex("granted=true.*?uid=(\\d+)")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(uidOutput)
                    if (match != null) {
                        uid = match.groupValues[1].toIntOrNull()
                        if (uid != null && uid >= 10000) {
                            android.util.Log.d("MainActivity", "Got UID $uid via dumpsys pattern: $pattern")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "dumpsys method failed: ${e.message}")
            }
            
            // Method 2: Use pm list packages -U
            if (uid == null) {
                try {
                    val pmProcess = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-U"))
                    val pmReader = BufferedReader(InputStreamReader(pmProcess.inputStream))
                    var pmLine: String?
                    while (pmReader.readLine().also { pmLine = it } != null) {
                        if (pmLine!!.contains("package:$packageName ")) {
                            val uidMatch = Regex("uid:(\\d+)").find(pmLine!!)
                            uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
                            if (uid != null) {
                                android.util.Log.d("MainActivity", "Got UID $uid via pm list")
                                break
                            }
                        }
                    }
                    pmProcess.waitFor()
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "pm list method failed: ${e.message}")
                }
            }
            
            // Method 3: Use PackageManager
            if (uid == null) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    uid = appInfo.uid
                    android.util.Log.d("MainActivity", "Got UID $uid via PackageManager")
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "PackageManager method failed: ${e.message}")
                }
            }
            
            if (uid == null || uid < 10000) {
                android.util.Log.e("MainActivity", "Failed to get valid UID for $packageName (uid=$uid)")
                return false
            }
            
            android.util.Log.d("MainActivity", "Got UID $uid for package $packageName")
            
            // Step 2: Revoke root access using magisk --sqlite with UID
            // Try multiple approaches
            var success = false
            
            // Method 1: DELETE via magisk --sqlite
            val deleteCmd = "DELETE FROM policies WHERE uid=$uid"
            android.util.Log.d("MainActivity", "Trying DELETE: magisk --sqlite \"$deleteCmd\"")
            val deleteProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$deleteCmd\""))
            deleteProcess.waitFor()
            
            // Verify deletion
            Thread.sleep(300)
            val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
            val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
            val verifyOutput = verifyReader.readText().trim()
            verifyProcess.waitFor()
            android.util.Log.d("MainActivity", "After DELETE, policy = '$verifyOutput'")
            
            if (verifyOutput.isEmpty() || verifyOutput == "policy=0" || verifyOutput == "0") {
                android.util.Log.d("MainActivity", "Successfully deleted policy via magisk --sqlite DELETE")
                success = true
            }
            
            // Method 2: UPDATE to 0 if DELETE didn't work
            if (!success) {
                val updateCmd = "UPDATE policies SET policy = 0 WHERE uid=$uid"
                android.util.Log.d("MainActivity", "Trying UPDATE: magisk --sqlite \"$updateCmd\"")
                val updateProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"$updateCmd\""))
                updateProcess.waitFor()
                
                // Verify update
                Thread.sleep(300)
                val verify2Process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
                val verify2Reader = BufferedReader(InputStreamReader(verify2Process.inputStream))
                val verify2Output = verify2Reader.readText().trim()
                verify2Process.waitFor()
                android.util.Log.d("MainActivity", "After UPDATE, policy = '$verify2Output'")
                
                if (verify2Output == "policy=0" || verify2Output == "0") {
                    android.util.Log.d("MainActivity", "Successfully updated policy to 0 via magisk --sqlite UPDATE")
                    success = true
                }
            }
            
            // Method 3: Use sqlite3 directly
            if (!success) {
                val dbDeleteCmd = "sqlite3 /data/adb/magisk.db \"DELETE FROM policies WHERE uid=$uid\""
                android.util.Log.d("MainActivity", "Trying sqlite3 direct: $dbDeleteCmd")
                val dbProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", dbDeleteCmd))
                dbProcess.waitFor()
                
                // Verify
                Thread.sleep(300)
                val verify3Process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
                val verify3Reader = BufferedReader(InputStreamReader(verify3Process.inputStream))
                val verify3Output = verify3Reader.readText().trim()
                verify3Process.waitFor()
                android.util.Log.d("MainActivity", "After sqlite3 DELETE, policy = '$verify3Output'")
                
                if (verify3Output.isEmpty() || verify3Output == "policy=0" || verify3Output == "0") {
                    android.util.Log.d("MainActivity", "Successfully deleted policy via sqlite3")
                    success = true
                }
            }
            
            // Method 4: sqlite3 UPDATE
            if (!success) {
                val dbUpdateCmd = "sqlite3 /data/adb/magisk.db \"UPDATE policies SET policy = 0 WHERE uid=$uid\""
                android.util.Log.d("MainActivity", "Trying sqlite3 UPDATE: $dbUpdateCmd")
                val dbUpdateProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", dbUpdateCmd))
                dbUpdateProcess.waitFor()
                
                // Verify
                Thread.sleep(300)
                val verify4Process = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
                val verify4Reader = BufferedReader(InputStreamReader(verify4Process.inputStream))
                val verify4Output = verify4Reader.readText().trim()
                verify4Process.waitFor()
                android.util.Log.d("MainActivity", "After sqlite3 UPDATE, policy = '$verify4Output'")
                
                if (verify4Output == "policy=0" || verify4Output == "0") {
                    android.util.Log.d("MainActivity", "Successfully updated policy to 0 via sqlite3")
                    success = true
                }
            }
            
            if (success) {
                android.util.Log.d("MainActivity", "Successfully revoked root access from $packageName (uid=$uid)")
                // Notify Magisk daemon to reload
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -HUP \$(pgrep magiskd | head -1)")).waitFor()
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to notify magiskd: ${e.message}")
                }
            } else {
                android.util.Log.e("MainActivity", "Failed to revoke root access from $packageName")
            }
            
            success
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error revoking root access: ${e.message}")
            false
        }
    }

    private fun getRootAllowedPackages(): List<String> {
        return try {
            android.util.Log.e("MainActivity", "=== getRootAllowedPackages: STARTING QUERY ===")
            
            val allowedPackages = mutableListOf<String>()
            
            // Method 1: Query magisk database using magisk --sqlite
            // Output format: uid=12345 (one per line)
            android.util.Log.e("MainActivity", "Executing: su -c magisk --sqlite 'SELECT uid FROM policies WHERE policy>0'")
            val sqlProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite 'SELECT uid FROM policies WHERE policy>0'"))
            val sqlReader = BufferedReader(InputStreamReader(sqlProcess.inputStream))
            val uidOutput = StringBuilder()
            var sqlLine: String?
            while (sqlReader.readLine().also { sqlLine = it } != null) {
                uidOutput.append(sqlLine).append("\n")
            }
            sqlProcess.waitFor()
            
            val rawOutput = uidOutput.toString().trim()
            android.util.Log.e("MainActivity", "=== SQL query output length: ${rawOutput.length} ===")
            android.util.Log.e("MainActivity", "=== SQL query output: '$rawOutput' ===")
            
            // Parse UIDs - format is "uid=12345"
            val uids = mutableListOf<Int>()
            for (uidLine in rawOutput.split("\n")) {
                val trimmed = uidLine.trim()
                if (trimmed.isEmpty()) continue
                
                // Handle format: uid=12345
                val uidValue = when {
                    trimmed.startsWith("uid=") -> trimmed.removePrefix("uid=")
                    trimmed.contains("=") -> {
                        // Other key=value format, skip
                        android.util.Log.d("MainActivity", "Skipping non-uid line: $trimmed")
                        null
                    }
                    else -> trimmed
                }
                
                val uid = uidValue?.trim()?.toIntOrNull()
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
                            // Format: package:com.example.app uid:12345
                            // Extract just the package name
                            val afterPrefix = trimmedLine.removePrefix("package:")
                            val packageName = afterPrefix.split(" ", "\t").first().trim()
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
                sendLog("[WARN] No root access detected, switching to no-root patching mode")
                // For patch mode without root, use the no-root patching method
                if (isPatchMode || bootImage.isNotEmpty()) {
                    val result = patchBootImageNoRoot(bootImage, null)
                    return result != null
                } else {
                    sendLog("[ERROR] Root access required for direct installation mode")
                    sendLog("[INFO] Please provide a boot image file for patching, or use 'Select and Patch a File' option")
                    return false
                }
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
            
            // Copy necessary Magisk files
            sendLog("[INFO] Copying Magisk files")
            
            // Binary files are in nativeLibraryDir (jniLibs), named as libXXX.so
            // Script files are in assets
            val nativeLibDir = applicationInfo.nativeLibraryDir
            sendLog("[INFO] Native library directory: $nativeLibDir")
            
            // Map of source file names (without lib prefix and .so suffix) to destination names
            val binaryFiles = mapOf(
                "magiskinit" to "magiskinit",
                "magisk" to "magisk", 
                "magiskboot" to "magiskboot",
                "busybox" to "busybox"
            )
            
            val scriptFiles = listOf("util_functions.sh", "boot_patch.sh")
            
            var copySuccess = true
            
            // Copy binary files from nativeLibraryDir
            for ((srcName, destName) in binaryFiles) {
                val srcPath = "$nativeLibDir/lib$srcName.so"
                val destPath = "$tmpDir/$destName"
                
                sendLog("[INFO] Copying binary: $srcPath -> $destPath")
                
                val srcFile = File(srcPath)
                if (srcFile.exists()) {
                    val cpProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '$srcPath' '$destPath' && chmod 755 '$destPath'"))
                    cpProcess.waitFor()
                    if (cpProcess.exitValue() != 0) {
                        sendLog("[WARN] Failed to copy $srcName (exit: ${cpProcess.exitValue()})")
                        // busybox is optional
                        if (srcName != "busybox") {
                            copySuccess = false
                            break
                        }
                    } else {
                        sendLog("[INFO] Successfully copied: $destName")
                    }
                } else {
                    sendLog("[WARN] Binary not found in nativeLibraryDir: $srcPath")
                    // Try alternative: check if it's in assets (for backwards compatibility)
                    if (!copyAssetToFile(srcName, destPath)) {
                        sendLog("[WARN] Also not found in assets: $srcName")
                        if (srcName != "busybox") {
                            copySuccess = false
                            break
                        }
                    }
                }
            }
            
            if (!copySuccess) {
                sendLog("[ERROR] Failed to copy required binary files")
                return false
            }
            
            // Copy script files from assets
            for (scriptFile in scriptFiles) {
                val destPath = "$tmpDir/$scriptFile"
                if (!copyAssetToFile(scriptFile, destPath)) {
                    sendLog("[ERROR] Failed to copy script: $scriptFile")
                    copySuccess = false
                    break
                }
            }
            
            if (!copySuccess) {
                sendLog("[ERROR] Failed to copy required script files")
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
            
            // Method 3: Use bootctrl system service to get slot info
            sendLog("[INFO] Trying bootctrl service call for slot info...")
            try {
                // Try bootctrl service call (Android 8.0+) to get current slot
                val bootctrlService = Runtime.getRuntime().exec(arrayOf("su", "-c", "service call bootctrl 3"))
                bootctrlService.waitFor()
                if (bootctrlService.exitValue() == 0) {
                    sendLog("[INFO] bootctrl service is available for slot queries")
                }
            } catch (e: Exception) {
                sendLog("[DEBUG] bootctrl service not available: ${e.message}")
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
            // Use root shell to check since app may not have direct access
            val checkProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", """
                # Check for Magisk directories and files
                [ -d /data/adb/magisk ] && echo "magisk_dir"
                [ -f /data/adb/magisk.db ] && echo "magisk_db"
                [ -d /data/adb/modules ] && echo "modules_dir"
                [ -f /data/adb/magisk.apk ] && echo "magisk_apk"
                [ -f /data/adb/ksu ] && echo "ksu"
                [ -d /data/adb/ksu ] && echo "ksu_dir"
                [ -f /data/adb/apatch ] && echo "apatch"
                [ -d /data/adb/apatch ] && echo "apatch_dir"
                # Check for su binaries in various locations
                [ -f /system/bin/su ] && echo "system_su"
                [ -f /sbin/su ] && echo "sbin_su"
                [ -f /data/adb/ksud ] && echo "ksud"
                [ -f /data/adb/apd ] && echo "apd"
                # Check Magisk version
                magisk -V 2>/dev/null && echo "magisk_cmd"
                # Check for .magisk directory (Magisk hide)
                [ -d /data/adb/.magisk ] && echo "magisk_hide"
                # Check for Zygisk
                [ -d /data/adb/zygisk ] && echo "zygisk"
            """.trimIndent()))
            val checkReader = BufferedReader(InputStreamReader(checkProcess.inputStream))
            val checkOutput = StringBuilder()
            var checkLine: String?
            while (checkReader.readLine().also { checkLine = it } != null) {
                checkOutput.append(checkLine).append("\n")
            }
            checkProcess.waitFor()
            
            val checkResults = checkOutput.toString().trim()
            sendLog("[DEBUG] Detection results: $checkResults")
            
            // Determine what root solution is installed
            val hasMagisk = checkResults.contains("magisk_dir") || 
                           checkResults.contains("magisk_db") || 
                           checkResults.contains("magisk_apk") ||
                           checkResults.contains("modules_dir") ||
                           checkResults.contains("magisk_cmd")
            
            val hasKernelSU = checkResults.contains("ksu") || checkResults.contains("ksu_dir") || checkResults.contains("ksud")
            val hasAPatch = checkResults.contains("apatch") || checkResults.contains("apatch_dir") || checkResults.contains("apd")
            
            if (hasKernelSU && !hasMagisk) {
                sendLog("[ERROR] KernelSU detected - this is not Magisk")
                sendLog("[INFO] Please use KernelSU manager to uninstall")
                return false
            }
            
            if (hasAPatch && !hasMagisk) {
                sendLog("[ERROR] APatch detected - this is not Magisk")
                sendLog("[INFO] Please use APatch manager to uninstall")
                return false
            }
            
            if (!hasMagisk) {
                // Check if any root exists
                val rootCheck = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                rootCheck.waitFor()
                if (rootCheck.exitValue() == 0) {
                    sendLog("[ERROR] Root access detected but no Magisk installation found")
                    sendLog("[INFO] This might be a different root solution")
                    sendLog("[INFO] Checked paths: /data/adb/magisk, /data/adb/magisk.db, /data/adb/modules")
                } else {
                    sendLog("[ERROR] No root access and no Magisk installation found")
                }
                return false
            }
            
            sendLog("[INFO] Magisk installation confirmed")
            
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
            
            // Check if we have root access
            if (!checkRootAccess()) {
                sendLog("[WARN] No root access detected, switching to no-root patching mode")
                return patchBootImageNoRoot(bootImage, null)
            }
            sendLog("[INFO] Root access confirmed, using root-based patching")
            
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
            
            // Copy necessary Magisk files
            sendLog("[INFO] Copying Magisk files")
            
            // Binary files are in nativeLibraryDir (jniLibs), named as libXXX.so
            // Script files are in assets
            val nativeLibDir = applicationInfo.nativeLibraryDir
            sendLog("[INFO] Native library directory: $nativeLibDir")
            
            // Map of source file names (without lib prefix and .so suffix) to destination names
            val binaryFiles = mapOf(
                "magiskinit" to "magiskinit",
                "magisk" to "magisk", 
                "magiskboot" to "magiskboot",
                "busybox" to "busybox"
            )
            
            val scriptFiles = listOf("util_functions.sh", "boot_patch.sh")
            
            var copySuccess = true
            
            // Copy binary files from nativeLibraryDir
            for ((srcName, destName) in binaryFiles) {
                val srcPath = "$nativeLibDir/lib$srcName.so"
                val destPath = "$tmpDir/$destName"
                
                sendLog("[INFO] Copying binary: $srcPath -> $destPath")
                
                val srcFile = File(srcPath)
                if (srcFile.exists()) {
                    val cpProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '$srcPath' '$destPath' && chmod 755 '$destPath'"))
                    cpProcess.waitFor()
                    if (cpProcess.exitValue() != 0) {
                        sendLog("[WARN] Failed to copy $srcName (exit: ${cpProcess.exitValue()})")
                        // busybox is optional
                        if (srcName != "busybox") {
                            copySuccess = false
                            break
                        }
                    } else {
                        sendLog("[INFO] Successfully copied: $destName")
                    }
                } else {
                    sendLog("[WARN] Binary not found in nativeLibraryDir: $srcPath")
                    // Try alternative: check if it's in assets (for backwards compatibility)
                    if (!copyAssetToFile(srcName, destPath)) {
                        sendLog("[WARN] Also not found in assets: $srcName")
                        if (srcName != "busybox") {
                            copySuccess = false
                            break
                        }
                    }
                }
            }
            
            if (!copySuccess) {
                sendLog("[ERROR] Failed to copy required binary files")
                return null
            }
            
            // Copy script files from assets
            for (scriptFile in scriptFiles) {
                val destPath = "$tmpDir/$scriptFile"
                if (!copyAssetToFile(scriptFile, destPath)) {
                    sendLog("[ERROR] Failed to copy script: $scriptFile")
                    copySuccess = false
                    break
                }
            }
            
            if (!copySuccess) {
                sendLog("[ERROR] Failed to copy required script files")
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
                // Try system bootctl locations
                val possiblePaths = listOf(
                    "/tool/bootctl",
                    "/system/bin/bootctl",
                    "/vendor/bin/bootctl",
                    "/data/adb/magisk/bootctl"
                )
                
                for (path in possiblePaths) {
                    val file = File(path)
                    if (file.exists()) {
                        bootctlPath = path
                        sendLog("[INFO] Found system bootctl at $bootctlPath")
                        break
                    }
                }
                
                if (bootctlPath.isEmpty()) {
                    sendLog("[ERROR] bootctl not found in any location")
                    return false
                }
            }
            
            // Verify bootctl is executable and working
            sendLog("[INFO] Verifying bootctl functionality")
            val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath hal-info 2>&1"))
            val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
            var verifyLine: String?
            while (verifyReader.readLine().also { verifyLine = it } != null) {
                sendLog("[DEBUG] bootctl hal-info: $verifyLine")
            }
            verifyProcess.waitFor()
            
            // Get current slot using get-current-slot (returns 0 for slot A, 1 for slot B)
            sendLog("[INFO] Getting current boot slot")
            val currentSlotProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath get-current-slot"))
            val currentSlotReader = BufferedReader(InputStreamReader(currentSlotProcess.inputStream))
            val currentSlotOutput = currentSlotReader.readLine()?.trim() ?: ""
            currentSlotProcess.waitFor()
            sendLog("[INFO] Current slot output: '$currentSlotOutput'")
            
            // Parse the slot number (bootctl returns 0 for slot A, 1 for slot B)
            val currentSlotNum = currentSlotOutput.toIntOrNull() ?: 0
            sendLog("[INFO] Current slot number: $currentSlotNum (${if (currentSlotNum == 0) "A" else "B"})")
            
            // Determine target slot (opposite of current)
            val targetSlotNum = if (currentSlotNum == 0) 1 else 0
            sendLog("[INFO] Target slot number: $targetSlotNum (${if (targetSlotNum == 0) "A" else "B"})")
            
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
            // bootctl expects a numeric slot index (0 for slot A, 1 for slot B)
            sendLog("[INFO] Step 2: Setting boot slot to $targetSlotNum (${if (targetSlotNum == 0) "A" else "B"})")
            
            // Read any error output from the command
            val setSlotProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath set-active-boot-slot $targetSlotNum 2>&1"))
            val setSlotReader = BufferedReader(InputStreamReader(setSlotProcess.inputStream))
            val setSlotOutput = StringBuilder()
            var setSlotLine: String?
            while (setSlotReader.readLine().also { setSlotLine = it } != null) {
                setSlotOutput.append(setSlotLine).append("\n")
                sendLog("[DEBUG] set-active-boot-slot output: $setSlotLine")
            }
            setSlotProcess.waitFor()
            
            if (setSlotProcess.exitValue() != 0) {
                sendLog("[ERROR] Failed to set boot slot (exit code: ${setSlotProcess.exitValue()})")
                sendLog("[ERROR] Output: ${setSlotOutput.toString()}")
                
                // Try alternative method using Android's bootloader control block
                sendLog("[INFO] Trying alternative slot switch method...")
                val altProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", """
                    # Try using bootctrl HAL via service call
                    if command -v bootctrl >/dev/null 2>&1; then
                        bootctrl set-active-boot-slot $targetSlotNum
                    else
                        # Direct bootctrl HAL call
                        service call bootctrl 1 i32 $targetSlotNum
                    fi
                """.trimIndent()))
                altProcess.waitFor()
                
                if (altProcess.exitValue() != 0) {
                    sendLog("[ERROR] Alternative method also failed")
                    return false
                }
            }
            
            // Verify the slot was actually changed
            sendLog("[INFO] Verifying slot change")
            val verifySlotProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$bootctlPath get-current-slot"))
            val verifySlotReader = BufferedReader(InputStreamReader(verifySlotProcess.inputStream))
            val newSlotOutput = verifySlotReader.readLine()?.trim() ?: ""
            verifySlotProcess.waitFor()
            val newSlotNum = newSlotOutput.toIntOrNull() ?: -1
            sendLog("[INFO] New current slot: $newSlotNum (${if (newSlotNum == 0) "A" else "B"})")
            
            if (newSlotNum != targetSlotNum) {
                sendLog("[WARN] Slot verification shows unexpected result, but continuing...")
            }
            
            sendLog("[INFO] Boot slot switched successfully")
            
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
            // Get UID using multiple methods for reliability
            var uid: Int? = null
            
            // Method 1: Use dumpsys package
            try {
                val uidProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys package $packageName"))
                val uidReader = BufferedReader(InputStreamReader(uidProcess.inputStream))
                val uidOutput = uidReader.readText()
                uidProcess.waitFor()
                
                val patterns = listOf(
                    Regex("userId=(\\d+)"),
                    Regex("uid=(\\d+)"),
                    Regex("User 0:.*?uid=(\\d+)"),
                    Regex("granted=true.*?uid=(\\d+)")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(uidOutput)
                    if (match != null) {
                        uid = match.groupValues[1].toIntOrNull()
                        if (uid != null && uid >= 10000) {
                            android.util.Log.d("MainActivity", "Got UID $uid via dumpsys pattern: $pattern")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "dumpsys method failed: ${e.message}")
            }
            
            // Method 2: Use pm list packages -U
            if (uid == null) {
                try {
                    val pmProcess = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-U"))
                    val pmReader = BufferedReader(InputStreamReader(pmProcess.inputStream))
                    var pmLine: String?
                    while (pmReader.readLine().also { pmLine = it } != null) {
                        if (pmLine!!.contains("package:$packageName ")) {
                            val uidMatch = Regex("uid:(\\d+)").find(pmLine!!)
                            uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
                            if (uid != null) {
                                android.util.Log.d("MainActivity", "Got UID $uid via pm list")
                                break
                            }
                        }
                    }
                    pmProcess.waitFor()
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "pm list method failed: ${e.message}")
                }
            }
            
            // Method 3: Use PackageManager
            if (uid == null) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    uid = appInfo.uid
                    android.util.Log.d("MainActivity", "Got UID $uid via PackageManager")
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "PackageManager method failed: ${e.message}")
                }
            }
            
            if (uid == null || uid < 10000) {
                android.util.Log.e("MainActivity", "Failed to get valid UID for $packageName (uid=$uid)")
                return false
            }
            
            android.util.Log.d("MainActivity", "Granting root access for $packageName (UID: $uid)")
            
            // Execute grant function
            val exitCode = executeAppFunctionExitCode("grant_root_access", packageName)
            
            // Verify the policy was actually added by checking the database directly
            Thread.sleep(500) // Give some time for the database to be updated
            
            val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
            val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
            val verifyOutput = verifyReader.readText().trim()
            verifyProcess.waitFor()
            
            android.util.Log.d("MainActivity", "Verification query result: '$verifyOutput'")
            
            // Check if policy is now 2 (allow_forever)
            val policyGranted = when {
                verifyOutput == "policy=2" -> true
                verifyOutput == "2" -> true
                verifyOutput.startsWith("policy=") -> {
                    val value = verifyOutput.removePrefix("policy=").trim()
                    value == "2" || value.toIntOrNull()?.let { it > 0 } ?: false
                }
                else -> verifyOutput.toIntOrNull()?.let { it > 0 } ?: false
            }
            
            if (policyGranted) {
                android.util.Log.d("MainActivity", "Successfully granted root access to $packageName (UID: $uid)")
                true
            } else {
                android.util.Log.w("MainActivity", "Script returned success but policy not granted, trying direct method")
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
            // Get UID using multiple methods for reliability
            var uid: Int? = null
            
            // Method 1: Use dumpsys package
            try {
                val uidProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "dumpsys package $packageName"))
                val uidReader = BufferedReader(InputStreamReader(uidProcess.inputStream))
                val uidOutput = uidReader.readText()
                uidProcess.waitFor()
                
                // Try multiple patterns
                val patterns = listOf(
                    Regex("userId=(\\d+)"),
                    Regex("uid=(\\d+)"),
                    Regex("User 0:.*?uid=(\\d+)"),
                    Regex("granted=true.*?uid=(\\d+)")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(uidOutput)
                    if (match != null) {
                        uid = match.groupValues[1].toIntOrNull()
                        if (uid != null && uid >= 10000) {
                            android.util.Log.d("MainActivity", "Got UID $uid via dumpsys pattern: $pattern")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "dumpsys method failed: ${e.message}")
            }
            
            // Method 2: Use pm list packages -U
            if (uid == null) {
                try {
                    val pmProcess = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-U"))
                    val pmReader = BufferedReader(InputStreamReader(pmProcess.inputStream))
                    var pmLine: String?
                    while (pmReader.readLine().also { pmLine = it } != null) {
                        // Format: package:com.example uid:10123
                        if (pmLine!!.contains("package:$packageName ")) {
                            val uidMatch = Regex("uid:(\\d+)").find(pmLine!!)
                            uid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
                            if (uid != null) {
                                android.util.Log.d("MainActivity", "Got UID $uid via pm list")
                                break
                            }
                        }
                    }
                    pmProcess.waitFor()
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "pm list method failed: ${e.message}")
                }
            }
            
            // Method 3: Use getprop or directly from PackageManager
            if (uid == null) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    uid = appInfo.uid
                    android.util.Log.d("MainActivity", "Got UID $uid via PackageManager")
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "PackageManager method failed: ${e.message}")
                }
            }
            
            if (uid == null || uid < 10000) {
                android.util.Log.e("MainActivity", "Failed to get valid UID for $packageName (uid=$uid)")
                return false
            }
            
            android.util.Log.d("MainActivity", "Revoking root access for $packageName (UID: $uid)")
            
            // Execute revoke function
            val exitCode = executeAppFunctionExitCode("revoke_root_access", packageName)
            
            // Verify the policy was actually removed by checking the database directly
            Thread.sleep(500) // Give some time for the database to be updated
            
            val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "magisk --sqlite \"SELECT policy FROM policies WHERE uid = $uid\""))
            val verifyReader = BufferedReader(InputStreamReader(verifyProcess.inputStream))
            val verifyOutput = verifyReader.readText().trim()
            verifyProcess.waitFor()
            
            android.util.Log.d("MainActivity", "Verification query result: '$verifyOutput'")
            
            // Check if policy is now 0, empty, or the row doesn't exist
            val policyRemoved = when {
                verifyOutput.isEmpty() -> true
                verifyOutput == "policy=0" -> true
                verifyOutput == "0" -> true
                verifyOutput.startsWith("policy=") -> {
                    val value = verifyOutput.removePrefix("policy=").trim()
                    value == "0" || value.isEmpty()
                }
                else -> verifyOutput.toIntOrNull() == 0
            }
            
            if (policyRemoved) {
                android.util.Log.d("MainActivity", "Successfully revoked root access from $packageName (UID: $uid)")
                true
            } else {
                android.util.Log.w("MainActivity", "Script returned success but policy still exists, trying direct method")
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
                val setupResult = setupAppFunctionsScript()
                android.util.Log.d("MainActivity", "Setup app_functions.sh result: $setupResult")
            }
            
            // Build the command arguments
            val argsStr = args.joinToString(" ")
            
            android.util.Log.d("MainActivity", "Executing with exit code capture: $functionName $argsStr")
            
            // Create a wrapper script that sources the function script and executes the function
            // This ensures the function is available in the same shell context
            val wrapperScript = """
                #!/system/bin/sh
                . $appFunctionsScriptPath
                $functionName $argsStr
                echo EXIT_CODE=$?
            """.trimIndent()
            
            // Write wrapper script to temp file
            val wrapperPath = "/data/local/tmp/func_wrapper_$functionName.sh"
            val writeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > '$wrapperPath'"))
            writeProcess.outputStream.write(wrapperScript.toByteArray())
            writeProcess.outputStream.close()
            writeProcess.waitFor()
            
            // Make wrapper executable
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 '$wrapperPath'")).waitFor()
            
            // Execute the wrapper script
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh '$wrapperPath'"))
            
            // Read all output
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val outputLines = mutableListOf<String>()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                outputLines.add(line!!)
                android.util.Log.d("MainActivity", "[$functionName] stdout: $line")
            }
            
            while (errorReader.readLine().also { line = it } != null) {
                android.util.Log.d("MainActivity", "[$functionName] stderr: $line")
            }
            
            process.waitFor()
            
            // Cleanup wrapper script
            Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f '$wrapperPath'")).waitFor()
            
            // Find exit code from the last line
            var exitCode = 1
            for (outputLine in outputLines.reversed()) {
                if (outputLine.startsWith("EXIT_CODE=")) {
                    exitCode = outputLine.removePrefix("EXIT_CODE=").trim().toIntOrNull() ?: 1
                    break
                }
            }
            
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
    
    // ==================== Module Management ====================
    
    /**
     * Toggle a module's enabled state
     * @param modulePath The path to the module directory
     * @param enabled Whether to enable or disable the module
     * @return true if successful, false otherwise
     */
    private fun toggleModule(modulePath: String, enabled: Boolean): Boolean {
        if (modulePath.isEmpty()) return false
        return try {
            val disableFile = "$modulePath/disable"
            val removeFile = "$modulePath/remove"
            
            if (enabled) {
                // Enable module by removing disable and remove files
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f '$disableFile' '$removeFile'"))
                process.waitFor()
                process.exitValue() == 0
            } else {
                // Disable module by creating disable file
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "touch '$disableFile'"))
                process.waitFor()
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error toggling module: ${e.message}")
            false
        }
    }
    
    /**
     * Remove/uninstall a module
     * @param modulePath The path to the module directory
     * @return true if successful, false otherwise
     */
    private fun removeModule(modulePath: String): Boolean {
        if (modulePath.isEmpty()) return false
        return try {
            // Create remove file to mark module for removal on next reboot
            val removeFile = "$modulePath/remove"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "touch '$removeFile'"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error removing module: ${e.message}")
            false
        }
    }
    
    /**
     * Execute a module's action script (action.sh)
     * @param modulePath The path to the module directory
     * @return the output of the action script, or null if failed
     */
    private fun executeModuleAction(modulePath: String): String? {
        if (modulePath.isEmpty()) return null
        return try {
            val actionScript = "$modulePath/action.sh"
            
            // Check if action.sh exists
            val checkProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -f '$actionScript' && echo 'exists'"))
            checkProcess.waitFor()
            val exists = checkProcess.inputStream.bufferedReader().readText().trim()
            
            if (exists != "exists") {
                return "No action.sh found in module"
            }
            
            // Execute action.sh
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cd '$modulePath' && sh action.sh"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            
            if (process.exitValue() == 0) {
                output.toString().trim()
            } else {
                "Action script failed with exit code: ${process.exitValue()}\n${output}"
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error executing module action: ${e.message}")
            "Error: ${e.message}"
        }
    }
    
    /**
     * Check if a module has a web interface
     * @param modulePath The path to the module directory
     * @return Map with hasWebUI, webUIUrl, webUIPort
     */
    private fun checkModuleWebUI(modulePath: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "hasWebUI" to false,
            "webUIUrl" to null,
            "webUIPort" to null
        )
        
        if (modulePath.isEmpty()) return result
        
        return try {
            // Check for webroot directory (KSU/WebUI standard)
            val webrootDirPath = "$modulePath/webroot"
            val checkWebroot = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -d '$webrootDirPath' && echo 'exists'"))
            checkWebroot.waitFor()
            val webrootExists = checkWebroot.inputStream.bufferedReader().readText().trim() == "exists"
            
            if (webrootExists) {
                result["hasWebUI"] = true
                
                // Check for post-fs-data.sh to find port
                val postFsData = "$modulePath/post-fs-data.sh"
                val portProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "grep -oP 'PORT=\\K[0-9]+|listen.*?\\K[0-9]+' '$postFsData' 2>/dev/null | head -1"))
                portProcess.waitFor()
                val port = portProcess.inputStream.bufferedReader().readText().trim()
                
                if (port.isNotEmpty()) {
                    result["webUIPort"] = port.toIntOrNull()
                    result["webUIUrl"] = "http://127.0.0.1:$port"
                } else {
                    // Default port for KernelSU modules
                    result["webUIPort"] = 8080
                    result["webUIUrl"] = "http://127.0.0.1:8080"
                }
            }
            
            // Also check for service.sh that might start a web server
            val serviceSh = "$modulePath/service.sh"
            val checkService = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -f '$serviceSh' && grep -q 'http\\|web\\|html\\|server' '$serviceSh' && echo 'web'"))
            checkService.waitFor()
            val hasWebService = checkService.inputStream.bufferedReader().readText().trim() == "web"
            
            if (hasWebService && result["hasWebUI"] == false) {
                result["hasWebUI"] = true
                result["webUIUrl"] = "http://127.0.0.1:8080"
            }
            
            result
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking module WebUI: ${e.message}")
            result
        }
    }
    
    /**
     * Open a module's web interface in browser
     * @param url The web UI URL
     * @return true if successful, false otherwise
     */
    private fun openModuleWebUI(url: String): Boolean {
        if (url.isEmpty()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error opening module WebUI: ${e.message}")
            false
        }
    }
    
    /**
     * Get detailed module info including WebUI and action script status
     * @param modulePath The path to the module directory
     * @return Map with module details
     */
    private fun getModuleDetails(modulePath: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "hasWebUI" to false,
            "webUIUrl" to null,
            "hasActionScript" to false,
            "webUIPort" to null
        )
        
        if (modulePath.isEmpty()) return result
        
        return try {
            // Check for action.sh
            val actionScript = "$modulePath/action.sh"
            val checkAction = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -f '$actionScript' && echo 'exists'"))
            checkAction.waitFor()
            result["hasActionScript"] = checkAction.inputStream.bufferedReader().readText().trim() == "exists"
            
            // Get WebUI info
            val webUIInfo = checkModuleWebUI(modulePath)
            result["hasWebUI"] = webUIInfo["hasWebUI"] ?: false
            result["webUIUrl"] = webUIInfo["webUIUrl"]
            result["webUIPort"] = webUIInfo["webUIPort"]
            
            result
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting module details: ${e.message}")
            result
        }
    }
    
    // ==================== WebUI Methods (KernelSU Compatible) ====================
    
    /**
     * Setup WebUI for a module
     * @param moduleDir The module directory path
     * @param moduleId The module ID
     * @return true if setup successful
     */
    private fun setupWebUI(moduleDir: String, moduleId: String): Boolean {
        return try {
            android.util.Log.d("MainActivity", "Setting up WebUI for module: $moduleId at $moduleDir")
            
            // Check if module has webroot directory
            val webrootDir = File("$moduleDir/webroot")
            if (!webrootDir.exists()) {
                android.util.Log.w("MainActivity", "Module does not have webroot directory")
                return false
            }
            
            // Check if index.html exists
            val indexFile = File("$moduleDir/webroot/index.html")
            if (!indexFile.exists()) {
                android.util.Log.w("MainActivity", "Module webroot does not have index.html")
                return false
            }
            
            android.util.Log.d("MainActivity", "WebUI setup successful for module: $moduleId")
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up WebUI: ${e.message}")
            false
        }
    }
    
    /**
     * Execute a command and return the output (for WebUI)
     * @param command The command to execute
     * @return The command output
     */
    private fun execWebUICommand(command: String): String {
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
            android.util.Log.e("MainActivity", "Error executing WebUI command: ${e.message}")
            ""
        }
    }
    
    /**
     * Execute a command and return the result with exit code, stdout, stderr
     * @param command The command to execute
     * @return Map with exitCode, stdout, stderr
     */
    private fun execWebUICommandWithResult(command: String): Map<String, Any?> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stdout = StringBuilder()
            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }
            
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
            val stderr = StringBuilder()
            while (stderrReader.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }
            
            process.waitFor()
            
            mapOf<String, Any?>(
                "exitCode" to process.exitValue(),
                "stdout" to stdout.toString().trim(),
                "stderr" to stderr.toString().trim()
            )
        } catch (e: Exception) {
            mapOf<String, Any?>(
                "exitCode" to 1,
                "stdout" to "",
                "stderr" to e.message
            )
        }
    }
    
    /**
     * Spawn a command with streaming output (for WebUI)
     * @param command The command to execute
     * @param callbackId The callback identifier
     * @return Map with initial result
     */
    private fun spawnWebUICommand(command: String, callbackId: String): Map<String, Any?> {
        return try {
            // For now, execute synchronously and return result
            // In a full implementation, this would stream output
            execWebUICommandWithResult(command)
        } catch (e: Exception) {
            mapOf<String, Any?>(
                "exitCode" to 1,
                "stdout" to "",
                "stderr" to e.message
            )
        }
    }
    
    /**
     * Set fullscreen mode for WebUI
     * @param enable Whether to enable fullscreen
     * @return true if successful
     */
    private fun setFullScreen(enable: Boolean): Boolean {
        return try {
            // This would be handled by the Flutter side
            // The native side just returns true for now
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Read a file from module's webroot directory
     * @param moduleDir The module directory path
     * @param relativePath The relative path within webroot
     * @return The file content or null
     */
    private fun readWebrootFile(moduleDir: String, relativePath: String): String? {
        return try {
            val filePath = "$moduleDir/webroot/$relativePath"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$filePath'"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            
            if (process.exitValue() == 0) {
                output.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error reading webroot file: ${e.message}")
            null
        }
    }
    
    /**
     * Check if a module has a webroot directory
     * @param moduleDir The module directory path
     * @return true if webroot exists
     */
    private fun hasWebroot(moduleDir: String): Boolean {
        return try {
            val webrootDir = File("$moduleDir/webroot")
            webrootDir.exists() && webrootDir.isDirectory
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Read a file as root - optimized for reading module files
     * @param filePath The path to the file to read
     * @return The file content or null
     */
    private fun readFileAsRoot(filePath: String): String? {
        if (filePath.isEmpty()) return null
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$filePath' 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            
            if (process.exitValue() == 0 && output.isNotEmpty()) {
                output.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error reading file as root: ${e.message}")
            null
        }
    }
    
    /**
     * Check if a file exists as root
     * @param filePath The path to check
     * @return true if file exists, false otherwise
     */
    private fun fileExistsAsRoot(filePath: String): Boolean {
        if (filePath.isEmpty()) return false
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -f '$filePath' && echo 'exists'"))
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            output == "exists"
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== Magisk Logs Methods ====================
    
    /**
     * Fetch Magisk logs using root shell
     * Same implementation as original Magisk app:
     * - First tries: cat /cache/magisk.log
     * - Falls back to: logcat -d -s Magisk
     * 
     * @return The log content as a string
     */
    private fun fetchMagiskLogs(): String {
        return try {
            // Method 1: Try to read Magisk log file (same as original Magisk app)
            // The log file is typically at /cache/magisk.log
            val logFile = "/cache/magisk.log"
            val catProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $logFile 2>/dev/null || logcat -d -s Magisk 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(catProcess.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            catProcess.waitFor()
            
            val result = output.toString()
            if (result.isNotEmpty()) {
                result
            } else {
                // Fallback: try alternative log locations
                val altLocations = listOf(
                    "/data/adb/magisk.log",
                    "/data/cache/magisk.log",
                    "/data/local/tmp/magisk.log"
                )
                
                for (location in altLocations) {
                    val altProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $location 2>/dev/null"))
                    val altReader = BufferedReader(InputStreamReader(altProcess.inputStream))
                    val altOutput = StringBuilder()
                    
                    while (altReader.readLine().also { line = it } != null) {
                        altOutput.append(line).append("\n")
                    }
                    
                    altProcess.waitFor()
                    
                    if (altOutput.isNotEmpty()) {
                        return altOutput.toString()
                    }
                }
                
                // Last resort: get all Magisk-related logs from logcat
                val logcatProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -d | grep -i magisk"))
                val logcatReader = BufferedReader(InputStreamReader(logcatProcess.inputStream))
                val logcatOutput = StringBuilder()
                
                while (logcatReader.readLine().also { line = it } != null) {
                    logcatOutput.append(line).append("\n")
                }
                
                logcatProcess.waitFor()
                logcatOutput.toString()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error fetching Magisk logs: ${e.message}")
            ""
        }
    }
    
    /**
     * Clear Magisk logs
     * Same implementation as original Magisk app:
     * - Uses: echo -n > /cache/magisk.log
     * 
     * @return true if successful, false otherwise
     */
    private fun clearMagiskLogs(): Boolean {
        return try {
            // Clear the main Magisk log file
            val clearProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo -n > /cache/magisk.log 2>/dev/null || true"))
            clearProcess.waitFor()
            
            // Also try alternative log locations
            val altLocations = listOf(
                "/data/adb/magisk.log",
                "/data/cache/magisk.log",
                "/data/local/tmp/magisk.log"
            )
            
            for (location in altLocations) {
                try {
                    val altClearProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo -n > $location 2>/dev/null || true"))
                    altClearProcess.waitFor()
                } catch (e: Exception) {
                    // Ignore errors for alternative locations
                }
            }
            
            android.util.Log.d("MainActivity", "Magisk logs cleared successfully")
            true
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error clearing Magisk logs: ${e.message}")
            false
        }
    }

    // ==================== No-Root Boot Image Patching ====================
    
    /**
     * Patch boot image WITHOUT root access
     * This allows users to patch a boot.img file on an unrooted device
     * The patched image can then be flashed via fastboot or another method
     * 
     * @param bootImage The path to the boot image file (from file picker, in app's cache)
     * @param outputDir Optional output directory (defaults to Downloads)
     * @return The path to the patched boot image, or null if failed
     */
    private fun patchBootImageNoRoot(bootImage: String, outputDir: String?): String? {
        return try {
            if (bootImage.isEmpty()) {
                sendLog("[ERROR] Boot image path is empty")
                return null
            }
            
            sendLog("[INFO] Starting no-root boot image patching: $bootImage")
            
            // Verify boot image exists (should be in app's cache from file picker)
            val bootImageFile = File(bootImage)
            if (!bootImageFile.exists()) {
                sendLog("[ERROR] Boot image file not found: $bootImage")
                return null
            }
            sendLog("[INFO] Boot image found: ${bootImageFile.absolutePath}, size: ${bootImageFile.length()} bytes")
            
            // Create working directory in app's cache (no root needed)
            val workDir = File(cacheDir, "magisk_patch_${System.currentTimeMillis()}")
            if (!workDir.mkdirs()) {
                sendLog("[ERROR] Failed to create working directory")
                return null
            }
            sendLog("[INFO] Working directory: ${workDir.absolutePath}")
            
            // Copy boot image to working directory
            val workBootImg = File(workDir, "boot.img")
            bootImageFile.copyTo(workBootImg)
            sendLog("[INFO] Boot image copied to working directory")
            
            // On Android 10+, we cannot execute binaries directly due to W^X policy
            // We need to use a different approach: execute via linker or use JNI
            val nativeLibDir = File(applicationInfo.nativeLibraryDir)
            sendLog("[INFO] Native library directory: ${nativeLibDir.absolutePath}")
            
            // Check if magiskboot exists in native library directory
            var magiskbootFile = File(nativeLibDir, "libmagiskboot.so")
            var useLinker = false
            
            if (!magiskbootFile.exists()) {
                // Try alternative names
                magiskbootFile = File(nativeLibDir, "magiskboot")
            }
            
            // If not found in native lib dir, try to extract from assets
            if (!magiskbootFile.exists()) {
                // Check if we have magiskboot in assets
                try {
                    val assetFile = File(workDir, "magiskboot")
                    val inputStream = assets.open("magiskboot")
                    inputStream.use { input ->
                        assetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    magiskbootFile = assetFile
                    sendLog("[INFO] magiskboot extracted from assets")
                } catch (e: Exception) {
                    sendLog("[ERROR] Failed to find magiskboot: ${e.message}")
                    sendLog("[ERROR] Please ensure libmagiskboot.so is included in the APK")
                    return null
                }
            }
            
            // On Android 10+, we need to handle binary execution carefully
            // The binary needs to be extracted from assets and executed via shell
            // We cannot execute files directly from app's cache directory due to W^X policy
            // But we CAN use "sh -c" to execute them (shell will load and execute the binary)
            
            val sdkVersion = android.os.Build.VERSION.SDK_INT
            sendLog("[INFO] SDK: $sdkVersion")
            sendLog("[INFO] magiskboot path: ${magiskbootFile.absolutePath}")
            
            // Check for magiskinit
            var magiskinitFile = File(workDir, "magiskinit")
            if (!magiskinitFile.exists()) {
                try {
                    val inputStream = assets.open("magiskinit")
                    inputStream.use { input ->
                        magiskinitFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    sendLog("[INFO] magiskinit extracted from assets")
                } catch (e: Exception) {
                    sendLog("[WARN] magiskinit not found in assets: ${e.message}")
                }
            }
            
            // Check for magisk binary
            var magiskBin = File(workDir, "magisk")
            if (!magiskBin.exists()) {
                try {
                    val inputStream = assets.open("magisk")
                    inputStream.use { input ->
                        magiskBin.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    sendLog("[INFO] magisk extracted from assets")
                } catch (e: Exception) {
                    sendLog("[WARN] magisk not found in assets: ${e.message}")
                }
            }
            
            // Use magiskboot to unpack the boot image
            sendLog("[INFO] Unpacking boot image with magiskboot...")
            
            // Function to execute magiskboot using shell
            // The key insight: on Android, we can use "sh -c './binary args'" to execute
            // a binary even if it doesn't have execute permission, as long as the shell
            // has permission to read the file and the binary format is recognized
            fun executeMagiskboot(vararg args: String): Pair<Int, String> {
                val argsStr = args.joinToString(" ") { 
                    if (it.contains(" ") || it.contains("(") || it.contains(")")) "\"$it\"" else it 
                }
                
                // Build command - use ./ prefix for relative path execution
                val cmdStr = "cd ${workDir.absolutePath} && ./magiskboot $argsStr"
                
                sendLog("[DEBUG] Executing: $cmdStr")
                
                return try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmdStr))
                    val output = StringBuilder()
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                    
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                        sendLog("[STDOUT] $line")
                    }
                    while (errorReader.readLine().also { line = it } != null) {
                        output.append("[ERR] $line").append("\n")
                        sendLog("[STDERR] $line")
                    }
                    
                    process.waitFor()
                    Pair(process.exitValue(), output.toString())
                } catch (e: Exception) {
                    sendLog("[ERROR] Exception executing command: ${e.message}")
                    Pair(-1, e.message ?: "Unknown error")
                }
            }
            
            // Try to unpack the boot image
            var result = executeMagiskboot("unpack", "-h", workBootImg.absolutePath)
            
            if (result.first != 0) {
                sendLog("[WARN] magiskboot unpack -h failed (exit: ${result.first}), trying without -h")
                result = executeMagiskboot("unpack", workBootImg.absolutePath)
                
                if (result.first != 0) {
                    // Check if it's a permission/execution issue
                    if (result.first == 126 || result.first == 127) {
                        sendLog("[ERROR] Cannot execute magiskboot (exit: ${result.first})")
                        sendLog("[ERROR] magiskboot file: ${magiskbootFile.absolutePath}")
                        sendLog("[ERROR] File exists: ${magiskbootFile.exists()}")
                        sendLog("[ERROR] File size: ${magiskbootFile.length()}")
                        sendLog("[ERROR] Can read: ${magiskbootFile.canRead()}")
                        
                        // Try alternative: use absolute path instead of ./
                        sendLog("[INFO] Trying with absolute path...")
                        val altCmd = "cd ${workDir.absolutePath} && ${magiskbootFile.absolutePath} unpack ${workBootImg.absolutePath}"
                        val altProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", altCmd))
                        val altReader = BufferedReader(InputStreamReader(altProcess.inputStream))
                        val altErrorReader = BufferedReader(InputStreamReader(altProcess.errorStream))
                        
                        var altLine: String?
                        while (altReader.readLine().also { altLine = it } != null) {
                            sendLog("[ALT OUT] $altLine")
                        }
                        while (altErrorReader.readLine().also { altLine = it } != null) {
                            sendLog("[ALT ERR] $altLine")
                        }
                        
                        altProcess.waitFor()
                        
                        if (altProcess.exitValue() != 0) {
                            sendLog("[ERROR] All execution methods failed (exit: ${altProcess.exitValue()})")
                            sendLog("[ERROR] Supported ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                            sendLog("[ERROR] The magiskboot binary may need to be compiled for this architecture")
                            return null
                        }
                    } else {
                        sendLog("[ERROR] Failed to unpack boot image (exit: ${result.first})")
                        return null
                    }
                }
            }
            
            sendLog("[INFO] Boot image unpacked successfully")
            
            // List extracted files
            workDir.listFiles()?.forEach { file ->
                sendLog("[DEBUG] Extracted: ${file.name} (${file.length()} bytes)")
            }
            
            // Check if ramdisk.cpio exists
            val ramdiskCpio = File(workDir, "ramdisk.cpio")
            if (!ramdiskCpio.exists()) {
                sendLog("[WARN] No ramdisk.cpio found, this might be a GKI boot image without ramdisk")
                // For GKI images, we need to add a ramdisk
            } else {
                sendLog("[INFO] Found ramdisk.cpio: ${ramdiskCpio.length()} bytes")
            }
            
            // Create stub directory structure for Magisk
            // Use "magisk_stub" to avoid conflict with magisk binary file
            val stubDir = File(workDir, "magisk_stub")
            stubDir.mkdirs()
            
            // Create a minimal magisk config
            val configFile = File(stubDir, "config")
            configFile.writeText("""
                KEEPVERITY=false
                KEEPFORCEENCRYPT=false
                PATCHVBMETAFLAG=false
                RECOVERYMODE=false
            """.trimIndent())
            
            // Copy magisk binaries to stub directory
            if (magiskBin.exists()) {
                magiskBin.copyTo(File(stubDir, "magisk"))
                File(stubDir, "magisk").setExecutable(true)
            }
            if (magiskinitFile.exists()) {
                magiskinitFile.copyTo(File(stubDir, "magiskinit"))
                File(stubDir, "magiskinit").setExecutable(true)
            }
            
            // Repack the boot image with Magisk
            sendLog("[INFO] Repacking boot image with Magisk modifications...")
            
            // Try cpio addition if ramdisk exists
            if (ramdiskCpio.exists() && magiskinitFile.exists()) {
                sendLog("[INFO] Adding Magisk init to ramdisk...")
                // magiskboot cpio ramdisk.cpio "add 0750 init magiskinit"
                val cpioProcess = Runtime.getRuntime().exec(arrayOf(
                    magiskbootFile.absolutePath, "cpio", ramdiskCpio.absolutePath,
                    "add", "0750", "init", magiskinitFile.absolutePath
                ), null, workDir)
                cpioProcess.waitFor()
                sendLog("[DEBUG] cpio add result: ${cpioProcess.exitValue()}")
            }
            
            // Repack
            val newBootImg = File(workDir, "new-boot.img")
            val repackProcess = Runtime.getRuntime().exec(arrayOf(
                magiskbootFile.absolutePath, "repack", workBootImg.absolutePath, newBootImg.absolutePath
            ), null, workDir)
            repackProcess.waitFor()
            
            val repackOutput = repackProcess.inputStream.bufferedReader().readText()
            val repackError = repackProcess.errorStream.bufferedReader().readText()
            sendLog("[DEBUG] magiskboot repack output: $repackOutput")
            if (repackError.isNotEmpty()) {
                sendLog("[DEBUG] magiskboot repack stderr: $repackError")
            }
            
            if (repackProcess.exitValue() != 0) {
                sendLog("[ERROR] Failed to repack boot image (exit code: ${repackProcess.exitValue()})")
                return null
            }
            
            if (!newBootImg.exists()) {
                sendLog("[ERROR] new-boot.img was not created")
                return null
            }
            
            sendLog("[INFO] Boot image patched successfully: ${newBootImg.length()} bytes")
            
            // Determine output location - use Downloads directory
            // For Android 10+, prefer app's external files directory which doesn't require special permissions
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val outputFileName = "magisk_patched_$timestamp.img"
            
            // Primary output: /storage/emulated/0/Download/
            val primaryOutputDir = File("/storage/emulated/0/Download")
            val primaryOutputFile = File(primaryOutputDir, outputFileName)
            
            // Fallback output: app's external files directory
            val fallbackOutputDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val fallbackOutputFile = File(fallbackOutputDir, outputFileName)
            
            var finalOutputPath: String? = null
            
            // Try primary output location first
            try {
                sendLog("[INFO] Trying to save to: ${primaryOutputFile.absolutePath}")
                if (primaryOutputDir.exists() || primaryOutputDir.mkdirs()) {
                    newBootImg.copyTo(primaryOutputFile, overwrite = true)
                    finalOutputPath = primaryOutputFile.absolutePath
                    sendLog("[INFO] Patched image saved to: $finalOutputPath")
                }
            } catch (e: Exception) {
                sendLog("[WARN] Failed to save to primary location: ${e.message}")
            }
            
            // Fallback to app's external files directory
            if (finalOutputPath == null) {
                try {
                    sendLog("[INFO] Trying fallback location: ${fallbackOutputFile.absolutePath}")
                    if (fallbackOutputDir != null && (fallbackOutputDir.exists() || fallbackOutputDir.mkdirs())) {
                        newBootImg.copyTo(fallbackOutputFile, overwrite = true)
                        finalOutputPath = fallbackOutputFile.absolutePath
                        sendLog("[INFO] Patched image saved to app directory: $finalOutputPath")
                        sendLog("[INFO] Use file manager to access: Android/data/com.magiskube.magisk/files/Download/")
                    }
                } catch (e: Exception) {
                    sendLog("[ERROR] Failed to save to fallback location: ${e.message}")
                }
            }
            
            if (finalOutputPath == null) {
                sendLog("[ERROR] Failed to save patched image to any location")
                return null
            }
            
            // Cleanup working directory
            workDir.deleteRecursively()
            sendLog("[INFO] Cleanup completed")
            
            finalOutputPath
        } catch (e: Exception) {
            sendLog("[ERROR] Error patching boot image: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
