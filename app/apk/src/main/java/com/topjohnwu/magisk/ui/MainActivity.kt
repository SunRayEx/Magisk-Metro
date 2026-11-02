package com.topjohnwu.magisk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.app.Activity
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.content.pm.ShortcutManagerCompat

import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.VMFactory
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.base.ActivityExtension
import com.topjohnwu.magisk.core.base.SplashController
import com.topjohnwu.magisk.core.base.SplashScreenHost
import com.topjohnwu.magisk.core.wrap
import com.topjohnwu.magisk.ui.component.ConfirmResult
import com.topjohnwu.magisk.ui.component.rememberConfirmDialog
import com.topjohnwu.magisk.ui.deny.DenyListViewModel
import com.topjohnwu.magisk.ui.flash.FlashScreen
import com.topjohnwu.magisk.ui.flash.FlashUtils
import com.topjohnwu.magisk.ui.flash.FlashViewModel
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.module.ActionScreen
import com.topjohnwu.magisk.ui.module.ActionViewModel
import com.topjohnwu.magisk.ui.module.WebUiScreen
import com.topjohnwu.magisk.ui.navigation.LocalNavigator
import com.topjohnwu.magisk.ui.navigation.Navigator
import com.topjohnwu.magisk.ui.navigation.Route
import com.topjohnwu.magisk.ui.navigation.rememberNavigator
import com.topjohnwu.magisk.ui.superuser.SuperuserDetailScreen
import com.topjohnwu.magisk.ui.home.MetroHomeScreen
import com.topjohnwu.magisk.ui.home.EditTilesScreen
import com.topjohnwu.magisk.ui.home.MetroContributorScreen
import com.topjohnwu.magisk.ui.theme.MetroThemeScreen
import com.topjohnwu.magisk.ui.theme.MetroColorsScreen
import com.topjohnwu.magisk.ui.home.TileGroupScreen
import com.topjohnwu.magisk.ui.metro.LocalMetroRootSize
import com.topjohnwu.magisk.ui.metro.LocalMetroTransition
import com.topjohnwu.magisk.ui.metro.MetroTileContinuum
import com.topjohnwu.magisk.ui.metro.MetroTransitionController
import com.topjohnwu.magisk.ui.metro.MetroTransitionPhase
import com.topjohnwu.magisk.ui.module.PersistentScreen
import com.topjohnwu.magisk.ui.pivot.MetroPivotScreen
import com.topjohnwu.magisk.ui.pivot.PivotSection
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.theme.MagisKubeTheme
import com.topjohnwu.magisk.ui.theme.LocalMetroBackdropVeil
import com.topjohnwu.magisk.ui.theme.MetroWallpaperBackdrop
import com.topjohnwu.magisk.ui.theme.METRO_DENSITY_SCALE
import com.topjohnwu.magisk.ui.theme.Theme
import com.topjohnwu.magisk.view.Shortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull
import com.topjohnwu.magisk.core.R as CoreR

private const val MAX_DISPLAY_NAME_LENGTH = 100
private const val DISPLAY_NAME_QUERY_TIMEOUT_MS = 3000L

class MainActivity : ComponentActivity(), SplashScreenHost {

    override val extension = ActivityExtension(this)
    override val splashController = SplashController(this)

    private val intentState = MutableStateFlow(0)
    // A tab requested by a new intent while the activity is running (it's singleTask)
    private val tabRequest = MutableStateFlow<Int?>(null)
    internal val showUnsupported = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())

    override fun attachBaseContext(base: Context) {
        // Windows Phone Metro look: globally scale down the display density so every
        // screen (View and Compose alike) renders at 0.75x, matching the desired Metro
        // visual scale without requiring a system-wide DPI change.
        val wrapped = base.wrap()
        val config = Configuration(wrapped.resources.configuration)
        config.densityDpi = (config.densityDpi * METRO_DENSITY_SCALE).toInt()
        // 主题颜色: force light or dark regardless of the system setting. Overriding the UI
        // mode here makes the XML night resources and Compose's isSystemInDarkTheme() both
        // follow the user's choice, so the whole UI flips with no per-screen plumbing.
        // -1 (follow system) leaves the configuration's night bits untouched.
        val night = Config.metroNightMode
        if (night == 0 || night == 1) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night == 1) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(wrapped.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The user's selected Metro theme (Piplup, Rayquaza, ...) is an XML theme; MagisKubeTheme
        // reads its accent colors from it, so it must be applied at the activity level.
        setTheme(Theme.selected.themeRes)
        if (Theme.selected == Theme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The library's own OEM whitelist skips many Android 12 devices; apply the overlay directly.
            setTheme(com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight)
        }

        extension.onCreate(savedInstanceState)
        splashController.preOnCreate()
        super.onCreate(savedInstanceState)
        splashController.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        splashController.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        extension.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("InlinedApi", "DEPRECATION")
    override fun onCreateUi(savedInstanceState: Bundle?) {
        showUnsupportedMessage()

        // Only ask on startup, not every time the activity is recreated
        if (savedInstanceState == null && Config.checkUpdate) {
            extension.withPermission(Manifest.permission.POST_NOTIFICATIONS) {
                Config.checkUpdate = it
            }
        }

        val initialTab = getRequestedTab(intent) ?: Tab.HOME.ordinal

        setContent {
            MagiskTheme {
                // The compose root reports its px size so the transition ghost and the tile
                // glass can address the full window (the activity runs edge-to-edge).
                var rootSize by remember { mutableStateOf(IntSize.Zero) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { rootSize = it.size },
                ) {

                    val navigator = rememberNavigator(Route.Main)
                    val context = LocalContext.current
                    // The transition coordinator must outlive every NavDisplay entry: it is
                    // what keeps the leave/return state alive across the route swap.
                    var introPlayed by rememberSaveable { mutableStateOf(false) }
                    val transition = remember {
                        MetroTransitionController(context, playedIntro = introPlayed)
                    }
                    transition.onIntroPlayed = { introPlayed = true }
                    DisposableEffect(transition, navigator) {
                        transition.bind(navigator)
                        onDispose {
                            // Do not leave a stale interceptor attached if the root is
                            // replaced during recreation or a host teardown.
                            if (navigator.popInterceptor != null) {
                                navigator.popInterceptor = null
                            }
                        }
                    }
                    // Pump the transition state machine from the composition: this scope
                    // carries the MonotonicFrameClock Compose animations suspend on, and it
                    // lives exactly as long as the activity (longer than any nav entry).
                    LaunchedEffect(transition) {
                        with(transition) { pump() }
                    }
                    CompositionLocalProvider(
                        LocalNavigator provides navigator,
                        LocalMetroTransition provides transition,
                        LocalMetroRootSize provides rootSize,
                    ) {
                        MagisKubeTheme {
                        // One shared backdrop behind every route; pages render translucent
                        // backgrounds over it when the wallpaper theme is active.
                        // Keep the secondary-page backdrop/veil connected throughout the
                        // return handoff. The route is popped at the midpoint of the
                        // animation, so deriving this only from the back stack briefly
                        // removes the veil and exposes the board before the transition has
                        // finished, producing a one-frame flash.
                        val transitionPhase = transition.phase
                        val secondaryPage = navigator.current() != Route.Main ||
                            transitionPhase is MetroTransitionPhase.ReturnPrepare ||
                            transitionPhase is MetroTransitionPhase.Returning
                        MetroWallpaperBackdrop(
                            secondaryPage = secondaryPage,
                        )
                        HandleFlashIntent(navigator)

                        CompositionLocalProvider(LocalMetroBackdropVeil provides secondaryPage) {
                        NavDisplay(
                            modifier = Modifier.then(
                                if (navigator.current() == Route.Main) Modifier
                                else Modifier.windowInsetsPadding(WindowInsets.systemBars),
                            ),
                            backStack = navigator.backStack,
                            onBack = { navigator.pop() },
                            // The Metro transition coordinator owns all home <-> page motion
                            // (tile press, board fly-out, ghost expand/shrink); the nav
                            // host's own transitions must stay off or they would fight it.
                            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator<Any>()
                            ),
                            entryProvider = entryProvider {
                                entry<Route.Main> {
                                    val homeVM: HomeViewModel = viewModel(factory = VMFactory)
                                    LaunchedEffect(Unit) { homeVM.startLoading() }
                                    MagisKubeTheme {
                                        MetroHomeScreen(
                                            viewModel = homeVM,
                                            onSettingsClick = { navigator.push(Route.MetroPivot("SETTINGS")) },
                                            onModulesClick = { navigator.push(Route.MetroPivot("MODULES")) },
                                            onAppsClick = { navigator.push(Route.MetroPivot("APPS")) },
                                            onLogsClick = { navigator.push(Route.MetroPivot("LOGS")) },
                                            onContributorsClick = { navigator.push(Route.Contributors) },
                                            onMagiskClick = { navigator.push(Route.MetroPivot("MAGISK")) },
                                        )
                                    }
                                }
                                entry<Route.MetroPivot> { key ->
                                    val superuserVm: SuperuserViewModel = viewModel(
                                        viewModelStoreOwner = this@MainActivity, factory = VMFactory
                                    )
                                    val homeVM: HomeViewModel = viewModel(factory = VMFactory)
                                    val logVM: LogViewModel = viewModel(factory = VMFactory)
                                    val moduleVM: ModuleViewModel = viewModel(factory = VMFactory)
                                    val settingsVM: SettingsViewModel = viewModel(factory = VMFactory)
                                    val denyListVM: DenyListViewModel = viewModel(factory = VMFactory)
                                    LaunchedEffect(Unit) {
                                        homeVM.startLoading()
                                        superuserVm.startLoading()
                                        logVM.startLoading()
                                        moduleVM.startLoading()
                                        denyListVM.startLoading()
                                    }
                                    MagisKubeTheme {
                                        MetroPivotScreen(
                                            superuserVM = superuserVm,
                                            logVM = logVM,
                                            moduleVM = moduleVM,
                                            settingsVM = settingsVM,
                                            homeVM = homeVM,
                                            denyListVM = denyListVM,
                                            showDenyListInitially = key.section == PivotSection.DENYLIST_ALIAS,
                                            initialSection = PivotSection.fromName(key.section),
                                        )
                                    }
                                }
                                entry<Route.TileGroup> { key ->
                                    MagisKubeTheme {
                                        TileGroupScreen(key.tileId)
                                    }
                                }
                                entry<Route.PersistentModules> {
                                    MagisKubeTheme {
                                        PersistentScreen()
                                    }
                                }
                                entry<Route.EditTiles> {
                                    MagisKubeTheme {
                                        EditTilesScreen()
                                    }
                                }
                                entry<Route.Contributors> {
                                    val homeVM: HomeViewModel = viewModel(factory = VMFactory)
                                    MagisKubeTheme {
                                        MetroContributorScreen(onLinkClick = homeVM::onLinkPressed)
                                    }
                                }
                                entry<Route.Theme> {
                                    val view = LocalView.current
                                    val navigator = LocalNavigator.current
                                    MagisKubeTheme {
                                        MetroThemeScreen(
                                            onEditCustomColors = { navigator.push(Route.MetroColors) },
                                            onThemeApplied = {
                                                // The activity theme is resolved in onCreate, so
                                                // the whole UI must be recreated for the new
                                                // palette to take effect.
                                                (view.context as? Activity)?.recreate()
                                            },
                                        )
                                    }
                                }
                                entry<Route.MetroColors> {
                                    val view = LocalView.current
                                    MagisKubeTheme {
                                        MetroColorsScreen(
                                            onApplied = {
                                                // Same as a theme switch: the palette is resolved
                                                // in onCreate, so recreate to repaint everything.
                                                (view.context as? Activity)?.recreate()
                                            },
                                        )
                                    }

                                }
                                entry<Route.Flash> { key ->
                                    val vm: FlashViewModel = viewModel(factory = VMFactory)
                                    if (vm.isInterrupted) {
                                        // Restored after process death: nothing is left to show
                                        LeaveInterruptedScreen(navigator, key)
                                        return@entry
                                    }
                                    LaunchedEffect(key) {
                                        if (vm.flashAction.isEmpty()) {
                                            vm.flashAction = key.action
                                            vm.flashUri = key.additionalData?.toUri()
                                            vm.usbDevice = key.usbDevice
                                            vm.startFlashing()
                                        }
                                    }
                                    FlashScreen(vm, onBack = { navigator.pop() })
                                }
                                entry<Route.SuperuserDetail> { key ->
                                    val vm: SuperuserViewModel = viewModel(
                                        viewModelStoreOwner = this@MainActivity, factory = VMFactory
                                    )
                                    SuperuserDetailScreen(
                                        uid = key.uid,
                                        viewModel = vm,
                                        onBack = { navigator.pop() },
                                    )
                                }
                                entry<Route.Action> { key ->
                                    val vm: ActionViewModel = viewModel(factory = VMFactory)
                                    if (vm.isInterrupted) {
                                        // Restored after process death: nothing is left to show
                                        LeaveInterruptedScreen(navigator, key)
                                        return@entry
                                    }
                                    LaunchedEffect(key) {
                                        if (vm.actionDir.isEmpty()) {
                                            vm.actionDir = key.dir
                                            vm.actionName = key.name
                                            vm.startRunAction()
                                        }
                                    }
                                    ActionScreen(vm, actionName = key.name, onBack = { navigator.pop() })
                                }
                                entry<Route.WebUi> { key ->
                                    MagisKubeTheme {
                                        WebUiScreen(moduleId = key.id, moduleName = key.name)
                                    }
                                }
                            }
                        )
                        }

                        // The tapped tile's own capture, morphing across the swap.
                        MetroTileContinuum(transition)
                        }
                    }
                    MainActivityDialogs(
                        unsupportedMessages = showUnsupported.collectAsStateWithLifecycle().value,
                    )
                }
            }
        }
    }

    @Composable
    private fun HandleFlashIntent(navigator: Navigator) {
        val confirmDialog = rememberConfirmDialog()
        val context = LocalContext.current
        val resources = LocalResources.current
        val intentVersion by intentState.collectAsStateWithLifecycle()

        LaunchedEffect(intentVersion) {
            val currentIntent = intent ?: return@LaunchedEffect
            if (currentIntent.action != FlashUtils.INTENT_FLASH) return@LaunchedEffect
            val action = currentIntent.getStringExtra(FlashUtils.EXTRA_FLASH_ACTION)
            val uriString = currentIntent.getStringExtra(FlashUtils.EXTRA_FLASH_URI)

            val route = when (action) {
                Const.Value.FLASH_ZIP -> {
                    if (uriString.isNullOrEmpty()) {
                        null
                    } else if (FlashUtils.isTrusted(currentIntent)) {
                        // Sent by our own module download, which the user already chose to install
                        Route.Flash(action, uriString)
                    } else {
                        val uri = uriString.toUri()
                        val name = queryDisplayName(context, uri)?.sanitizeDisplayName()?.ifEmpty { null }
                            ?: uri.lastPathSegment?.sanitizeDisplayName()?.ifEmpty { null }
                            ?: "module.zip"
                        // The name is chosen by the app that sent the intent, so also show where it comes from
                        val authority = uri.authority?.sanitizeDisplayName()?.ifEmpty { null }
                        val displayName = authority?.let { "$name ($it)" } ?: name

                        // This confirmation is the only safeguard, so don't let other apps draw over it
                        setHideOverlayWindows(true)
                        val result = try {
                            confirmDialog.awaitConfirm(
                                title = resources.getString(CoreR.string.confirm_install_title),
                                content = resources.getString(CoreR.string.confirm_install, displayName)
                            )
                        } finally {
                            setHideOverlayWindows(false)
                        }
                        if (result == ConfirmResult.Confirmed) Route.Flash(action, uriString) else null
                    }
                }
                // Only installing modules can be requested through this intent (our module
                // downloads use it too); drop any other action, e.g. uninstalling Magisk
                else -> null
            }

            // Only consume the intent once the request is resolved. If a configuration change
            // cancels this effect while the confirmation is showing, the new Activity replays it.
            currentIntent.action = null
            route?.let { navigator.push(it) }
        }
    }

    /**
     * Names from external apps are untrusted: drop control and formatting characters
     * (e.g. bidi overrides that can reorder the text) and cap the length.
     */
    private fun String.sanitizeDisplayName(): String {
        val sb = StringBuilder()
        var count = 0
        var i = 0
        // Work on code points, so supplementary characters (e.g. invisible tag characters)
        // are recognized and never split
        while (i < length && count < MAX_DISPLAY_NAME_LENGTH) {
            val cp = codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp)
            if (Character.isISOControl(cp) || type == Character.FORMAT.toInt() ||
                type == Character.SURROGATE.toInt()) continue
            sb.appendCodePoint(cp)
            count++
        }
        return sb.toString().trim()
    }

    /**
     * Query the display name from the provider of an external URI. The provider is controlled
     * by another app, so never block on it indefinitely: give up after a timeout and cancel it.
     */
    private suspend fun queryDisplayName(context: Context, uri: Uri): String? {
        val signal = CancellationSignal()
        // Not a child of the caller, so giving up doesn't have to wait for the provider
        val query = CoroutineScope(Dispatchers.IO).async {
            runCatching {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal
                )?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                }
            }.getOrNull()
        }
        return try {
            withTimeoutOrNull(DISPLAY_NAME_QUERY_TIMEOUT_MS) { query.await() }
        } finally {
            if (!query.isCompleted) {
                signal.cancel()
                query.cancel()
            }
        }
    }

    private fun setHideOverlayWindows(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(hide)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        getRequestedTab(intent)?.let { tabRequest.value = it }
        intentState.value += 1
    }

    private fun getRequestedTab(intent: Intent?): Int? {
        val section = when {
            intent?.action == Intent.ACTION_APPLICATION_PREFERENCES -> Const.Nav.SETTINGS
            intent?.action == FlashUtils.INTENT_FLASH &&
                intent.getStringExtra(FlashUtils.EXTRA_FLASH_ACTION) == Const.Value.FLASH_ZIP -> Const.Nav.MODULES
            else -> intent?.getStringExtra(Const.Key.OPEN_SECTION)
        }
        return when (section) {
            Const.Nav.HOME -> Tab.HOME.ordinal
            Const.Nav.SUPERUSER -> Tab.SUPERUSER.ordinal
            Const.Nav.MODULES -> Tab.MODULES.ordinal
            Const.Nav.SETTINGS -> Tab.SETTINGS.ordinal
            else -> null
        }
    }

    private fun showUnsupportedMessage() {
        val messages = mutableListOf<Pair<Int, Int>>()

        if (Info.env.isUnsupported) {
            messages.add(CoreR.string.unsupport_magisk_title to CoreR.string.unsupport_magisk_msg)
        }
        if (!Info.isEmulator && Info.env.isActive && System.getenv("PATH")
                ?.split(':')
                ?.filterNot { File("$it/magisk").exists() }
                ?.any { File("$it/su").exists() } == true) {
            messages.add(CoreR.string.unsupport_general_title to CoreR.string.unsupport_other_su_msg)
        }
        if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            messages.add(CoreR.string.unsupport_general_title to CoreR.string.unsupport_system_app_msg)
        }
        if (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0) {
            messages.add(CoreR.string.unsupport_general_title to CoreR.string.unsupport_external_storage_msg)
        }

        if (messages.isNotEmpty()) {
            showUnsupported.value = messages
        }
    }
}

/**
 * Leave a flash or action screen restored after process death. Its job and output only lived
 * in memory, and running the job again may not be safe, so there is nothing left to show.
 */
@Composable
private fun LeaveInterruptedScreen(navigator: Navigator, key: Route) {
    LaunchedEffect(key) {
        if (navigator.current() == key) navigator.pop()
    }
}

@Composable
private fun MainActivityDialogs(
    unsupportedMessages: List<Pair<Int, Int>>,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current

    var currentUnsupportedIndex by rememberSaveable { mutableIntStateOf(0) }
    val unsupportedDialog = rememberConfirmDialog(
        onConfirm = { currentUnsupportedIndex++ },
        onDismiss = { currentUnsupportedIndex++ }
    )

    val currentUnsupported = unsupportedMessages.getOrNull(currentUnsupportedIndex)
    LaunchedEffect(currentUnsupported) {
        if (currentUnsupported != null) {
            val (titleRes, msgRes) = currentUnsupported
            unsupportedDialog.showConfirm(
                title = resources.getString(titleRes),
                content = resources.getString(msgRes),
            )
        }
    }
}
