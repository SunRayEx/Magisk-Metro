package com.topjohnwu.magisk.ui.module

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.PersistentModules
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.ui.anim.MetroFlipItem
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.home.MetroUiState
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.theme.MetroAccent
import java.text.DateFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Installed(val id: String, val name: String, val version: String, val versionCode: Int)

@Composable
internal fun PersistentScreen(visible: Boolean) {
    val accent = LocalMetroPalette.current.modules
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(MetroUiState.version) }

    val supported = PersistentModules.supported()

    val manifest by produceState<PersistentModulesResult>(initialValue = PersistentModulesResult(null, emptyList()), revision) {
        value = withContext(Dispatchers.IO) {
            val declaration = PersistentModules.fetch()
            val installed = if (Info.env.isActive) {
                runCatching { LocalModule.installed() }.getOrDefault(emptyList())
                    .map { Installed(it.id, it.name, it.version, it.versionCode) }
            } else emptyList()
            PersistentModulesResult(declaration, installed)
        }
    }

    // Serialize writes to the shared manifest and always release the UI after a failure.
    fun runOperation(applied: Boolean = false, operation: () -> com.topjohnwu.magisk.core.PersistentResult) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { operation() }
                if (!result.ok) {
                    reportFailure(context, result)
                } else if (applied) {
                    android.widget.Toast.makeText(
                        context,
                        R.string.metro_persistent_applied,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(context, com.topjohnwu.magisk.core.PersistentResult(false, null, e.message))
            } finally {
                revision++
                busy = false
            }
        }
    }

    val pinned = manifest.declaration?.modules.orEmpty()
    val unpinned = remember(manifest) {
        val pinnedIds = pinned.mapTo(HashSet()) { it.id }
        manifest.installed.filterNot { it.id in pinnedIds }
    }

    // The parent pivot owns the title, background and page transition.
    Column(modifier = Modifier.fillMaxSize()) {
        manifest.declaration?.appliedAt?.let { applied ->
            Text(
                text = stringResource(
                    R.string.metro_persistent_last_apply,
                    DateFormat.getDateTimeInstance().format(applied),
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, end = 28.dp, bottom = 4.dp),
            )
        } ?: Text(
            text = stringResource(R.string.metro_persistent_never),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, end = 28.dp, bottom = 4.dp),
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var showInfo by remember { mutableStateOf(false) }
            if (showInfo) {
                MagiskDialog(
                    onDismissRequest = { showInfo = false },
                    title = stringResource(R.string.metro_persistent_info_title),
                    confirmText = stringResource(android.R.string.ok),
                    onConfirm = { showInfo = false },
                ) {
                    Text(text = stringResource(R.string.metro_persistent_info))
                }
            }
            MetroActionButton(
                text = stringResource(R.string.metro_persistent_apply),
                accent = accent,
                enabled = supported && !busy,
            ) {
                if (!com.topjohnwu.magisk.core.Config.metroPersistentModules) {
                    // Not configured yet: tell the user to enable it in Settings first.
                    android.widget.Toast.makeText(
                        context,
                        R.string.metro_persistent_goto_settings,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    return@MetroActionButton
                }
                runOperation(applied = true) { PersistentModules.apply() }
            }
            Spacer(Modifier.width(8.dp))
            MetroActionButton(
                text = stringResource(R.string.metro_persistent_info_title),
                accent = accent,
                enabled = true,
                onClick = { showInfo = true },
            )
        }

        if (!supported) {
            Text(
                text = stringResource(R.string.metro_persistent_unavailable),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (pinned.isNotEmpty()) {
                item {
                    MetroSectionHeader(stringResource(R.string.metro_persistent_pinned), accent)
                }
            }
            itemsIndexed(pinned, key = { _, entry -> "pinned:${entry.id}" }) { index, entry ->
                MetroFlipItem(index = index, visible = visible) {
                    PersistentRow(
                        title = entry.name,
                        subtitle = listOf(
                            entry.id,
                            if (entry.version.isNotEmpty())
                                stringResource(R.string.metro_persistent_version, entry.version, entry.versionCode)
                            else ""
                        ).filter(String::isNotEmpty).joinToString(" · "),
                        checked = entry.enabled,
                        accent = accent,
                        onToggle = { checked ->
                            runOperation { PersistentModules.toggle(entry.id, checked) }
                        },
                        onUnpin = {
                            runOperation { PersistentModules.unpin(entry.id) }
                        },
                    )
                }
            }

            item { MetroSectionHeader(stringResource(R.string.metro_persistent_available), accent) }
            if (unpinned.isEmpty() && pinned.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.metro_persistent_empty),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            itemsIndexed(unpinned, key = { _, module -> "available:${module.id}" }) { index, module ->
                MetroFlipItem(index = index + pinned.size, visible = visible) {
                    PersistentRow(
                        title = module.name,
                        subtitle = module.id,
                        checked = false,
                        accent = accent,
                        onToggle = { checked ->
                            if (!checked) return@PersistentRow
                            runOperation { PersistentModules.snapshot(module.id) }
                        },
                        onUnpin = null,
                    )
                }
            }
        }
    }
}

private data class PersistentModulesResult(
    val declaration: com.topjohnwu.magisk.core.PersistentManifest?,
    val installed: List<Installed>,
)

private fun reportFailure(context: android.content.Context, result: com.topjohnwu.magisk.core.PersistentResult) {
    val text = context.getString(R.string.metro_persistent_failed) +
        (result.message?.let { ": $it" } ?: "")
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
}

@Composable
private fun MetroSectionHeader(text: String, accent: MetroAccent) {
    Text(
        text = text,
        color = accent.color,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 28.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun MetroActionButton(
    text: String,
    accent: MetroAccent,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(if (enabled) accent.color else accent.color.copy(alpha = 0.28f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = accent.onColor.copy(alpha = if (enabled) 1f else 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PersistentRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: MetroAccent,
    onToggle: (Boolean) -> Unit,
    onUnpin: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accent.onColor,
                    checkedTrackColor = accent.color,
                    checkedBorderColor = accent.color,
                ),
            )
        }
        if (onUnpin != null) {
            Text(
                text = stringResource(R.string.metro_module_remove),
                color = accent.color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onUnpin),
            )
        }
    }
}
