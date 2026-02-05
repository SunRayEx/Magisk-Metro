package com.magiskube.magisk.ui.settings

import android.os.Bundle
import android.view.View
import com.magiskube.magisk.R
import androidx.fragment.app.Fragment
import com.magiskube.magisk.arch.BaseFragment
import com.magiskube.magisk.arch.viewModel
import com.magiskube.magisk.databinding.FragmentSettingsMd2Binding
import com.magiskube.magisk.core.R as CoreR

class SettingsFragment : BaseFragment<FragmentSettingsMd2Binding>() {

    override val layoutRes = R.layout.fragment_settings_md2
    override val viewModel by viewModel<SettingsViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(CoreR.string.settings)
    }
}
