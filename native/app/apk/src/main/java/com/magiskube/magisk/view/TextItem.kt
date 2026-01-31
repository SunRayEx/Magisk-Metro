package com.magiskube.magisk.view

import com.magiskube.magisk.R
import com.magiskube.magisk.databinding.DiffItem
import com.magiskube.magisk.databinding.ItemWrapper
import com.magiskube.magisk.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
