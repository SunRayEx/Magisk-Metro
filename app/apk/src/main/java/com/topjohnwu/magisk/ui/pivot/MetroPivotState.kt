package com.topjohnwu.magisk.ui.pivot

import com.topjohnwu.magisk.ui.theme.MetroAccentRole

/**
 * The pivot lives entirely in Compose while dialogs are raised through ViewEvents, which resolve
 * the accent role from the hosting fragment. The pivot fragment hosts every section, so the
 * visible section is published here and the fragment forwards it as its accent role — dialogs
 * then follow the accent of the tile the user actually navigated from.
 */
object MetroPivotState {
    val section = androidx.compose.runtime.mutableStateOf(PivotSection.APPS)

    val accentRole: MetroAccentRole
        get() = when (section.value) {
            PivotSection.MAGISK -> MetroAccentRole.MAGISK
            PivotSection.APPS -> MetroAccentRole.APPS
            PivotSection.LOGS -> MetroAccentRole.LOGS
            PivotSection.MODULES -> MetroAccentRole.MODULES
            PivotSection.SETTINGS -> MetroAccentRole.SETTINGS
        }
}
