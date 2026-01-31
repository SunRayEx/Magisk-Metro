package com.MagisKube.magisk.ui.theme

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.MagisKube.magisk.BR
import com.MagisKube.magisk.R
import com.MagisKube.magisk.arch.BaseFragment
import com.MagisKube.magisk.arch.viewModel
import com.MagisKube.magisk.databinding.FragmentThemeMd2Binding
import com.MagisKube.magisk.databinding.ItemThemeBindingImpl
import com.MagisKube.magisk.core.R as CoreR

class ThemeFragment : BaseFragment<FragmentThemeMd2Binding>() {

    override val layoutRes = R.layout.fragment_theme_md2
    override val viewModel by viewModel<ThemeViewModel>()

    private fun <T> Array<T>.paired(): List<Pair<T, T?>> {
        val iterator = iterator()
        if (!iterator.hasNext()) return emptyList()
        val result = mutableListOf<Pair<T, T?>>()
        while (iterator.hasNext()) {
            val a = iterator.next()
            val b = if (iterator.hasNext()) iterator.next() else null
            result.add(a to b)
        }
        return result
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        for ((a, b) in Theme.values().paired()) {
            val c = inflater.inflate(R.layout.item_theme_container, null, false)
            val left = c.findViewById<FrameLayout>(R.id.left)
            val right = c.findViewById<FrameLayout>(R.id.right)

            for ((theme, view) in listOf(a to left, b to right)) {
                theme ?: continue
                val themed = ContextThemeWrapper(activity, theme.themeRes)
                ItemThemeBindingImpl.inflate(LayoutInflater.from(themed), view, true).also {
                    it.setVariable(BR.viewModel, viewModel)
                    it.setVariable(BR.theme, theme)
                    it.lifecycleOwner = viewLifecycleOwner
                }
            }

            binding.themeContainer.addView(c)
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        activity?.title = getString(CoreR.string.section_theme)
    }

}
