package com.topjohnwu.magisk.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
            // Global 0.75 Metro density is applied at the activity level
            // (UIActivity.attachBaseContext), so no extra Compose density override here.
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
        return binding.root
    }

    private fun navigateToSettings() {
        navigateToPivot("SETTINGS")
    }

    private fun navigateToModules() {
        navigateToPivot("MODULES")
    }

    private fun navigateToSuperuser() {
        navigateToPivot("APPS")
    }

    private fun navigateToLogs() {
        navigateToPivot("LOGS")
    }

    // All four top-level sections now live inside the single Metro pivot; open it at the tapped
    // section. Swiping the pivot header switches between them (replaces the old bottom nav).
    private fun navigateToPivot(section: String) {
        activity?.let {
            NavigationActivity.navigate(
                MainDirections.actionSectionPivotFragment(section),
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
