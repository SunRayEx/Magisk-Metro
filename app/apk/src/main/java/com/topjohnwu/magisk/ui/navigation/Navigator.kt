package com.topjohnwu.magisk.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

class Navigator(initialKey: NavKey) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(initialKey)

    /**
     * When set, every pop is offered here first (system back, in-page back buttons and
     * programmatic calls all funnel through [pop]). Returning true means the interceptor
     * owns this pop and will call [popImmediate] itself — this is how the Metro return
     * animation runs before the back stack actually changes.
     */
    var popInterceptor: ((NavKey) -> Boolean)? = null

    private var bypassInterceptor = false

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        val top = backStack.lastOrNull() ?: return
        if (!bypassInterceptor && popInterceptor?.invoke(top) == true) return
        backStack.removeLastOrNull()
    }

    /** Pops without consulting [popInterceptor]; used by whoever intercepted the pop. */
    fun popImmediate() {
        bypassInterceptor = true
        try {
            pop()
        } finally {
            bypassInterceptor = false
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { navigator -> navigator.backStack.toList() },
            restore = { savedList ->
                val initialKey = savedList.firstOrNull() ?: Route.Main
                Navigator(initialKey).also {
                    it.backStack.clear()
                    it.backStack.addAll(savedList)
                }
            }
        )
    }
}

@Composable
fun rememberNavigator(startRoute: NavKey): Navigator {
    return rememberSaveable(startRoute, saver = Navigator.Saver) {
        Navigator(startRoute)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
