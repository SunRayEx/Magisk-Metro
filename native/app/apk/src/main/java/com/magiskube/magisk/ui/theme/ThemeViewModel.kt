package com.magiskube.magisk.ui.theme

import com.magiskube.magisk.arch.BaseViewModel
import com.magiskube.magisk.core.Config
import com.magiskube.magisk.dialog.DarkThemeDialog
import com.magiskube.magisk.events.RecreateEvent
import com.magiskube.magisk.view.TappableHeadlineItem

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
