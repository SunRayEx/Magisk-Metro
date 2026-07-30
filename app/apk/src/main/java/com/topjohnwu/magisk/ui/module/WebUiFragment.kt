package com.topjohnwu.magisk.ui.module

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.databinding.FragmentWebuiMd2Binding
import com.topjohnwu.magisk.ui.theme.MetroAccentRole
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

class WebUiViewModel : BaseViewModel()

class WebUiFragment : BaseFragment<FragmentWebuiMd2Binding>() {

    override val layoutRes = R.layout.fragment_webui_md2
    override val metroAccentRole = MetroAccentRole.MODULES
    override val viewModel by viewModel<WebUiViewModel>()
    override val snackbarView: View get() = binding.snackbarContainer

    private lateinit var args: WebUiFragmentArgs
    private lateinit var webRoot: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        args = WebUiFragmentArgs.fromBundle(requireArguments())
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(args.name)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.moduleWebview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            webViewClient = ModuleWebViewClient()
            addJavascriptInterface(ModuleWebInterface(this), JS_BRIDGE_NAME)
        }

        if (!MODULE_ID.matches(args.id)) {
            showError()
            return
        }

        webRoot = File(requireContext().cacheDir, "module_webui/${args.id}")
        viewLifecycleOwner.lifecycleScope.launch {
            val ready = withContext(Dispatchers.IO) { stageWebRoot() }
            if (ready) {
                binding.loadingContainer.isVisible = false
                binding.moduleWebview.loadUrl(ENTRY_URL)
            } else {
                showError()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return if (binding.moduleWebview.canGoBack()) {
            binding.moduleWebview.goBack()
            true
        } else {
            false
        }
    }

    override fun onDestroyView() {
        binding.moduleWebview.apply {
            removeJavascriptInterface(JS_BRIDGE_NAME)
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroyView()
    }

    private fun stageWebRoot(): Boolean {
        val source = "/data/adb/modules/${args.id}/webroot"
        val target = webRoot.absolutePath
        val uid = requireContext().applicationInfo.uid
        val result = Shell.cmd(
            "rm -rf ${target.shellQuote()}",
            "mkdir -p ${target.shellQuote()}",
            "cp -R ${"$source/.".shellQuote()} ${target.shellQuote()}/",
            "chown -R $uid:$uid ${target.shellQuote()}"
        ).exec()
        return result.isSuccess && File(webRoot, "index.html").isFile
    }

    private fun showError() {
        binding.loadingContainer.isVisible = false
        binding.moduleWebview.isVisible = false
        binding.errorText.isVisible = true
    }

    private inner class ModuleWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val uri = request.url
            if (uri.scheme != LOCAL_SCHEME || uri.host != LOCAL_HOST) return null
            return localResponse(Uri.decode(uri.encodedPath.orEmpty()).trimStart('/'))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == LOCAL_SCHEME && uri.host == LOCAL_HOST) return false
            return if (uri.scheme == "http" || uri.scheme == "https") {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                true
            } else {
                true
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            binding.loadingContainer.isVisible = false
        }
    }

    private fun localResponse(relativePath: String): WebResourceResponse {
        val path = relativePath.ifBlank { "index.html" }
        if ('\u0000' in path) return notFound()
        return runCatching {
            val rootPath = webRoot.canonicalFile
            val requested = File(rootPath, path).canonicalFile
            val rootPrefix = rootPath.path.trimEnd(File.separatorChar) + File.separator
            if (!requested.path.startsWith(rootPrefix) || !requested.isFile) return notFound()
            WebResourceResponse(
                mimeType(requested.name),
                if (requested.extension.lowercase() in TEXT_EXTENSIONS) "utf-8" else null,
                FileInputStream(requested)
            )
        }.getOrElse { notFound() }
    }

    private fun notFound() = WebResourceResponse(null, null, null)

    private inner class ModuleWebInterface(private val webView: WebView) {
        @JavascriptInterface
        fun exec(command: String): String {
            return Shell.cmd(command).exec().out.joinToString("\n")
        }

        @JavascriptInterface
        fun exec(command: String, callbackName: String) {
            exec(command, "{}", callbackName)
        }

        @JavascriptInterface
        fun exec(command: String, optionsJson: String?, callbackName: String) {
            if (!CALLBACK_NAME.matches(callbackName)) return
            val commandWithOptions = applyOptions(command, optionsJson)
            Shell.cmd(commandWithOptions).submit { result ->
                val script = "window[${JSONObject.quote(callbackName)}](" +
                    "${result.code},${JSONObject.quote(result.out.joinToString("\n"))}," +
                    "${JSONObject.quote(result.err.joinToString("\n"))})"
                webView.post { webView.evaluateJavascript(script, null) }
            }
        }

        @JavascriptInterface
        fun toast(message: String) {
            webView.post {
                Toast.makeText(webView.context, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun moduleInfo(): String = JSONObject().apply {
            put("id", args.id)
            put("name", args.name)
            put("moduleDir", "/data/adb/modules/${args.id}")
        }.toString()

        @JavascriptInterface
        fun fullScreen(enable: Boolean) = Unit

        @JavascriptInterface
        fun enableEdgeToEdge(enable: Boolean) = Unit

        @JavascriptInterface
        fun exit() {
            webView.post { activity?.onBackPressed() }
        }

        private fun applyOptions(command: String, optionsJson: String?): String {
            val options = runCatching { JSONObject(optionsJson ?: "{}") }.getOrNull()
                ?: return command
            val cwd = options.optString("cwd").takeIf { it.isNotBlank() }
                ?: "/data/adb/modules/${args.id}"
            return "cd ${cwd.shellQuote()} && $command"
        }
    }

    private fun mimeType(name: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(name).lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: MIME_FALLBACKS[extension]
            ?: "application/octet-stream"
    }

    private fun String.shellQuote() = "'${replace("'", "'\\''")}'"

    companion object {
        private const val JS_BRIDGE_NAME = "ksu"
        private const val LOCAL_SCHEME = "https"
        private const val LOCAL_HOST = "module.local"
        private const val ENTRY_URL = "$LOCAL_SCHEME://$LOCAL_HOST/index.html"
        private val MODULE_ID = Regex("[A-Za-z0-9._-]+")
        private val CALLBACK_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
        private val TEXT_EXTENSIONS = setOf("html", "htm", "css", "js", "mjs", "json", "xml", "svg", "txt")
        private val MIME_FALLBACKS = mapOf(
            "js" to "application/javascript",
            "mjs" to "application/javascript",
            "wasm" to "application/wasm",
            "json" to "application/json",
            "svg" to "image/svg+xml",
            "woff" to "font/woff",
            "woff2" to "font/woff2"
        )
    }
}