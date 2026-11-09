package com.topjohnwu.magisk.ui.module

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.download.Subject
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.core.model.module.OnlineModule
import com.topjohnwu.magisk.utils.TextHolder
import com.topjohnwu.magisk.utils.asText
import com.topjohnwu.magisk.ui.flash.FlashUtils
import com.topjohnwu.magisk.ui.navigation.Route
import com.topjohnwu.magisk.utils.asFlow
import com.topjohnwu.magisk.view.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.parcelize.Parcelize
import com.topjohnwu.magisk.core.R as CoreR

data class ModuleItem(
    val module: LocalModule,
    val isEnabled: Boolean = module.enable,
    val isRemoved: Boolean = module.remove,
    val showUpdate: Boolean = module.updateInfo != null && module.outdated,
) {
    val showNotice: Boolean
    val showAction: Boolean
    val showWebUi: Boolean = module.hasWebUi
    val noticeText: TextHolder
    val isUpdated = module.updated
    val updateReady get() = module.outdated && !isRemoved && isEnabled

    init {
        val isZygisk = module.isZygisk
        val isRiru = module.isRiru
        val zygiskUnloaded = isZygisk && module.zygiskUnloaded

        showNotice = zygiskUnloaded ||
            (Info.isZygiskEnabled && isRiru) ||
            (!Info.isZygiskEnabled && isZygisk)
        showAction = module.hasAction && !showNotice
        noticeText =
            when {
                zygiskUnloaded -> CoreR.string.zygisk_module_unloaded.asText()
                isRiru -> CoreR.string.suspend_text_riru.asText(CoreR.string.zygisk.asText())
                else -> CoreR.string.suspend_text_zygisk.asText(CoreR.string.zygisk.asText())
            }
    }
}

@Parcelize
class OnlineModuleSubject(
    override val module: OnlineModule,
    override val autoLaunch: Boolean,
    override val notifyId: Int = Notifications.nextId(),
) : Subject.Module() {
    override fun pendingIntent(context: Context) = FlashUtils.installIntent(context, file, notifyId)
}

class ModuleViewModel : AsyncLoadViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val modules: List<ModuleItem> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            Info.isConnected.asFlow().collect {
                if (Info.env.isActive) startLoading()
            }
        }
    }

    private var updateJob: Job? = null
    private var loadGeneration = 0L

    override suspend fun doLoadWork() {
        val generation = ++loadGeneration
        updateJob?.cancel()
        // Keep showing the current list while refreshing: loading is only true until the first
        // load finishes, so a refresh doesn't swap the list for a spinner and lose its scroll
        val moduleLoaded = Info.env.isActive &&
            withContext(Dispatchers.IO) { LocalModule.loaded() }
        if (moduleLoaded) {
            val previous = _uiState.value.modules.associateBy { it.module.base.name }
            val modules = withContext(Dispatchers.Default) {
                LocalModule.installed().map { module ->
                    // Keep the update info of the same module version until it is fetched
                    // again, so update buttons don't disappear on every refresh
                    previous[module.base.name]?.module
                        ?.takeIf { it.versionCode == module.versionCode }
                        ?.let {
                            module.updateInfo = it.updateInfo
                            module.outdated = it.outdated
                        }
                    ModuleItem(module)
                }
            }
            // Local discovery never waits for a repository. Keep existing update metadata
            // while checking, then publish the results as one snapshot.
            _uiState.update { it.copy(loading = false, modules = modules) }
            updateJob = viewModelScope.launch {
                val updates = withTimeoutOrNull(8_000L) {
                    withContext(Dispatchers.IO) {
                        modules.map { item ->
                            item.module.fetch()
                            item.module
                        }.associateBy { it.base.name }
                    }
                } ?: return@launch
                if (generation != loadGeneration) return@launch
                _uiState.update { state ->
                    state.copy(modules = state.modules.map { current ->
                        val module = updates[current.module.base.name]
                        if (module != null && module === current.module) {
                            // Preserve toggles made while the network check was running.
                            current.copy(showUpdate = module.updateInfo != null && module.outdated)
                        } else current
                    })
                }
            }
        } else {
            _uiState.update { it.copy(loading = false, modules = emptyList()) }
        }
    }

    fun confirmLocalInstall(uri: Uri) {
        navigateTo(Route.Flash(Const.Value.FLASH_ZIP, uri.toString()))
    }

    fun runAction(dir: String, name: String) {
        navigateTo(Route.Action(dir, name))
    }

    fun openWebUi(id: String, name: String) {
        navigateTo(Route.WebUi(id, name))
    }

    fun toggleEnabled(item: ModuleItem) {
        val newEnabled = !item.isEnabled
        item.module.enable = newEnabled
        _uiState.update { state ->
            state.copy(
                modules = state.modules.map {
                    if (it.module.base.name == item.module.base.name) it.copy(isEnabled = newEnabled) else it
                }
            )
        }
    }

    fun toggleRemove(item: ModuleItem) {
        val newRemoved = !item.isRemoved
        item.module.remove = newRemoved
        _uiState.update { state ->
            state.copy(
                modules = state.modules.map {
                    if (it.module.base.name == item.module.base.name) it.copy(isRemoved = newRemoved) else it
                }
            )
        }
    }
}
