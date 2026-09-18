package com.topjohnwu.magisk.ui.home

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.ui.anim.MetroEaseOut
import com.topjohnwu.magisk.ui.anim.MetroFlipItem
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.component.SettingsArrow
import com.topjohnwu.magisk.ui.component.SmallTitle
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.theme.MetroAccentRole
import com.topjohnwu.magisk.ui.theme.MetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

private enum class PickerMode { App, Group, Magisk }

/** Built-in Start tiles that can be shown or hidden from this screen. */
private data class BuiltInTile(
    val id: String,
    val labelRes: Int,
    val protected: Boolean = false,
)

private object BuiltInTiles {
    val all = listOf(
        BuiltInTile(MetroTileLayout.Magisk, R.string.metro_install),
        BuiltInTile(MetroTileLayout.Modules, R.string.metro_modules),
        BuiltInTile(MetroTileLayout.Apps, R.string.metro_apps),
        // Hiding Settings would lock the user out of this screen, so it stays on.
        BuiltInTile(MetroTileLayout.Settings, R.string.metro_settings, protected = true),
        BuiltInTile(MetroTileLayout.Logs, R.string.metro_logs),
        BuiltInTile(MetroTileLayout.Contributors, R.string.metro_contributors),
        BuiltInTile(MetroTileLayout.Sponsor, R.string.metro_sponsor),
        BuiltInTile(MetroTileLayout.Support, R.string.metro_support),
        BuiltInTile(MetroTileLayout.Donate, R.string.metro_donate),
    )
}

@Composable
internal fun EditTilesScreen() {
    val accent = LocalMetroPalette.current.settings
    val context = LocalContext.current
    var pickerMode by remember { mutableStateOf<PickerMode?>(null) }

    // Full Metro entrance: the page slides in from the right.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 260, easing = MetroEaseOut))
    }

    // Deleting, adding and built-in toggles all bump MetroUiState, which this list reads
    // directly, so every edit in this screen (or anywhere else) refreshes the rows below.
    val customTiles = remember(MetroUiState.version) { MetroCustomTiles.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .graphicsLayer {
                alpha = entrance.value
                translationX = (1f - entrance.value) * 120f * density
            },
    ) {
        Text(
            text = stringResource(R.string.metro_edit_tiles_title),
            color = accent.color,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, end = 28.dp, top = 18.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.metro_edit_tiles_summary),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, end = 28.dp, bottom = 4.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item { SmallTitle(text = stringResource(R.string.metro_edit_tiles_builtin)) }
            itemsIndexed(BuiltInTiles.all, key = { _, it -> it.id }) { index, tile ->
                MetroFlipItem(index = index) {
                    BuiltInTileRow(tile = tile, accent = accent)
                }
            }
            item { SmallTitle(text = stringResource(R.string.metro_edit_tiles_custom)) }
            if (customTiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.metro_edit_tiles_no_custom),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            } else {
                items(customTiles, key = { it.id }) { tile ->
                    CustomTileRow(
                        tile = tile,
                    )
                }
            }
            item { SmallTitle(text = stringResource(R.string.metro_edit_tiles_add)) }
            item {
                SettingsArrow(
                    title = stringResource(R.string.metro_edit_tiles_add_app),
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = accent.color) },
                    onClick = { pickerMode = PickerMode.App },
                )
            }
            item {
                SettingsArrow(
                    title = stringResource(R.string.metro_edit_tiles_add_group),
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = accent.color) },
                    onClick = { pickerMode = PickerMode.Group },
                )
            }
            item {
                SettingsArrow(
                    title = stringResource(R.string.metro_edit_tiles_add_setting),
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = accent.color) },
                    onClick = { pickerMode = PickerMode.Magisk },
                )
            }
        }
    }

    when (val mode = pickerMode) {
        PickerMode.App, PickerMode.Group -> AppPicker(
            multiSelect = mode == PickerMode.Group,
            onDismiss = { pickerMode = null },
            onConfirm = { selection ->
                pickerMode = null
                if (selection.isEmpty()) return@AppPicker
                val color = tileColor(context)
                if (selection.size == 1) {
                    val app = selection.first()
                    MetroCustomTiles.add(app.packageName, app.label, app.label, color)
                } else {
                    val first = selection.first()
                    MetroCustomTiles.add(
                        packageName = first.packageName,
                        title = first.label,
                        ticker = selection.joinToString("\n") { it.label },
                        color = color,
                        groupMembers = selection.map { it.packageName },
                    )
                }
            },
        )
        PickerMode.Magisk -> MagiskShortcutPicker(
            onDismiss = { pickerMode = null },
            onPick = { destination ->
                pickerMode = null
                MetroCustomTiles.add(
                    packageName = destination.pseudoPackage,
                    title = context.getString(destination.labelRes),
                    ticker = "",
                    color = tileColor(context),
                )
            },
        )
        null -> {}
    }
}

@Composable
private fun BuiltInTileRow(tile: BuiltInTile, accent: com.topjohnwu.magisk.ui.theme.MetroAccent) {
    // Toggle/restore from anywhere bumps the global tile version; reading it here makes the
    // switch flip back even though the actual boolean lives in Config.
    MetroUiState.version
    val context = LocalContext.current
    val visible = MetroTileLayout.isVisible(tile.id)
    ListItem(
        headlineContent = { Text(text = stringResource(tile.labelRes), style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                text = stringResource(if (visible) R.string.metro_edit_tiles_visible else R.string.metro_edit_tiles_hidden),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            Switch(
                checked = visible,
                enabled = !tile.protected,
                onCheckedChange = { checked ->
                    if (checked) MetroTileLayout.show(tile.id) else MetroTileLayout.hide(tile.id)
                    // Hidden tiles leave the board, so the geometry must be re-packed.
                    MetroUiState.invalidate()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = accent.color),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    if (tile.protected) {
        Text(
            text = stringResource(R.string.metro_edit_tiles_lockout),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun CustomTileRow(tile: MetroCustomTile) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val pm = context.packageManager
    val label = remember(tile.id) {
        when {
            MetroMagiskShortcuts.isShortcut(tile.packageName) -> {
                MetroMagiskShortcuts.destinations
                    .firstOrNull { it.pseudoPackage == tile.packageName }
                    ?.let { context.getString(it.labelRes) }
            }
            else -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(tile.packageName, 0)).toString() }.getOrNull()
        } ?: tile.packageName
    }

    if (confirmDelete) {
        MagiskDialog(
            onDismissRequest = { confirmDelete = false },
            title = tile.title.takeIf { it.isNotBlank() } ?: label,
            confirmText = stringResource(R.string.metro_edit_tiles_delete),
            onConfirm = {
                confirmDelete = false
                MetroCustomTiles.remove(tile.id)
                MetroUiState.invalidate()
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { confirmDelete = false },
        ) {
            Text(text = stringResource(R.string.metro_edit_tiles_delete))
        }
    }

    ListItem(
        headlineContent = {
            Text(
                text = tile.title.takeIf { it.isNotBlank() } ?: label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val memberInfo = if (tile.groupMembers.size > 1) " · ${tile.groupMembers.size} apps" else ""
            Text(
                text = label + memberInfo,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.metro_edit_tiles_delete),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(22.dp)
                    .clickable { confirmDelete = true },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AppPicker(
    multiSelect: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<InstalledApp>) -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = AppContext.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != AppContext.packageName }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { info ->
                    InstalledApp(
                        packageName = info.packageName,
                        label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                        icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }
    }

    val selection = remember { mutableStateListOf<InstalledApp>() }
    MagiskDialog(
        onDismissRequest = onDismiss,
        dismissOnClickOutside = false,
        title = stringResource(if (multiSelect) R.string.metro_edit_tiles_pick_apps else R.string.metro_edit_tiles_pick_app),
        confirmText = stringResource(R.string.metro_edit_tiles_confirm),
        onConfirm = { onConfirm(selection.toList()) },
        confirmEnabled = selection.isNotEmpty(),
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss,
    ) {
        if (apps.isEmpty()) {
            Text(
                text = stringResource(R.string.metro_edit_tiles_no_apps),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    val selected = app in selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (multiSelect) {
                                    if (selected) selection.remove(app) else if (selection.size < 9) selection.add(app)
                                } else {
                                    onConfirm(listOf(app))
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        if (multiSelect) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.size(22.dp),
                            )
                            Box(modifier = Modifier.size(8.dp)) {}
                        }
                        app.icon?.let {
                            Image(
                                painter = rememberDrawablePainter(it),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                            Box(modifier = Modifier.size(10.dp)) {}
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = app.label,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MagiskShortcutPicker(
    onDismiss: () -> Unit,
    onPick: (MetroMagiskShortcuts.Destination) -> Unit,
) {
    MagiskDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.metro_edit_tiles_add_setting),
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(MetroMagiskShortcuts.destinations, key = { it.key }) { destination ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(destination.labelRes),
                            fontSize = 15.sp,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(destination) },
                )
            }
        }
    }
}

private fun tileColor(context: android.content.Context): String {
    val argb = MetroColors.accent(context, MetroAccentRole.APPS)
    return String.format("#%08X", argb)
}
