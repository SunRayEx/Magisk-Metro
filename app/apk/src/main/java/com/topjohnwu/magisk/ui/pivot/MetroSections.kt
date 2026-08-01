package com.topjohnwu.magisk.ui.pivot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.ui.anim.MetroFlipItem
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.InstallModule
import com.topjohnwu.magisk.ui.module.LocalModuleRvItem
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.settings.BaseSettingsItem
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.PolicyRvItem
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.theme.MetroAccent
import com.topjohnwu.magisk.core.R as CoreR
import kotlinx.coroutines.launch

// ---- Shared Metro primitives -------------------------------------------------

private val SectionPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

@Composable
private fun MetroCentered(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun metroSwitchColors(accent: MetroAccent) = SwitchDefaults.colors(
    checkedThumbColor = accent.onColor,
    checkedTrackColor = accent.color,
    checkedBorderColor = accent.color,
)

@Composable
private fun MetroTextButton(
    text: String,
    accent: MetroAccent,
    enabled: Boolean = true,
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
            color = accent.onColor.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The Windows Phone pivot pattern used inside one primary destination. The primary destination is
 * selected from the home board; horizontal gestures here only move between related secondary
 * views.
 */
@Composable
private fun MetroSubPivot(
    titles: List<String>,
    content: @Composable (page: Int, visible: Boolean) -> Unit,
) {
    val pagerState = rememberPagerState { titles.size }
    val scope = rememberCoroutineScope()
    val headerState = rememberLazyListState()

    // Keep the selected heading comfortably in view whether the page changed from a tap or a
    // content swipe. The pager remains the single source of truth for the active subsection.
    LaunchedEffect(pagerState.currentPage) {
        headerState.animateScrollToItem(pagerState.currentPage)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            state = headerState,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(pagerState.currentPage) {
                    var dragDistance = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            dragDistance += amount
                            change.consume()
                        },
                        onDragEnd = {
                            val target = when {
                                dragDistance <= -28f -> pagerState.currentPage + 1
                                dragDistance >= 28f -> pagerState.currentPage - 1
                                else -> pagerState.currentPage
                            }.coerceIn(0, titles.lastIndex)
                            dragDistance = 0f
                            if (target != pagerState.currentPage) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        },
                        onDragCancel = { dragDistance = 0f },
                    )
                }
                .padding(start = 24.dp, end = 28.dp, top = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            titles.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                item(key = title) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else 0.38f),
                        fontSize = if (selected) 56.sp else 48.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Light,
                        maxLines = 1,
                        modifier = Modifier.clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            content(page, page == pagerState.currentPage)
        }
    }
}

// ---- Apps (Superuser) --------------------------------------------------------

@Composable
fun MagiskSection(vm: HomeViewModel) {
    val accent = LocalMetroPalette.current.magisk
    MetroSubPivot(
        titles = listOf(
            stringResource(R.string.metro_actions),
            stringResource(R.string.metro_status),
        ),
    ) { page, visible ->
        if (page == 0) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    MetroFlipItem(index = 0, visible = visible) {
                        MetroActionRow(
                            title = stringResource(R.string.metro_install_magisk),
                            summary = stringResource(R.string.metro_install_magisk_summary),
                            accent = accent,
                            onClick = vm::onInstallPressed,
                        )
                    }
                }
                item {
                    MetroFlipItem(index = 1, visible = visible) {
                        MetroActionRow(
                            title = stringResource(R.string.metro_update_manager),
                            summary = stringResource(R.string.metro_update_manager_summary),
                            accent = accent,
                            onClick = vm::onManagerPressed,
                        )
                    }
                }
                item {
                    MetroFlipItem(index = 2, visible = visible) {
                        MetroActionRow(
                            title = stringResource(R.string.metro_uninstall_magisk),
                            summary = stringResource(R.string.metro_uninstall_magisk_summary),
                            accent = accent,
                            onClick = vm::onDeletePressed,
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    MetroFlipItem(index = 0, visible = visible) {
                        Column(modifier = Modifier.fillMaxWidth().padding(SectionPadding)) {
                            MetroStatusRow(stringResource(R.string.metro_info_root), vm.rooted, accent)
                            MetroStatusRow(stringResource(R.string.metro_info_zygisk), vm.zygiskEnabled, accent)
                            MetroStatusRow(stringResource(R.string.metro_info_ramdisk), vm.ramdisk, accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetroActionRow(
    title: String,
    summary: String,
    accent: MetroAccent,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(SectionPadding),
    ) {
        Text(title, color = accent.color, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(summary, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun MetroStatusRow(label: String, enabled: Boolean, accent: MetroAccent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
        Text(
            stringResource(if (enabled) R.string.metro_yes else R.string.metro_no),
            color = if (enabled) accent.color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AppsSection(vm: SuperuserViewModel) {
    val accent = LocalMetroPalette.current.apps
    val tick = vm.observeAsTick()
    val loading = remember(tick) { vm.loading }
    val policyRevision = remember(tick) { vm.policyRevision }
    val items by vm.items.asComposeState()

    if (loading && items.isEmpty()) {
        MetroCentered(stringResource(R.string.metro_loading))
        return
    }
    val policies = remember(items, policyRevision) { items.filterIsInstance<PolicyRvItem>() }
    val granted = policies.filter { it.isEnabled }
    val denied = policies.filter { it.item.policy == SuPolicy.DENY }
    val pending = policies.filter { it.item.policy == SuPolicy.QUERY }
    MetroSubPivot(
        titles = listOf(
            stringResource(R.string.metro_granted),
            stringResource(R.string.metro_blocked),
            stringResource(CoreR.string.prompt),
        ),
    ) { page, visible ->
        val pagePolicies = when (page) {
            0 -> granted
            1 -> denied
            else -> pending
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (pagePolicies.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            when (page) {
                                0 -> R.string.metro_no_granted_apps
                                1 -> R.string.metro_no_blocked_apps
                                else -> CoreR.string.superuser_policy_none
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(SectionPadding),
                    )
                }
            } else {
                itemsIndexed(pagePolicies) { index, item ->
                    MetroFlipItem(index = index, visible = visible) { PolicyRow(item, accent) }
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(item: PolicyRvItem, accent: MetroAccent) {
    val tick = item.observeAsTick()
    key(tick) {
        val enabled = item.isEnabled
        val expanded = item.isExpanded
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { item.toggleExpand() }
                .padding(SectionPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.packageName,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { item.isEnabled = it },
                    colors = metroSwitchColors(accent),
                )
            }
            if (expanded) {
                Spacer(Modifier.width(0.dp))
                if (item.showSlider) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetroTextButton(stringResource(CoreR.string.deny), accent) {
                            item.sliderValue = SuPolicy.DENY
                        }
                        MetroTextButton(stringResource(CoreR.string.restrict), accent) {
                            item.sliderValue = SuPolicy.RESTRICT
                        }
                        MetroTextButton(stringResource(CoreR.string.grant), accent) {
                            item.sliderValue = SuPolicy.ALLOW
                        }
                    }
                }
                MetroToggleLine(
                    label = stringResource(R.string.metro_notifications),
                    checked = item.shouldNotify,
                    accent = accent,
                ) { item.toggleNotify() }
                MetroToggleLine(
                    label = stringResource(R.string.metro_logging),
                    checked = item.shouldLog,
                    accent = accent,
                ) { item.toggleLog() }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    MetroTextButton(stringResource(R.string.metro_revoke), accent) { item.revoke() }
                }
            }
        }
    }
}

@Composable
private fun MetroToggleLine(
    label: String,
    checked: Boolean,
    accent: MetroAccent,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = metroSwitchColors(accent),
        )
    }
}

// ---- Logs --------------------------------------------------------------------

@Composable
fun LogsSection(vm: LogViewModel) {
    val accent = LocalMetroPalette.current.logs
    val tick = vm.observeAsTick()
    val loading = remember(tick) { vm.loading }
    val suItems by vm.items.asComposeState()
    val magiskItems by vm.logs.asComposeState()

    MetroSubPivot(
        titles = listOf(
            stringResource(R.string.metro_log_su),
            stringResource(R.string.metro_log_magisk),
        ),
    ) { page, visible ->
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (page == 1) {
                    MetroTextButton(stringResource(R.string.metro_save), accent) { vm.saveMagiskLog() }
                    Spacer(Modifier.width(8.dp))
                }
                MetroTextButton(stringResource(R.string.metro_clear), accent) {
                    if (page == 1) vm.clearMagiskLog() else vm.clearLog()
                }
            }
            if (loading) {
                MetroCentered(stringResource(R.string.metro_loading))
            } else if (page == 1 && magiskItems.isEmpty()) {
                MetroCentered(stringResource(R.string.metro_no_logs))
            } else if (page == 0 && suItems.isEmpty()) {
                MetroCentered(stringResource(R.string.metro_no_su_logs))
            } else {
                if (page == 1) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                    ) {
                        itemsIndexed(magiskItems) { index, line ->
                            MetroFlipItem(index = index, visible = visible) {
                                Text(
                                    text = line.item,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(suItems) { index, log ->
                        MetroFlipItem(index = index, visible = visible) {
                            Text(
                                text = log.info,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

// ---- Modules -----------------------------------------------------------------

@Composable
fun ModulesSection(vm: ModuleViewModel) {
    val accent = LocalMetroPalette.current.modules
    val tick = vm.observeAsTick()
    val loading = remember(tick) { vm.loading }
    val items by vm.items.asComposeState()

    if (loading && items.isEmpty()) {
        MetroCentered(stringResource(R.string.metro_loading))
        return
    }
    if (items.isEmpty()) {
        MetroCentered(stringResource(R.string.metro_no_modules))
        return
    }
    val installModule = items.filterIsInstance<InstallModule>().firstOrNull()
    val modules = items.filterIsInstance<LocalModuleRvItem>()
    val updates = modules.filter { it.showUpdate || it.showNotice }
    MetroSubPivot(
        titles = listOf(
            stringResource(R.string.metro_installed),
            stringResource(R.string.metro_updates),
        ),
    ) { page, visible ->
        val pageModules = if (page == 0) modules else updates
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (page == 0 && installModule != null) {
                item {
                    MetroFlipItem(index = 0, visible = visible) {
                        Row(modifier = Modifier.padding(SectionPadding)) {
                            MetroTextButton(stringResource(R.string.metro_install_module), accent) {
                                vm.installPressed()
                            }
                        }
                    }
                }
            }
            if (pageModules.isEmpty()) {
                item {
                    MetroCentered(stringResource(if (page == 0) R.string.metro_no_modules else R.string.metro_no_updates))
                }
            } else {
                itemsIndexed(pageModules) { index, item ->
                    MetroFlipItem(index = index + if (page == 0 && installModule != null) 1 else 0, visible = visible) {
                        ModuleRow(vm, item, accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(vm: ModuleViewModel, item: LocalModuleRvItem, accent: MetroAccent) {
    val tick = item.observeAsTick()
    key(tick) {
        val module = item.item
        val enabled = item.isEnabled
        val removed = item.isRemoved
        val inactive = removed || !enabled
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (inactive) 0.38f else 1f)
                .padding(SectionPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${module.version} \u00b7 ${module.author}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = if (removed) null else { checked -> item.isEnabled = checked },
                    enabled = !removed,
                    colors = metroSwitchColors(accent),
                )
            }
            if (module.description.isNotEmpty()) {
                Text(
                    text = module.description,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (item.showNotice) {
                Text(
                    text = item.noticeText.getText(LocalContext.current.resources).toString(),
                    color = accent.color,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetroTextButton(
                    if (removed) stringResource(R.string.metro_module_restore)
                    else stringResource(R.string.metro_module_remove),
                    accent,
                ) { item.delete() }
                if (item.showUpdate) {
                    MetroTextButton(
                        text = stringResource(R.string.metro_module_update),
                        accent = accent,
                        enabled = item.updateReady,
                    ) {
                        vm.downloadPressed(module.updateInfo)
                    }
                }
                if (item.showAction) {
                    MetroTextButton(stringResource(R.string.metro_module_action), accent) {
                        vm.runAction(module.id, module.name)
                    }
                }
                if (item.showWebUi) {
                    MetroTextButton(stringResource(R.string.metro_module_webui), accent) {
                        vm.openWebUi(module.id, module.name)
                    }
                }
            }
        }
    }
}

private data class MetroSettingsGroup(
    val header: BaseSettingsItem.Section?,
    val items: List<BaseSettingsItem>,
)

@Composable
fun SettingsSection(vm: SettingsViewModel) {
    val accent = LocalMetroPalette.current.settings
    val items = remember { vm.items }
    if (items.isEmpty()) {
        MetroCentered(stringResource(R.string.metro_no_settings))
        return
    }

    val groups = remember(items) {
        val result = mutableListOf<MetroSettingsGroup>()
        var header: BaseSettingsItem.Section? = null
        var sectionItems = mutableListOf<BaseSettingsItem>()
        fun addGroup() {
            if (header != null || sectionItems.isNotEmpty()) {
                result += MetroSettingsGroup(header, sectionItems)
            }
        }
        items.forEach { item ->
            if (item is BaseSettingsItem.Section) {
                addGroup()
                header = item
                sectionItems = mutableListOf()
            } else {
                sectionItems += item
            }
        }
        addGroup()
        result
    }
    val resources = LocalContext.current.resources
    MetroSubPivot(
        titles = groups.map { group ->
            group.header?.title?.getText(resources)?.toString()
                ?: stringResource(R.string.metro_settings)
        },
    ) { page, visible ->
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(groups[page].items) { index, item ->
                MetroFlipItem(index = index, visible = visible) {
                    when (item) {
                        is BaseSettingsItem.Toggle -> SettingsToggleRow(vm, item, accent)
                        else -> SettingsClickRow(vm, item)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(vm: SettingsViewModel, item: BaseSettingsItem, accent: MetroAccent) {
    val view = LocalView.current
    val res = LocalContext.current.resources
    val tick = item.observeAsTick()
    key(tick) {
        val enabled = item.isEnabled
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { item.onToggle(view, vm, !item.isChecked) }
                .padding(SectionPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsTitleAndDesc(item, res, Modifier.weight(1f))
            Switch(
                checked = item.isChecked,
                onCheckedChange = { item.onToggle(view, vm, it) },
                enabled = enabled,
                colors = metroSwitchColors(accent),
            )
        }
    }
}

@Composable
private fun SettingsClickRow(vm: SettingsViewModel, item: BaseSettingsItem) {
    val view = LocalView.current
    val res = LocalContext.current.resources
    val tick = item.observeAsTick()
    key(tick) {
        val enabled = item.isEnabled
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { item.onPressed(view, vm) }
                .padding(SectionPadding),
        ) {
            SettingsTitleAndDesc(item, res, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsTitleAndDesc(
    item: BaseSettingsItem,
    res: android.content.res.Resources,
    modifier: Modifier,
) {
    val enabled = item.isEnabled
    Column(modifier = modifier) {
        Text(
            text = item.title.getText(res).toString(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 17.sp,
        )
        val desc = item.description
        if (!desc.isEmpty) {
            Text(
                text = desc.getText(res).toString(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
