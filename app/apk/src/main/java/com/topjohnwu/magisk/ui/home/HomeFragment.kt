package com.topjohnwu.magisk.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.MenuProvider
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.MainDirections
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.download.DownloadEngine
import com.topjohnwu.magisk.databinding.FragmentHomeMd2Binding
import com.topjohnwu.magisk.core.R as CoreR
import androidx.navigation.findNavController
import com.topjohnwu.magisk.arch.NavigationActivity
import com.topjohnwu.magisk.ui.theme.MagisKubeTheme

class HomeFragment : BaseFragment<FragmentHomeMd2Binding>(), MenuProvider {

    override val layoutRes = R.layout.fragment_home_md2
    override val viewModel by viewModel<HomeViewModel>()

    override fun onStart() {
        super.onStart()
        activity?.setTitle(CoreR.string.section_home)
        DownloadEngine.observeProgress(this, viewModel::onProgressUpdate)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.metroHomeCompose.setContent {
            // Reduce display density so the Metro tile grid matches the visual scale of
            // other (View-based) screens without requiring a system DPI change.
            val systemDensity = LocalDensity.current
            val scaledDensity = Density(
                density = systemDensity.density * 0.85f,
                fontScale = systemDensity.fontScale,
            )
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                MagisKubeTheme {
                    MetroHomeScreen(
                        viewModel = viewModel,
                        onSettingsClick = ::navigateToSettings,
                        onModulesClick = ::navigateToModules,
                        onAppsClick = ::navigateToSuperuser,
                        onLogsClick = ::navigateToLogs,
                        onContributorsClick = ::navigateToContributors,
                    )
                }
            }
        }
        return binding.root
    }

    private fun navigateToSettings() {
        activity?.let {
            NavigationActivity.navigate(
                HomeFragmentDirections.actionHomeFragmentToSettingsFragment(),
                it.findNavController(R.id.main_nav_host),
                it.contentResolver,
            )
        }
    }

    private fun navigateToModules() {
        activity?.let {
            NavigationActivity.navigate(
                MainDirections.actionModuleFragment(),
                it.findNavController(R.id.main_nav_host),
                it.contentResolver,
            )
        }
    }

    private fun navigateToSuperuser() {
        activity?.let {
            NavigationActivity.navigate(
                MainDirections.actionSuperuserFragment(),
                it.findNavController(R.id.main_nav_host),
                it.contentResolver,
            )
        }
    }

    private fun navigateToLogs() {
        activity?.let {
            NavigationActivity.navigate(
                MainDirections.actionLogFragment(),
                it.findNavController(R.id.main_nav_host),
                it.contentResolver,
            )
        }
    }

    private fun navigateToContributors() {
        activity?.let {
            NavigationActivity.navigate(
                HomeFragmentDirections.actionHomeFragmentToContributorFragment(),
                it.findNavController(R.id.main_nav_host),
                it.contentResolver,
            )
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_home_md2, menu)
        if (!Info.isRooted)
            menu.removeItem(R.id.action_reboot)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> navigateToSettings()
            R.id.action_reboot -> activity?.let { RebootMenu.inflate(it).show() }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        viewModel.stateManagerProgress = 0
    }
}
