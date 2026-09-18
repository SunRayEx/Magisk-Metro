package com.topjohnwu.magisk.ui.home

import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.ui.navigation.Route

/**
 * Built-in Magisk destinations that can be pinned to the Start screen as a custom tile.
 *
 * A pinned destination is stored as an ordinary [MetroCustomTile] whose package name is the
 * pseudo-package [prefix] plus the destination key (`magisk:theme`, `magisk:persistent`, ...).
 * That keeps the 6-field tile serialization unchanged: the tile reads like any other, and only
 * [CustomTile] tap routing needs to know about the prefix.
 */
object MetroMagiskShortcuts {
    const val prefix = "magisk:"

    enum class Destination(val key: String, val labelRes: Int) {
        Install("install", R.string.metro_install),
        Modules("modules", R.string.metro_modules),
        Apps("apps", R.string.metro_apps),
        Logs("logs", R.string.metro_logs),
        Settings("settings", R.string.metro_settings),
        Theme("theme", R.string.metro_custom_theme),
        Persistent("persistent", R.string.metro_persistent_title),
        DenyList("denylist", R.string.metro_denylist_sandbox),
        Contributors("contributors", R.string.metro_contributors),
        ;

        val pseudoPackage get() = prefix + key
    }

    /** Every destination that can be offered in the "add a Magisk tile" picker. */
    val destinations: List<Destination> = Destination.entries

    /** Resolve a stored pseudo-package back to the route it should open, if it is one. */
    fun routeFor(packageName: String): Route? = packageName.removePrefix(prefix).let { key ->
        when (Destination.entries.firstOrNull { it.key == key }) {
            Destination.Install -> Route.MetroPivot("MAGISK")
            Destination.Modules -> Route.MetroPivot("MODULES")
            Destination.Apps -> Route.MetroPivot("APPS")
            Destination.Logs -> Route.MetroPivot("LOGS")
            Destination.Settings -> Route.MetroPivot("SETTINGS")
            Destination.Theme -> Route.Theme
            Destination.Persistent -> Route.PersistentModules
            Destination.DenyList -> Route.DenyList
            Destination.Contributors -> Route.Contributors
            null -> null
        }
    }

    fun isShortcut(packageName: String) = packageName.startsWith(prefix)
}
