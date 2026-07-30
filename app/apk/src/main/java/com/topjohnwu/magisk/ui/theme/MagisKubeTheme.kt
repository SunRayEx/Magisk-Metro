package com.topjohnwu.magisk.ui.theme

import android.os.Build
import android.util.TypedValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.topjohnwu.magisk.R

private fun android.content.Context.themeColor(attr: Int): Color {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return Color(value.data)
}

private fun android.content.Context.themeColor(name: String): Color =
    themeColor(resources.getIdentifier(name, "attr", packageName))

enum class MetroAccentRole {
    MAGISK, MODULES, APPS, SETTINGS, CONTRIBUTORS, LOGS, SPONSOR
}

data class MetroAccent(val color: Color, val onColor: Color)

data class MetroPalette(
    val magisk: MetroAccent,
    val modules: MetroAccent,
    val apps: MetroAccent,
    val settings: MetroAccent,
    val contributors: MetroAccent,
    val logs: MetroAccent,
    val sponsor: MetroAccent,
) {
    operator fun get(role: MetroAccentRole) = when (role) {
        MetroAccentRole.MAGISK -> magisk
        MetroAccentRole.MODULES -> modules
        MetroAccentRole.APPS -> apps
        MetroAccentRole.SETTINGS -> settings
        MetroAccentRole.CONTRIBUTORS -> contributors
        MetroAccentRole.LOGS -> logs
        MetroAccentRole.SPONSOR -> sponsor
    }
}

private val DefaultMetroPalette = MetroPalette(
    // Palette sampled from the provided Windows 8 Metro reference artwork.
    magisk = MetroAccent(Color(0xFF1FB153), Color.Black),
    modules = MetroAccent(Color(0xFF2479C9), Color.Black),
    apps = MetroAccent(Color(0xFFC21C20), Color.Black),
    settings = MetroAccent(Color(0xFFFFC512), Color.Black),
    contributors = MetroAccent(Color(0xFFA748AA), Color.Black),
    logs = MetroAccent(Color(0xFFF7F7F7), Color.Black),
    sponsor = MetroAccent(Color(0xFFF56FB5), Color.Black),
)

val LocalMetroPalette = staticCompositionLocalOf { DefaultMetroPalette }

private fun uniformMetroPalette(accent: MetroAccent) = MetroPalette(
    magisk = accent,
    modules = accent,
    apps = accent,
    settings = accent,
    contributors = accent,
    logs = accent,
    sponsor = accent,
)

@Composable
fun MagisKubeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (Theme.selected == Theme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val primary = context.themeColor("colorPrimary")
        val secondary = context.themeColor("colorSecondary")
        val background = context.themeColor("colorSurface")
        val surfaceVariant = context.themeColor(R.attr.colorSurfaceVariant)
        val onPrimary = context.themeColor("colorOnPrimary")
        val onSurface = context.themeColor("colorOnSurface")
        val onSurfaceVariant = context.themeColor(R.attr.colorOnSurfaceVariant)
        if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                background = background,
                surface = background,
                surfaceVariant = surfaceVariant,
                onBackground = onSurface,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                background = background,
                surface = background,
                surfaceVariant = surfaceVariant,
                onBackground = onSurface,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
            )
        }
    }
    val usesDynamicColor = Theme.selected == Theme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val palette = if (!usesDynamicColor && Theme.selected == Theme.Default) {
        DefaultMetroPalette
    } else {
        uniformMetroPalette(MetroAccent(colors.primary, colors.onPrimary))
    }
    CompositionLocalProvider(LocalMetroPalette provides palette) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}