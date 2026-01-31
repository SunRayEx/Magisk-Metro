package com.MagisKube.magisk.ui.theme

import com.MagisKube.magisk.arch.BaseViewModel
import com.MagisKube.magisk.core.Config
import com.MagisKube.magisk.dialog.DarkThemeDialog
import com.MagisKube.magisk.events.RecreateEvent
import com.MagisKube.magisk.view.TappableHeadlineItem

class ThemeViewModel : BaseViewModel(), TappableHeadlineItem.Listener {

    val themeHeadline = TappableHeadlineItem.ThemeMode

    override fun onItemPressed(item: TappableHeadlineItem) = when (item) {
        is TappableHeadlineItem.ThemeMode -> DarkThemeDialog().show()
    }

    fun saveTheme(theme: Theme) {
        if (!theme.isSelected) {
            Config.themeOrdinal = theme.ordinal
            RecreateEvent().publish()
        }
    }
}
