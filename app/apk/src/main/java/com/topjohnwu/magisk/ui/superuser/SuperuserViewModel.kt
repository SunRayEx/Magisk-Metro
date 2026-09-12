package com.topjohnwu.magisk.ui.superuser

import android.annotation.SuppressLint
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R
import com.topjohnwu.magisk.core.data.magiskdb.PolicyDao
import com.topjohnwu.magisk.core.ktx.getLabel
import com.topjohnwu.magisk.core.model.su.SuPolicy
<<<<<<< HEAD
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
=======
import com.topjohnwu.magisk.core.su.SuEvents
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class PolicyItem(
    val policy: SuPolicy,
    val packageName: String,
    val isSharedUid: Boolean,
    val icon: Drawable,
    val appName: String,
    val policyValue: Int = policy.policy,
    val notification: Boolean = policy.notification,
    val logging: Boolean = policy.logging,
) {
    val title get() = appName
    val isEnabled get() = policyValue >= SuPolicy.ALLOW
    val isRestricted get() = policyValue == SuPolicy.RESTRICT
}

class SuperuserViewModel(
    private val db: PolicyDao
) : AsyncLoadViewModel() {

    var authenticate: (onSuccess: () -> Unit) -> Unit = { it() }

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            SuEvents.policyChanged.debounce(500).collect { reload() }
        }
    }

    data class UiState(
        val loading: Boolean = true,
        val policies: List<PolicyItem> = emptyList(),
        val suRestrict: Boolean = Config.suRestrict,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    @get:Bindable
    var policyRevision = 0
        private set(value) = set(value, field, { field = it }, BR.policyRevision)

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        if (!Info.showSuperUser) {
            _uiState.update { it.copy(loading = false) }
            return
        }
        _uiState.update { it.copy(loading = true) }
        withContext(Dispatchers.IO) {
            db.deleteOutdated()
            db.delete(AppContext.applicationInfo.uid)
<<<<<<< HEAD
=======
            val policies = ArrayList<PolicyItem>()
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
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
<<<<<<< HEAD
                .toList()

            val policies = applications.map { app ->
                val policy = policiesByUid.getOrPut(app.uid) {
                    storedPolicies[app.uid] ?: SuPolicy(uid = app.uid)
=======
                val map = pkgs.mapNotNull { pkg ->
                    try {
                        val info = pm.getPackageInfo(pkg, MATCH_UNINSTALLED_PACKAGES)
                        PolicyItem(
                            policy = policy,
                            packageName = info.packageName,
                            isSharedUid = info.sharedUserId != null,
                            icon = info.applicationInfo?.loadIcon(pm) ?: pm.defaultActivityIcon,
                            appName = info.applicationInfo?.getLabel(pm) ?: info.packageName,
                            policyValue = policy.policy,
                            notification = policy.notification,
                            logging = policy.logging,
                        )
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
                }
                val sharedUid = (pm.getPackagesForUid(app.uid)?.size ?: 0) > 1
                PolicyRvItem(
                    this@SuperuserViewModel,
                    policy,
                    app.packageName,
                    sharedUid,
                    runCatching { app.loadIcon(pm) }.getOrDefault(pm.defaultActivityIcon),
                    app.getLabel(pm),
                    app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
                    runCatching {
                        pm.getPackageInfo(app.packageName, MATCH_UNINSTALLED_PACKAGES).firstInstallTime
                    }.getOrDefault(0L),
                )
            }.toMutableList()

            storedPolicies.keys
                .filter { uid -> pm.getPackagesForUid(uid) == null }
                .forEach { uid -> db.delete(uid) }

            policies.sortWith(compareBy(
                { it.appName.lowercase(Locale.ROOT) },
                { it.packageName }
            ))
<<<<<<< HEAD
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
                policy = SuPolicy.QUERY
                remain = -1L
                logging = true
                notification = true
            }
            itemsPolicies.forEach {
                if (it.item.uid == item.item.uid) {
                    it.resetToQuery()
                }
            }
            policyRevision++
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            SuperuserRevokeDialog(item.title) { updateState() }.show()
=======
            _uiState.update { it.copy(loading = false, policies = policies, suRestrict = Config.suRestrict) }
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
        }
    }

    fun refreshSuRestrict() {
        _uiState.update { it.copy(suRestrict = Config.suRestrict) }
    }

    val requiresAuth get() = Config.suAuth

    fun performDelete(item: PolicyItem, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            db.delete(item.policy.uid)
            _uiState.update { state ->
                state.copy(policies = state.policies.filter { it.policy.uid != item.policy.uid })
            }
            onDeleted()
        }
    }

    fun updateNotify(item: PolicyItem) {
        val newNotification = !item.notification
        item.policy.notification = newNotification
        viewModelScope.launch {
            db.update(item.policy)
            _uiState.update { state ->
                state.copy(
                    policies = state.policies.map {
                        if (it.policy.uid == item.policy.uid) it.copy(notification = newNotification) else it
                    }
                )
            }
            val res = if (newNotification) R.string.su_snack_notif_on else R.string.su_snack_notif_off
            showSnackbar(AppContext.getString(res, item.appName))
        }
    }

    fun updateLogging(item: PolicyItem) {
        val newLogging = !item.logging
        item.policy.logging = newLogging
        viewModelScope.launch {
            db.update(item.policy)
            _uiState.update { state ->
                state.copy(
                    policies = state.policies.map {
                        if (it.policy.uid == item.policy.uid) it.copy(logging = newLogging) else it
                    }
                )
            }
            val res = if (newLogging) R.string.su_snack_log_on else R.string.su_snack_log_off
            showSnackbar(AppContext.getString(res, item.appName))
        }
    }

    fun updatePolicy(item: PolicyItem, newPolicy: Int) {
        fun updateState() {
            viewModelScope.launch {
<<<<<<< HEAD
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
                policyRevision++
                SnackbarEvent(res.asText(item.appName)).publish()
=======
                item.policy.policy = newPolicy
                db.update(item.policy)
                _uiState.update { state ->
                    state.copy(
                        policies = state.policies.map {
                            if (it.policy.uid == item.policy.uid) it.copy(policyValue = newPolicy) else it
                        }
                    )
                }
                val res = if (newPolicy >= SuPolicy.ALLOW) R.string.su_snack_grant else R.string.su_snack_deny
                showSnackbar(AppContext.getString(res, item.appName))
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
            }
        }

        if (Config.suAuth) {
            authenticate { updateState() }
        } else {
            updateState()
        }
    }

    fun togglePolicy(item: PolicyItem) {
        val newPolicy = if (item.isEnabled) SuPolicy.DENY else SuPolicy.ALLOW
        updatePolicy(item, newPolicy)
    }

    fun toggleRestrict(item: PolicyItem) {
        val newPolicy = if (item.isRestricted) SuPolicy.ALLOW else SuPolicy.RESTRICT
        updatePolicy(item, newPolicy)
    }
}
