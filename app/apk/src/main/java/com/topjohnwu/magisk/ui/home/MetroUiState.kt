package com.topjohnwu.magisk.ui.home

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf

/** Bridges preference-backed settings changes to already mounted Compose screens. */
object MetroUiState {
    private val versionState: MutableIntState = mutableIntStateOf(0)

    val version: Int get() = versionState.intValue

    fun invalidate() {
        versionState.intValue++
    }
}