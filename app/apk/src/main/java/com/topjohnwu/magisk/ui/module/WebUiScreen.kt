package com.topjohnwu.magisk.ui.module

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.utils.RootUtils
import timber.log.Timber
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Hosts a module's WebUI. The fragment version is excluded from the Compose build, so the
 * WebView is hosted directly here; the staging rules, local URL scheme and the `ksu` JS bridge
 * are kept identical so existing module frontends keep working.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebUiScreen(moduleId: String, moduleName: String) {
    val context = LocalContext.current
    val accent = LocalMetroPalette.current.modules

    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Text(
            text = moduleName,
            color = accent.color,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            if (!ready && !failed) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accent.color,
                )
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.metro_module_webui_missing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            // The web root is only readable after root staging, so load only when it is ready.
            if (ready) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            webViewClient = ModuleWebViewClient(context.cacheDir, moduleId)
                            addJavascriptInterface(
                                ModuleWebInterface(this, moduleId, moduleName), JS_BRIDGE_NAME,
                            )
                            loadUrl(ENTRY_URL)
                        }
                    },
                )
            }
        }
    }

    // Stage the module webroot into the app cache (the module dir itself is not readable by the
    // app uid), then flip to the WebView once index.html is in place.
    LaunchedEffect(moduleId) {
        if (!MODULE_ID.matches(moduleId)) {
            failed = true
            return@LaunchedEffect
        }
        val ok = withContext(Dispatchers.IO) { stageWebRoot(context.cacheDir, moduleId) }
        ready = ok
        failed = !ok
    }
}

private fun stageWebRoot(cacheDir: File, moduleDir: String): Boolean {
    // Copy through the root file service the same way the module list is read, and write the
    // staged tree from the app side. Letting the root shell create the target directory leaves
    // it owned by root, which the app cannot traverse to stat index.html.
    if (stageViaFs(cacheDir, moduleDir)) return true
    Timber.w("WebUI: fs staging failed for $moduleDir, falling back to shell copy")
    return stageViaShell(cacheDir, moduleDir)
}

private fun stageViaFs(cacheDir: File, moduleDir: String): Boolean {
    val source = RootUtils.fs.getFile("/data/adb/modules/$moduleDir/webroot")
    if (!source.isDirectory) return false
    val target = File(cacheDir, "module_webui/$moduleDir")
    target.deleteRecursively()
    if (!target.mkdirs()) return false
    copyTree(source, target)
    return File(target, "index.html").isFile
}

private fun stageViaShell(cacheDir: File, moduleDir: String): Boolean {
    val source = "/data/adb/modules/$moduleDir/webroot"
    val parent = File(cacheDir, "module_webui")
    if (!parent.exists() && !parent.mkdirs()) return false
    val target = File(parent, moduleDir)
    val uid = android.os.Process.myUid()
    val result = Shell.cmd(
        "rm -rf ${target.absolutePath.shellQuote()}",
        "cp -R ${"$source/.".shellQuote()} ${target.absolutePath.shellQuote()}/",
        "chown -R $uid:$uid ${target.absolutePath.shellQuote()}",
    ).exec()
    return result.isSuccess && File(target, "index.html").isFile
}

private fun copyTree(source: ExtendedFile, target: File) {
    source.listFiles().orEmpty().forEach { child ->
        val dest = File(target, child.name)
        if (child.isDirectory) {
            dest.mkdirs()
            copyTree(child, dest)
        } else {
            child.newInputStream().use { input ->
                java.io.FileOutputStream(dest).use { input.copyTo(it) }
            }
        }
    }
}

private class ModuleWebViewClient(
    private val cacheDir: File,
    private val moduleId: String,
) : WebViewClient() {

    private val webRoot = File(cacheDir, "module_webui/$moduleId")
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        if (uri.scheme != LOCAL_SCHEME || uri.host != LOCAL_HOST) return null
        return localResponse(Uri.decode(uri.encodedPath.orEmpty()).trimStart('/'))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme == LOCAL_SCHEME && uri.host == LOCAL_HOST) return false
        return if (uri.scheme == "http" || uri.scheme == "https") {
            runCatching {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            true
        } else {
            true
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
                FileInputStream(requested),
            )
        }.getOrElse { notFound() }
    }

    private fun notFound() = WebResourceResponse(null, null, null)

    private fun mimeType(name: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(name).lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: MIME_FALLBACKS[extension]
            ?: "application/octet-stream"
    }
}

private class ModuleWebInterface(
    private val webView: WebView,
    private val moduleId: String,
    private val moduleName: String,
) {
    @JavascriptInterface
    fun exec(command: String): String = Shell.cmd(command).exec().out.joinToString("\n")

    @JavascriptInterface
    fun exec(command: String, callbackName: String) = exec(command, "{}", callbackName)

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
        put("id", moduleId)
        put("name", moduleName)
        put("moduleDir", "/data/adb/modules/$moduleId")
    }.toString()

    @JavascriptInterface
    fun fullScreen(enable: Boolean) = Unit

    @JavascriptInterface
    fun enableEdgeToEdge(enable: Boolean) = Unit

    @JavascriptInterface
    fun exit() {
        webView.post { (webView.context as? android.app.Activity)?.onBackPressed() }
    }

    private fun applyOptions(command: String, optionsJson: String?): String {
        val options = runCatching { JSONObject(optionsJson ?: "{}") }.getOrNull()
            ?: return command
        val cwd = options.optString("cwd").takeIf { it.isNotBlank() }
            ?: "/data/adb/modules/$moduleId"
        return "cd ${cwd.shellQuote()} && $command"
    }
}

private fun String.shellQuote() = "'${replace("'", "'\\''")}'"

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
    "woff2" to "font/woff2",
)
