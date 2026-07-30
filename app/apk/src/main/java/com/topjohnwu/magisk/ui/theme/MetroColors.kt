package com.topjohnwu.magisk.ui.theme

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import com.topjohnwu.magisk.R

/**
 * Single source of truth for Metro accent colors on the View side.
 *
 * The resolution rules are intentionally identical to [MagisKubeTheme] so that Compose screens
 * (home / contributors) and the classic DataBinding screens never drift apart:
 *
 *  - dynamic color enabled (Android 12+) -> follow the wallpaper derived `colorPrimary`
 *  - "Default" Metro theme               -> the seven role specific Metro colors
 *  - any other packaged theme            -> that theme's `colorPrimary`
 */
object MetroColors {

    /** Tag markers understood by [applyMetroAccent]. */
    const val TAG_ACCENT_BG = "metroAccentBg"
    const val TAG_ACCENT_BG_SOFT = "metroAccentBgSoft"
    const val TAG_ACCENT_FG = "metroAccentFg"
    const val TAG_ACCENT_TINT = "metroAccentTint"
    const val TAG_ON_ACCENT_FG = "metroOnAccentFg"
    const val TAG_ON_ACCENT_TINT = "metroOnAccentTint"

    /** Solid control sitting inside an accent block: background flips to onAccent, text to accent. */
    const val TAG_INVERSE_BUTTON = "metroInverseButton"

    private const val SOFT_ALPHA = 0x33

    /**
     * View-based secondary screens always use the fixed Metro palette so that their
     * background / text / accent never follow wallpaper-derived colors (requirement: the
     * Magisk tile secondary screen is excluded from dynamic theming).
     */
    private fun usesRoleColors() = true

    @ColorInt
    private fun Context.themeAttrColor(name: String, @ColorInt fallback: Int): Int {
        val attr = resources.getIdentifier(name, "attr", packageName)
        if (attr == 0) return fallback
        return obtainStyledAttributes(intArrayOf(attr)).use { it.getColor(0, fallback) }
    }

    @ColorInt
    fun themePrimary(context: Context): Int =
        context.themeAttrColor("colorPrimary", Color.DKGRAY)

    @ColorInt
    fun themeOnPrimary(context: Context): Int =
        context.themeAttrColor("colorOnPrimary", Color.WHITE)

    @ColorInt
    fun accent(context: Context, role: MetroAccentRole): Int {
        if (!usesRoleColors()) {
            return context.themeAttrColor("colorPrimary", Color.DKGRAY)
        }
        val res = when (role) {
            MetroAccentRole.MAGISK -> R.color.metro_accent_magisk
            MetroAccentRole.MODULES -> R.color.metro_accent_modules
            MetroAccentRole.APPS -> R.color.metro_accent_apps
            MetroAccentRole.SETTINGS -> R.color.metro_accent_settings
            MetroAccentRole.CONTRIBUTORS -> R.color.metro_accent_contributors
            MetroAccentRole.LOGS -> R.color.metro_accent_logs
            MetroAccentRole.SPONSOR -> R.color.metro_accent_sponsor
        }
        return ContextCompat.getColor(context, res)
    }

    /**
     * Foreground color to be used *on top of* [accent]. Metro tiles use flat fills, so instead of
     * hardcoding black we pick whichever of black/white stays readable. That keeps custom themes
     * and wallpaper colors legible without any per-theme tables.
     */
    @ColorInt
    fun onAccent(context: Context, role: MetroAccentRole): Int {
        if (!usesRoleColors()) {
            return context.themeAttrColor("colorOnPrimary", Color.WHITE)
        }
        return readableOn(accent(context, role))
    }

    @ColorInt
    fun readableOn(@ColorInt background: Int): Int =
        if (ColorUtils.calculateLuminance(background) > 0.45) Color.BLACK else Color.WHITE

    @ColorInt
    fun softAccent(context: Context, role: MetroAccentRole): Int {
        val color = accent(context, role)
        return Color.argb(SOFT_ALPHA, Color.red(color), Color.green(color), Color.blue(color))
    }

    /**
     * Walks [root] and paints every view that opted in through `android:tag`.
     * Called by the shared fragment plumbing so *every* secondary screen is tinted the same way
     * as the tile the user came from, without duplicating colors in the layouts.
     */
    fun applyMetroAccent(root: View, role: MetroAccentRole) {
        val context = root.context
        val accent = accent(context, role)
        val onAccent = onAccent(context, role)
        val soft = softAccent(context, role)
        paint(root, accent, onAccent, soft)
    }

    private fun paint(view: View, accent: Int, onAccent: Int, soft: Int) {
        when (view.tag as? String) {
            TAG_ACCENT_BG -> view.setBackgroundColor(accent)
            TAG_ACCENT_BG_SOFT -> view.setBackgroundColor(soft)
            TAG_ACCENT_FG -> (view as? TextView)?.setTextColor(accent)
            TAG_ACCENT_TINT -> (view as? ImageView)?.setColorFilter(accent)
            TAG_ON_ACCENT_FG -> (view as? TextView)?.setTextColor(onAccent)
            TAG_ON_ACCENT_TINT -> (view as? ImageView)?.setColorFilter(onAccent)
            TAG_INVERSE_BUTTON -> {
                view.setBackgroundColor(onAccent)
                (view as? TextView)?.setTextColor(accent)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                paint(view.getChildAt(i), accent, onAccent, soft)
            }
        }
    }
}
