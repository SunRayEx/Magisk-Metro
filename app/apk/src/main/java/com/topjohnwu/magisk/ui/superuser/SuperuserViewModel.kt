package com.topjohnwu.magisk.ui.superuser

import android.annotation.SuppressLint
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R
import com.topjohnwu.magisk.core.data.magiskdb.PolicyDao
import com.topjohnwu.magisk.core.ktx.getLabel
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.databinding.MergeObservableList
import com.topjohnwu.magisk.databinding.RvItem
import com.topjohnwu.magisk.databinding.bindExtra
import com.topjohnwu.magisk.databinding.diffList
import com.topjohnwu.magisk.databinding.set
import com.topjohnwu.magisk.dialog.SuperuserRevokeDialog
import com.topjohnwu.magisk.events.AuthEvent
import com.topjohnwu.magisk.events.SnackbarEvent
import com.topjohnwu.magisk.utils.asText
import com.topjohnwu.magisk.view.TextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SuperuserViewModel(
    private val db: PolicyDao
) : AsyncLoadViewModel() {

    private val itemNoData = TextItem(R.string.superuser_policy_none)

    private val itemsHelpers = ObservableArrayList<TextItem>()
    private val itemsPolicies = diffList<PolicyRvItem>()

    val items = MergeObservableList<RvItem>()
        .insertList(itemsHelpers)
        .insertList(itemsPolicies)
    val extraBindings = bindExtra {
        it.put(BR.listener, this)
    }

    @get:Bindable
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        if (!Info.showSuperUser) {
            loading = false
            return
        }
        loading = true
        withContext(Dispatchers.IO) {
            db.deleteOutdated()
            db.delete(AppContext.applicationInfo.uid)
            val pm = AppContext.packageManager
            val storedPolicies = db.fetchAll().associateBy { it.uid }
            val policiesByUid = HashMap<Int, SuPolicy>()
            val applications = pm.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES)
                .asSequence()
                .filter { app ->
                    app.uid >= Process.FIRST_APPLICATION_UID &&
                        app.uid != AppContext.applicationInfo.uid &&
                        app.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED != 0
                }
                .toList()

            val policies = applications.map { app ->
                val policy = policiesByUid.getOrPut(app.uid) {
                    storedPolicies[app.uid] ?: SuPolicy(
                        uid = app.uid,
                        policy = SuPolicy.DENY,
                        remain = 0L,
                    )
                }
                val sharedUid = (pm.getPackagesForUid(app.uid)?.size ?: 0) > 1
                PolicyRvItem(
                    this@SuperuserViewModel,
                    policy,
                    app.packageName,
                    sharedUid,
                    runCatching { app.loadIcon(pm) }.getOrDefault(pm.defaultActivityIcon),
                    app.getLabel(pm),
                )
            }.toMutableList()

            storedPolicies.keys
                .filter { uid -> pm.getPackagesForUid(uid) == null }
                .forEach { uid -> db.delete(uid) }

            policies.sortWith(compareBy(
                { it.appName.lowercase(Locale.ROOT) },
                { it.packageName }
            ))
            itemsPolicies.update(policies)
        }
        if (itemsPolicies.isNotEmpty())
            itemsHelpers.clear()
        else if (itemsHelpers.isEmpty())
            itemsHelpers.add(itemNoData)
        loading = false
    }

    // ---

    fun deletePressed(item: PolicyRvItem) {
        fun updateState() = viewModelScope.launch {
            db.delete(item.item.uid)
            item.item.apply {
                policy = SuPolicy.DENY
                remain = 0L
                logging = true
                notification = true
            }
            itemsPolicies.forEach {
                if (it.item.uid == item.item.uid) {
                    it.isExpanded = false
                    it.notifyPropertyChanged(BR.enabled)
                    it.notifyPropertyChanged(BR.rootGranted)
                    it.notifyPropertyChanged(BR.sliderValue)
                    it.notifyPropertyChanged(BR.showSlider)
                    it.notifyPropertyChanged(BR.shouldNotify)
                    it.notifyPropertyChanged(BR.shouldLog)
                }
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            SuperuserRevokeDialog(item.title) { updateState() }.show()
        }
    }

    fun updateNotify(item: PolicyRvItem) {
        viewModelScope.launch {
            db.update(item.item)
            val res = when {
                item.item.notification -> R.string.su_snack_notif_on
                else -> R.string.su_snack_notif_off
            }
            itemsPolicies.forEach {
                if (it.item.uid == item.item.uid) {
                    it.notifyPropertyChanged(BR.shouldNotify)
                }
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun updateLogging(item: PolicyRvItem) {
        viewModelScope.launch {
            db.update(item.item)
            val res = when {
                item.item.logging -> R.string.su_snack_log_on
                else -> R.string.su_snack_log_off
            }
            itemsPolicies.forEach {
                if (it.item.uid == item.item.uid) {
                    it.notifyPropertyChanged(BR.shouldLog)
                }
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun updatePolicy(item: PolicyRvItem, policy: Int) {
        val items = itemsPolicies.filter { it.item.uid == item.item.uid }
        fun updateState() {
            viewModelScope.launch {
                val res = if (policy >= SuPolicy.ALLOW) R.string.su_snack_grant else R.string.su_snack_deny
                item.item.policy = policy
                item.item.remain = 0L
                db.update(item.item)
                items.forEach {
                    it.notifyPropertyChanged(BR.enabled)
                    it.notifyPropertyChanged(BR.rootGranted)
                    it.notifyPropertyChanged(BR.sliderValue)
                    it.notifyPropertyChanged(BR.showSlider)
                }
                SnackbarEvent(res.asText(item.appName)).publish()
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            updateState()
        }
    }
}
