package com.MagisKube.magisk.ui.log

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.MagisKube.magisk.R
import com.MagisKube.magisk.databinding.DiffItem
import com.MagisKube.magisk.databinding.ItemWrapper
import com.MagisKube.magisk.databinding.ObservableRvItem
import com.MagisKube.magisk.databinding.ViewAwareItem

class LogRvItem(
    override val item: String
) : ObservableRvItem(), DiffItem<LogRvItem>, ItemWrapper<String>, ViewAwareItem {

    override val layoutRes = R.layout.item_log_textview

    override fun onBind(binding: ViewDataBinding, recyclerView: RecyclerView) {
        val view = binding.root as MaterialTextView
        view.measure(0, 0)
        val desiredWidth = view.measuredWidth
        val layoutParams = view.layoutParams
        layoutParams.width = desiredWidth
        if (recyclerView.width < desiredWidth) {
            recyclerView.requestLayout()
        }
    }
}
