package com.topjohnwu.magisk.ui.theme

import android.content.Intent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.topjohnwu.magisk.ui.home.MetroUiState
import com.topjohnwu.magisk.ui.metro.metroPageShell
import com.topjohnwu.magisk.ui.theme.MetroColors.previewPalette
import kotlin.math.roundToInt

/**
 * The Metro theme chooser. Every preview is sampled through [MetroColors.previewPalette], so a
 * row always shows the exact colors its theme will paint on the Start board, even while a
 * wallpaper or custom theme is currently selected.
 *
 * Selecting a theme writes the same preference keys the classic DataBinding APK wrote, then
 * recreates the activity so [com.topjohnwu.magisk.ui.MainActivity.onCreate] re-applies the
 * theme and the whole UI re-inflates with the new palette.
 *
 * The image wallpaper section above the list is independent of the packaged themes: it
 * stores a persisted SAF Uri plus a blur preference, and while it is enabled its extracted
 * accent overrides the palette (see [MagisKubeTheme]).
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

    // SAF image picker: the returned grant is persisted so the URI survives reboots.
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            Config.metroWallpaperUri = uri.toString()
            Config.metroWallpaperEnabled = true
            selected = Theme.Wallpaper
            MetroWallpaper.invalidate()
            MetroUiState.invalidate()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .metroScreenBackground()
            .metroPageShell(),
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
        item(key = "wallpaper") {
            MetroWallpaperSection(onPickImage = { wallpaperPicker.launch(arrayOf("image/*")) })
        }
        items(themes, key = { it.name }) { theme ->
            ThemeRow(
                theme = theme,
                // While the image wallpaper is enabled it IS the selection: no packaged
                // theme row stays checked.
                isSelected = theme == selected,
                onClick = {
                    if (theme == Theme.Custom) {
                        // The Custom theme *is* its colors: tapping the row always opens the
                        // editor, selecting the theme first so the preview matches.
                        if (Config.metroWallpaperEnabled) {
                            Config.metroWallpaperEnabled = false
                            MetroWallpaper.invalidate()
                            MetroUiState.invalidate()
                        }
                        if (!Config.metroCustomTheme) {
                            Config.metroCustomTheme = true
                            Config.dynamicColor = false
                        }
                        selected = Theme.Custom
                        onEditCustomColors()
                        return@ThemeRow
                    }
                    if (theme == selected) return@ThemeRow
                    // Picking a packaged theme drops the image wallpaper (they are
                    // mutually exclusive selections).
                    if (Config.metroWallpaperEnabled) {
                        Config.metroWallpaperEnabled = false
                        MetroWallpaper.invalidate()
                        MetroUiState.invalidate()
                    }
                    // Same persistence order as the old ThemeViewModel.saveTheme.
                    Config.metroCustomTheme = theme.isCustom
                    Config.dynamicColor = theme.isDynamic
                    selected = theme
                    if (!theme.isDynamic && !theme.isCustom) {
                        Config.themeOrdinal = theme.ordinal
                    }
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

/**
 * Image wallpaper management: pick/replace the photo (SAF, grant persisted), toggle it
 * on/off, tune the tile glass blur and remove it. Everything reacts immediately through
 * [MetroWallpaper]'s snapshot state; no activity recreation needed.
 */
@Composable
private fun MetroWallpaperSection(onPickImage: () -> Unit) {
    // Config reads here are plain preferences; keying the section on MetroUiState.version
    // makes every write below recompose this section immediately.
    val uiVersion = MetroUiState.version
    val wallpaperActive = MetroWallpaper.active
    val wallpaperArt = MetroWallpaper.art
    val loadFailed = MetroWallpaper.loadFailed
    val hasImage = Config.metroWallpaperUri.isNotBlank()
    // Draft blur while dragging; committed (and re-blurred) only when released.
    var blurDraft by remember { mutableStateOf<Float?>(null) }

    @Composable
    fun SectionRow(
        title: String,
        summary: String? = null,
        trailing: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        color = if (loadFailed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            trailing?.invoke()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionRow(
            title = stringResource(R.string.metro_wallpaper_title),
            summary = when {
                loadFailed -> stringResource(R.string.metro_wallpaper_failed)
                wallpaperActive -> stringResource(R.string.metro_wallpaper_summary)
                hasImage -> stringResource(R.string.metro_wallpaper_summary)
                else -> stringResource(R.string.metro_wallpaper_summary)
            },
            trailing = {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (wallpaperActive && wallpaperArt != null) wallpaperArt.accent
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = if (wallpaperActive && wallpaperArt != null) wallpaperArt.onAccent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            onClick = onPickImage,
        )
        if (hasImage) {
            SectionRow(
                title = stringResource(R.string.metro_wallpaper_enabled),
                trailing = {
                    Switch(
                        checked = Config.metroWallpaperEnabled,
                        onCheckedChange = { enabled ->
                            Config.metroWallpaperEnabled = enabled
                            MetroWallpaper.invalidate()
                            MetroUiState.invalidate()
                        },
                    )
                },
            )
        }
        if (hasImage && Config.metroWallpaperEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.metro_wallpaper_blur),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // Discrete stops read as off/low/medium/high(+); each stop maps to one
                // pre-blurred bitmap so the glass never re-renders mid-drag.
                Slider(
                    value = blurDraft ?: MetroWallpaper.blurLevel.toFloat(),
                    onValueChange = { blurDraft = it },
                    onValueChangeFinished = {
                        blurDraft?.let { draft ->
                            Config.metroWallpaperBlur = draft.roundToInt().coerceIn(0, 100)
                            MetroWallpaper.invalidate()
                        }
                        blurDraft = null
                    },
                    valueRange = 0f..100f,
                    steps = 4,
                )
            }
            SectionRow(
                title = stringResource(R.string.metro_wallpaper_replace),
                onClick = onPickImage,
            )
            SectionRow(
                title = stringResource(R.string.metro_wallpaper_remove),
                trailing = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                },
                onClick = {
                    Config.metroWallpaperUri = ""
                    Config.metroWallpaperEnabled = false
                    MetroWallpaper.invalidate()
                    MetroUiState.invalidate()
                },
            )
        }
    }
}
