package com.topjohnwu.magisk.ui

import android.Manifest
import android.Manifest.permission.REQUEST_INSTALL_PACKAGES
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import com.topjohnwu.magisk.core.isRunningAsStub
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.tasks.AppMigration
import com.topjohnwu.magisk.core.wrap
import com.topjohnwu.magisk.ui.component.rememberConfirmDialog
import com.topjohnwu.magisk.ui.deny.DenyListScreen
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
import com.topjohnwu.magisk.ui.module.PersistentScreen
import com.topjohnwu.magisk.ui.pivot.MetroPivotScreen
import com.topjohnwu.magisk.ui.pivot.PivotSection
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.theme.MagisKubeTheme
import com.topjohnwu.magisk.ui.theme.METRO_DENSITY_SCALE
import com.topjohnwu.magisk.ui.theme.Theme
import com.topjohnwu.magisk.view.Shortcuts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.topjohnwu.magisk.core.R as CoreR

class MainActivity : ComponentActivity(), SplashScreenHost {

    override val extension = ActivityExtension(this)
    override val splashController = SplashController(this)

    private val intentState = MutableStateFlow(0)
    internal val showInvalidState = MutableStateFlow(false)
    internal val showUnsupported = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    internal val showShortcutPrompt = MutableStateFlow(false)

    override fun attachBaseContext(base: Context) {
        // Windows Phone Metro look: globally scale down the display density so every
        // screen (View and Compose alike) renders at 0.75x, matching the desired Metro
        // visual scale without requiring a system-wide DPI change.
        val wrapped = base.wrap()
        val config = Configuration(wrapped.resources.configuration)
        config.densityDpi = (config.densityDpi * METRO_DENSITY_SCALE).toInt()
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

    @SuppressLint("InlinedApi")
    override fun onCreateUi(savedInstanceState: Bundle?) {
        showUnsupportedMessage()
        askForHomeShortcut()

        if (Config.checkUpdate) {
            extension.withPermission(Manifest.permission.POST_NOTIFICATIONS) {
                Config.checkUpdate = it
            }
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val initialTab = getInitialTab(intent)

        setContent {
            MagiskTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val navigator = rememberNavigator(Route.Main)
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        HandleFlashIntent(navigator)

                        NavDisplay(
                            backStack = navigator.backStack,
                            onBack = { navigator.pop() },
                            // The Metro screens run their own tile entrance/exit choreography
                            // (MetroFlipItem stagger + board leave animation), so the nav host's
                            // own crossfade would double up on top of it.
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
                                    LaunchedEffect(Unit) {
                                        superuserVm.authenticate = { onSuccess ->
                                            extension.withAuthentication { if (it) onSuccess() }
                                        }
                                        settingsVM.authenticate = { onSuccess ->
                                            extension.withAuthentication { if (it) onSuccess() }
                                        }
                                    }
                                    MagisKubeTheme {
                                        MetroPivotScreen(
                                            superuserVM = superuserVm,
                                            logVM = logVM,
                                            moduleVM = moduleVM,
                                            settingsVM = settingsVM,
                                            homeVM = homeVM,
                                            denyListVM = denyListVM,
                                            showDenyListInitially = false,
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
                                entry<Route.DenyList> { _ ->
                                    val vm: DenyListViewModel = viewModel(factory = VMFactory)
                                    LaunchedEffect(Unit) { vm.startLoading() }
                                    DenyListScreen(vm, onBack = { navigator.pop() })
                                }
                                entry<Route.Flash> { key ->
                                    val vm: FlashViewModel = viewModel(factory = VMFactory)
                                    LaunchedEffect(key) {
                                        if (vm.flashAction.isEmpty()) {
                                            vm.flashAction = key.action
                                            vm.flashUri = key.additionalData?.toUri()
                                            vm.startFlashing()
                                        }
                                    }
                                    FlashScreen(vm, onBack = { navigator.pop() })
                                }
                                entry<Route.SuperuserDetail> { key ->
                                    val vm: SuperuserViewModel = viewModel(
                                        viewModelStoreOwner = this@MainActivity, factory = VMFactory
                                    )
                                    LaunchedEffect(Unit) {
                                        vm.authenticate = { onSuccess ->
                                            extension.withAuthentication { if (it) onSuccess() }
                                        }
                                    }
                                    SuperuserDetailScreen(
                                        uid = key.uid,
                                        viewModel = vm,
                                        onBack = { navigator.pop() },
                                        onAuthenticate = { action ->
                                            extension.withAuthentication { if (it) action() }
                                        }
                                    )
                                }
                                entry<Route.Action> { key ->
                                    val vm: ActionViewModel = viewModel(factory = VMFactory)
                                    LaunchedEffect(key) {
                                        if (vm.actionId.isEmpty()) {
                                            vm.actionId = key.id
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
                    MainActivityDialogs(
                        showInvalid = showInvalidState.collectAsStateWithLifecycle().value,
                        unsupportedMessages = showUnsupported.collectAsStateWithLifecycle().value,
                        showShortcut = showShortcutPrompt.collectAsStateWithLifecycle().value,
                        onInvalidConfirmed = {
                            showInvalidState.value = false
                            handleInvalidStateInstall()
                        },
                        onShortcutConfirmed = {
                            showShortcutPrompt.value = false
                            Shortcuts.addHomeIcon(this@MainActivity)
                        },
                        onShortcutDismissed = {
                            showShortcutPrompt.value = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun HandleFlashIntent(navigator: Navigator) {
        val intentVersion by intentState.collectAsStateWithLifecycle()
        LaunchedEffect(intentVersion) {
            val currentIntent = intent ?: return@LaunchedEffect
            if (currentIntent.action == FlashUtils.INTENT_FLASH) {
                val action = currentIntent.getStringExtra(FlashUtils.EXTRA_FLASH_ACTION)
                    ?: return@LaunchedEffect
                val uri = currentIntent.getStringExtra(FlashUtils.EXTRA_FLASH_URI)
                navigator.push(Route.Flash(action, uri))
                currentIntent.action = null
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value += 1
    }

    private fun getInitialTab(intent: Intent?): Int {
        val section = when {
            intent?.action == Intent.ACTION_APPLICATION_PREFERENCES -> Const.Nav.SETTINGS
            intent?.action == FlashUtils.INTENT_FLASH &&
                intent.getStringExtra(FlashUtils.EXTRA_FLASH_ACTION) == Const.Value.FLASH_ZIP -> Const.Nav.MODULES
            else -> intent?.getStringExtra(Const.Key.OPEN_SECTION)
        }
        return when (section) {
            Const.Nav.SUPERUSER -> Tab.SUPERUSER.ordinal
            Const.Nav.MODULES -> Tab.MODULES.ordinal
            Const.Nav.SETTINGS -> Tab.SETTINGS.ordinal
            else -> Tab.HOME.ordinal
        }
    }

    @SuppressLint("InlinedApi")
    override fun showInvalidStateMessage() {
        showInvalidState.value = true
    }

    internal fun handleInvalidStateInstall() {
        extension.withPermission(REQUEST_INSTALL_PACKAGES) {
            if (!it) {
                toast(CoreR.string.install_unknown_denied, Toast.LENGTH_SHORT)
                showInvalidState.value = true
            } else {
                lifecycleScope.launch {
                    if (!AppMigration.restoreApp(this@MainActivity)) {
                        toast(CoreR.string.failure, Toast.LENGTH_LONG)
                    }
                }
            }
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

    private fun askForHomeShortcut() {
        if (isRunningAsStub && !Config.askedHome &&
            ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Config.askedHome = true
            showShortcutPrompt.value = true
        }
    }
}

@Composable
private fun MainActivityDialogs(
    showInvalid: Boolean,
    unsupportedMessages: List<Pair<Int, Int>>,
    showShortcut: Boolean,
    onInvalidConfirmed: () -> Unit,
    onShortcutConfirmed: () -> Unit,
    onShortcutDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    val invalidDialog = rememberConfirmDialog(
        onConfirm = onInvalidConfirmed,
        onDismiss = {}
    )

    LaunchedEffect(showInvalid) {
        if (showInvalid) {
            invalidDialog.showConfirm(
                title = resources.getString(CoreR.string.unsupport_nonroot_stub_title),
                content = resources.getString(CoreR.string.unsupport_nonroot_stub_msg),
                confirm = resources.getString(CoreR.string.install),
            )
        }
    }

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

    val shortcutDialog = rememberConfirmDialog(
        onConfirm = onShortcutConfirmed,
        onDismiss = onShortcutDismissed
    )

    LaunchedEffect(showShortcut) {
        if (showShortcut) {
            shortcutDialog.showConfirm(
                title = resources.getString(CoreR.string.add_shortcut_title),
                content = resources.getString(CoreR.string.add_shortcut_msg),
            )
        }
    }
}
