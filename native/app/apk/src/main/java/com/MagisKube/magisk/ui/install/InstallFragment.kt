package com.MagisKube.magisk.ui.install

import com.MagisKube.magisk.R
import com.MagisKube.magisk.arch.BaseFragment
import com.MagisKube.magisk.arch.viewModel
import com.MagisKube.magisk.databinding.FragmentInstallMd2Binding
import com.MagisKube.magisk.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
