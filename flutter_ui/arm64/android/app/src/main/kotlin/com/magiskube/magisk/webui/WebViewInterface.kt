package com.magiskube.magisk.webui

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Window
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * JavaScript interface for WebView to communicate with native code
 * Provides KernelSU WebUI compatible API
 * 
 * Based on APatch's WebViewInterface design
 * Uses pure Java Process API for root command execution
 */
class WebViewInterface(
    val context: Context,
    private val webView: WebView,
    private val modDir: String
) {
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun exec(cmd: String): String {
        return executeRootCommand(cmd).stdout
    }

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String) {
        exec(cmd, null, callbackFunc)
    }

    private fun processOptions(sb: StringBuilder, options: String?) {
        val opts = if (options == null) JSONObject() else {
            JSONObject(options)
        }

        val cwd = opts.optString("cwd")
        if (!TextUtils.isEmpty(cwd)) {
            sb.append("cd $cwd;")
        }

        opts.optJSONObject("env")?.let { env ->
            env.keys().forEach { key ->
                sb.append("export $key=${env.getString(key)};")
            }
        }
    }

    @JavascriptInterface
    fun exec(
        cmd: String,
        options: String?,
        callbackFunc: String
    ) {
        val finalCommand = StringBuilder()
        processOptions(finalCommand, options)
        finalCommand.append(cmd)

        executor.execute {
            val result = executeRootCommand(finalCommand.toString())
            
            val jsCode = "javascript: (function() { try { $callbackFunc(${result.exitCode}, ${JSONObject.quote(result.stdout)}, ${JSONObject.quote(result.stderr)}); } catch(e) { console.error(e); } })();"
            
            mainHandler.post {
                webView.evaluateJavascript(jsCode, null)
            }
        }
    }

    @JavascriptInterface
    fun spawn(command: String, args: String, options: String?, callbackFunc: String) {
        val finalCommand = StringBuilder()

        processOptions(finalCommand, options)

        if (!TextUtils.isEmpty(args)) {
            finalCommand.append(command).append(" ")
            JSONArray(args).let { argsArray ->
                for (i in 0 until argsArray.length()) {
                    finalCommand.append(argsArray.getString(i))
                    finalCommand.append(" ")
                }
            }
        } else {
            finalCommand.append(command)
        }

        executor.execute {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su"))
                val outputStream = process.outputStream
                val inputStream = process.inputStream
                val errorStream = process.errorStream

                // Send command
                outputStream.write((finalCommand.toString() + "\n").toByteArray())
                outputStream.write("exit\n".toByteArray())
                outputStream.flush()

                // Read stdout line by line
                val stdoutReader = BufferedReader(InputStreamReader(inputStream))
                val stderrReader = BufferedReader(InputStreamReader(errorStream))

                val emitData = fun(name: String, data: String) {
                    val jsCode = "javascript: (function() { try { $callbackFunc.$name.emit('data', ${JSONObject.quote(data)}); } catch(e) { console.error('emitData', e); } })();"
                    mainHandler.post {
                        webView.evaluateJavascript(jsCode, null)
                    }
                }

                // Read stdout
                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    emitData("stdout", line ?: "")
                }

                // Read stderr
                while (stderrReader.readLine().also { line = it } != null) {
                    emitData("stderr", line ?: "")
                }

                val exitCode = process.waitFor()

                // Emit exit
                val emitExitCode = "javascript: (function() { try { $callbackFunc.emit('exit', $exitCode); } catch(e) { console.error('emitExit error: ' + e); } })();"
                mainHandler.post {
                    webView.evaluateJavascript(emitExitCode, null)
                }

                if (exitCode != 0) {
                    val emitErrCode = "javascript: (function() { try { var err = new Error(); err.exitCode = $exitCode; err.message = 'Command failed with exit code $exitCode'; $callbackFunc.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"
                    mainHandler.post {
                        webView.evaluateJavascript(emitErrCode, null)
                    }
                }

                process.destroy()
            } catch (e: Exception) {
                val emitErrCode = "javascript: (function() { try { var err = new Error(); err.exitCode = 1; err.message = ${JSONObject.quote(e.message)}; $callbackFunc.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"
                mainHandler.post {
                    webView.evaluateJavascript(emitErrCode, null)
                }
            }
        }
    }

    @JavascriptInterface
    fun toast(msg: String) {
        mainHandler.post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun fullScreen(enable: Boolean) {
        if (context is Activity) {
            mainHandler.post {
                if (enable) {
                    hideSystemUI(context.window)
                } else {
                    showSystemUI(context.window)
                }
            }
        }
        enableInsets(enable)
    }

    @JavascriptInterface
    fun enableInsets(enable: Boolean = true) {
        // This can be implemented by the WebView host
    }

    @JavascriptInterface
    fun moduleInfo(): String {
        val currentModuleInfo = JSONObject()
        currentModuleInfo.put("moduleDir", modDir)
        val moduleId = File(modDir).name
        currentModuleInfo.put("id", moduleId)
        return currentModuleInfo.toString()
    }
    
    /**
     * Get module directory path - KernelSU compatible method
     * @return The absolute path to the module directory
     */
    @JavascriptInterface
    fun getModuleDir(): String {
        return modDir
    }
    
    /**
     * Execute root command - KernelSU compatible method
     * This is the same as exec() but explicitly named for KSU compatibility
     * @param cmd The command to execute
     * @return The stdout output
     */
    @JavascriptInterface
    fun execRoot(cmd: String): String {
        return exec(cmd)
    }
    
    /**
     * Go fullscreen - KernelSU compatible method
     * @param enable Whether to enable fullscreen mode
     */
    @JavascriptInterface
    fun goFullScreen(enable: Boolean) {
        fullScreen(enable)
    }

    @JavascriptInterface
    fun listPackages(type: String): String {
        val packageManager = context.packageManager
        val packages = packageManager.getInstalledPackages(0)
        
        val filteredPackages = packages.filter { pkg ->
            val app = pkg.applicationInfo
            when (type.lowercase()) {
                "system" -> (app?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                "user" -> (app?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM == 0
                else -> true
            }
        }.map { it.packageName }.sorted()

        val jsonArray = JSONArray()
        for (pkgName in filteredPackages) {
            jsonArray.put(pkgName)
        }
        return jsonArray.toString()
    }

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val jsonArray = JSONArray()
        val packageManager = context.packageManager
        
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            try {
                val pkg = packageManager.getPackageInfo(pkgName, 0)
                val app = pkg.applicationInfo
                val obj = JSONObject()
                obj.put("packageName", pkg.packageName)
                obj.put("versionName", pkg.versionName ?: "")
                obj.put("versionCode", PackageInfoCompat.getLongVersionCode(pkg))
                obj.put("appLabel", app?.loadLabel(packageManager)?.toString() ?: pkgName)
                obj.put("isSystem", if (app != null) ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) else JSONObject.NULL)
                obj.put("uid", app?.uid ?: JSONObject.NULL)
                jsonArray.put(obj)
            } catch (e: Exception) {
                val obj = JSONObject()
                obj.put("packageName", pkgName)
                obj.put("error", "Package not found or inaccessible")
                jsonArray.put(obj)
            }
        }
        return jsonArray.toString()
    }

    /**
     * Execute a root command and return the result
     */
    private fun executeRootCommand(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su"))
            val outputStream = process.outputStream
            val inputStream = process.inputStream
            val errorStream = process.errorStream

            // Send command
            outputStream.write((command + "\n").toByteArray())
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            outputStream.close()

            // Read output
            val stdout = BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
            val stderr = BufferedReader(InputStreamReader(errorStream)).use { reader ->
                reader.readText()
            }

            val exitCode = process.waitFor()
            process.destroy()

            CommandResult(exitCode, stdout.trimEnd('\n'), stderr.trimEnd('\n'))
        } catch (e: Exception) {
            CommandResult(1, "", e.message ?: "Unknown error")
        }
    }

    /**
     * Result of a command execution
     */
    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    fun destroy() {
        executor.shutdown()
    }
}

fun hideSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

fun showSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
