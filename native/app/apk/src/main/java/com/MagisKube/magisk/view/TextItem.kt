package com.MagisKube.magisk.view

import com.MagisKube.magisk.R
import com.MagisKube.magisk.databinding.DiffItem
import com.MagisKube.magisk.databinding.ItemWrapper
import com.MagisKube.magisk.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
