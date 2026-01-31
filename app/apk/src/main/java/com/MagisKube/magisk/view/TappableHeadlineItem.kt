package com.MagisKube.magisk.view

import com.MagisKube.magisk.R
import com.MagisKube.magisk.databinding.DiffItem
import com.MagisKube.magisk.databinding.RvItem
import com.MagisKube.magisk.core.R as CoreR

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
