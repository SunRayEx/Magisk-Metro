package com.topjohnwu.magisk.ui.settings

import android.app.Activity
import android.webkit.URLUtil
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.utils.LocaleSetting
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.ui.component.AdaptiveColumns
import androidx.compose.ui.platform.LocalResources
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.component.SettingsArrow
import com.topjohnwu.magisk.ui.component.SettingsDropdown
import com.topjohnwu.magisk.ui.component.SettingsSwitch
import com.topjohnwu.magisk.ui.component.SmallTitle
import com.topjohnwu.magisk.ui.component.onlyHorizontal
import com.topjohnwu.magisk.ui.component.rememberBiometricAuth
import com.topjohnwu.magisk.ui.component.rememberNotificationPermissionLauncher
import com.topjohnwu.magisk.ui.component.verticalScrollbar
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requireAuth = rememberBiometricAuth()
    val enableCheckUpdate = rememberNotificationPermissionLauncher { viewModel.setCheckUpdate(true) }

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshEnvironment()
        onPauseOrDispose {}
    }

    SettingsContent(
        viewModel = viewModel,
        uiState = uiState,
        onOpenSystemLocale = {
            context.startActivity(LocaleSetting.localeSettingsIntent)
        },
        onLanguageSelected = viewModel::setLanguage,
        onColorModeSelected = viewModel::setColorMode,
        onUpdateChannelSelected = viewModel::setUpdateChannel,
        onOpenCustomUrlDialog = viewModel::openCustomChannelUrlDialog,
        onSaveCustomUrl = viewModel::saveCustomChannelUrl,
        onToggleDoh = viewModel::setDoh,
        // Update notifications need the notification permission
        onToggleCheckUpdate = { enabled ->
            if (enabled) enableCheckUpdate() else viewModel.setCheckUpdate(false)
        },
        onOpenDownloadPathDialog = viewModel::openDownloadPathDialog,
        onSaveDownloadPath = viewModel::saveDownloadPath,
        onToggleRandomPackageName = viewModel::setRandomPackageName,
        onCreateHosts = viewModel::createHosts,
        onToggleZygisk = viewModel::toggleZygisk,
        onToggleDenyList = viewModel::toggleDenyList,
        // Drop taps while a screen transition runs, so a quick second tap can't push again
        onToggleSuAuth = { newValue ->
            requireAuth(force = true) {
                viewModel.setSuAuth(newValue)
            }
        },
        onMultiuserModeSelected = viewModel::setMultiuserMode,
        onNamespaceModeSelected = viewModel::setNamespaceMode,
        onNotificationModeSelected = viewModel::setNotificationMode,
        onToggleSuRestrict = viewModel::setSuRestrict,
        onDismissDialog = viewModel::dismissDialog,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onOpenSystemLocale: () -> Unit,
    onLanguageSelected: (Int) -> Unit,
    onColorModeSelected: (Int) -> Unit,
    onUpdateChannelSelected: (Int) -> Unit,
    onOpenCustomUrlDialog: () -> Unit,
    onSaveCustomUrl: (String) -> Unit,
    onToggleDoh: (Boolean) -> Unit,
    onToggleCheckUpdate: (Boolean) -> Unit,
    onOpenDownloadPathDialog: () -> Unit,
    onSaveDownloadPath: (String) -> Unit,
    onToggleRandomPackageName: (Boolean) -> Unit,
    onCreateHosts: () -> Unit,
    onToggleZygisk: (Boolean) -> Unit,
    onToggleDenyList: (Boolean) -> Unit,
    onToggleSuAuth: (Boolean) -> Unit,
    onMultiuserModeSelected: (Int) -> Unit,
    onNamespaceModeSelected: (Int) -> Unit,
    onNotificationModeSelected: (Int) -> Unit,
    onToggleSuRestrict: (Boolean) -> Unit,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.settings)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        // Without root, the Magisk and Superuser sections are hidden, so move the
        // App section to the second column to avoid leaving it empty.
        // Each section emits a title and a card, so wrap them in a Column to keep them together
        AdaptiveColumns(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .consumeWindowInsets(padding)
                .padding(padding.onlyHorizontal())
                .verticalScrollbar(
                    scrollState,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = padding.calculateBottomPadding() + 12.dp,
                    ),
                )
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp,
                ),
            spacing = 16.dp,
            first = {
                Column {
                    CustomizationSection(
                        viewModel = viewModel,
                        uiState = uiState,
                        onOpenSystemLocale = onOpenSystemLocale,
                        onLanguageSelected = onLanguageSelected,
                        onColorModeSelected = onColorModeSelected,
                    )
                }
                if (uiState.hasRootSections) {
                    Column {
                        AppSettingsSection(
                            viewModel = viewModel,
                            uiState = uiState,
                            onUpdateChannelSelected = onUpdateChannelSelected,
                            onOpenCustomUrlDialog = onOpenCustomUrlDialog,
                            onToggleDoh = onToggleDoh,
                            onToggleCheckUpdate = onToggleCheckUpdate,
                            onOpenDownloadPathDialog = onOpenDownloadPathDialog,
                            onToggleRandomPackageName = onToggleRandomPackageName,
                        )
                    }
                }
            },
            second = {
                if (!uiState.hasRootSections) {
                    Column {
                        AppSettingsSection(
                            viewModel = viewModel,
                            uiState = uiState,
                            onUpdateChannelSelected = onUpdateChannelSelected,
                            onOpenCustomUrlDialog = onOpenCustomUrlDialog,
                            onToggleDoh = onToggleDoh,
                            onToggleCheckUpdate = onToggleCheckUpdate,
                            onOpenDownloadPathDialog = onOpenDownloadPathDialog,
                            onToggleRandomPackageName = onToggleRandomPackageName,
                        )
                    }
                }
                if (uiState.isMagiskInstalled) {
                    Column {
                        MagiskSection(
                            viewModel = viewModel,
                            uiState = uiState,
                            onCreateHosts = onCreateHosts,
                            onToggleZygisk = onToggleZygisk,
                            onToggleDenyList = onToggleDenyList,
                        )
                    }
                }
                if (uiState.isSuperUserAvailable) {
                    Column {
                        SuperuserSection(
                            uiState = uiState,
                            onToggleSuAuth = onToggleSuAuth,
                            onMultiuserModeSelected = onMultiuserModeSelected,
                            onNamespaceModeSelected = onNamespaceModeSelected,
                            onNotificationModeSelected = onNotificationModeSelected,
                            onToggleSuRestrict = onToggleSuRestrict,
                        )
                    }
                }
            },
        )
    }

    SettingsDialogs(uiState, onSaveCustomUrl, onSaveDownloadPath, onDismissDialog)
}

@Composable
internal fun SettingsDialogs(
    uiState: SettingsUiState,
    onSaveCustomUrl: (String) -> Unit,
    onSaveDownloadPath: (String) -> Unit,
    onDismissDialog: () -> Unit,
) {
    when (val dialog = uiState.activeDialog) {
        is SettingsDialog.CustomChannelUrl -> {
            UpdateChannelUrlDialog(
                initialUrl = dialog.initialUrl,
                onConfirm = onSaveCustomUrl,
                onDismiss = onDismissDialog,
            )
        }
        is SettingsDialog.DownloadPath -> {
            DownloadPathDialog(
                initialPath = dialog.initialPath,
                onConfirm = onSaveDownloadPath,
                onDismiss = onDismissDialog,
            )
        }
        null -> Unit
    }
}

// --- Customization ---

@Composable
internal fun CustomizationSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onOpenSystemLocale: () -> Unit,
    onLanguageSelected: (Int) -> Unit,
    onColorModeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    SmallTitle(text = stringResource(CoreR.string.settings_customization))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    ) {
        // Metro theme chooser: previews every packaged, wallpaper and custom theme.
        SettingsArrow(
            title = stringResource(CoreR.string.section_theme),
            summary = stringResource(R.string.metro_theme_summary),
            onClick = { viewModel.navigateToTheme() },
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        if (uiState.useLocaleManager) {
            val appLocale = LocaleSetting.instance.appLocale
            SettingsArrow(
                title = stringResource(CoreR.string.language),
                summary = appLocale?.getDisplayName(appLocale)
                    ?: stringResource(CoreR.string.system_default),
                onClick = onOpenSystemLocale
            )
        } else {
            SettingsDropdown(
                title = stringResource(CoreR.string.language),
                items = uiState.availableLanguages,
                selectedIndex = uiState.selectedLanguageIndex,
                onSelectedIndexChange = onLanguageSelected
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 主题颜色 (Color mode). The upstream ThemeState/ColorMode machinery is not wired into
        // MagisKubeTheme, so a Metro-specific preference drives the night mode instead: the
        // selected mode is forced onto the activity configuration, which both the XML night
        // resources and Compose's isSystemInDarkTheme() follow. The configuration is resolved
        // in attachBaseContext, so the activity is recreated for the change to take effect.
        val resources = LocalResources.current
        val nightModeEntries = remember {
            resources.getStringArray(R.array.metro_night_mode).toList()
        }
        val nightModeIndex = remember {
            when (Config.metroNightMode) {
                1 -> 0   // Dark mode
                0 -> 1   // Light mode
                else -> 2 // Follow system
            }
        }

        SettingsDropdown(
            title = stringResource(CoreR.string.settings_color_mode),
            items = nightModeEntries,
            selectedIndex = nightModeIndex,
            onSelectedIndexChange = { index ->
                Config.metroNightMode = when (index) {
                    0 -> 1   // Dark mode
                    1 -> 0   // Light mode
                    else -> -1 // Follow system
                }
                (context as? Activity)?.recreate()
            }

        )
    }

    // --- Start tile customization ---
    SmallTitle(
        text = stringResource(R.string.metro_start_tiles),
        modifier = Modifier.padding(top = 8.dp),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    ) {
        val resources = LocalResources.current
        val tileCustomization by viewModel.tileCustomization.collectAsStateWithLifecycle()
        SettingsSwitch(
            title = stringResource(R.string.metro_tile_customize),
            summary = stringResource(R.string.metro_tile_customize_summary),
            checked = tileCustomization,
            onCheckedChange = { viewModel.toggleTileCustomization(it) },
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        // The grid is a single fixed shape (portrait 3 columns, landscape 4 rows) with no
        // variants, so there is nothing to choose and no row is shown for it.
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        SettingsArrow(
            title = stringResource(R.string.metro_tile_restore),
            summary = stringResource(R.string.metro_edit_tiles_summary),
            onClick = { viewModel.navigateToEditTiles() },
        )
    }
}

// --- App Settings ---

@Composable
internal fun AppSettingsSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onUpdateChannelSelected: (Int) -> Unit,
    onOpenCustomUrlDialog: () -> Unit,
    onToggleDoh: (Boolean) -> Unit,
    onToggleCheckUpdate: (Boolean) -> Unit,
    onOpenDownloadPathDialog: () -> Unit,
    onToggleRandomPackageName: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SmallTitle(text = stringResource(CoreR.string.home_app_title))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    ) {
        // Update Channel
        SettingsDropdown(
            title = stringResource(CoreR.string.settings_update_channel_title),
            items = stringArrayResource(CoreR.array.update_channel).asList(),
            selectedIndex = uiState.selectedUpdateChannelIndex,
            onSelectedIndexChange = onUpdateChannelSelected
        )

        // Update Channel URL (for custom channel)
        if (uiState.isCustomChannel) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingsArrow(
                title = stringResource(CoreR.string.settings_update_custom),
                summary = uiState.customChannelUrl.ifBlank { null },
                onClick = onOpenCustomUrlDialog
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // DoH Toggle
        SettingsSwitch(
            title = stringResource(CoreR.string.settings_doh_title),
            summary = stringResource(CoreR.string.settings_doh_description),
            checked = uiState.dohEnabled,
            onCheckedChange = onToggleDoh
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Update Checker
        SettingsSwitch(
            title = stringResource(CoreR.string.settings_check_update_title),
            summary = stringResource(CoreR.string.settings_check_update_summary),
            checked = uiState.checkUpdateEnabled,
            onCheckedChange = onToggleCheckUpdate
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Download Path
        SettingsArrow(
            title = stringResource(CoreR.string.settings_download_path_title),
            summary = uiState.downloadPathDisplay,
            onClick = onOpenDownloadPathDialog
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Random Package Name
        SettingsSwitch(
            title = stringResource(CoreR.string.settings_random_name_title),
            summary = stringResource(CoreR.string.settings_random_name_description),
            checked = uiState.randomPackageNameEnabled,
            onCheckedChange = onToggleRandomPackageName
        )

        if (uiState.isMagiskInstalled) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Keep only the master switch here; management lives in the Modules pivot.
            val persistentModules by viewModel.persistentModules.collectAsStateWithLifecycle()
            SettingsSwitch(
                title = stringResource(R.string.metro_persistent_title),
                summary = stringResource(R.string.metro_persistent_summary),
                checked = persistentModules,
                enabled = Info.env.isActive,
                onCheckedChange = { viewModel.togglePersistentModules(it) }
            )
        }
    }
}

// --- Magisk ---

@Composable
internal fun MagiskSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onCreateHosts: () -> Unit,
    onToggleZygisk: (Boolean) -> Unit,
    onToggleDenyList: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SmallTitle(text = stringResource(CoreR.string.magisk))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    ) {
        // Systemless Hosts
        SettingsArrow(
            title = stringResource(CoreR.string.settings_hosts_title),
            summary = stringResource(CoreR.string.settings_hosts_summary),
            onClick = onCreateHosts
        )

        if (uiState.isZygiskSupported) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Zygisk
            val zygisk = uiState.zygiskEnabled
            val externalZygisk by viewModel.externalZygisk.collectAsStateWithLifecycle()
            val externalProviderActive = !Info.isZygiskEnabled && externalZygisk != null
            SettingsSwitch(
                title = stringResource(CoreR.string.zygisk),
                summary = when {
                    externalProviderActive -> stringResource(
                        CoreR.string.settings_zygisk_external,
                        externalZygisk.orEmpty(),
                    )
                    zygisk != Info.isZygiskEnabled -> stringResource(CoreR.string.reboot_apply_change)
                    else -> stringResource(CoreR.string.settings_zygisk_summary)
                },
                checked = zygisk,
                enabled = !externalProviderActive,
                onCheckedChange = onToggleZygisk
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // DenyList
            SettingsSwitch(
                title = stringResource(CoreR.string.settings_denylist_title),
                summary = stringResource(CoreR.string.settings_denylist_summary),
                checked = uiState.denyListEnabled,
                onCheckedChange = onToggleDenyList
            )

        }
    }
}

// --- Superuser ---

@Composable
internal fun SuperuserSection(
    uiState: SettingsUiState,
    onToggleSuAuth: (Boolean) -> Unit,
    onMultiuserModeSelected: (Int) -> Unit,
    onNamespaceModeSelected: (Int) -> Unit,
    onNotificationModeSelected: (Int) -> Unit,
    onToggleSuRestrict: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SmallTitle(text = stringResource(CoreR.string.superuser))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    ) {
        // Authentication
        SettingsSwitch(
            title = stringResource(CoreR.string.settings_su_auth_title),
            summary = stringResource(
                if (uiState.isDeviceSecure) CoreR.string.settings_su_auth_summary
                else CoreR.string.settings_su_auth_insecure
            ),
            checked = uiState.suAuthEnabled,
            enabled = uiState.isDeviceSecure,
            onCheckedChange = onToggleSuAuth
        )

        if (uiState.isMultiuserSupported) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Multiuser Mode
            val multiuserSummaries = stringArrayResource(CoreR.array.multiuser_summary)
            SettingsDropdown(
                title = stringResource(CoreR.string.multiuser_mode),
                summary = multiuserSummaries.getOrElse(uiState.selectedMultiuserModeIndex) { "" },
                items = stringArrayResource(CoreR.array.multiuser_mode).asList(),
                selectedIndex = uiState.selectedMultiuserModeIndex,
                enabled = uiState.isMultiuserEditable,
                onSelectedIndexChange = onMultiuserModeSelected
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Mount Namespace Mode
        val namespaceSummaries = stringArrayResource(CoreR.array.namespace_summary)
        SettingsDropdown(
            title = stringResource(CoreR.string.mount_namespace_mode),
            summary = namespaceSummaries.getOrElse(uiState.selectedNamespaceModeIndex) { "" },
            items = stringArrayResource(CoreR.array.namespace).asList(),
            selectedIndex = uiState.selectedNamespaceModeIndex,
            onSelectedIndexChange = onNamespaceModeSelected
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // SU Notification
        SettingsDropdown(
            title = stringResource(CoreR.string.superuser_notification),
            items = stringArrayResource(CoreR.array.su_notification).asList(),
            selectedIndex = uiState.selectedNotificationModeIndex,
            onSelectedIndexChange = onNotificationModeSelected
        )

        // Restrict (version >= 30.1)
        if (uiState.isSuRestrictSupported) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingsSwitch(
                title = stringResource(CoreR.string.settings_su_restrict_title),
                summary = stringResource(CoreR.string.settings_su_restrict_summary),
                checked = uiState.suRestrictEnabled,
                onCheckedChange = onToggleSuRestrict
            )
        }
    }
}

// --- Dialogs ---

@Composable
private fun UpdateChannelUrlDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    val isValidUrl = remember(url) {
        url.isBlank() || (
            (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) &&
            URLUtil.isValidUrl(url)
        )
    }

    MagiskDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = stringResource(CoreR.string.settings_update_custom_msg),
        confirmText = stringResource(android.R.string.ok),
        confirmEnabled = isValidUrl,
        onConfirm = {
            if (isValidUrl) {
                onConfirm(url)
            }
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss,
        scrollable = true,
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                isError = !isValidUrl,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DownloadPathDialog(
    initialPath: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var path by rememberSaveable(initialPath) { mutableStateOf(initialPath) }

    MagiskDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = stringResource(CoreR.string.settings_download_path_title),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            onConfirm(path.trim())
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss,
        scrollable = true,
    ) {
        Column {
            Text(
                text = stringResource(CoreR.string.settings_download_path_message, MediaStoreUtils.fullPath(path)),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
