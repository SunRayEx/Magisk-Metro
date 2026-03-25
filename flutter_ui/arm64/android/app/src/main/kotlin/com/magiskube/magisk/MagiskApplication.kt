package com.magiskube.magisk

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File

class MagiskApplication : Application() {
    companion object {
        var isRootAvailable = false
            private set
        
        // Common su binary locations
        private val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/adb/magisk/busybox/su",
            "/data/adb/ksu/bin/su",
            "su" // fallback to PATH
        )
        
        init {
            // Remove synchronous root check to avoid blocking main thread during app startup
        }
        
        private fun findSuPath(): String? {
            for (path in suPaths) {
                if (File(path).exists()) {
                    return path
                }
            }
            return null
        }
        
        fun initializeRoot(): Boolean {
            if (isRootAvailable) return true
            
            try {
                // Find su binary
                val suPath = findSuPath()
                if (suPath == null) {
                    // Try using su from PATH as fallback
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val output = reader.readText()
                        process.waitFor()
                        if (output.contains("uid=0")) {
                            isRootAvailable = true
                            return true
                        }
                    } catch (e: Exception) {}
                    
                    isRootAvailable = false
                    return false
                }
                
                // Use found su path with magisk namespace
                val process = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "id; echo ROOT_CHECK_COMPLETED"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                process.waitFor()
                
                isRootAvailable = output.contains("uid=0") && output.contains("ROOT_CHECK_COMPLETED")
            } catch (e: Exception) {
                isRootAvailable = false
            }
            
            return isRootAvailable
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        // Ensure root is requested when application starts and show status
        Handler(Looper.getMainLooper()).postDelayed({
            val status = initializeRoot()
            val errorMsg = if (status) {
                "Root access granted!"
            } else {
                val suFound = suPaths.any { File(it).exists() }
                "Root access denied/failed\nsu found: $suFound"
            }
            Toast.makeText(
                this,
                errorMsg,
                Toast.LENGTH_LONG
            ).show()
        }, 500)
    }
}
