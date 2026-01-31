package com.MagisKube.magisk.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.MagisKube.magisk.R
import com.MagisKube.magisk.core.Config

enum class Theme(
    val themeName: String,
    val themeRes: Int
) {

    Piplup(
        themeName = "Piplup",
        themeRes = R.style.ThemeFoundationMD2_Piplup
    ),
    PiplupAmoled(
        themeName = "AMOLED",
        themeRes = R.style.ThemeFoundationMD2_Amoled
    ),
    Rayquaza(
        themeName = "Rayquaza",
        themeRes = R.style.ThemeFoundationMD2_Rayquaza
    ),
    Zapdos(
        themeName = "Zapdos",
        themeRes = R.style.ThemeFoundationMD2_Zapdos
    ),
    Charmeleon(
        themeName = "Charmeleon",
        themeRes = R.style.ThemeFoundationMD2_Charmeleon
    ),
    Mew(
        themeName = "Mew",
        themeRes = R.style.ThemeFoundationMD2_Mew
    ),
    Salamence(
        themeName = "Salamence",
        themeRes = R.style.ThemeFoundationMD2_Salamence
    ),
    Fraxure(
        themeName = "Fraxure (Legacy)",
        themeRes = R.style.ThemeFoundationMD2_Fraxure
    );

    val isSelected get() = Config.themeOrdinal == ordinal

    companion object {
        val selected get() = values().getOrNull(Config.themeOrdinal) ?: Piplup

        fun dynamicColorScheme(dominantColor: Int): ColorScheme {
            val primary = Color(dominantColor)
            val onPrimary = if (isColorLight(primary)) Color.Black else Color.White
            val background = primary.copy(alpha = 0.1f)
            val onBackground = if (isColorLight(background)) Color.Black else Color.White
            val surface = primary.copy(alpha = 0.2f)
            val onSurface = if (isColorLight(surface)) Color.Black else Color.White

            return ColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = primary,
                onSecondary = onPrimary,
                tertiary = primary,
                onTertiary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                error = Color.Red,
                onError = Color.White
            )
        }

        private fun isColorLight(color: Color): Boolean {
            val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue) / 255
            return luminance > 0.5
        }
    }

}
