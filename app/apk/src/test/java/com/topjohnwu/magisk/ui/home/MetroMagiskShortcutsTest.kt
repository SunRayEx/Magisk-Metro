package com.topjohnwu.magisk.ui.home

import com.topjohnwu.magisk.ui.navigation.Route
import com.topjohnwu.magisk.ui.pivot.PivotSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MetroMagiskShortcutsTest {
    @Test
    fun persistentTileOpensModulesPivot() {
        // Existing serialized tiles must open the embedded page, not a second destination.
        assertEquals(
            Route.MetroPivot(PivotSection.MODULES.name),
            MetroMagiskShortcuts.routeFor("magisk:persistent"),
        )
        assertEquals(
            MetroMagiskShortcuts.routeFor("magisk:modules"),
            MetroMagiskShortcuts.routeFor("magisk:persistent"),
        )
    }

    @Test
    fun everyOfferedTileStillHasADestination() {
        MetroMagiskShortcuts.destinations.forEach { destination ->
            assertNotNull(
                destination.key,
                MetroMagiskShortcuts.routeFor(destination.pseudoPackage),
            )
        }
    }
}
