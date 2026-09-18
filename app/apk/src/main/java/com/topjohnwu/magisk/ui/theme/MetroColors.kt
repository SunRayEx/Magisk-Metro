package com.topjohnwu.magisk.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Menu
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.R as AppCompatR
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.R as MaterialR
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.topjohnwu.magisk.core.Config
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
    const val TAG_ACCENT_BUTTON = "metroAccentButton"
    const val TAG_ON_ACCENT_FG = "metroOnAccentFg"
    const val TAG_ON_ACCENT_TINT = "metroOnAccentTint"

    /** Solid control sitting inside an accent block: background flips to onAccent, text to accent. */
    const val TAG_INVERSE_BUTTON = "metroInverseButton"

    private const val SOFT_ALPHA = 0x33

    /** Stable blue used by the theme chooser for the wallpaper-colour preview. */
    const val THEME_PREVIEW_BLUE = 0xFF4EAFF5.toInt()

    /**
     * Keep the dedicated Metro palette for the default theme, while allowing wallpaper and
     * packaged themes to provide their own primary color to every View-based secondary screen.
     */
    private fun usesRoleColors() = Theme.selected == Theme.Default

    private fun roleKey(role: MetroAccentRole) = role.name

    @ColorInt
    private fun defaultAccent(context: Context, role: MetroAccentRole): Int {
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

    @ColorInt
    fun customAccent(context: Context, role: MetroAccentRole): Int {
        val fallback = defaultAccent(context, role)
        if (!Config.metroCustomTheme) return fallback
        val value = Config.metroCustomColors
            .split(';')
            .asSequence()
            .mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size == 2 && parts[0].equals(roleKey(role), true)) parts[1] else null
            }
            .firstOrNull()
            ?.trim()
            ?.let { if (it.startsWith("#")) it else "#$it" }
        return value?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback
    }

    fun customHex(role: MetroAccentRole, context: Context): String =
        String.format("#%06X", 0xFFFFFF and customAccent(context, role))

    fun saveCustomColors(colors: Map<MetroAccentRole, String>) {
        Config.metroCustomColors = colors.entries.joinToString(";") { (role, value) ->
            "${roleKey(role)}=${value.removePrefix("#").uppercase()}"
        }
    }

    @ColorInt
    private fun Context.themeAttrColor(@androidx.annotation.AttrRes attr: Int, @ColorInt fallback: Int): Int =
        obtainStyledAttributes(intArrayOf(attr)).use { it.getColor(0, fallback) }

    /**
     * Resolves the first attribute that is actually set in the current theme. Library-merged
     * attrs (colorPrimary lives in appcompat AND material) must all be probed: depending on
     * resource merging exactly one ID is populated by the theme styles, and probing only one
     * previously made every packaged theme preview fall back to dark gray.
     */
    @ColorInt
    private fun Context.firstThemeAttrColor(
        @androidx.annotation.AttrRes vararg attrs: Int,
        @ColorInt fallback: Int,
    ): Int {
        for (attr in attrs) {
            val color = obtainStyledAttributes(intArrayOf(attr)).use { it.getColor(0, 0) }
            if (color != 0) return color
        }
        return fallback
    }

    /**
     * Wallpaper (Dynamic) themes must not depend on Material's device whitelist: the library
     * gates its dynamic-color overlay on an OEM list that silently excludes many Android 12
     * devices, which previously left every attr-based surface on the packaged default blue.
     * The dynamic palette resources resolve the real wallpaper colors on all S+ devices.
     */
    private fun dynamicPrimary(context: Context): Int {
        val dark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return ContextCompat.getColor(
            context,
            if (dark) MaterialR.color.m3_sys_color_dynamic_dark_primary
            else MaterialR.color.m3_sys_color_dynamic_light_primary,
        )
    }

    @ColorInt
    private fun packagedPrimary(context: Context): Int =
        context.firstThemeAttrColor(AppCompatR.attr.colorPrimary, fallback = Color.DKGRAY)

    /** Runtime accent for the ACTIVE theme only; previews must use [previewPrimary]. */
    @ColorInt
    fun themePrimary(context: Context): Int {
        if (Theme.selected == Theme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return dynamicPrimary(context)
        }
        return packagedPrimary(context)
    }

    @ColorInt
    private fun Context.themeAttrByName(name: String, @ColorInt fallback: Int): Int {
        val attr = resources.getIdentifier(name, "attr", packageName)
        if (attr == 0) return fallback
        return obtainStyledAttributes(intArrayOf(attr)).use { it.getColor(0, fallback) }
    }

    @ColorInt
    fun themeOnPrimary(context: Context): Int {
        if (Theme.selected == Theme.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            return ContextCompat.getColor(
                context,
                if (dark) MaterialR.color.m3_sys_color_dynamic_dark_on_primary
                else MaterialR.color.m3_sys_color_dynamic_light_on_primary,
            )
        }
        return context.themeAttrByName("colorOnPrimary", Color.WHITE)
    }

    @ColorInt
    fun themeOnSurface(context: Context): Int =
        context.themeAttrColor(MaterialR.attr.colorOnSurface, Color.WHITE)

    @ColorInt
    fun themeOnSurfaceVariant(context: Context): Int =
        context.themeAttrColor(MaterialR.attr.colorOnSurfaceVariant, Color.GRAY)

    @ColorInt
    fun softThemePrimary(context: Context): Int {
        val color = themePrimary(context)
        return Color.argb(SOFT_ALPHA, Color.red(color), Color.green(color), Color.blue(color))
    }

    @ColorInt
    fun accent(context: Context, role: MetroAccentRole): Int {
        if (Theme.selected == Theme.Custom) {
            return customAccent(context, role)
        }
        if (!usesRoleColors()) {
            return themePrimary(context)
        }
        return defaultAccent(context, role)
    }

    /**
     * Foreground color to be used *on top of* [accent]. Metro tiles use flat fills, so instead of
     * hardcoding black we pick whichever of black/white stays readable. That keeps custom themes
     * and wallpaper colors legible without any per-theme tables.
     */
    @ColorInt
    fun onAccent(context: Context, role: MetroAccentRole): Int {
        if (Theme.selected == Theme.Custom) {
            return readableOn(accent(context, role))
        }
        if (!usesRoleColors()) {
            return themeOnPrimary(context)
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
     * The canonical preview palette for a theme, sampled through the exact same pipeline the
     * running UI uses ([accent]); previews can therefore never drift from the real tile colors.
     * For the Metro Default and Custom themes the full seven-role palette is returned (the
     * Start board is multi-color); for wallpaper and packaged themes every role shares that
     * theme's primary, so the palette degenerates to a single (possibly two-tone) accent.
     */
    /** The themed context a theme's preview must be sampled from, independent of the
     * currently selected theme (selecting wallpaper colors must not bleed into previews). */
    fun previewThemeContext(context: Context, theme: Theme): Context = when (theme) {
        Theme.Dynamic ->
            // Direct overlay, not DynamicColors.wrapContextIfAvailable: the library's
            // OEM whitelist must not gate the preview either.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.view.ContextThemeWrapper(
                    context,
                    MaterialR.style.ThemeOverlay_Material3_DynamicColors_DayNight,
                )
            } else context
        else -> theme.takeIf { it != Theme.Default && it != Theme.Custom }
            ?.let { android.view.ContextThemeWrapper(context, it.themeRes) }
            ?: context
    }

    fun previewPalette(context: Context, theme: Theme): List<Int> {
        val themed = previewThemeContext(context, theme)
        val resolve: (MetroAccentRole) -> Int = when (theme) {
            Theme.Custom -> { role -> customAccent(themed, role) }
            Theme.Default -> { role -> defaultAccent(themed, role) }
            Theme.Dynamic -> { _ -> dynamicPrimary(context) }
            else -> { _ -> packagedPrimary(themed) }
        }
        val entries = MetroAccentRole.entries.associateWith(resolve)
        // If every role collapses to one color (wallpaper/packaged themes), blend a darker
        // variant so the gradient still communicates "this is the single accent you will see".
        val distinct = entries.values.distinct()
        return if (distinct.size == 1) {
            val primary = distinct.first()
            listOf(primary, ColorUtils.blendARGB(primary, Color.BLACK, 0.22f))
        } else {
            MetroAccentRole.entries.map { entries.getValue(it) }
        }
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

    /** Applies the same role accent to action-bar menu icons as to the page content. */
    fun applyMenuAccent(menu: Menu, context: Context, role: MetroAccentRole) {
        val color = accent(context, role)
        for (index in 0 until menu.size()) {
            menu.getItem(index).icon?.mutate()?.setTint(color)
        }
    }

    private fun paint(view: View, accent: Int, onAccent: Int, soft: Int) {
        when (view.tag as? String) {
            TAG_ACCENT_BG -> setBackgroundColor(view, accent)
            TAG_ACCENT_BG_SOFT -> view.setBackgroundColor(soft)
            TAG_ACCENT_FG -> {
                (view as? TextView)?.setTextColor(accent)
                (view as? MaterialButton)?.iconTint = ColorStateList.valueOf(accent)
            }
            TAG_ACCENT_TINT -> (view as? ImageView)?.setColorFilter(accent)
            TAG_ON_ACCENT_FG -> (view as? TextView)?.setTextColor(onAccent)
            TAG_ON_ACCENT_TINT -> (view as? ImageView)?.setColorFilter(onAccent)
            TAG_ACCENT_BUTTON -> {
                setBackgroundColor(view, accent)
                (view as? TextView)?.setTextColor(onAccent)
                (view as? MaterialButton)?.iconTint = ColorStateList.valueOf(onAccent)
                (view as? ImageView)?.setColorFilter(onAccent)
            }
            TAG_INVERSE_BUTTON -> {
                setBackgroundColor(view, onAccent)
                (view as? TextView)?.setTextColor(accent)
                (view as? MaterialButton)?.iconTint = ColorStateList.valueOf(accent)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                paint(view.getChildAt(i), accent, onAccent, soft)
            }
        }
    }

    private fun setBackgroundColor(view: View, @ColorInt color: Int) {
        if (view is MaterialButton || view is FloatingActionButton) {
            view.backgroundTintList = ColorStateList.valueOf(color)
        } else {
            view.setBackgroundColor(color)
        }
    }
}
