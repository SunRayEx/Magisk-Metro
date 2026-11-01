package com.topjohnwu.magisk.ui.module

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.ui.navigation.LocalNavigator
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.magisk.core.utils.RootUtils
import timber.log.Timber
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.theme.metroScreenBackground
import com.topjohnwu.magisk.ui.metro.metroPageShell
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.Shell
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    val navigator = LocalNavigator.current
    val accent = LocalMetroPalette.current.modules

    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var bridge by remember { mutableStateOf<ModuleWebInterface?>(null) }

    // <input type="file"> needs a chooser: WebView hands us a callback that must be answered
    // exactly once (null on cancel) or every later file input on the page stops working.
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingFiles?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        pendingFiles = null
    }

    // Back walks the page history first, like a browser; only then leaves the WebUI.
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = canGoBack,
        onBackCompleted = { webView?.goBack() },
    )

    DisposableEffect(Unit) {
        onDispose {
            pendingFiles?.onReceiveValue(null)
            pendingFiles = null
            context.findActivity()?.let { setFullScreen(it, false) }
            bridge?.release()
            bridge = null
            webView?.apply {
                removeJavascriptInterface(JS_BRIDGE_NAME)
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .metroScreenBackground()
            .metroPageShell(),
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
                            webViewClient = ModuleWebViewClient(context.cacheDir, moduleId) {
                                canGoBack = it
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(
                                    view: WebView,
                                    callback: ValueCallback<Array<Uri>>,
                                    params: FileChooserParams,
                                ): Boolean {
                                    pendingFiles?.onReceiveValue(null)
                                    pendingFiles = callback
                                    val launched = runCatching {
                                        fileLauncher.launch(params.createIntent())
                                    }.isSuccess
                                    if (!launched) {
                                        pendingFiles = null
                                        callback.onReceiveValue(null)
                                    }
                                    return true
                                }
                            }
                            val jsBridge = ModuleWebInterface(this, moduleId, moduleName) {
                                navigator.pop()
                            }
                            bridge = jsBridge
                            addJavascriptInterface(jsBridge, JS_BRIDGE_NAME)
                            loadUrl(ENTRY_URL)
                            webView = this
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
    private val onHistoryChanged: (Boolean) -> Unit,
) : WebViewClient() {

    private val webRoot = File(cacheDir, "module_webui/$moduleId")
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        // KernelSU-style app icons: <img src="ksu://icon/com.example.app">.
        if (uri.scheme == ICON_SCHEME && uri.host == ICON_HOST) {
            return iconResponse(view.context, uri.lastPathSegment.orEmpty())
        }
        if (uri.scheme != LOCAL_SCHEME || uri.host != LOCAL_HOST) return null
        return localResponse(Uri.decode(uri.encodedPath.orEmpty()).trimStart('/'))
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        onHistoryChanged(view.canGoBack())
    }

    private fun iconResponse(context: Context, packageName: String): WebResourceResponse {
        if (!PACKAGE_NAME.matches(packageName)) return notFound()
        return runCatching {
            val icon = context.packageManager.getApplicationIcon(packageName)
            val bytes = ByteArrayOutputStream().use { out ->
                icon.toBitmap(ICON_SIZE, ICON_SIZE).compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            WebResourceResponse("image/png", null, ByteArrayInputStream(bytes))
        }.getOrElse { notFound() }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme == LOCAL_SCHEME && uri.host == LOCAL_HOST) return false
        if (uri.scheme == ICON_SCHEME) return true
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
        // getFileExtensionFromUrl() returns "" for names with spaces or other URL-unsafe
        // characters, which served such files as octet-stream.
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: MIME_FALLBACKS[extension]
            ?: "application/octet-stream"
    }
}

/**
 * The `ksu` JS bridge, following the KernelSU WebUI API so module frontends written for it
 * work unchanged: exec, spawn, toast, fullScreen, moduleInfo, listPackages, getPackagesInfo.
 */
private class ModuleWebInterface(
    private val webView: WebView,
    private val moduleId: String,
    private val moduleName: String,
    private val onExit: () -> Unit,
) {
    private val moduleDir = "/data/adb/modules/$moduleId"

    // Long-running spawn() jobs each get their own root shell so they never hold the shared
    // shell that the rest of the app (module list, settings) queues behind.
    private val spawned = java.util.Collections.synchronizedSet(mutableSetOf<Shell>())
    @Volatile private var released = false

    fun release() {
        released = true
        synchronized(spawned) {
            spawned.forEach { runCatching { it.close() } }
            spawned.clear()
        }
    }

    @JavascriptInterface
    fun exec(command: String): String =
        Shell.cmd("($command)").exec().out.joinToString("\n")

    @JavascriptInterface
    fun exec(command: String, callbackName: String) = exec(command, "{}", callbackName)

    @JavascriptInterface
    fun exec(command: String, optionsJson: String?, callbackName: String) {
        if (!JS_IDENTIFIER.matches(callbackName)) return
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        // stderr is only captured when a separate list is passed; without it the page always
        // received an empty stderr.
        Shell.cmd(subshell(command, optionsJson)).to(out, err).submit { result ->
            evaluate(
                "window[${JSONObject.quote(callbackName)}](${result.code}," +
                    "${JSONObject.quote(out.joinToString("\n"))}," +
                    "${JSONObject.quote(err.joinToString("\n"))})"
            )
        }
    }

    /** `ksu.spawn(cmd, argsJson, optionsJson, callbackName)`: streams output as it arrives. */
    @JavascriptInterface
    fun spawn(command: String, argsJson: String?, optionsJson: String?, callbackName: String) {
        if (!JS_IDENTIFIER.matches(callbackName) || released) return
        val args = runCatching { JSONArray(argsJson ?: "[]") }.getOrNull() ?: JSONArray()
        val full = buildString {
            append(command)
            for (i in 0 until args.length()) append(' ').append(args.optString(i).shellQuote())
        }
        val target = "window[${JSONObject.quote(callbackName)}]"
        fun emitter(stream: String) = object : CallbackList<String>() {
            override fun onAddElement(line: String) {
                evaluate("$target.$stream.emit('data',${JSONObject.quote(line)})")
            }
        }
        val shell = runCatching { Shell.Builder.create().build() }.getOrElse {
            Timber.w(it, "WebUI: cannot start shell for spawn")
            evaluate("$target.emit('error',${JSONObject.quote(it.message.orEmpty())})")
            return
        }
        spawned += shell
        if (released) {
            spawned -= shell
            runCatching { shell.close() }
            return
        }
        shell.newJob().add(subshell(full, optionsJson))
            .to(emitter("stdout"), emitter("stderr"))
            .submit { result ->
                evaluate("$target.emit('exit',${result.code})")
                if (!result.isSuccess) {
                    evaluate("$target.emit('error',${JSONObject.quote("exit code ${result.code}")})")
                }
                spawned -= shell
                runCatching { shell.close() }
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
        put("moduleDir", moduleDir)
    }.toString()

    @JavascriptInterface
    fun fullScreen(enable: Boolean) {
        webView.post { webView.context.findActivity()?.let { setFullScreen(it, enable) } }
    }

    /** Pages are always laid out inside the system bars here; accepted for API parity. */
    @JavascriptInterface
    fun enableEdgeToEdge(enable: Boolean) = Unit

    /** `ksu.listPackages("user" | "system" | "all")` -> JSON array of package names. */
    @JavascriptInterface
    fun listPackages(type: String): String {
        val apps = runCatching {
            webView.context.packageManager.getInstalledApplications(0)
        }.getOrDefault(emptyList())
        val names = apps.filter { app ->
            val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            when (type.lowercase()) {
                "system" -> system
                "user" -> !system
                else -> true
            }
        }.map { it.packageName }.sorted()
        return JSONArray(names).toString()
    }

    /** `ksu.getPackagesInfo('["pkg", ...]')` -> JSON array of package details. */
    @JavascriptInterface
    fun getPackagesInfo(packagesJson: String): String {
        val pm = webView.context.packageManager
        val input = runCatching { JSONArray(packagesJson) }.getOrNull() ?: JSONArray()
        val result = JSONArray()
        for (i in 0 until input.length()) {
            val name = input.optString(i)
            val info = runCatching { pm.getPackageInfo(name, 0) }.getOrNull() ?: continue
            val app = info.applicationInfo ?: continue
            result.put(JSONObject().apply {
                put("packageName", info.packageName)
                put("versionName", info.versionName.orEmpty())
                put("versionCode", info.longVersionCodeCompat())
                put("appLabel", app.loadLabel(pm).toString())
                put("isSystem", app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                put("uid", app.uid)
            })
        }
        return result.toString()
    }

    @JavascriptInterface
    fun exit() {
        webView.post(onExit)
    }

    private fun evaluate(script: String) {
        if (released) return
        webView.post { if (!released) webView.evaluateJavascript(script, null) }
    }

    /**
     * Runs [command] in a subshell so `cd` and `export` from one call cannot leak into the
     * shared root shell. Options follow KernelSU: `cwd` (defaults to the module dir) and `env`.
     */
    private fun subshell(command: String, optionsJson: String?): String {
        val options = runCatching { JSONObject(optionsJson ?: "{}") }.getOrNull() ?: JSONObject()
        val cwd = options.optString("cwd").takeIf { it.isNotBlank() } ?: moduleDir
        val prefix = StringBuilder("cd ${cwd.shellQuote()} && ")
        options.optJSONObject("env")?.let { env ->
            env.keys().forEach { key ->
                if (ENV_NAME.matches(key)) {
                    prefix.append("export $key=${env.optString(key).shellQuote()}; ")
                }
            }
        }
        return "($prefix$command)"
    }
}

@Suppress("DEPRECATION")
private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
    if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setFullScreen(activity: Activity, enable: Boolean) {
    val window = activity.window
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (enable) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun String.shellQuote() = "'${replace("'", "'\\''")}'"

private const val JS_BRIDGE_NAME = "ksu"
private const val LOCAL_SCHEME = "https"
private const val LOCAL_HOST = "module.local"
private const val ICON_SCHEME = "ksu"
private const val ICON_HOST = "icon"
private const val ICON_SIZE = 192
private const val ENTRY_URL = "$LOCAL_SCHEME://$LOCAL_HOST/index.html"
private val MODULE_ID = Regex("[A-Za-z0-9._-]+")
private val JS_IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
private val ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")
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
