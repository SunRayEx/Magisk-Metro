package com.topjohnwu.magisk.ui.theme

import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.color.DynamicColors
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.databinding.FragmentThemeMd2Binding
import com.topjohnwu.magisk.databinding.ItemThemeBindingImpl
import com.topjohnwu.magisk.core.R as CoreR

class ThemeFragment : BaseFragment<FragmentThemeMd2Binding>() {

    override val layoutRes = R.layout.fragment_theme_md2
    override val metroAccentRole = MetroAccentRole.SETTINGS
    override val viewModel by viewModel<ThemeViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        for (theme in Theme.displayValues) {
            val themed = if (theme == Theme.Dynamic) {
                DynamicColors.wrapContextIfAvailable(requireContext())
            } else {
                ContextThemeWrapper(activity, theme.themeRes)
            }
            ItemThemeBindingImpl.inflate(
                LayoutInflater.from(themed), binding.themeContainer, true
            ).also {
                it.setVariable(BR.viewModel, viewModel)
                it.setVariable(BR.theme, theme)
                it.lifecycleOwner = viewLifecycleOwner
                if (theme == Theme.Default) {
                    it.themePreview.background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(
                            Color.rgb(31, 177, 83), Color.rgb(36, 121, 201),
                            Color.rgb(194, 28, 32), Color.rgb(255, 197, 18),
                            Color.rgb(167, 72, 170), Color.rgb(247, 247, 247),
                            Color.rgb(245, 111, 181),
                        ),
                    )
                }
            }
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        activity?.title = getString(CoreR.string.section_theme)
    }

}
