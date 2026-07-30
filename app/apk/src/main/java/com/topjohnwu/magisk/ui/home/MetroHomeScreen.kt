package com.topjohnwu.magisk.ui.home

import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SUPPORT_URL = "https://afdian.com/a/SunRayEx"
private val ImportantLogPattern = Regex("^\\S+\\s+\\S+\\s+\\d+\\s+\\d+\\s+([EW])\\s*:\\s*(.*)$")

private fun importantLogLabel(line: String): String? {
    val match = ImportantLogPattern.find(line) ?: return null
    val severity = if (match.groupValues[1] == "E") "DANGER" else "WARNING"
    return "$severity · ${match.groupValues[2].trim()}"
}

@Composable
fun MetroHomeScreen(
    viewModel: HomeViewModel,
    onSettingsClick: () -> Unit,
    onModulesClick: () -> Unit,
    onAppsClick: () -> Unit,
    onLogsClick: () -> Unit,
    onContributorsClick: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val numCols = if (isTablet) 5 else 3

    val modules by produceState<List<LocalModule>>(initialValue = emptyList()) {
        value = if (LocalModule.loaded()) {
            runCatching { LocalModule.installed() }.getOrDefault(emptyList())
        } else emptyList()
    }
    val rootApps by produceState<List<String>>(initialValue = emptyList()) {
        value = if (Info.showSuperUser) {
            withContext(Dispatchers.IO) {
                val packageManager = AppContext.packageManager
                ServiceLocator.policyDB.fetchAll()
                    .asSequence()
                    .filter { it.policy >= SuPolicy.ALLOW }
                    .flatMap { policy ->
                        val packages = if (policy.uid == Process.SYSTEM_UID) {
                            arrayOf("android")
                        } else {
                            packageManager.getPackagesForUid(policy.uid).orEmpty()
                        }
                        packages.asSequence()
                    }
                    .mapNotNull { packageName ->
                        runCatching {
                            packageManager.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES)
                                .applicationInfo
                                ?.loadLabel(packageManager)
                                ?.toString()
                        }.getOrNull()
                    }
                    .distinct()
                    .sorted()
                    .toList()
            }
        } else {
            emptyList()
        }
    }
    val importantLogs by produceState<List<String>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            runCatching { ServiceLocator.logRepo.fetchMagiskLogs() }
                .getOrDefault("")
                .lineSequence()
                .map(String::trim)
                .mapNotNull(::importantLogLabel)
                .toList()
                .takeLast(100)
        }
    }

    MetroGrid(
        viewModel = viewModel,
        isTablet = isTablet,
        numCols = numCols,
        modules = modules,
        rootApps = rootApps,
        importantLogs = importantLogs,
        onSettingsClick = onSettingsClick,
        onModulesClick = onModulesClick,
        onAppsClick = onAppsClick,
        onLogsClick = onLogsClick,
        onContributorsClick = onContributorsClick,
    )
}

@Composable
private fun MetroGrid(
    viewModel: HomeViewModel,
    isTablet: Boolean,
    numCols: Int,
    modules: List<LocalModule>,
    rootApps: List<String>,
    importantLogs: List<String>,
    onSettingsClick: () -> Unit,
    onModulesClick: () -> Unit,
    onAppsClick: () -> Unit,
    onLogsClick: () -> Unit,
    onContributorsClick: () -> Unit,
) {
    val gap = 3.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        val tileSize = (maxWidth - gap * (numCols + 1)) / numCols
        Box(modifier = Modifier.padding(gap)) {
            MetroBoard(
                viewModel = viewModel,
                tileSize = tileSize,
                gap = gap,
                rows = if (isTablet) 7 else 6,
                isTablet = isTablet,
                modules = modules,
                rootApps = rootApps,
                importantLogs = importantLogs,
                onSettingsClick = onSettingsClick,
                onModulesClick = onModulesClick,
                onAppsClick = onAppsClick,
                onLogsClick = onLogsClick,
                onContributorsClick = onContributorsClick,
            )
        }
    }
}

@Composable
private fun MetroBoard(
    viewModel: HomeViewModel,
    tileSize: Dp,
    gap: Dp,
    rows: Int,
    isTablet: Boolean,
    modules: List<LocalModule>,
    rootApps: List<String>,
    importantLogs: List<String>,
    onSettingsClick: () -> Unit,
    onModulesClick: () -> Unit,
    onAppsClick: () -> Unit,
    onLogsClick: () -> Unit,
    onContributorsClick: () -> Unit,
) {
    fun place(column: Int, row: Int, width: Int = 1, height: Int = 1): Modifier =
        Modifier
            .offset(x = (tileSize + gap) * column, y = (tileSize + gap) * row)
            .size(
                width = tileSize * width + gap * (width - 1),
                height = tileSize * height + gap * (height - 1),
            )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tileSize * rows + gap * (rows - 1)),
    ) {
        val sideColumn = if (isTablet) 4 else 2
        val wideColumn = if (isTablet) 2 else 0

        // Magisk 2x2 top-left
        MagiskTile(
            modifier = place(0, 0, 2, 2),
            onClick = viewModel::onMagiskPressed,
        )
        // Modules 1x1 and Apps 1x1 to the right of Magisk
        ModuleTile(
            modifier = place(sideColumn, 0),
            modules = modules,
            onClick = onModulesClick,
        )
        AppTile(
            modifier = place(sideColumn, 1),
            apps = rootApps,
            onClick = onAppsClick,
        )
        // Settings 2x1
        SettingsTile(
            modifier = place(wideColumn, 2, 2),
            onClick = onSettingsClick,
        )
        // Logs 1x2 (tall, right column)
        LogTile(
            modifier = place(sideColumn, 2, 1, 2),
            logs = importantLogs,
            onClick = onLogsClick,
        )
        // Contributor 2x1
        ContributorTile(
            modifier = place(wideColumn, 3, 2),
            onClick = onContributorsClick,
        )
        // Sponsor row: three 1x1 tiles
        val sponsorStart = if (isTablet) 1 else 0
        SponsorTile(
            modifier = place(sponsorStart, 4),
            label = stringResource(R.string.metro_sponsor),
        ) {
            viewModel.onLinkPressed(SUPPORT_URL)
        }
        SponsorTile(
            modifier = place(sponsorStart + 1, 4),
            label = stringResource(R.string.metro_support),
        ) {
            viewModel.onLinkPressed(SUPPORT_URL)
        }
        SponsorTile(
            modifier = place(sponsorStart + 2, 4),
            label = stringResource(R.string.metro_donate),
        ) {
            viewModel.onLinkPressed(SUPPORT_URL)
        }
    }
}

@Composable
private fun MetroTile(
    modifier: Modifier = Modifier,
    color: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tileModifier = modifier
        .clip(RectangleShape)
        .background(color)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(8.dp)

    Box(modifier = tileModifier, content = content)
}

@Composable
private fun MagiskTile(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.magisk
    MetroTile(
        modifier = modifier,
        color = accent.color,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Top) {
                Image(
                    painter = painterResource(R.drawable.ic_magiskube),
                    contentDescription = "Magisk",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(
                            R.string.metro_magisk_version,
                            BuildConfig.APP_VERSION_NAME.ifBlank { "dev" },
                        ),
                        color = accent.onColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            if (Info.env.isActive) R.string.metro_enabled else R.string.metro_disabled
                        ),
                        color = accent.onColor.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            StatusRow(R.string.metro_root_status, Info.isRooted, accent.onColor)
            StatusRow(R.string.metro_zygisk_status, Info.isZygiskEnabled, accent.onColor)
            StatusRow(R.string.metro_ramdisk_status, Info.ramdisk, accent.onColor)
        }
    }
}

@Composable
private fun StatusRow(label: Int, enabled: Boolean, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(label),
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
        )
        Text(
            text = stringResource(if (enabled) R.string.metro_yes else R.string.metro_no),
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ModuleTile(
    modifier: Modifier,
    modules: List<LocalModule>,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.modules
    MetroTile(modifier = modifier, color = accent.color, onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.metro_modules),
                color = accent.onColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Start),
            )
            AutoRollingList(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                items = modules.map { it.name },
                visibleItems = 2,
                textAlign = TextAlign.Start,
                emptyText = stringResource(R.string.metro_no_modules),
                textColor = accent.onColor,
            )
            Text(
                text = modules.size.toString(),
                color = accent.onColor,
                fontWeight = FontWeight.Light,
                fontSize = 32.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun AppTile(
    modifier: Modifier,
    apps: List<String>,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.apps
    MetroTile(modifier = modifier, color = accent.color, onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.metro_apps),
                color = accent.onColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Start),
            )
            AutoRollingList(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                items = apps,
                visibleItems = 2,
                textAlign = TextAlign.Start,
                emptyText = stringResource(R.string.metro_no_rooted_apps),
                textColor = accent.onColor,
            )
            Text(
                text = apps.size.toString(),
                color = accent.onColor,
                fontWeight = FontWeight.Light,
                fontSize = 32.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun SettingsTile(modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.settings
    MetroTile(modifier = modifier, color = accent.color, onClick = onClick) {
        Text(
            text = stringResource(R.string.metro_settings),
            color = accent.onColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun ContributorTile(modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.contributors
    MetroTile(
        modifier = modifier,
        color = accent.color,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.metro_contributor),
                color = accent.onColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "@SunRayEx",
                color = accent.onColor.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun LogTile(modifier: Modifier, logs: List<String>, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.logs
    MetroTile(modifier = modifier, color = accent.color, onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.metro_logs),
                color = accent.onColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            AutoRollingList(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                items = logs,
                visibleItems = 10,
                textAlign = TextAlign.Start,
                emptyText = stringResource(R.string.metro_no_warnings),
                textColor = accent.onColor,
            )
        }
    }
}

@Composable
private fun AutoRollingList(
    modifier: Modifier,
    items: List<String>,
    visibleItems: Int,
    textAlign: TextAlign = TextAlign.Start,
    emptyText: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val canRoll = items.size > visibleItems
    val rollingItems = remember(items, visibleItems) {
        if (canRoll) List(items.size * 3) { items[it % items.size] } else items
    }
    val listState = rememberLazyListState()

    LaunchedEffect(items, visibleItems) {
        if (canRoll) {
            var index = items.size
            listState.scrollToItem(index)
            while (true) {
                delay(2200L)
                index += 1
                listState.animateScrollToItem(index)
                if (index >= items.size * 2) {
                    index = items.size
                    listState.scrollToItem(index)
                }
            }
        } else {
            listState.scrollToItem(0)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        if (items.isEmpty()) {
            Text(
                text = emptyText,
                color = textColor.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val rowHeight = (maxHeight / visibleItems) * 0.82f
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                userScrollEnabled = false,
            ) {
                itemsIndexed(rollingItems) { _, value ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                    Text(
                        text = value,
                        color = textColor.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun SponsorTile(modifier: Modifier, label: String, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.sponsor
    MetroTile(
        modifier = modifier,
        color = accent.color,
        onClick = onClick,
    ) {
        Text(
            text = label,
            color = accent.onColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}
