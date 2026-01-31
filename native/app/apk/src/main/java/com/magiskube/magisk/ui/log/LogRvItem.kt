package com.magiskube.magisk.ui.log

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.magiskube.magisk.R
import com.magiskube.magisk.databinding.DiffItem
import com.magiskube.magisk.databinding.ItemWrapper
import com.magiskube.magisk.databinding.ObservableRvItem
import com.magiskube.magisk.databinding.ViewAwareItem

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
