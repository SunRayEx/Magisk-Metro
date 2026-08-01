package com.topjohnwu.magisk.ui.deny

import android.annotation.SuppressLint
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import androidx.databinding.Bindable
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.ktx.concurrentMap
import com.topjohnwu.magisk.databinding.bindExtra
import com.topjohnwu.magisk.databinding.filterList
import com.topjohnwu.magisk.databinding.addOnPropertyChangedCallback
import com.topjohnwu.magisk.databinding.set
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.withContext
import java.util.Locale

class DenyListViewModel : AsyncLoadViewModel() {

    enum class AppFilter {
        USER,
        SYSTEM,
    }

    enum class SortOrder {
        INSTALL_TIME,
        ALPHABETICAL,
    }

    private var allItems = emptyList<DenyListRvItem>()

    var appFilter = AppFilter.USER
        set(value) {
            field = value
            doQuery(query)
        }

    var sortOrder = SortOrder.INSTALL_TIME
        set(value) {
            field = value
            doQuery(query)
        }

    var query = ""
        set(value) {
            field = value
            doQuery(value)
        }

    val items = filterList<DenyListRvItem>(viewModelScope)
    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }

    @get:Bindable
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    @get:Bindable
    var stateRevision = 0
        private set(value) = set(value, field, { field = it }, BR.stateRevision)

    fun isDenied(packageName: String): Boolean =
        allItems.any { it.info.packageName == packageName && it.itemsChecked > 0 }

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        loading = true
        val apps = withContext(Dispatchers.Default) {
            val pm = AppContext.packageManager
            val denyList = Shell.cmd("magisk --denylist ls").exec().out
                .map { CmdlineListItem(it) }
            val apps = pm.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES).run {
                asFlow()
                    .filter { AppContext.packageName != it.packageName }
                    .concurrentMap { AppProcessInfo(it, pm, denyList) }
                    .filter { it.processes.isNotEmpty() }
                    .concurrentMap { DenyListRvItem(it) }
                    .toCollection(ArrayList(size))
            }
            apps
        }
        allItems = apps
        apps.forEach { item ->
            item.addOnPropertyChangedCallback(BR.checkedPercent) {
                stateRevision++
                doQuery(query)
            }
        }
        doQuery(query)
    }

    private fun doQuery(s: String) {
        val comparator = compareBy<DenyListRvItem> { it.itemsChecked == 0 }
            .thenByDescending {
                if (sortOrder == SortOrder.INSTALL_TIME) it.info.installTime else 0L
            }
            .thenBy {
                if (sortOrder == SortOrder.ALPHABETICAL) it.info.label.lowercase(Locale.ROOT) else ""
            }
            .thenBy { it.info.packageName }
        val filtered = allItems.asSequence().filter {
            val matchesAppType = when (appFilter) {
                AppFilter.USER -> !it.info.isSystemApp()
                AppFilter.SYSTEM -> it.info.isSystemApp()
            }
            val matchesQuery = it.info.label.contains(s, true) ||
                it.info.packageName.contains(s, true) ||
                it.processes.any { process -> process.process.name.contains(s, true) }
            matchesAppType && matchesQuery
        }
            .sortedWith(comparator)
            .toList()
        items.set(filtered)
        items.filter { true }
        loading = false
    }
}
