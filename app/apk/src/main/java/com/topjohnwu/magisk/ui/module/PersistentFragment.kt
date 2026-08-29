package com.topjohnwu.magisk.ui.module

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.PersistentModules
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.databinding.FragmentPersistentBinding
import com.topjohnwu.magisk.ui.anim.MetroEaseOut
import com.topjohnwu.magisk.ui.anim.MetroFlipItem
import com.topjohnwu.magisk.ui.home.MetroUiState
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.theme.MagisKubeTheme
import com.topjohnwu.magisk.ui.theme.MetroAccent
import com.topjohnwu.magisk.ui.theme.MetroAccentRole
import com.topjohnwu.magisk.ui.theme.MetroColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersistentViewModel : com.topjohnwu.magisk.arch.BaseViewModel()

/**
 * The persistent-modules management page: a nix-like immutable, reproducible declaration of the
 * user's module set, presented as a plain Metro screen of switches. Toggling a module snapshots
 * it into (or removes it from) the declarative manifest; the native link manager reconciles the
 * device against that manifest on every boot.
 */
class PersistentFragment : BaseFragment<FragmentPersistentBinding>() {

    override val layoutRes = R.layout.fragment_persistent
    override val viewModel by viewModel<PersistentViewModel>()
    override val metroAccentRole = MetroAccentRole.MODULES

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.persistentCompose.setContent {
            MagisKubeTheme {
                PersistentScreen()
            }
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        activity?.title = getString(R.string.metro_persistent)
    }
}

private data class Installed(val id: String, val name: String, val version: String, val versionCode: Int)

@Composable
private fun PersistentScreen() {
    val accent = LocalMetroPalette.current.modules
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(MetroUiState.version) }

    val supported = remember { PersistentModules.supported() }

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

    // Full Metro entrance: the page slides in from the right while its rows flip in.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 260, easing = MetroEaseOut))
    }

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
            text = stringResource(R.string.metro_persistent),
            color = accent.color,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, end = 28.dp, top = 18.dp, bottom = 2.dp),
        )
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
            MetroActionButton(
                text = stringResource(R.string.metro_persistent_apply),
                accent = accent,
                enabled = supported && !busy,
            ) {
                if (!com.topjohnwu.magisk.core.Config.metroPersistentModules) {
                    // Not configured yet: send the user to Settings · Misc to flip the switch.
                    android.widget.Toast.makeText(
                        context,
                        R.string.metro_persistent_goto_settings,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    (activity as? android.app.Activity)?.let { host ->
                        androidx.navigation.Navigation.findNavController(
                            host,
                            R.id.main_nav_host,
                        ).navigate(
                            com.topjohnwu.magisk.MainDirections.actionSectionPivotFragment("SETTINGS"),
                        )
                    }
                    return@MetroActionButton
                }
                busy = true
                scope.launch(Dispatchers.IO) {
                    val result = PersistentModules.apply()
                    withContext(Dispatchers.Main) {
                        if (result.ok) {
                            android.widget.Toast.makeText(
                                context,
                                R.string.metro_persistent_applied,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            reportFailure(context, result)
                        }
                        busy = false
                        revision++
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            MetroActionButton(
                text = stringResource(R.string.metro_persistent_info_title),
                accent = accent,
                enabled = true,
            ) {
                activity?.let { host ->
                    com.topjohnwu.magisk.view.MagiskDialog(
                        host,
                        metroAccentRole = MetroAccentRole.MODULES,
                    ).apply {
                        setTitle(R.string.metro_persistent_info_title)
                        setMessage(R.string.metro_persistent_info)
                        setButton(com.topjohnwu.magisk.view.MagiskDialog.ButtonType.POSITIVE) {
                            text = android.R.string.ok
                        }
                    }.show()
                }
            }
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

        val declaration = manifest.declaration
        val pinned = declaration?.modules.orEmpty()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (pinned.isNotEmpty()) {
                item {
                    MetroSectionHeader(stringResource(R.string.metro_persistent_pinned), accent)
                }
            }
            itemsIndexed(pinned) { index, entry ->
                MetroFlipItem(index = index, visible = true) {
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
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                val result = PersistentModules.toggle(entry.id, checked)
                                withContext(Dispatchers.Main) {
                                    if (!result.ok) reportFailure(context, result)
                                    busy = false
                                    revision++
                                }
                            }
                        },
                        onUnpin = {
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                val result = PersistentModules.unpin(entry.id)
                                withContext(Dispatchers.Main) {
                                    if (!result.ok) reportFailure(context, result)
                                    busy = false
                                    revision++
                                }
                            }
                        },
                    )
                }
            }

            item { MetroSectionHeader(stringResource(R.string.metro_persistent_available), accent) }
            val pinnedIds = pinned.map { it.id }.toSet()
            val unpinned = manifest.installed.filterNot { it.id in pinnedIds }
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
            itemsIndexed(unpinned) { index, module ->
                MetroFlipItem(index = index + pinned.size, visible = true) {
                    PersistentRow(
                        title = module.name,
                        subtitle = module.id,
                        checked = false,
                        accent = accent,
                        onToggle = { checked ->
                            if (!checked) return@PersistentRow
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                val result = PersistentModules.snapshot(module.id)
                                withContext(Dispatchers.Main) {
                                    if (!result.ok) reportFailure(context, result)
                                    busy = false
                                    revision++
                                }
                            }
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
