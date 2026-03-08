package com.magiskube.magisk

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
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
import java.io.InputStreamReader

class MainActivity : FlutterActivity() {
    private var rootAccessGranted = false

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

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, FILEPICKER_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "pickFile" -> {
                    pickFile(result)
                }
                else -> result.notImplemented()
            }
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
            // Method 1: Check if su binary exists and works
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            
            if (output != null && output.contains("uid=0")) {
                return true
            }
            
            // Method 2: Check if Magisk binary exists
            val magiskCheck = Runtime.getRuntime().exec(arrayOf("su", "-c", "which magisk"))
            val magiskReader = BufferedReader(InputStreamReader(magiskCheck.inputStream))
            val magiskPath = magiskReader.readLine()
            magiskCheck.waitFor()
            
            if (magiskPath != null && magiskPath.contains("magisk")) {
                return true
            }
            
            // Method 3: Check if /data/adb/magisk directory exists
            if (File("/data/adb/magisk").exists()) {
                return true
            }
            
            false
        } catch (e: Exception) {
            // Fallback: check if Magisk directory exists
            File("/data/adb/magisk").exists()
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
            
            // Method 2: Check /proc/cmdline for skip_initramfs (original method)
            val cmdlineFile = File("/proc/cmdline")
            if (cmdlineFile.exists()) {
                val cmdline = cmdlineFile.readText()
                if (!cmdline.contains("skip_initramfs")) {
                    return true
                }
            }
            
            // Method 3: Check if Magisk daemon is running
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A | grep magiskd"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            if (output.contains("magiskd")) {
                return true
            }
            
            // Method 4: Check for Magisk boot image backup
            val backupDir = File("/data/adb/boot-backup")
            if (backupDir.exists() && backupDir.isDirectory) {
                val backupFiles = backupDir.listFiles()
                if (backupFiles != null && backupFiles.isNotEmpty()) {
                    return true
                }
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
            // Add to Magisk SU allow list (if using Magisk's built-in SU manager)
            // Note: This requires Magisk's SU functionality to be enabled
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"))
            process.waitFor()
            // For Magisk SU, we track this in a simple file-based list
            val suListFile = File("/data/adb/su_allowed_packages.txt")
            val currentList = if (suListFile.exists()) suListFile.readText().split("\n").filter { it.isNotEmpty() } else emptyList()
            if (!currentList.contains(packageName)) {
                suListFile.appendText("$packageName\n")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun revokeRootAccess(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            // Revoke secure settings permission
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm revoke $packageName android.permission.WRITE_SECURE_SETTINGS"))
            process.waitFor()
            // Remove from allowed list
            val suListFile = File("/data/adb/su_allowed_packages.txt")
            if (suListFile.exists()) {
                val currentList = suListFile.readText().split("\n").filter { it.isNotEmpty() && it != packageName }
                suListFile.writeText(currentList.joinToString("\n") + if (currentList.isNotEmpty()) "\n" else "")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getRootAllowedPackages(): List<String> {
        return try {
            val suListFile = File("/data/adb/su_allowed_packages.txt")
            if (suListFile.exists()) {
                suListFile.readText().split("\n").filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun installMagisk(bootImage: String, isPatchMode: Boolean): Boolean {
        return try {
            // Create temporary directory for Magisk files
            val tmpDir = "/data/local/tmp/magisk_install"
            val processMkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $tmpDir"))
            processMkdir.waitFor()
            
            if (bootImage.isNotEmpty()) {
                // Copy boot image to tmp directory
                val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $bootImage $tmpDir/boot.img"))
                processCp.waitFor()
                
                // Copy necessary Magisk files to tmp directory
                val magiskFiles = listOf("magiskinit", "magisk", "magiskboot", "init-ld", "stub.apk", "util_functions.sh", "boot_patch.sh")
                for (file in magiskFiles) {
                    val processCopy = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp /data/adb/magisk/$file $tmpDir/"))
                    processCopy.waitFor()
                }
                
                // Make files executable
                val processChmod = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpDir/*"))
                processChmod.waitFor()
                
                // Execute boot patch script
                val processPatch = Runtime.getRuntime().exec(arrayOf("su", "-c", "cd $tmpDir && ./boot_patch.sh boot.img"))
                processPatch.waitFor()
                
                if (processPatch.exitValue() == 0) {
                    if (isPatchMode) {
                        // For patch mode, just copy the patched image to a new location
                        val outputFile = "/storage/emulated/0/Download/magisk_patched_$(System.currentTimeMillis()).img"
                        val processCopyOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $tmpDir/new-boot.img $outputFile"))
                        processCopyOut.waitFor()
                        processCopyOut.exitValue() == 0
                    } else {
                        // For install mode, flash the patched image
                        val processFlash = Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$tmpDir/new-boot.img of=$bootImage"))
                        processFlash.waitFor()
                        processFlash.exitValue() == 0
                    }
                } else {
                    false
                }
            } else {
                // Find boot image automatically (only for install mode, not patch mode)
                if (isPatchMode) {
                    // Patch mode requires a specific boot image file
                    return false
                }
                
                val bootImageAuto = findBootImage()
                if (bootImageAuto.isEmpty()) {
                    return false
                }
                
                // Copy boot image to tmp directory
                val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $bootImageAuto $tmpDir/boot.img"))
                processCp.waitFor()
                
                // Copy necessary Magisk files to tmp directory
                val magiskFiles = listOf("magiskinit", "magisk", "magiskboot", "init-ld", "stub.apk", "util_functions.sh", "boot_patch.sh")
                for (file in magiskFiles) {
                    val processCopy = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp /data/adb/magisk/$file $tmpDir/"))
                    processCopy.waitFor()
                }
                
                // Make files executable
                val processChmod = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpDir/*"))
                processChmod.waitFor()
                
                // Execute boot patch script
                val processPatch = Runtime.getRuntime().exec(arrayOf("su", "-c", "cd $tmpDir && ./boot_patch.sh boot.img"))
                processPatch.waitFor()
                
                if (processPatch.exitValue() == 0) {
                    // Flash the patched image
                    val processFlash = Runtime.getRuntime().exec(arrayOf("su", "-c", "dd if=$tmpDir/new-boot.img of=$bootImageAuto"))
                    processFlash.waitFor()
                    processFlash.exitValue() == 0
                } else {
                    false
                }
            }
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
            
            // Check for init_boot first (for newer devices)
            if (slotSuffix.isNotEmpty()) {
                val initBootWithSlot = "/dev/block/by-name/init_boot$slotSuffix"
                if (File(initBootWithSlot).exists()) {
                    return initBootWithSlot
                }
            }
            val initBoot = "/dev/block/by-name/init_boot"
            if (File(initBoot).exists()) {
                return initBoot
            }
            
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
            // Create temporary directory for uninstaller
            val tmpDir = "/data/local/tmp/magisk_uninstall"
            val processMkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $tmpDir"))
            processMkdir.waitFor()
            
            // Check if uninstaller.sh exists in /data/adb/magisk/, if not, copy from app assets
            var uninstallerPath = "/data/adb/magisk/uninstaller.sh"
            val uninstallerFile = File(uninstallerPath)
            if (!uninstallerFile.exists()) {
                // Copy from app assets to temporary location
                val assetUninstallerPath = "/data/local/tmp/uninstaller.sh"
                // Read uninstaller.sh from app assets and write to temporary location
                val inputStream = assets.open("uninstaller.sh")
                val content = inputStream.bufferedReader().readText()
                inputStream.close()
                
                // Write to temporary file
                val processWrite = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo '$content' > $assetUninstallerPath"))
                processWrite.waitFor()
                uninstallerPath = assetUninstallerPath
            }
            
            // Copy uninstaller script and necessary files
            val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $uninstallerPath $tmpDir/"))
            processCp.waitFor()
            
            // Copy Magisk binaries if they exist
            val magiskFiles = listOf("magisk", "magiskboot", "util_functions.sh")
            for (file in magiskFiles) {
                val sourceFile = "/data/adb/magisk/$file"
                if (File(sourceFile).exists()) {
                    val processCopy = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $sourceFile $tmpDir/"))
                    processCopy.waitFor()
                }
            }
            
            // Make files executable
            val processChmod = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpDir/*"))
            processChmod.waitFor()
            
            // Execute uninstaller script
            val cmd = if (restoreImages) {
                "$tmpDir/uninstaller.sh --restore-images"
            } else {
                "$tmpDir/uninstaller.sh"
            }
            
            val processUninstall = Runtime.getRuntime().exec(arrayOf("su", "-c", "cd $tmpDir && $cmd"))
            processUninstall.waitFor()
            processUninstall.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun patchBootImage(bootImage: String): String? {
        return try {
            if (bootImage.isEmpty()) return null
            
            // Create temporary directory
            val tmpDir = "/data/local/tmp/magisk_patch"
            val processMkdir = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $tmpDir"))
            processMkdir.waitFor()
            
            // Copy boot image to tmp directory
            val processCp = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $bootImage $tmpDir/boot.img"))
            processCp.waitFor()
            
            // Copy necessary Magisk files
            val magiskFiles = listOf("magiskinit", "magisk", "magiskboot", "init-ld", "stub.apk", "util_functions.sh", "boot_patch.sh")
            for (file in magiskFiles) {
                val processCopy = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp /data/adb/magisk/$file $tmpDir/"))
                processCopy.waitFor()
            }
            
            // Make files executable
            val processChmod = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 755 $tmpDir/*"))
            processChmod.waitFor()
            
            // Execute boot patch script
            val processPatch = Runtime.getRuntime().exec(arrayOf("su", "-c", "cd $tmpDir && ./boot_patch.sh boot.img"))
            processPatch.waitFor()
            
            if (processPatch.exitValue() == 0) {
                val outputFile = bootImage.replace(".img", "_patched.img")
                // Copy patched image back
                val processCopyOut = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp $tmpDir/new-boot.img $outputFile"))
                processCopyOut.waitFor()
                if (processCopyOut.exitValue() == 0) {
                    outputFile
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateMagiskManager(): Boolean {
        return try {
            // Download latest Magisk Manager from GitHub
            val downloadUrl = "https://github.com/topjohnwu/Magisk/releases/latest/download/app-release.apk"
            val apkPath = "/data/local/tmp/MagiskManager.apk"
            
            // Download the APK
            val processDownload = Runtime.getRuntime().exec(arrayOf("su", "-c", "curl -L -o $apkPath $downloadUrl"))
            processDownload.waitFor()
            
            if (processDownload.exitValue() == 0) {
                // Install the APK
                val processInstall = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r $apkPath"))
                processInstall.waitFor()
                processInstall.exitValue() == 0
            } else {
                false
            }
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
