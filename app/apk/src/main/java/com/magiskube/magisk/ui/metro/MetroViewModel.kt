package com.magiskube.magisk.ui.metro

import androidx.databinding.Bindable
import com.magiskube.magisk.BR
import com.magiskube.magisk.arch.AsyncLoadViewModel
import com.magiskube.magisk.arch.ViewEvent
import com.magiskube.magisk.core.Info
import com.magiskube.magisk.databinding.set
import com.magiskube.magisk.utils.asText
import com.magiskube.magisk.core.R as CoreR

class MetroViewModel : AsyncLoadViewModel() {

    enum class TileType {
        MAGISK, ROOTED, EXCLUDED, MODULES, CONTRIBUTORS, LOGS
    }

    @get:Bindable
    var magiskStatus = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.magiskStatus)

    @get:Bindable
    var magiskVersion = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.magiskVersion)

    @get:Bindable
    var rootedAppsCount = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.rootedAppsCount)

    @get:Bindable
    var excludedAppsCount = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.excludedAppsCount)

    @get:Bindable
    var modulesCount = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.modulesCount)

    @get:Bindable
    var contributorsCount = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.contributorsCount)

    @get:Bindable
    var logErrors = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.logErrors)

    @get:Bindable
    var logWarnings = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.logWarnings)

    private var errorCount = 0
    private var warningCount = 0

    override suspend fun doLoadWork() {
        loadMagiskInfo()
        loadRootedAppsCount()
        loadExcludedAppsCount()
        loadModulesCount()
        loadLogStats()
        loadContributors()
    }

    private fun loadMagiskInfo() {
        try {
            if (Info.env.isActive) {
                magiskStatus = if (Info.env.isUnsupported) {
                    CoreR.string.unsupport_magisk_title.asText()
                } else {
                    CoreR.string.home_installed_version.asText()
                }
                val version = Info.env.versionString ?: ""
                val code = Info.env.versionCode.toString()
                magiskVersion = "$version ($code)".asText()
            } else {
                magiskStatus = CoreR.string.not_available.asText()
                magiskVersion = CoreR.string.not_available.asText()
            }
        } catch (e: Exception) {
            magiskStatus = CoreR.string.not_available.asText()
            magiskVersion = CoreR.string.not_available.asText()
        }
    }

    private fun loadRootedAppsCount() {
        try {
            rootedAppsCount = CoreR.string.not_available.asText()
        } catch (e: Exception) {
            rootedAppsCount = CoreR.string.loading.asText()
        }
    }

    private fun loadExcludedAppsCount() {
        try {
            excludedAppsCount = CoreR.string.not_available.asText()
        } catch (e: Exception) {
            excludedAppsCount = CoreR.string.loading.asText()
        }
    }

    private fun loadModulesCount() {
        try {
            modulesCount = CoreR.string.not_available.asText()
        } catch (e: Exception) {
            modulesCount = CoreR.string.loading.asText()
        }
    }

    private fun loadLogStats() {
        try {
            logErrors = "$errorCount Errors".asText()
            logWarnings = "$warningCount Warnings".asText()
        } catch (e: Exception) {
            logErrors = CoreR.string.loading.asText()
            logWarnings = CoreR.string.loading.asText()
        }
    }

    private fun loadContributors() {
        try {
            contributorsCount = "5".asText()
        } catch (e: Exception) {
            contributorsCount = CoreR.string.loading.asText()
        }
    }

    fun onMagiskInstall() {
        // Navigate to install screen - implement in fragment
    }

    fun onMagiskSettings() {
        // Navigate to settings - implement in fragment
    }

    fun onRootedAppsList() {
        CloseMenuEvent().publish()
    }

    fun onExcludedAppsList() {
        CloseMenuEvent().publish()
    }

    fun onModulesList() {
        CloseMenuEvent().publish()
    }

    fun onContributors() {
        CloseMenuEvent().publish()
    }

    fun onClearLogs() {
        CloseMenuEvent().publish()
    }

    fun onCloseMenu() {
        CloseMenuEvent().publish()
    }

    class CloseMenuEvent : ViewEvent()
}
