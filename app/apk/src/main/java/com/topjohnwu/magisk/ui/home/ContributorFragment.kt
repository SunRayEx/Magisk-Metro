package com.topjohnwu.magisk.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.databinding.FragmentContributorMd2Binding
import com.topjohnwu.magisk.ui.theme.MagisKubeTheme
import com.topjohnwu.magisk.ui.theme.MetroAccentRole

class ContributorFragment : BaseFragment<FragmentContributorMd2Binding>() {

    override val layoutRes = R.layout.fragment_contributor_md2
    override val metroAccentRole = MetroAccentRole.CONTRIBUTORS
    override val viewModel by viewModel<ContributorViewModel>()

    override fun onStart() {
        super.onStart()
        activity?.setTitle(R.string.metro_contributors)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.contributorMetroCompose.setContent {
            MagisKubeTheme {
                MetroContributorScreen(onLinkClick = viewModel::onLinkPressed)
            }
        }
        return binding.root
    }
}