package com.topjohnwu.magisk.ui.settings

import android.os.Build
import android.view.View
import android.widget.Toast
import android.graphics.Color
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.MainDirections
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R
import com.topjohnwu.magisk.core.isRunningAsStub
import com.topjohnwu.magisk.core.ktx.activity
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.tasks.AppMigration
import com.topjohnwu.magisk.core.utils.LocaleSetting
import com.topjohnwu.magisk.core.utils.RootUtils
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.databinding.bindExtra
import com.topjohnwu.magisk.events.AddHomeIconEvent
import com.topjohnwu.magisk.events.AuthEvent
import com.topjohnwu.magisk.events.SnackbarEvent
import com.topjohnwu.magisk.events.RecreateEvent
import kotlinx.coroutines.launch
import com.topjohnwu.magisk.core.PersistentModules as PersistentModulesTool
import com.topjohnwu.magisk.ui.home.MetroCustomTiles
import com.topjohnwu.magisk.ui.home.MetroTileLayout
import com.topjohnwu.magisk.ui.home.MetroUiState
import com.topjohnwu.magisk.ui.theme.MetroAccentRole
import com.topjohnwu.magisk.ui.theme.MetroColors
import com.topjohnwu.magisk.ui.dialog.MetroDialogViews
import com.topjohnwu.magisk.view.MagiskDialog
import com.topjohnwu.magisk.R as AppR

class SettingsViewModel : BaseViewModel(), BaseSettingsItem.Handler {

    val items = createItems()
    val extraBindings = bindExtra {
        it.put(BR.handler, this)
    }

    private fun createItems(): List<BaseSettingsItem> {
        val context = AppContext
        val hidden = context.packageName != BuildConfig.APP_PACKAGE_NAME

        // Customization
        val list = mutableListOf(
            Customization,
            Theme, MetroThemeColors, DarkMode, MetroTileCustomization, MetroTileGrid, MetroTileContent,
            if (LocaleSetting.useLocaleManager) LanguageSystem else Language
        )
        if (isRunningAsStub && ShortcutManagerCompat.isRequestPinShortcutSupported(context))
            list.add(AddShortcut)

        // Magisk
        if (Info.env.isActive) {
            list.addAll(listOf(
                Magisk,
                SystemlessHosts
            ))
            if (Const.Version.atLeast_24_0()) {
                list.addAll(listOf(Zygisk, DenyList))
            }
            list.add(GhostSandbox)
        }

        // Superuser
        if (Info.showSuperUser) {
            list.addAll(listOf(
                Superuser,
                Tapjack, Authentication, AccessMode, MultiuserMode, MountNamespaceMode,
                AutomaticResponse, RequestTimeout, SUNotification
            ))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Re-authenticate is not feasible on 8.0+
                list.add(Reauthenticate)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Can hide overlay windows on 12.0+
                list.remove(Tapjack)
            }
            if (Const.Version.atLeast_30_1()) {
                list.add(Restrict)
            }
        }

        // Misc stays last so app-management preferences do not split the security settings.
        list.addAll(listOf(
            Misc,
            UpdateChannel, DoHToggle, UpdateChecker, DownloadPath, RandNameToggle,
            PersistentModules, PersistentManage
        ))
        if (Info.env.isActive && Const.USER_ID == 0) {
            if (hidden) list.add(Restore) else list.add(Hide)
        }

        return list
    }

    override fun onItemPressed(view: View, item: BaseSettingsItem, doAction: () -> Unit) {
        when (item) {
            DownloadPath -> withExternalRW(doAction)
            UpdateChecker -> withPostNotificationPermission(doAction)
            Authentication -> AuthEvent(doAction).publish()
            AutomaticResponse -> if (Config.suAuth) AuthEvent(doAction).publish() else doAction()
            else -> doAction()
        }
    }

    override fun onItemAction(view: View, item: BaseSettingsItem) {
        when (item) {
            Theme -> MainDirections.actionThemeFragment().navigate()
            MetroThemeColors -> showCustomThemeDialog(view)
            MetroTileContent -> showTileContentDialog(view)
            DenyListConfig -> MainDirections.actionSectionPivotFragment("DENYLIST").navigate()
            DarkMode -> {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(Config.darkTheme)
                RecreateEvent().publish()
            }
            LanguageSystem -> view.activity.startActivity(LocaleSetting.localeSettingsIntent)
            AddShortcut -> AddHomeIconEvent().publish()
            SystemlessHosts -> createHosts()
            is Hide -> viewModelScope.launch { AppMigration.hide(view.activity, item.value) }
            Restore -> viewModelScope.launch { AppMigration.restore(view.activity) }
            Zygisk -> if (Zygisk.mismatch) SnackbarEvent(R.string.reboot_apply_change).publish()
            PersistentModules -> viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val ok = if (Config.metroPersistentModules) {
                    val installed = PersistentModulesTool.install()
                    installed && PersistentModulesTool.ensureBootScript(true)
                } else {
                    PersistentModulesTool.ensureBootScript(false)
                    true
                }
                if (!ok) {
                    // Keep the switch honest: without a working install nothing is enforced.
                    Config.metroPersistentModules = !Config.metroPersistentModules
                    PersistentManage.refresh()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        AppContext.toast(AppR.string.metro_persistent_failed, Toast.LENGTH_LONG)
                    }
                }
            }
            PersistentManage -> MainDirections.actionPersistentFragment().navigate()
            GhostSandbox -> viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val cmd = if (Config.metroGhostSandbox) {
                    "mkdir -p /data/adb/metromod && touch /data/adb/metromod/ghost_sandbox"
                } else {
                    "rm -f /data/adb/metromod/ghost_sandbox"
                }
                com.topjohnwu.superuser.Shell.cmd(cmd).exec()
            }
            else -> Unit
        }
    }

    private fun createHosts() {
        viewModelScope.launch {
            RootUtils.addSystemlessHosts()
            AppContext.toast(R.string.settings_hosts_toast, Toast.LENGTH_SHORT)
        }
    }

    private fun showCustomThemeDialog(view: View) {
        val context = view.context
        val fields = MetroAccentRole.entries.associateWith { role ->
            MetroDialogViews.editText(context).apply {
                hint = role.name
                setText(MetroColors.customHex(role, context))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 4, 24, 4)
            fields.values.forEach { addView(it) }
        }
        MagiskDialog(view.activity).apply {
            setTitle(AppR.string.metro_custom_colors)
            setView(content)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = android.R.string.ok
                onClick {
                    val parsed = fields.mapValues { (_, edit) -> edit.text.toString().trim() }
                    if (parsed.values.any { runCatching { Color.parseColor(it) }.isFailure }) {
                        doNotDismiss = true
                        editError(fields.values.firstOrNull { runCatching { Color.parseColor(it.text.toString()) }.isFailure })
                    } else {
                        MetroColors.saveCustomColors(parsed)
                        Config.metroCustomTheme = true
                        MetroUiState.invalidate()
                        RecreateEvent().publish()
                    }
                }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) { text = android.R.string.cancel }
        }.show()
    }

    private fun editError(edit: EditText?) {
        edit?.error = AppContext.getString(AppR.string.metro_invalid_hex)
    }

    private data class RootApp(val packageName: String, val label: String)

    private suspend fun rootApps(): List<RootApp> {
        if (!Info.showSuperUser) return emptyList()
        val pm = AppContext.packageManager
        return ServiceLocator.policyDB.fetchAll().asSequence()
            .filter { it.policy >= com.topjohnwu.magisk.core.model.su.SuPolicy.ALLOW }
            .flatMap { policy ->
                (if (policy.uid == android.os.Process.SYSTEM_UID) arrayOf("android")
                else pm.getPackagesForUid(policy.uid).orEmpty()).asSequence()
            }
            .distinct()
            .mapNotNull { packageName ->
                runCatching {
                    val info = pm.getApplicationInfo(packageName, 0)
                    RootApp(packageName, info.loadLabel(pm).toString())
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun showTileContentDialog(view: View) {
        val context = view.context
        val existing = MetroCustomTiles.load()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 4, 24, 4)
        }
        // Keep a reference so inner rows can dismiss before reopening, avoiding stacked dialogs.
        lateinit var dialog: MagiskDialog
        MetroTileLayout.ids.forEach { id ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(MetroDialogViews.editText(context).apply {
                setText(id)
                isEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(MetroDialogViews.button(context, if (MetroTileLayout.isVisible(id)) {
                context.getString(AppR.string.metro_tile_delete)
            } else {
                context.getString(AppR.string.metro_tile_restore)
            }).apply {
                setOnClickListener {
                    if (MetroTileLayout.isVisible(id)) MetroTileLayout.hide(id) else MetroTileLayout.show(id)
                    MetroUiState.invalidate()
                    dialog.dismiss()
                    showTileContentDialog(view)
                }
            })
            content.addView(row)
        }
        existing.forEach { tile ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(MetroDialogViews.editText(context).apply {
                setText(tile.title)
                isEnabled = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(MetroDialogViews.button(context, context.getString(AppR.string.metro_tile_delete)).apply {
                setOnClickListener {
                    MetroCustomTiles.remove(tile.id)
                    dialog.dismiss()
                    showTileContentDialog(view)
                }
            })
            content.addView(row)
        }
        content.addView(MetroDialogViews.button(context, context.getString(AppR.string.metro_tile_add)).apply {
            setOnClickListener {
                dialog.dismiss()
                showAddTileDialog(view)
            }
        })
        val scroll = ScrollView(context).apply { addView(content) }
        dialog = MagiskDialog(view.activity).apply {
            setTitle(AppR.string.metro_tile_content)
            setView(scroll)
            setButton(MagiskDialog.ButtonType.NEGATIVE) { text = android.R.string.cancel }
        }
        dialog.show()
    }

    private fun showAddTileDialog(view: View) {
        val context = view.context
        viewModelScope.launch {
            val apps = rootApps()
            val hiddenBuiltIns = MetroTileLayout.ids.filterNot(MetroTileLayout::isVisible)
            if (apps.isEmpty() && hiddenBuiltIns.isEmpty()) {
                AppContext.toast(AppR.string.metro_no_root_apps, Toast.LENGTH_SHORT)
                return@launch
            }
            val title = MetroDialogViews.editText(context, context.getString(AppR.string.metro_tile_name))
            val ticker = MetroDialogViews.editText(context, context.getString(AppR.string.metro_tile_ticker))
            // Tile colors follow the theme (only the custom-theme palette recolors them), so
            // no color input here on purpose.
            val checks = apps.map { app -> CheckBox(context).apply { text = "${app.label}\n${app.packageName}" } }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 4, 24, 4)
                addView(title)
                addView(ticker)
                checks.forEach { addView(it) }
            }
            // Deleted built-in tiles are re-addable right where new tiles are added, so a
            // default tile is never more than two taps away once removed.
            if (hiddenBuiltIns.isNotEmpty()) {
                content.addView(MetroDialogViews.editText(context, context.getString(AppR.string.metro_tile_add_builtin)).apply {
                    isEnabled = false
                })
                hiddenBuiltIns.forEach { id ->
                    content.addView(MetroDialogViews.button(context, id).apply {
                        setOnClickListener {
                            MetroTileLayout.show(id)
                            MetroUiState.invalidate()
                            dialogRef.get()?.dismiss()
                            view.post { showTileContentDialog(view) }
                        }
                    })
                }
            }
            dialogRef.set(MagiskDialog(view.activity).apply {
                setTitle(AppR.string.metro_tile_add)
                setView(ScrollView(context).apply { addView(content) })
                setButton(MagiskDialog.ButtonType.POSITIVE) {
                    text = android.R.string.ok
                    onClick {
                        val selected = checks.mapIndexedNotNull { index, check -> apps[index].takeIf { check.isChecked } }
                        if (selected.size > 9) {
                            doNotDismiss = true
                            AppContext.toast(AppR.string.metro_invalid_tile, Toast.LENGTH_SHORT)
                        } else if (selected.isEmpty()) {
                            // Nothing to add; the user only restored built-ins above.
                            MetroUiState.invalidate()
                            view.post { showTileContentDialog(view) }
                        } else {
                            MetroCustomTiles.add(
                                packageName = selected.first().packageName,
                                title = title.text.toString().ifBlank { selected.first().label },
                                ticker = ticker.text.toString(),
                                color = "",
                                groupMembers = selected.map { it.packageName },
                            )
                            MetroUiState.invalidate()
                            // The add dialog will dismiss automatically; reopen the tile list on next frame.
                            view.post { showTileContentDialog(view) }
                        }
                    }
                }
                setButton(MagiskDialog.ButtonType.NEGATIVE) { text = android.R.string.cancel }
            })
            dialogRef.get()?.show()
        }
    }

    private val dialogRef = java.util.concurrent.atomic.AtomicReference<MagiskDialog?>()

}
