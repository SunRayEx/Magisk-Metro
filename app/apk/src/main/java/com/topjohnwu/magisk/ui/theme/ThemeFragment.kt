package com.topjohnwu.magisk.ui.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            // Sample every preview through the same MetroColors pipeline the running UI uses,
            // and inflate the row from its own themed context so its tint matches the theme
            // it represents even while a wallpaper theme is selected.
            val themed = MetroColors.previewThemeContext(requireContext(), theme)
            val palette = MetroColors.previewPalette(requireContext(), theme)
            ItemThemeBindingImpl.inflate(LayoutInflater.from(themed), binding.themeContainer, true).also {
                it.setVariable(BR.viewModel, viewModel)
                it.setVariable(BR.theme, theme)
                it.lifecycleOwner = viewLifecycleOwner
                it.themePreview.background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    palette.toIntArray(),
                )
            }
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        activity?.title = getString(CoreR.string.section_theme)
    }

}
