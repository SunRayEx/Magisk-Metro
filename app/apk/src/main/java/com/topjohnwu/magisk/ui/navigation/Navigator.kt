package com.topjohnwu.magisk.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

class Navigator(val backStack: NavBackStack<NavKey>) {
    constructor(vararg initialKeys: NavKey) : this(NavBackStack(*initialKeys))

    /**
     * When set, every pop is offered here first (system back, in-page back buttons and
     * programmatic calls all funnel through [pop]). Returning true means the interceptor
     * owns this pop and will call [popImmediate] itself — this is how the Metro return
     * animation runs before the back stack actually changes.
     */
    var popInterceptor: ((NavKey) -> Boolean)? = null

    private var bypassInterceptor = false

    fun push(key: NavKey) {
        // Ignore pushing the screen that is already on top (e.g. a double tap, or the same
        // request delivered twice). Both copies would get the same content key, so they would
        // share one ViewModel and saved state, and need two back presses to leave.
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        val top = backStack.last()
        if (!bypassInterceptor && popInterceptor?.invoke(top) == true) return false
        backStack.removeAt(backStack.lastIndex)
        return true
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
                val stack = NavBackStack<NavKey>()
                if (savedList.isNotEmpty()) {
                    stack.addAll(savedList)
                } else {
                    stack.add(Route.Main)
                }
                Navigator(stack)
            }
        )
    }
}

@Composable
fun rememberNavigator(vararg startRoutes: NavKey = arrayOf(Route.Main)): Navigator {
    return rememberSaveable(saver = Navigator.Saver) {
        Navigator(*startRoutes)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
