package com.magiskube.magisk.view

import com.magiskube.magisk.R
import com.magiskube.magisk.databinding.DiffItem
import com.magiskube.magisk.databinding.RvItem
import com.magiskube.magisk.core.R as CoreR

sealed class TappableHeadlineItem : RvItem(), DiffItem<TappableHeadlineItem> {

    abstract val title: Int
    abstract val icon: Int

    override val layoutRes = R.layout.item_tappable_headline

    // --- listener

    interface Listener {

        fun onItemPressed(item: TappableHeadlineItem)

    }

    // --- objects

    object ThemeMode : TappableHeadlineItem() {
        override val title = CoreR.string.settings_dark_mode_title
        override val icon = R.drawable.ic_day_night
    }

}
