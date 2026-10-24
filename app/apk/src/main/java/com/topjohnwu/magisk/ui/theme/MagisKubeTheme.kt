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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.android.material.R as MaterialR
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.ui.home.MetroUiState

private fun android.content.Context.themeColor(attr: Int, fallback: Int = 0): Color =
    obtainStyledAttributes(intArrayOf(attr)).use { Color(it.getColor(0, fallback)) }

enum class MetroAccentRole {
    MAGISK, MODULES, APPS, SETTINGS, CONTRIBUTORS, LOGS, SPONSOR
}

/**
 * Global Metro display-density scale. The Windows Phone Metro look renders every screen
 * (View and Compose alike) at 0.75x; applied in [com.topjohnwu.magisk.ui.MainActivity.attachBaseContext].
 */
const val METRO_DENSITY_SCALE = 0.75f

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
    val view = LocalView.current
    val dark = isSystemInDarkTheme()

    // Image wallpaper: decode/refresh is idempotent and off the main thread. While the
    // wallpaper is active its extracted accent drives the GLOBAL palette (tiles included),
    // matching "图片壁纸生效后磁贴调色板用提取色".
    LaunchedEffect(MetroWallpaper.version, MetroUiState.version) {
        MetroWallpaper.ensureLoaded()
    }
    val wallpaperArt = MetroWallpaper.art
    val wallpaperActive = MetroWallpaper.active

    val colors = if (Theme.selected == Theme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val primary = Color(MetroColors.themePrimary(context))
        val secondary = context.themeColor(MaterialR.attr.colorSecondary)
        val background = context.themeColor(MaterialR.attr.colorSurface)
        val surfaceVariant = context.themeColor(MaterialR.attr.colorSurfaceVariant)
        val onPrimary = Color(MetroColors.themeOnPrimary(context))
        val onSurface = context.themeColor(MaterialR.attr.colorOnSurface)
        val onSurfaceVariant = context.themeColor(MaterialR.attr.colorOnSurfaceVariant)
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
    val wallpaperAccent = if (wallpaperActive && wallpaperArt != null) {
        MetroAccent(wallpaperArt.accent, wallpaperArt.onAccent)
    } else if (
        Config.metroWallpaperEnabled && Config.metroWallpaperUri.isNotBlank() &&
        !MetroWallpaper.loadFailed && Config.metroWallpaperAccent != 0
    ) {
        // Still decoding: the cached extracted accent keeps the palette stable from the very
        // first frame after a restart. A real load failure clears this and falls back to the
        // regular theme.
        MetroAccent(
            Color(Config.metroWallpaperAccent),
            Color(MetroColors.readableOn(Config.metroWallpaperAccent)),
        )
    } else null
    val themedColors = if (wallpaperAccent != null) {
        // The image wallpaper IS the selected theme while enabled: every MD3 accent
        // (primary/secondary/tertiary — buttons, switches, sliders, headers, nav chrome)
        // follows the extracted colour, so no packaged-theme colour can leak through.
        colors.copy(
            primary = wallpaperAccent.color,
            onPrimary = wallpaperAccent.onColor,
            primaryContainer = wallpaperAccent.color.copy(alpha = 0.22f),
            onPrimaryContainer = if (dark) Color.White else Color.Black,
            secondary = wallpaperAccent.color,
            onSecondary = wallpaperAccent.onColor,
            secondaryContainer = wallpaperAccent.color.copy(alpha = 0.18f),
            onSecondaryContainer = if (dark) Color.White else Color.Black,
            tertiary = wallpaperAccent.color,
            onTertiary = wallpaperAccent.onColor,
            tertiaryContainer = wallpaperAccent.color.copy(alpha = 0.16f),
            onTertiaryContainer = if (dark) Color.White else Color.Black,
            background = Color.Transparent,
            onBackground = if (dark) Color.White else Color.Black,
            surface = Color.Transparent,
            onSurface = if (dark) Color.White else Color.Black,
            surfaceVariant = wallpaperAccent.color.copy(alpha = 0.12f),
            onSurfaceVariant = if (dark) Color.White.copy(alpha = 0.82f)
            else Color.Black.copy(alpha = 0.78f),
            outline = wallpaperAccent.color.copy(alpha = 0.62f),
            outlineVariant = wallpaperAccent.color.copy(alpha = 0.32f),
            surfaceTint = wallpaperAccent.color,
        )
    } else colors
    if (wallpaperAccent != null) {
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val palette = if (wallpaperAccent != null) {
        uniformMetroPalette(wallpaperAccent)
    } else if (Theme.selected == Theme.Custom) {
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
        MaterialTheme(colorScheme = themedColors, content = content)
    }
}
