package com.topjohnwu.magisk.ui.home

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.NavigationActivity
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.databinding.FragmentMagiskManagerMd2Binding
import com.topjohnwu.magisk.ui.theme.MetroAccentRole

class MagiskManagerFragment : BaseFragment<FragmentMagiskManagerMd2Binding>() {

    override val layoutRes = R.layout.fragment_magisk_manager_md2
    override val metroAccentRole = MetroAccentRole.MAGISK
    override val viewModel by viewModel<HomeViewModel>()

    override fun onStart() {
        super.onStart()
        activity?.setTitle(R.string.metro_magisk_manager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Use the base (non-dynamic) theme so that background / text colors are never
        // affected by wallpaper-derived dynamic colors.
        val fixedContext = ContextThemeWrapper(requireContext(), R.style.ThemeFoundationMD2_Default)
        val fixedInflater = inflater.cloneInContext(fixedContext)
        super.onCreateView(fixedInflater, container, savedInstanceState)
        binding.installMagisk.setOnClickListener {
            activity?.let { activity ->
                NavigationActivity.navigate(
                    MagiskManagerFragmentDirections.actionMagiskManagerFragmentToInstallFragment(),
                    activity.findNavController(R.id.main_nav_host),
                    activity.contentResolver,
                )
            }
        }
        binding.uninstallMagisk.setOnClickListener { viewModel.onDeletePressed() }
        binding.updateManager.setOnClickListener { viewModel.onManagerPressed() }
        return binding.root
    }
}