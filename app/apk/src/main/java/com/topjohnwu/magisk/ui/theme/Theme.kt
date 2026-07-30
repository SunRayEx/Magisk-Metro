package com.topjohnwu.magisk.ui.theme

import android.os.Build
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.Config

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
    ),
    Default(
        themeName = "Default",
        themeRes = R.style.ThemeFoundationMD2_Default
    ),
    Dynamic(
        themeName = "Wallpaper colors",
        themeRes = R.style.ThemeFoundationMD2_Default
    );

    val isDynamic get() = this == Dynamic
    val isSelected get() = selected == this

    companion object {
        val selected
            get() = if (Config.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Dynamic
            } else {
                values().getOrNull(Config.themeOrdinal)?.takeUnless { it == Dynamic } ?: Default
            }

        val displayValues
            get() = buildList {
                add(Default)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Dynamic)
                addAll(values().filterNot { it == Default || it == Dynamic })
            }
    }

}
