package com.topjohnwu.magisk.ui.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize

sealed interface Route : NavKey, Parcelable {
    @Parcelize
    data object Main : Route

    @Parcelize
    data class MetroPivot(val section: String = "APPS") : Route

    @Parcelize
    data class TileGroup(val tileId: String) : Route

    @Parcelize
    data object Theme : Route

    @Parcelize
    data object MetroColors : Route

    @Parcelize
    data object Contributors : Route

    @Parcelize
    data object EditTiles : Route

    @Parcelize
    data class Flash(
        val action: String,
        val additionalData: String? = null,
        val usbDevice: String? = null,
    ) : Route

    @Parcelize
    data class SuperuserDetail(val uid: Int) : Route

    @Parcelize
    data class Action(val dir: String, val name: String) : Route

    @Parcelize
    data class WebUi(val id: String, val name: String) : Route
}
