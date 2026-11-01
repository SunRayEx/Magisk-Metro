package com.topjohnwu.magisk.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** The only icons that may be persisted for user-created Metro tiles. */
internal object MetroTileIcons {
    val names = listOf(
        "none", "add", "apps", "android", "build", "check", "close", "delete", "download",
        "edit", "email", "favorite", "help", "home", "info", "key", "language", "list",
        "lock", "menu", "more", "notifications", "open_in_new", "person", "phone", "play",
        "power", "refresh", "save", "search", "security", "send", "settings", "share",
        "shopping_cart", "star", "sync", "terminal", "thumb_up", "today", "upload", "visibility",
        "warning", "work", "wifi", "cloud", "code", "dashboard", "extension", "launch",
    )

    fun get(name: String): ImageVector? = when (name) {
        "add" -> Icons.Default.Add
        "apps" -> Icons.Default.Apps
        "android" -> Icons.Default.Android
        "build" -> Icons.Default.Build
        "check" -> Icons.Default.Check
        "close" -> Icons.Default.Close
        "delete" -> Icons.Default.Delete
        "download" -> Icons.Default.Download
        "edit" -> Icons.Default.Edit
        "email" -> Icons.Default.Email
        "favorite" -> Icons.Default.Favorite
        "help" -> Icons.Default.Help
        "home" -> Icons.Default.Home
        "info" -> Icons.Default.Info
        "key" -> Icons.Default.Key
        "language" -> Icons.Default.Language
        "list" -> Icons.Default.List
        "lock" -> Icons.Default.Lock
        "menu" -> Icons.Default.Menu
        "more" -> Icons.Default.MoreVert
        "notifications" -> Icons.Default.Notifications
        "open_in_new" -> Icons.Default.OpenInNew
        "person" -> Icons.Default.Person
        "phone" -> Icons.Default.Phone
        "play" -> Icons.Default.PlayArrow
        "power" -> Icons.Default.PowerSettingsNew
        "refresh" -> Icons.Default.Refresh
        "save" -> Icons.Default.Save
        "search" -> Icons.Default.Search
        "security" -> Icons.Default.Security
        "send" -> Icons.Default.Send
        "settings" -> Icons.Default.Settings
        "share" -> Icons.Default.Share
        "shopping_cart" -> Icons.Default.ShoppingCart
        "star" -> Icons.Default.Star
        "sync" -> Icons.Default.Sync
        "terminal" -> Icons.Default.Terminal
        "thumb_up" -> Icons.Default.ThumbUp
        "today" -> Icons.Default.Today
        "upload" -> Icons.Default.Upload
        "visibility" -> Icons.Default.Visibility
        "warning" -> Icons.Default.Warning
        "work" -> Icons.Default.Work
        "wifi" -> Icons.Default.Wifi
        "cloud" -> Icons.Default.Cloud
        "code" -> Icons.Default.Code
        "dashboard" -> Icons.Default.Dashboard
        "extension" -> Icons.Default.Extension
        "launch" -> Icons.Default.Launch
        else -> null
    }
}
