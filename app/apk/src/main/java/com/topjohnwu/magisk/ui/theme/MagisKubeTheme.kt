package com.topjohnwu.magisk.ui.theme

import android.os.Build
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
import com.google.android.material.R as MaterialR
import com.topjohnwu.magisk.R

private fun android.content.Context.themeColor(attr: Int, fallback: Int = 0): Color =
    obtainStyledAttributes(intArrayOf(attr)).use { Color(it.getColor(0, fallback)) }

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
        val primary = Color(MetroColors.themePrimary(context))
        val secondary = context.themeColor(MaterialR.attr.colorSecondary)
        val background = context.themeColor(MaterialR.attr.colorSurface)
        val surfaceVariant = context.themeColor(R.attr.colorSurfaceVariant)
        val onPrimary = Color(MetroColors.themeOnPrimary(context))
        val onSurface = context.themeColor(MaterialR.attr.colorOnSurface)
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
    val palette = if (Theme.selected == Theme.Custom) {
        MetroPalette(
            magisk = MetroAccent(Color(MetroColors.accent(context, MetroAccentRole.MAGISK)), Color(MetroColors.onAccent(context, MetroAccentRole.MAGISK))),
            modules = MetroAccent(Color(MetroColors.accent(context, MetroAccentRole.MODULES)), Color(MetroColors.onAccent(context, MetroAccentRole.MODULES))),
            apps = MetroAccent(Color(MetroColors.accent(context, MetroAccentRole.APPS)), Color(MetroColors.onAccent(context, MetroAccentRole.APPS))),
            settings = MetroAccent(Color(MetroColors.accent(context, MetroAccentRole.SETTINGS)), Color(MetroColors.onAccent(context, MetroAccentRole.SETTINGS))),
            contributors = MetroAccent(Color(MetroColors.accent(context, MetroAccentRole.CONTRIBUTORS)), Color(MetroColors.onAccent(context, MetroAccentRole.CONTRIBUTORS))),
            logs = MetroAccent(Color(MetroColors.accent(context, MetroAccentRole.LOGS)), Color(MetroColors.onAccent(context, MetroAccentRole.LOGS))),
            sponsor = MetroAccent(Color(MetroColors.accent(context, MetroAccentRole.SPONSOR)), Color(MetroColors.onAccent(context, MetroAccentRole.SPONSOR))),
        )
    } else if (!usesDynamicColor && Theme.selected == Theme.Default) {
        DefaultMetroPalette
    } else {
        uniformMetroPalette(MetroAccent(colors.primary, colors.onPrimary))
    }
    CompositionLocalProvider(LocalMetroPalette provides palette) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}