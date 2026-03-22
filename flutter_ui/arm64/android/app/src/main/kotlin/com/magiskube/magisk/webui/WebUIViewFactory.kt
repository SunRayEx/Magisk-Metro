package com.magiskube.magisk.webui

import android.content.Context
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
        
        // Create cache directory for WebUI
        val cacheDir = File(context.cacheDir, "webui/$moduleId")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        // Copy webroot files to cache
        val webrootSource = File(modulePath, "webroot")
        if (webrootSource.exists()) {
            copyDirectory(webrootSource, cacheDir)
        }
        
        // Set up WebViewAssetLoader
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
            .addPathHandler("/webui/", WebViewAssetLoader.InternalStoragePathHandler(context, cacheDir))
            .build()
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                return request?.url?.let { assetLoader?.shouldInterceptRequest(it) }
            }
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
        
        // Load the WebUI
        val indexFile = File(cacheDir, "index.html")
        if (indexFile.exists()) {
            // Load via asset loader
            webView.loadUrl("https://appassets.androidplatform.net/webui/index.html")
        } else {
            // Try direct file path
            webView.loadUrl("file://${cacheDir.absolutePath}/index.html")
        }
    }
    
    private fun copyDirectory(source: File, dest: File) {
        if (!dest.exists()) {
            dest.mkdirs()
        }
        
        // Use root to copy files from /data/adb/modules/
        // because app doesn't have direct access to that directory
        try {
            // First try direct copy (might work for some files)
            source.listFiles()?.forEach { file ->
                val destFile = File(dest, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    try {
                        file.copyTo(destFile, overwrite = true)
                    } catch (e: Exception) {
                        // Try with root for files we can't access directly
                        copyFileWithRoot(file.absolutePath, destFile.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            // If listing fails, use root to copy entire directory
            copyDirectoryWithRoot(source.absolutePath, dest.absolutePath)
        }
    }
    
    /**
     * Copy a file using root shell
     */
    private fun copyFileWithRoot(sourcePath: String, destPath: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '$sourcePath' '$destPath'"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            android.util.Log.e("WebUIView", "Failed to copy file with root: ${e.message}")
            false
        }
    }
    
    /**
     * Copy entire directory using root shell
     */
    private fun copyDirectoryWithRoot(sourcePath: String, destPath: String): Boolean {
        return try {
            // Remove existing destination
            Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -rf '$destPath'")).waitFor()
            // Create parent directory
            Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p '$destPath'")).waitFor()
            // Copy all files from webroot
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp -r '$sourcePath'/* '$destPath'/"))
            process.waitFor()
            val success = process.exitValue() == 0
            
            // Also copy hidden files (starting with .)
            Runtime.getRuntime().exec(arrayOf("su", "-c", "cp -r '$sourcePath'/.* '$destPath'/ 2>/dev/null || true")).waitFor()
            
            // Set permissions so app can access
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod -R 755 '$destPath'")).waitFor()
            
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
