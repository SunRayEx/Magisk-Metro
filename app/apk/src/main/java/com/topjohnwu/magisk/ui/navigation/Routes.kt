package com.topjohnwu.magisk.ui.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data class MetroPivot(val section: String = "APPS") : Route

    @Parcelize
    @Serializable
    data class TileGroup(val tileId: String) : Route

    @Parcelize
    @Serializable
    data object Theme : Route

    @Parcelize
    @Serializable
    data object MetroColors : Route

    @Parcelize
    @Serializable
    data object Contributors : Route

    @Parcelize
    @Serializable
    data object PersistentModules : Route

    @Parcelize
    @Serializable
    data object DenyList : Route

    @Parcelize
    @Serializable
    data object EditTiles : Route

    @Parcelize
    @Serializable
    data class Flash(
        val action: String,
        val additionalData: String? = null,
    ) : Route

    @Parcelize
    @Serializable
    data class SuperuserDetail(val uid: Int) : Route

    @Parcelize
    @Serializable
    data class Action(
        val id: String,
        val name: String,
    ) : Route
}
