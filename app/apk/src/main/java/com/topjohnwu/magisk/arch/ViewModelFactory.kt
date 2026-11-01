package com.topjohnwu.magisk.arch

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.ui.deny.DenyListViewModel
import com.topjohnwu.magisk.ui.flash.FlashViewModel
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.install.InstallViewModel
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.ActionViewModel
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.surequest.SuRequestViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserAppsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel

val VMFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { HomeViewModel(ServiceLocator.networkService) }
    initializer { LogViewModel(ServiceLocator.logRepo) }
    initializer { SuperuserViewModel(ServiceLocator.policyDB) }
    initializer { SuperuserAppsViewModel() }
    initializer { InstallViewModel(ServiceLocator.networkService, ServiceLocator.firmwareCrawler) }
    initializer { DenyListViewModel() }
    initializer { FlashViewModel(createSavedStateHandle()) }
    initializer { ActionViewModel(createSavedStateHandle()) }
    initializer { ModuleViewModel() }
    initializer { SettingsViewModel() }
    initializer { SuRequestViewModel(ServiceLocator.policyDB, ServiceLocator.timeoutPrefs) }
}
