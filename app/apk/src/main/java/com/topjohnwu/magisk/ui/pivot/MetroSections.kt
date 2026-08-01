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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.topjohnwu.magisk.ui.deny.DenyListRvItem
import com.topjohnwu.magisk.ui.deny.DenyListViewModel
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
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(if (enabled) accent.color else accent.color.copy(alpha = 0.28f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = if (compact) 5.dp else 8.dp,
            ),
    ) {
        Text(
            text = text,
            color = accent.onColor.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = if (compact) 11.sp else 13.sp,
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
    initialPage: Int = 0,
    content: @Composable (page: Int, visible: Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, titles.lastIndex),
    ) { titles.size }
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
fun AppsSection(
    vm: SuperuserViewModel,
    denyListVM: DenyListViewModel,
    showDenyListInitially: Boolean,
) {
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
    val denyTick = denyListVM.observeAsTick()
    val denyLoading = remember(denyTick) { denyListVM.loading }
    val denyItems by denyListVM.items.asComposeState()
    val authorizedQuery = remember { mutableStateOf("") }
    val authorizedAppFilter = remember { mutableStateOf(DenyListViewModel.AppFilter.USER) }
    val authorizedSortOrder = remember { mutableStateOf(DenyListViewModel.SortOrder.INSTALL_TIME) }
    // Keep unconfigured applications here so root can be granted without a separate prompt page.
    // ALLOW is sorted above QUERY, so newly authorized apps move immediately after the write.
    val authorized = remember(
        policies,
        policyRevision,
        denyTick,
        authorizedQuery.value,
        authorizedAppFilter.value,
        authorizedSortOrder.value,
    ) {
        policies.asSequence()
            .filter { it.item.policy != SuPolicy.DENY && !denyListVM.isDenied(it.packageName) }
            .filter { item ->
                val matchesType = when (authorizedAppFilter.value) {
                    DenyListViewModel.AppFilter.USER -> !item.isSystemApp
                    DenyListViewModel.AppFilter.SYSTEM -> item.isSystemApp
                }
                val query = authorizedQuery.value
                matchesType && (query.isBlank() ||
                    item.appName.contains(query, true) || item.packageName.contains(query, true))
            }
            .sortedWith(
                compareBy<PolicyRvItem> { it.item.policy < SuPolicy.ALLOW }
                    .thenByDescending {
                        if (authorizedSortOrder.value == DenyListViewModel.SortOrder.INSTALL_TIME) {
                            it.installTime
                        } else 0L
                    }
                    .thenBy {
                        if (authorizedSortOrder.value == DenyListViewModel.SortOrder.ALPHABETICAL) {
                            it.appName.lowercase()
                        } else ""
                    }
                    .thenBy { it.appName.lowercase() },
            )
            .toList()
    }
    MetroSubPivot(
        titles = listOf(
            stringResource(R.string.metro_granted),
            stringResource(R.string.metro_blocked),
        ),
        initialPage = if (showDenyListInitially) 1 else 0,
    ) { page, visible ->
        if (page == 0) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppSearchAndFilters(
                    query = authorizedQuery.value,
                    appFilter = authorizedAppFilter.value,
                    sortOrder = authorizedSortOrder.value,
                    accent = accent,
                    onQueryChange = { authorizedQuery.value = it },
                    onAppFilterChange = { authorizedAppFilter.value = it },
                    onSortOrderChange = { authorizedSortOrder.value = it },
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (authorized.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.metro_no_granted_apps),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.padding(SectionPadding),
                            )
                        }
                    } else {
                        itemsIndexed(authorized) { index, item ->
                            MetroFlipItem(index = index, visible = visible) { PolicyRow(item, accent) }
                        }
                    }
                }
            }
        } else {
            DenyListSection(denyListVM, denyItems, denyLoading, accent, visible)
        }
    }
}

@Composable
private fun DenyListSection(
    vm: DenyListViewModel,
    items: List<DenyListRvItem>,
    loading: Boolean,
    accent: MetroAccent,
    visible: Boolean,
) {
    var query by remember { mutableStateOf(vm.query) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppSearchAndFilters(
            query = query,
            appFilter = vm.appFilter,
            sortOrder = vm.sortOrder,
            accent = accent,
            onQueryChange = { query = it; vm.query = it },
            onAppFilterChange = { vm.appFilter = it },
            onSortOrderChange = { vm.sortOrder = it },
        )
        if (loading) {
            MetroCentered(stringResource(R.string.metro_loading))
        } else if (items.isEmpty()) {
            MetroCentered(stringResource(R.string.metro_no_blocked_apps))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items) { index, item ->
                    MetroFlipItem(index = index, visible = visible) { DenyListRow(item, accent) }
                }
            }
        }
    }
}

@Composable
private fun AppSearchAndFilters(
    query: String,
    appFilter: DenyListViewModel.AppFilter,
    sortOrder: DenyListViewModel.SortOrder,
    accent: MetroAccent,
    onQueryChange: (String) -> Unit,
    onAppFilterChange: (DenyListViewModel.AppFilter) -> Unit,
    onSortOrderChange: (DenyListViewModel.SortOrder) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(CoreR.string.hide_filter_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item {
            MetroTextButton(
                text = stringResource(R.string.show_user_app),
                accent = accent,
                enabled = appFilter == DenyListViewModel.AppFilter.USER,
                compact = true,
            ) { onAppFilterChange(DenyListViewModel.AppFilter.USER) }
        }
        item {
            MetroTextButton(
                text = stringResource(CoreR.string.show_system_app),
                accent = accent,
                enabled = appFilter == DenyListViewModel.AppFilter.SYSTEM,
                compact = true,
            ) { onAppFilterChange(DenyListViewModel.AppFilter.SYSTEM) }
        }
        item {
            MetroTextButton(
                text = stringResource(R.string.sort_by_install_time),
                accent = accent,
                enabled = sortOrder == DenyListViewModel.SortOrder.INSTALL_TIME,
                compact = true,
            ) { onSortOrderChange(DenyListViewModel.SortOrder.INSTALL_TIME) }
        }
        item {
            MetroTextButton(
                text = stringResource(R.string.sort_by_alphabetical),
                accent = accent,
                enabled = sortOrder == DenyListViewModel.SortOrder.ALPHABETICAL,
                compact = true,
            ) { onSortOrderChange(DenyListViewModel.SortOrder.ALPHABETICAL) }
        }
    }
}

@Composable
private fun DenyListRow(item: DenyListRvItem, accent: MetroAccent) {
    val tick = item.observeAsTick()
    key(tick) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable { item.isExpanded = !item.isExpanded }.padding(SectionPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.info.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.info.packageName, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = item.state == true, onCheckedChange = { item.state = it }, colors = metroSwitchColors(accent))
            }
            if (item.isExpanded) {
                item.processes.forEach { process ->
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(process.displayName, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Switch(checked = process.isEnabled, onCheckedChange = { process.isEnabled = it }, colors = metroSwitchColors(accent))
                    }
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
    val updates = modules.filter { it.item.updateInfo != null && it.item.outdated }
    MetroSubPivot(
        titles = listOf(
            stringResource(R.string.metro_installed),
            stringResource(R.string.metro_updatable),
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
