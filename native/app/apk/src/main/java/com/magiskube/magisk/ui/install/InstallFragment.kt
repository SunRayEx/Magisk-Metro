package com.magiskube.magisk.ui.install

import com.magiskube.magisk.R
import com.magiskube.magisk.arch.BaseFragment
import com.magiskube.magisk.arch.viewModel
import com.magiskube.magisk.databinding.FragmentInstallMd2Binding
import com.magiskube.magisk.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
