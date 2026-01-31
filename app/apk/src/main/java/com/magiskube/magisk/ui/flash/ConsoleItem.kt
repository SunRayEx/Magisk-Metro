package com.magiskube.magisk.ui.flash

import android.view.View
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.magiskube.magisk.R
import com.magiskube.magisk.databinding.DiffItem
import com.magiskube.magisk.databinding.ItemWrapper
import com.magiskube.magisk.databinding.RvItem
import com.magiskube.magisk.databinding.ViewAwareItem
import kotlin.math.max

class ConsoleItem(
    override val item: String
) : RvItem(), ViewAwareItem, DiffItem<ConsoleItem>, ItemWrapper<String> {
    override val layoutRes = R.layout.item_console_md2

    private var parentWidth = -1

    override fun onBind(binding: ViewDataBinding, recyclerView: RecyclerView) {
        if (parentWidth < 0)
            parentWidth = (recyclerView.parent as View).width

        val view = binding.root as TextView
        view.measure(0, 0)

        // We want our recyclerView at least as wide as screen
        val desiredWidth = max(view.measuredWidth, parentWidth)

        view.updateLayoutParams { width = desiredWidth }

        if (recyclerView.width < desiredWidth) {
            recyclerView.requestLayout()
        }
    }
}
