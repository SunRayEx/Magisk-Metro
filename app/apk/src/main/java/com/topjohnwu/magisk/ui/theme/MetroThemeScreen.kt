package com.topjohnwu.magisk.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.ui.theme.MetroColors.previewPalette

/**
 * The Metro theme chooser. Every preview is sampled through [MetroColors.previewPalette], so a
 * row always shows the exact colors its theme will paint on the Start board, even while a
 * wallpaper or custom theme is currently selected.
 *
 * Selecting a theme writes the same preference keys the classic DataBinding APK wrote, then
 * recreates the activity so [com.topjohnwu.magisk.ui.MainActivity.onCreate] re-applies the
 * theme and the whole UI re-inflates with the new palette.
 */
@Composable
fun MetroThemeScreen(
    onThemeApplied: () -> Unit,
    onEditCustomColors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = LocalMetroPalette.current.settings

    var selected by remember { mutableStateOf(Theme.selected) }
    val themes = remember { Theme.displayValues }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Text(
                text = stringResource(com.topjohnwu.magisk.core.R.string.section_theme),
                color = accent.color,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(themes, key = { it.name }) { theme ->
            ThemeRow(
                theme = theme,
                isSelected = theme == selected,
                onClick = {
                    if (theme == Theme.Custom) {
                        // The Custom theme *is* its colors: tapping the row always opens the
                        // editor, selecting the theme first so the preview matches.
                        if (!Config.metroCustomTheme) {
                            Config.metroCustomTheme = true
                            Config.dynamicColor = false
                            selected = Theme.Custom
                        }
                        onEditCustomColors()
                        return@ThemeRow
                    }
                    if (theme == selected) return@ThemeRow
                    // Same persistence order as the old ThemeViewModel.saveTheme.
                    Config.metroCustomTheme = theme.isCustom
                    Config.dynamicColor = theme.isDynamic
                    if (!theme.isDynamic && !theme.isCustom) {
                        Config.themeOrdinal = theme.ordinal
                    }
                    selected = theme
                    onThemeApplied()
                },
            )
        }
    }
}

@Composable
private fun ThemeRow(
    theme: Theme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // A diagonal gradient of the theme's own role palette, sampled from a themed context so a
    // wallpaper/custom selection never bleeds into the other previews.
    val gradient = remember(theme) {
        val colors = previewPalette(context, theme).map { Color(it) }
        if (colors.size >= 2) Brush.linearGradient(colors) else Brush.linearGradient(listOf(colors.first(), colors.first()))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(gradient),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = theme.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (theme.isDynamic || theme.isCustom) {
                Text(
                    text = stringResource(R.string.metro_theme_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        if (theme.isCustom) {
            // The row opens the color editor, so advertise it with an edit affordance.
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
