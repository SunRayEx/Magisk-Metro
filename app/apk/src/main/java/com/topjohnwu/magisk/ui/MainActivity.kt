package com.topjohnwu.magisk.ui

import android.Manifest
import android.Manifest.permission.REQUEST_INSTALL_PACKAGES
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import com.topjohnwu.magisk.MainDirections
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.arch.NavigationActivity
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.base.SplashController
import com.topjohnwu.magisk.core.base.SplashScreenHost
import com.topjohnwu.magisk.core.isRunningAsStub
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.tasks.AppMigration
import com.topjohnwu.magisk.databinding.ActivityMainMd2Binding
import com.topjohnwu.magisk.ui.theme.MetroAccentRole
import com.topjohnwu.magisk.ui.theme.Theme
import com.topjohnwu.magisk.view.MagiskDialog
import com.topjohnwu.magisk.view.Shortcuts
import kotlinx.coroutines.launch
import java.io.File
import com.topjohnwu.magisk.core.R as CoreR

class MainViewModel : BaseViewModel()

class MainActivity : NavigationActivity<ActivityMainMd2Binding>(), SplashScreenHost {

    override val layoutRes = R.layout.activity_main_md2
    override val viewModel by viewModel<MainViewModel>()
    override val navHostId: Int = R.id.main_nav_host
    override val splashController = SplashController(this)
    override val snackbarView: View
        get() {
            val fragmentOverride = currentFragment?.snackbarView
            return fragmentOverride ?: super.snackbarView
        }
    override val snackbarAnchorView: View?
        get() = currentFragment?.snackbarAnchorView?.takeIf { it.isVisible }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Theme.selected.themeRes)
        splashController.preOnCreate()
        super.onCreate(savedInstanceState)
        splashController.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        splashController.onResume()
    }

    @SuppressLint("InlinedApi")
    override fun onCreateUi(savedInstanceState: Bundle?) {
        setContentView()
        showUnsupportedMessage()
        askForHomeShortcut()

        // Ask permission to post notifications for background update check
        if (Config.checkUpdate) {
            withPermission(Manifest.permission.POST_NOTIFICATIONS) {
                Config.checkUpdate = it
            }
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Top toolbar + bottom navigation were removed for the Metro redesign. The home screen is
        // an edge-to-edge Compose tile grid and the four sections live in a Compose pivot that
        // manages its own status-bar tint, so there is no Activity-level chrome left to wire up.

        val section =
            if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES)
                Const.Nav.SETTINGS
            else
                intent.getStringExtra(Const.Key.OPEN_SECTION)

        getScreen(section)?.navigate()
    }

    private fun getScreen(name: String?): NavDirections? {
        return when (name) {
            Const.Nav.SUPERUSER -> MainDirections.actionSectionPivotFragment("APPS")
            Const.Nav.MODULES -> MainDirections.actionSectionPivotFragment("MODULES")
            Const.Nav.SETTINGS -> MainDirections.actionSectionPivotFragment("SETTINGS")
            else -> null
        }
    }

    @SuppressLint("InlinedApi")
    override fun showInvalidStateMessage(): Unit = runOnUiThread {
        MagiskDialog(this, metroAccentRole = MetroAccentRole.MAGISK).apply {
            setTitle(CoreR.string.unsupport_nonroot_stub_title)
            setMessage(CoreR.string.unsupport_nonroot_stub_msg)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = CoreR.string.install
                onClick {
                    withPermission(REQUEST_INSTALL_PACKAGES) {
                        if (!it) {
                            toast(CoreR.string.install_unknown_denied, Toast.LENGTH_SHORT)
                            showInvalidStateMessage()
                        } else {
                            lifecycleScope.launch {
                                AppMigration.restore(this@MainActivity)
                            }
                        }
                    }
                }
            }
            setCancelable(false)
            show()
        }
    }

    private fun showUnsupportedMessage() {
        if (Info.env.isUnsupported) {
            MagiskDialog(this, metroAccentRole = MetroAccentRole.MAGISK).apply {
                setTitle(CoreR.string.unsupport_magisk_title)
                setMessage(CoreR.string.unsupport_magisk_msg, Const.Version.MIN_VERSION)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        if (!Info.isEmulator && Info.env.isActive && System.getenv("PATH")
                ?.split(':')
                ?.filterNot { File("$it/magisk").exists() }
                ?.any { File("$it/su").exists() } == true) {
            MagiskDialog(this, metroAccentRole = MetroAccentRole.MAGISK).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_other_su_msg)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            MagiskDialog(this, metroAccentRole = MetroAccentRole.MAGISK).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_system_app_msg)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }

        if (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0) {
            MagiskDialog(this, metroAccentRole = MetroAccentRole.MAGISK).apply {
                setTitle(CoreR.string.unsupport_general_title)
                setMessage(CoreR.string.unsupport_external_storage_msg)
                setButton(MagiskDialog.ButtonType.POSITIVE) { text = android.R.string.ok }
                setCancelable(false)
            }.show()
        }
    }

    private fun askForHomeShortcut() {
        if (isRunningAsStub && !Config.askedHome &&
            ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            // Ask and show dialog
            Config.askedHome = true
            MagiskDialog(this, metroAccentRole = MetroAccentRole.MAGISK).apply {
                setTitle(CoreR.string.add_shortcut_title)
                setMessage(CoreR.string.add_shortcut_msg)
                setButton(MagiskDialog.ButtonType.NEGATIVE) {
                    text = android.R.string.cancel
                }
                setButton(MagiskDialog.ButtonType.POSITIVE) {
                    text = android.R.string.ok
                    onClick {
                        Shortcuts.addHomeIcon(this@MainActivity)
                    }
                }
                setCancelable(true)
            }.show()
        }
    }
}
