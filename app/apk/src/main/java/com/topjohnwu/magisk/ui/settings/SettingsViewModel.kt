package com.topjohnwu.magisk.ui.settings

import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.utils.RootUtils
import com.topjohnwu.magisk.ui.navigation.Route
import com.topjohnwu.magisk.view.Shortcuts
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.topjohnwu.magisk.core.PersistentModules as PersistentModulesTool
import com.topjohnwu.magisk.ui.home.MetroCustomTiles
import com.topjohnwu.magisk.ui.home.MetroTileLayout
import com.topjohnwu.magisk.ui.home.MetroUiState
import com.topjohnwu.magisk.ui.theme.MetroAccentRole
import com.topjohnwu.magisk.ui.theme.MetroColors
import com.topjohnwu.magisk.ui.dialog.MetroDialogViews
import com.topjohnwu.magisk.R as AppR

class SettingsViewModel : BaseViewModel() {

    private val _denyListEnabled = MutableStateFlow(Config.denyList)
    val denyListEnabled: StateFlow<Boolean> = _denyListEnabled.asStateFlow()

    private val _persistentModules = MutableStateFlow(Config.metroPersistentModules)
    val persistentModules: StateFlow<Boolean> = _persistentModules.asStateFlow()

    private val _ghostSandbox = MutableStateFlow(Config.metroGhostSandbox)
    val ghostSandbox: StateFlow<Boolean> = _ghostSandbox.asStateFlow()

    val zygiskMismatch get() = Config.zygisk != Info.isZygiskEnabled

    private val _tileCustomization = MutableStateFlow(Config.metroTileCustomization)
    val tileCustomization: StateFlow<Boolean> = _tileCustomization.asStateFlow()


    var authenticate: (onSuccess: () -> Unit) -> Unit = { it() }

    fun navigateToDenyList() {
        navigateTo(Route.DenyList)
    }

    fun navigateToPersistentModules() {
        if (!Config.metroPersistentModules) {
            // Nothing to configure yet: the master switch must be enabled first.
            showSnackbar(AppR.string.metro_persistent_goto_settings)
        } else {
            navigateTo(Route.PersistentModules)
        }
    }

    fun navigateToTheme() {
        navigateTo(Route.Theme)
    }

    fun navigateToEditTiles() {
        navigateTo(Route.EditTiles)
    }

    fun toggleTileCustomization(enabled: Boolean) {
        Config.metroTileCustomization = enabled
        _tileCustomization.value = enabled
        MetroUiState.invalidate()
    }


    fun restoreBuiltInTiles() {
        MetroTileLayout.showAll()
        MetroUiState.invalidate()
    }

    fun requestAddShortcut() {
        Shortcuts.addHomeIcon(AppContext)
    }

    fun createHosts() {
        viewModelScope.launch {
            RootUtils.addSystemlessHosts()
            AppContext.toast(R.string.settings_hosts_toast, Toast.LENGTH_SHORT)
        }
    }

    fun toggleDenyList(enabled: Boolean) {
        _denyListEnabled.value = enabled
        val cmd = if (enabled) "enable" else "disable"
        Shell.cmd("magisk --denylist $cmd").submit { result ->
            if (result.isSuccess) {
                Config.denyList = enabled
            } else {
                _denyListEnabled.value = !enabled
            }
        }
    }

    fun withAuth(action: () -> Unit) = authenticate(action)

    fun notifyZygiskChange() {
        if (zygiskMismatch) showSnackbar(R.string.reboot_apply_change)
    }

    /** Master switch for the persistent-modules feature; owns the boot-time enforcement script. */
    fun togglePersistentModules(enabled: Boolean) {
        _persistentModules.value = enabled
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = if (enabled) {
                PersistentModulesTool.install() && PersistentModulesTool.ensureBootScript(true)
            } else {
                PersistentModulesTool.ensureBootScript(false)
                true
            }
            if (!ok) {
                // Keep the switch honest: without a working install nothing is enforced.
                Config.metroPersistentModules = !enabled
                _persistentModules.value = !enabled
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    AppContext.toast(AppR.string.metro_persistent_failed, Toast.LENGTH_LONG)
                }
            }
        }
    }

    /** Ghost sandbox: seccomp BPF hardening with invisible auditing for deny-listed apps. */
    fun toggleGhostSandbox(enabled: Boolean) {
        _ghostSandbox.value = enabled
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cmd = if (enabled) {
                "mkdir -p /data/adb/metromod && touch /data/adb/metromod/ghost_sandbox"
            } else {
                "rm -f /data/adb/metromod/ghost_sandbox"
            }
            Shell.cmd(cmd).exec()
        }
    }
}
