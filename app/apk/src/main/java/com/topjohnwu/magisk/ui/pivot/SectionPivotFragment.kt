package com.topjohnwu.magisk.ui.pivot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import androidx.navigation.fragment.findNavController
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.databinding.FragmentSectionPivotBinding
import com.topjohnwu.magisk.ui.anim.MetroPageExit
import com.topjohnwu.magisk.ui.deny.DenyListViewModel
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.theme.MagisKubeTheme

/**
 * Compose host for the Metro pivot. Instead of four separate destinations (each with its own top
 * toolbar + bottom-nav entry), this single fragment owns all four section ViewModels and presents
 * them inside a swipeable [MetroPivotScreen]. The ViewModels are reused verbatim; only their
 * presentation is rewritten in Compose.
 */
class SectionPivotFragment : BaseFragment<FragmentSectionPivotBinding>() {

    override val layoutRes = R.layout.fragment_section_pivot
    override val viewModel by viewModel<SuperuserViewModel>()

    private val logVM by viewModel<LogViewModel>()
    private val moduleVM by viewModel<ModuleViewModel>()
    private val settingsVM by viewModel<SettingsViewModel>()
    private val homeVM by viewModel<HomeViewModel>()
    private val denyListVM by viewModel<DenyListViewModel>()

    private val args by navArgs<SectionPivotFragmentArgs>()

    override fun startObserveLiveData() {
        super.startObserveLiveData()
        // The pivot hosts four ViewModels; forward every one's events to the shared dispatcher
        // (BaseFragment.onEventDispatched) since all events are Activity/Context executors.
        logVM.viewEvents.observe(this, this::onEventDispatched)
        moduleVM.viewEvents.observe(this, this::onEventDispatched)
        settingsVM.viewEvents.observe(this, this::onEventDispatched)
        homeVM.viewEvents.observe(this, this::onEventDispatched)
        denyListVM.viewEvents.observe(this, this::onEventDispatched)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.pivotCompose.setContent {
            MagisKubeTheme {
                MetroPivotScreen(
                    superuserVM = viewModel,
                    logVM = logVM,
                    moduleVM = moduleVM,
                    settingsVM = settingsVM,
                    homeVM = homeVM,
                    denyListVM = denyListVM,
                    showDenyListInitially = args.initialSection == "DENYLIST",
                    initialSection = PivotSection.fromName(args.initialSection),
                )
            }
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // BaseFragment.onResume() only starts the primary (superuser) ViewModel; start the others.
        (logVM as? AsyncLoadViewModel)?.startLoading()
        (moduleVM as? AsyncLoadViewModel)?.startLoading()
        homeVM.startLoading()
        denyListVM.startLoading()
        settingsVM.items.forEach { it.refresh() }
    }

    override fun onBackPressed(): Boolean {
        if (binding.root.tag == "metro_exiting") return true
        binding.root.tag = "metro_exiting"
        MetroPageExit.active = true
        binding.root.postDelayed({ findNavController().popBackStack() }, 650L)
        return true
    }

    override fun onDestroyView() {
        MetroPageExit.active = false
        super.onDestroyView()
    }
}
