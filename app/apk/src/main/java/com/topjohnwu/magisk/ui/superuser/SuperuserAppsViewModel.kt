package com.topjohnwu.magisk.ui.superuser

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import androidx.compose.runtime.Immutable
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.data.magiskdb.PolicyDao
import com.topjohnwu.magisk.core.ktx.concurrentMap
import com.topjohnwu.magisk.core.ktx.getLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.withContext

/** An installed app that can be granted root from Apps > Authorized. */
@Immutable
data class SuperuserAppInfo(
    val label: String,
    val packageName: String,
    /** Normalized like the uids stored in the policy database. */
    val uid: Int,
    val isSystemApp: Boolean,
    val isSharedUid: Boolean,
    val firstInstallTime: Long,
) {
    val isShell: Boolean
        get() = packageName == SHELL_PACKAGE || uid == SHELL_UID

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val SHELL_UID = 2000
    }
}

/** Loads the installed apps offered for an active root grant. Policies stay in [SuperuserViewModel]. */
class SuperuserAppsViewModel : AsyncLoadViewModel() {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _apps = MutableStateFlow<List<SuperuserAppInfo>>(emptyList())
    val apps: StateFlow<List<SuperuserAppInfo>> = _apps.asStateFlow()

    override suspend fun doLoadWork() {
        if (!Info.showSuperUser) {
            _loading.value = false
            return
        }
        val apps = withContext(Dispatchers.Default) {
            val pm = AppContext.packageManager
            val myUid = AppContext.applicationInfo.uid
            val myPackage = AppContext.packageName

            val installed = runCatching {
                pm.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES)
            }.getOrElse { emptyList() }.toMutableList()

            // The shell package is hidden from the list on some devices, but adb root is
            // granted through it
            if (installed.none { it.packageName == SuperuserAppInfo.SHELL_PACKAGE }) {
                runCatching {
                    pm.getApplicationInfo(SuperuserAppInfo.SHELL_PACKAGE, MATCH_UNINSTALLED_PACKAGES)
                }.recoverCatching {
                    pm.getApplicationInfo(SuperuserAppInfo.SHELL_PACKAGE, 0)
                }.getOrNull()?.let { installed.add(it) }
            }

            installed
                .distinctBy { it.packageName }
                .asFlow()
                .filter {
                    it.packageName != myPackage &&
                        it.packageName != BuildConfig.APP_PACKAGE_NAME &&
                        it.uid != myUid
                }
                .concurrentMap { appInfo ->
                    val pkg = runCatching {
                        pm.getPackageInfo(appInfo.packageName, MATCH_UNINSTALLED_PACKAGES)
                    }.getOrNull()
                    SuperuserAppInfo(
                        label = appInfo.getLabel(pm),
                        packageName = appInfo.packageName,
                        uid = PolicyDao.normalizeUid(appInfo.uid),
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        isSharedUid = pkg?.sharedUserId != null,
                        firstInstallTime = pkg?.firstInstallTime ?: 0L,
                    )
                }
                .toCollection(ArrayList(installed.size))
        }
        _apps.value = apps
        _loading.value = false
    }
}
