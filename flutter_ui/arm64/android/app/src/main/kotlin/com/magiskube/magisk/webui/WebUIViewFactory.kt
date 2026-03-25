package com.magiskube.magisk.webui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import java.io.File

/**
 * Factory for creating WebUI Platform Views
 */
class WebUIViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val params = args as? Map<String, Any> ?: emptyMap()
        val modulePath = params["modulePath"] as? String ?: ""
        val moduleId = params["moduleId"] as? String ?: ""
        
        return WebUIView(context, modulePath, moduleId)
    }
}

/**
 * Platform View for WebUI using WebViewAssetLoader
 */
class WebUIView(
    private val context: Context,
    private val modulePath: String,
    private val moduleId: String
) : PlatformView {
    
    private val webView: WebView = WebView(context)
    private var assetLoader: WebViewAssetLoader? = null
    
    init {
        setupWebView()
    }
    
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        // Add JavaScript interface for native functions
        // IMPORTANT: Use "ksu" as the interface name for KernelSU compatibility
        webView.addJavascriptInterface(
            WebViewInterface(context, webView, modulePath),
            "ksu"
        )
        
        // Also add with "KernelSU" name for backwards compatibility
        webView.addJavascriptInterface(
            WebViewInterface(context, webView, modulePath),
            "KernelSU"
        )

        // Load empty or loading state initially to avoid ERR_FILE_NOT_FOUND flash
        val loadingHtml = "<html><body style='background-color:#1a1a1a;color:white;display:flex;justify-content:center;align-items:center;height:100vh;font-family:sans-serif;'><h2>Loading WebUI...</h2></body></html>"
        webView.loadData(loadingHtml, "text/html", "UTF-8")
        
        // Run file copying and checks asynchronously to avoid blocking UI thread (App startup stutter fix)
        Thread {
            prepareWebrootAndLoad()
        }.start()
    }
    
    private fun prepareWebrootAndLoad() {
        val cacheDir = File(context.cacheDir, "webui/$moduleId")
        
        android.util.Log.d("WebUIView", "Starting WebUI setup for module: $moduleId")
        android.util.Log.d("WebUIView", "Module path: $modulePath, Target cache dir: ${cacheDir.absolutePath}")

        // Clear existing cache to ensure we get the latest version (Cache cleaning mechanism)
        if (cacheDir.exists()) {
            android.util.Log.d("WebUIView", "Clearing existing WebUI cache for $moduleId")
            cacheDir.deleteRecursively()
        }
        cacheDir.mkdirs()
        
        // Pre-check if source webroot exists
        val webrootPath = "$modulePath/webroot"
        val checkProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -d '$webrootPath' && echo 'exists'"))
        checkProcess.waitFor()
        val exists = checkProcess.inputStream.bufferedReader().readText().trim() == "exists"
        
        var copySuccess = false
        if (exists) {
            android.util.Log.d("WebUIView", "Source webroot exists. Copying files...")
            copySuccess = copyDirectoryWithRoot(webrootPath, cacheDir.absolutePath)
        } else {
            android.util.Log.e("WebUIView", "Source webroot does not exist at: $webrootPath")
        }
        
        // Post back to main thread
        Handler(Looper.getMainLooper()).post {
            val indexFile = File(cacheDir, "index.html")
            
            if (copySuccess && indexFile.exists()) {
                android.util.Log.d("WebUIView", "File verification passed. Index exists at: ${indexFile.absolutePath}")
                
                // Set up WebViewAssetLoader matching physical path
                assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
                    .addPathHandler("/webui/", WebViewAssetLoader.InternalStoragePathHandler(context, cacheDir))
                    .setDomain("magiskube.local")
                    .build()
                
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        return request?.url?.let { assetLoader?.shouldInterceptRequest(it) }
                    }
                }
                
                android.util.Log.d("WebUIView", "Loading WebUI via WebViewAssetLoader")
                webView.loadUrl("https://magiskube.local/webui/index.html")
            } else {
                android.util.Log.e("WebUIView", "Fallback strategy: WebUI files missing or copy failed")
                val errorHtml = "<html><body style='background-color:#1a1a1a;color:#ff5252;display:flex;flex-direction:column;justify-content:center;align-items:center;height:100vh;font-family:sans-serif;'><h2>Error: WebUI Not Found</h2><p>net::ERR_FILE_NOT_FOUND</p><p>Please check if the module has a valid webroot.</p></body></html>"
                webView.loadData(errorHtml, "text/html", "UTF-8")
            }
        }
    }
    
    /**
     * Copy entire directory using root shell
     */
    private fun copyDirectoryWithRoot(sourcePath: String, destPath: String): Boolean {
        return try {
            // Remove existing destination just in case
            Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -rf '$destPath'")).waitFor()
            // Create parent directory
            Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p '$destPath'")).waitFor()
            // Copy all files from webroot
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp -r '$sourcePath'/* '$destPath'/"))
            process.waitFor()
            val success = process.exitValue() == 0
            
            // Also copy hidden files (starting with .)
            Runtime.getRuntime().exec(arrayOf("su", "-c", "cp -r '$sourcePath'/.* '$destPath'/ 2>/dev/null || true")).waitFor()
            
            // Important: Set permissions so app can access them
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod -R 755 '$destPath'")).waitFor()
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chown -R system:system '$destPath' 2>/dev/null || true")).waitFor()
            
            android.util.Log.d("WebUIView", "Copied directory with root: $sourcePath -> $destPath, success=$success")
            success
        } catch (e: Exception) {
            android.util.Log.e("WebUIView", "Failed to copy directory with root: ${e.message}")
            false
        }
    }
    
    override fun getView(): WebView = webView
    
    override fun dispose() {
        webView.destroy()
    }
}
