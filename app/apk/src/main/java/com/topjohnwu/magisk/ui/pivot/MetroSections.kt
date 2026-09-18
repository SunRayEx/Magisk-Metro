package com.topjohnwu.magisk.ui.pivot

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.VMFactory
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.PersistentModules
import com.topjohnwu.magisk.core.ktx.timeDateFormat
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.model.su.SuLog
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.ui.anim.MetroFlipItem
import com.topjohnwu.magisk.ui.component.MagiskDialog
import com.topjohnwu.magisk.ui.deny.DenyAppState
import com.topjohnwu.magisk.ui.deny.DenyListViewModel
import com.topjohnwu.magisk.ui.deny.SortBy
import com.topjohnwu.magisk.ui.deny.DenyProcessState
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.install.InstallDialog
import com.topjohnwu.magisk.ui.install.InstallViewModel
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.log.MagiskLogEntry
import com.topjohnwu.magisk.ui.module.ModuleItem
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.module.OnlineModuleSubject
import com.topjohnwu.magisk.ui.settings.AppSettingsSection
import com.topjohnwu.magisk.ui.settings.CustomizationSection
import com.topjohnwu.magisk.ui.settings.MagiskSection as SettingsMagiskSection
import com.topjohnwu.magisk.ui.settings.SuperuserSection
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.PolicyItem
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.theme.MetroAccent
import com.topjohnwu.magisk.utils.textHolder
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.magisk.core.R as CoreR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    selected: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val solid = enabled && selected
    Box(
        modifier = Modifier
            .background(if (solid) accent.color else accent.color.copy(alpha = 0.28f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = if (compact) 5.dp else 8.dp,
            ),
    ) {
        Text(
            text = text,
            color = accent.onColor.copy(alpha = when {
                !enabled -> 0.5f
                selected -> 1f
                else -> 0.75f
            }),
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
                val distance = kotlin.math.abs(index - pagerState.currentPage - pagerState.currentPageOffsetFraction)
                val selected = distance < 0.5f
                item(key = title) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 1f - distance.coerceIn(0f, 1f) * 0.62f),
                        fontSize = (56f - distance.coerceIn(0f, 1f) * 8f).sp,
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
    val installVm: InstallViewModel = viewModel(factory = VMFactory)
    var showInstallDialog by remember { mutableStateOf(false) }

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
                            onClick = { showInstallDialog = true },
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
                            MetroStatusRow(stringResource(R.string.metro_info_root), Info.isRooted, accent)
                            MetroStatusRow(stringResource(R.string.metro_info_zygisk), Info.isZygiskEnabled, accent)
                            MetroStatusRow(stringResource(R.string.metro_info_ramdisk), Info.ramdisk, accent)
                        }
                    }
                }
            }
        }
    }

    InstallDialog(
        show = showInstallDialog,
        onDismiss = { showInstallDialog = false },
        installVm = installVm,
    )
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
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val loading = uiState.loading
    val policies = uiState.policies

    if (loading && policies.isEmpty()) {
        MetroCentered(stringResource(R.string.metro_loading))
        return
    }
    val denyLoading by denyListVM.loading.collectAsStateWithLifecycle()
    val denyItems by denyListVM.filteredApps.collectAsStateWithLifecycle()
    val authorizedQuery = remember { mutableStateOf("") }
    val authorizedAppFilter = remember { mutableStateOf(AppFilter.USER) }
    val authorizedSortOrder = remember { mutableStateOf(SortOrder.ALPHABETICAL) }
    // Keep unconfigured applications here so root can be granted without a separate prompt page.
    // ALLOW is sorted above QUERY, so newly authorized apps move immediately after the write.
    val authorized = remember(
        policies,
        denyItems,
        authorizedQuery.value,
        authorizedAppFilter.value,
        authorizedSortOrder.value,
    ) {
        policies.asSequence()
            .filter { it.policy.policy != SuPolicy.DENY && !denyListVM.isDenied(it.packageName) }
            .filter { item ->
                val matchesType = when (authorizedAppFilter.value) {
                    AppFilter.USER -> !item.isSystemApp
                    AppFilter.SYSTEM -> item.isSystemApp
                }
                val query = authorizedQuery.value
                matchesType && (query.isBlank() ||
                    item.appName.contains(query, true) || item.packageName.contains(query, true))
            }
            .sortedWith(
                compareBy<PolicyItem> { it.policy.policy < SuPolicy.ALLOW }
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
                            MetroFlipItem(index = index, visible = visible) { PolicyRow(vm, item, accent) }
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
    items: List<DenyAppState>,
    loading: Boolean,
    accent: MetroAccent,
    visible: Boolean,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val showSystem by vm.showSystem.collectAsStateWithLifecycle()
    val showOS by vm.showOS.collectAsStateWithLifecycle()
    val sortBy by vm.sortBy.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        AppSearchAndFilters(
            query = query,
            appFilter = when {
                showOS -> AppFilter.SYSTEM
                showSystem -> AppFilter.SYSTEM
                else -> AppFilter.USER
            },
            sortOrder = if (sortBy == SortBy.INSTALL_TIME)
                SortOrder.INSTALL_TIME else SortOrder.ALPHABETICAL,
            accent = accent,
            onQueryChange = { vm.setQuery(it) },
            onAppFilterChange = {
                when (it) {
                    AppFilter.USER -> { vm.setShowSystem(false); vm.setShowOS(false) }
                    AppFilter.SYSTEM -> { vm.setShowSystem(true); vm.setShowOS(false) }
                }
            },
            onSortOrderChange = {
                vm.setSortBy(
                    if (it == SortOrder.INSTALL_TIME)
                        SortBy.INSTALL_TIME
                    else SortBy.NAME
                )
            },
        )
        if (loading) {
            MetroCentered(stringResource(R.string.metro_loading))
        } else if (items.isEmpty()) {
            MetroCentered(stringResource(R.string.metro_no_blocked_apps))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items) { index, item ->
                    MetroFlipItem(index = index, visible = visible) { DenyListRow(vm, item, accent) }
                }
            }
        }
    }
}

private enum class AppFilter { USER, SYSTEM }
private enum class SortOrder { INSTALL_TIME, ALPHABETICAL }

@Composable
private fun AppSearchAndFilters(
    query: String,
    appFilter: AppFilter,
    sortOrder: SortOrder,
    accent: MetroAccent,
    onQueryChange: (String) -> Unit,
    onAppFilterChange: (AppFilter) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
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
                selected = appFilter == AppFilter.USER,
                compact = true,
            ) { onAppFilterChange(AppFilter.USER) }
        }
        item {
            MetroTextButton(
                text = stringResource(CoreR.string.show_system_app),
                accent = accent,
                selected = appFilter == AppFilter.SYSTEM,
                compact = true,
            ) { onAppFilterChange(AppFilter.SYSTEM) }
        }
        item {
            MetroTextButton(
                text = stringResource(R.string.sort_by_install_time),
                accent = accent,
                selected = sortOrder == SortOrder.INSTALL_TIME,
                compact = true,
            ) { onSortOrderChange(SortOrder.INSTALL_TIME) }
        }
        item {
            MetroTextButton(
                text = stringResource(R.string.sort_by_alphabetical),
                accent = accent,
                selected = sortOrder == SortOrder.ALPHABETICAL,
                compact = true,
            ) { onSortOrderChange(SortOrder.ALPHABETICAL) }
        }
    }
}

@Composable
private fun DenyListRow(vm: DenyListViewModel, item: DenyAppState, accent: MetroAccent) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { vm.toggleExpanded(item) }.padding(SectionPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.info.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.info.packageName, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = item.isChecked, onCheckedChange = { vm.toggleAll(item) }, colors = metroSwitchColors(accent))
        }
        if (item.isExpanded) {
            item.processes.forEach { process ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(process.displayName, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Switch(checked = process.isEnabled, onCheckedChange = { vm.toggleProcess(item, process) }, colors = metroSwitchColors(accent))
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(vm: SuperuserViewModel, item: PolicyItem, accent: MetroAccent) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = item.isEnabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
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
                    text = if (item.policy.policy == SuPolicy.ZERO)
                        "${item.packageName} · " + stringResource(R.string.metro_su_policy_zero)
                    else item.packageName,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { vm.togglePolicy(item) },
                colors = metroSwitchColors(accent),
            )
        }
        if (expanded) {
            Spacer(Modifier.width(0.dp))
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetroTextButton(stringResource(CoreR.string.deny), accent) {
                    vm.updatePolicy(item, SuPolicy.DENY)
                }
                MetroTextButton(stringResource(CoreR.string.restrict), accent) {
                    vm.updatePolicy(item, SuPolicy.RESTRICT)
                }
                MetroTextButton(stringResource(CoreR.string.grant), accent) {
                    vm.updatePolicy(item, SuPolicy.ALLOW)
                }
            }
            MetroToggleLine(
                label = stringResource(R.string.metro_notifications),
                checked = item.notification,
                accent = accent,
            ) { vm.updateNotify(item) }
            MetroToggleLine(
                label = stringResource(R.string.metro_logging),
                checked = item.logging,
                accent = accent,
            ) { vm.updateLogging(item) }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                MetroTextButton(stringResource(R.string.metro_revoke), accent) { vm.performDelete(item) }
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
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val loading = uiState.loading
    val suItems = uiState.suLogs
    val magiskItems = uiState.magiskLogEntries
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showGhostLog by remember { mutableStateOf(false) }

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
                if (page == 0) {
                    MetroTextButton(stringResource(R.string.metro_su_ghost_log), accent) {
                        showGhostLog = true
                    }
                    Spacer(Modifier.width(8.dp))
                }
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
                                    text = formatMagiskLog(line),
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
                                    text = formatSuLog(log, context),
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

    if (showGhostLog) {
        val auditText by produceState("", showGhostLog) {
            value = withContext(Dispatchers.IO) {
                com.topjohnwu.superuser.Shell.cmd(
                    "cat ${PersistentModules.GHOST_AUDIT_LOG} 2>/dev/null",
                ).exec().out.joinToString("\n").trim()
            }
        }
        MagiskDialog(
            onDismissRequest = { showGhostLog = false },
            title = stringResource(R.string.metro_su_ghost_log),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { showGhostLog = false },
        ) {
            Text(
                text = auditText.ifEmpty { context.getString(R.string.metro_no_logs) },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}

private fun formatMagiskLog(entry: MagiskLogEntry): String =
    if (entry.timestamp.isEmpty()) entry.message
    else "${entry.timestamp} ${entry.message}"

private fun formatSuLog(log: SuLog, context: android.content.Context): String {
    val res = context.resources
    val sb = StringBuilder()
    val date = log.time.toTime(timeDateFormat)
    val toUid = res.getString(CoreR.string.target_uid, log.toUid)
    val fromPid = res.getString(CoreR.string.pid, log.fromPid)
    sb.append("$date\n$toUid  $fromPid")
    if (log.target != -1) {
        val pid = if (log.target == 0) "magiskd" else log.target.toString()
        val target = res.getString(CoreR.string.target_pid, pid)
        sb.append("  $target")
    }
    if (log.context.isNotEmpty()) {
        val context = res.getString(CoreR.string.selinux_context, log.context)
        sb.append("\n$context")
    }
    if (log.gids.isNotEmpty()) {
        val gids = res.getString(CoreR.string.supp_group, log.gids)
        sb.append("\n$gids")
    }
    sb.append("\n${log.command}")
    return sb.toString()
}

// ---- Modules -----------------------------------------------------------------

@Composable
fun ModulesSection(vm: ModuleViewModel) {
    val accent = LocalMetroPalette.current.modules
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val loading = uiState.loading
    val items = uiState.modules
    val context = LocalContext.current

    if (loading && items.isEmpty()) {
        MetroCentered(stringResource(R.string.metro_loading))
        return
    }
    if (items.isEmpty()) {
        MetroCentered(stringResource(R.string.metro_no_modules))
        return
    }
    val modules = items
    val updates = modules.filter { it.showUpdate }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.confirmLocalInstall(it) }
    }
    MetroSubPivot(
        titles = listOf(
            stringResource(R.string.metro_persistent),
            stringResource(R.string.metro_installed),
            stringResource(R.string.metro_updatable),
        ),
    ) { page, visible ->
        val pageModules = if (page == 1) modules else updates
        if (page == 0) {
            PersistentTab(accent, visible)
            return@MetroSubPivot
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (page == 1) {
                item {
                    MetroFlipItem(index = 0, visible = visible) {
                        Row(modifier = Modifier.padding(SectionPadding)) {
                            MetroTextButton(stringResource(R.string.metro_install_module), accent) {
                                filePicker.launch("application/zip")
                            }
                        }
                    }
                }
            }
            if (pageModules.isEmpty()) {
                item {
                    MetroCentered(stringResource(if (page == 1) R.string.metro_no_modules else R.string.metro_no_updates))
                }
            } else {
                itemsIndexed(pageModules) { index, item ->
                    MetroFlipItem(index = index + if (page == 1) 1 else 0, visible = visible) {
                        ModuleRow(vm, item, accent)
                    }
                }
            }
        }
    }
}

/** Read-only mirror of the persistent declaration with a jump into the management page. */
@Composable
private fun PersistentTab(accent: MetroAccent, visible: Boolean) {
    val context = LocalContext.current
    val snapshot by produceState<Pair<Int, com.topjohnwu.magisk.core.PersistentManifest?>>(0 to null) {
        value = 0 to withContext(Dispatchers.IO) { PersistentModules.fetch() }
    }
    val declaration = snapshot.second
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            MetroFlipItem(index = 0, visible = visible) {
                Column(modifier = Modifier.padding(SectionPadding)) {
                    Text(
                        text = stringResource(R.string.metro_persistent),
                        color = accent.color,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = declaration?.modules?.joinToString(", ") { it.name }
                            ?.takeIf(String::isNotEmpty)
                            ?: stringResource(R.string.metro_persistent_empty),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        item {
            MetroFlipItem(index = 1, visible = visible) {
                Row(modifier = Modifier.padding(SectionPadding)) {
                    MetroTextButton(stringResource(R.string.metro_persistent_apply), accent) {
                        if (!PersistentModules.configured()) {
                            // Not configured yet: send the user to Settings · Misc to flip the switch.
                            Toast.makeText(
                                context,
                                R.string.metro_persistent_goto_settings,
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            PersistentModules.apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(vm: ModuleViewModel, item: ModuleItem, accent: MetroAccent) {
    val context = LocalContext.current
    val module = item.module
    val enabled = item.isEnabled
    val removed = item.isRemoved
    val inactive = removed || !enabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (removed) 0.48f else if (!enabled) 0.68f else 1f)
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
                onCheckedChange = if (removed) null else ({ vm.toggleEnabled(item) }),
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
                text = textHolder(item.noticeText).toString(),
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
            ) { vm.toggleRemove(item) }
            if (item.showUpdate) {
                MetroTextButton(
                    text = stringResource(R.string.metro_module_update),
                    accent = accent,
                    enabled = item.updateReady,
                ) {
                    val updateInfo = module.updateInfo ?: return@MetroTextButton
                    val activity = context.findActivity()
                    com.topjohnwu.magisk.core.download.DownloadEngine.startWithActivity(
                        activity,
                        OnlineModuleSubject(updateInfo, true),
                    )
                }
            }
            if (item.showAction) {
                MetroTextButton(stringResource(R.string.metro_module_action), accent) {
                    vm.runAction(module.id, module.name)
                }
            }
        }
    }
}

private fun android.content.Context.findActivity(): com.topjohnwu.magisk.ui.MainActivity {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is com.topjohnwu.magisk.ui.MainActivity) return ctx
        ctx = ctx.baseContext
    }
    error("Activity not found in context chain")
}

// ---- Settings ----------------------------------------------------------------

@Composable
fun SettingsSection(vm: SettingsViewModel) {
    val accent = LocalMetroPalette.current.settings
    MetroSubPivot(
        titles = listOf(
            stringResource(CoreR.string.settings_customization),
            stringResource(CoreR.string.home_app_title),
            stringResource(CoreR.string.magisk),
            stringResource(CoreR.string.superuser),
        ),
    ) { page, _ ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            when (page) {
                0 -> CustomizationSection(vm)
                1 -> AppSettingsSection(vm)
                2 -> if (Info.env.isActive) SettingsMagiskSection(vm)
                3 -> if (Info.showSuperUser) SuperuserSection(vm)
            }
        }
    }
}
