package com.magiskube.magisk.ui.settings

import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.databinding.ObservableMap
import androidx.databinding.ObservableArrayList
import com.magiskube.magisk.BR
import com.magiskube.magisk.R
import com.magiskube.magisk.arch.BaseViewModel
import com.magiskube.magisk.ui.settings.BaseSettingsItem
import com.magiskube.magisk.databinding.bindExtra
import com.magiskube.magisk.databinding.MergeObservableList
import com.magiskube.magisk.databinding.set
import com.magiskube.magisk.databinding.RvItem
import com.magiskube.magisk.databinding.ObservableRvItem

class SettingsViewModel : BaseViewModel() {
    val items = MergeObservableList<RvItem>()
    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }
}

