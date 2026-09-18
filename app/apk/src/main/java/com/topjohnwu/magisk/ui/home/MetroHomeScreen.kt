package com.topjohnwu.magisk.ui.home

import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.navigation.LocalNavigator
import com.topjohnwu.magisk.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toArgb

private const val SUPPORT_URL = "https://afdian.com/a/SunRayEx"
private val ImportantLogPattern = Regex("^\\S+\\s+\\S+\\s+\\d+\\s+\\d+\\s+([EW])\\s*:\\s*(.*)$")
private const val MetroTileCount = 10

private class MetroTileNavigator(
    val leaving: Boolean,
    val entering: Boolean,
    val navigate: (Int, () -> Unit) -> Unit,
)

private val LocalMetroTileNavigator = androidx.compose.runtime.staticCompositionLocalOf<MetroTileNavigator?> { null }

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
    onMagiskClick: () -> Unit,
) {
    var leaving by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Ground truth for "the Start screen is on top". The back stack is a SnapshotStateList,
    // so reading its top entry here is reactive and this composable recomposes on every push
    // and pop.
    val navigator = LocalNavigator.current
    val isCurrentRoute = navigator.backStack.lastOrNull() === Route.Main
    // Tiles are hidden only while another route is actually on top. This is derived on purpose:
    // the manual leaving flag can get stuck if the composition is disposed mid fly-out or a
    // route change does not deliver the event the flag relied on, and then coming back to the
    // Start screen would show nothing and accept no touches (while leaving is true the tiles
    // are translated off-screen, so their hit areas move away too). When we are the current
    // route the tiles are always visible no matter what the flag says.
    val tilesLeaving = leaving || !isCurrentRoute
    LaunchedEffect(isCurrentRoute) {
        if (isCurrentRoute) {
            leaving = false
            entering = true
        }
    }

    LaunchedEffect(entering) {
        if (entering) {
            delay(10)
            entering = false
        }
    }
    // The pivot tints the status bar with the section accent; returning to the Start screen
    // must clear it again, exactly like returning from the browser (sponsor tile) does.
    val view = LocalView.current
    val boardBackground = MaterialTheme.colorScheme.background
    LaunchedEffect(view, boardBackground, isCurrentRoute) {
        if (isCurrentRoute) {
            (view.context as? android.app.Activity)?.window?.let { window ->
                window.statusBarColor = boardBackground.toArgb()
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars =
                    boardBackground.luminance() > 0.5f
            }
        }
    }
    val tileNavigator = MetroTileNavigator(tilesLeaving, entering) { index, action ->
        if (!leaving && !entering) {
            leaving = true
            scope.launch {
                // The bottom-right tile starts first; each predecessor follows toward the top-left.
                delay((MetroTileCount - 1 - index).coerceAtLeast(0) * 45L + 290L)
                action()
                // The pushed route now covers the Start screen, so this flag is free to clear.
                // !isCurrentRoute keeps the tiles hidden for as long as that route is on top.
                leaving = false
            }
        }
    }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    // A wide screen (landscape phone or tablet) scrolls sideways; a tall screen scrolls down.
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || isTablet
    val customTiles = remember(MetroUiState.version) { MetroCustomTiles.load() }

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

    CompositionLocalProvider(LocalMetroTileNavigator provides tileNavigator) {
        MetroGrid(
            viewModel = viewModel,
            isLandscape = isLandscape,
            modules = modules,
            rootApps = rootApps,
            importantLogs = importantLogs,
            customTiles = customTiles,
            onSettingsClick = onSettingsClick,
            onModulesClick = onModulesClick,
            onAppsClick = onAppsClick,
            onLogsClick = onLogsClick,
            onContributorsClick = onContributorsClick,
            onMagiskClick = onMagiskClick,
        )
    }
}

@Composable
private fun MetroGrid(
    viewModel: HomeViewModel,
    isLandscape: Boolean,
    modules: List<LocalModule>,
    rootApps: List<String>,
    importantLogs: List<String>,
    customTiles: List<MetroCustomTile>,
    onSettingsClick: () -> Unit,
    onModulesClick: () -> Unit,
    onAppsClick: () -> Unit,
    onLogsClick: () -> Unit,
    onContributorsClick: () -> Unit,
    onMagiskClick: () -> Unit,
) {
    val gap = 3.dp
    val customizing = Config.metroTileCustomization
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            // The board only scrolls while it is being customized; otherwise the tiles are
            // packed into exactly the visible free-axis extent, so the whole start screen
            // is always on screen.
            .then(
                if (!customizing) Modifier
                else if (isLandscape) Modifier.horizontalScroll(rememberScrollState())
                else Modifier.verticalScroll(rememberScrollState()),
            ),
    ) {
        val cap = MetroTileLayout.fixedSpan(isLandscape)
        val constrainedMax = if (isLandscape) maxHeight else maxWidth
        val tileSize = (constrainedMax - gap * (cap + 1)) / cap
        val freeViewport = if (isLandscape) maxWidth else maxHeight
        // Whole cells that fit on the free axis; used as the packing bound when not scrolling.
        val visibleFreeExtent = ((freeViewport - gap) / (tileSize + gap)).toInt().coerceAtLeast(1)
        val customIds = customTiles.map { it.id }
        val customWidths = customTiles.associate { it.id to (if (it.groupMembers.size > 1) 2 else 1) }
        var placements by remember(cap, isLandscape, MetroUiState.version, customIds, customWidths, customizing, visibleFreeExtent) {
            mutableStateOf(
                MetroTileLayout.load(
                    isLandscape, customIds, customWidths,
                    freeBound = if (customizing) Int.MAX_VALUE else visibleFreeExtent,
                ),
            )
        }
        // The board is only as long as the last tile, so the page never scrolls past it.
        // Without scrolling the board is pinned to the visible extent.
        val freeExtent = MetroTileLayout.usedFreeExtent(placements, isLandscape)
            .let { if (customizing) it else it.coerceAtMost(visibleFreeExtent) }.coerceAtLeast(1)
        fun persist(next: List<MetroTilePlacement>) {
            placements = MetroTileLayout.save(next, isLandscape)
        }
        val boardWidth = if (isLandscape) tileSize * freeExtent + gap * (freeExtent - 1) else tileSize * cap + gap * (cap - 1)
        val boardHeight = if (isLandscape) tileSize * cap + gap * (cap - 1) else tileSize * freeExtent + gap * (freeExtent - 1)
        Box(modifier = Modifier.padding(gap).size(boardWidth, boardHeight)) {
            MetroBoard(
                viewModel = viewModel,
                placements = placements,
                onPlacementsChange = ::persist,
                tileSize = tileSize,
                gap = gap,
                cap = cap,
                isLandscape = isLandscape,
                modules = modules,
                rootApps = rootApps,
                importantLogs = importantLogs,
                customTiles = customTiles,
                onSettingsClick = onSettingsClick,
                onModulesClick = onModulesClick,
                onAppsClick = onAppsClick,
                onLogsClick = onLogsClick,
                onContributorsClick = onContributorsClick,
                onMagiskClick = onMagiskClick,
            )
        }
    }
}

@Composable
private fun MetroBoard(
    viewModel: HomeViewModel,
    placements: List<MetroTilePlacement>,
    onPlacementsChange: (List<MetroTilePlacement>) -> Unit,
    tileSize: Dp,
    gap: Dp,
    cap: Int,
    isLandscape: Boolean,
    modules: List<LocalModule>,
    rootApps: List<String>,
    importantLogs: List<String>,
    customTiles: List<MetroCustomTile>,
    onSettingsClick: () -> Unit,
    onModulesClick: () -> Unit,
    onAppsClick: () -> Unit,
    onLogsClick: () -> Unit,
    onContributorsClick: () -> Unit,
    onMagiskClick: () -> Unit,
) {
    val cellPx = LocalDensity.current.run { (tileSize + gap).toPx() }
    val customizing = Config.metroTileCustomization
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var resizingId by remember { mutableStateOf<String?>(null) }
    var resizePreview by remember { mutableStateOf<MetroTilePlacement?>(null) }
    val boardScope = rememberCoroutineScope()
    var hoverJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    /**
     * The free axis is unbounded, so a drag only clamps the constrained one: columns in portrait,
     * rows in landscape.
     */
    fun clampColumn(raw: Int, width: Int) = if (isLandscape) raw.coerceAtLeast(0) else raw.coerceIn(0, cap - width)
    fun clampRow(raw: Int, height: Int) = if (isLandscape) raw.coerceIn(0, cap - height) else raw.coerceAtLeast(0)

    @Composable
    fun place(item: MetroTilePlacement): Modifier {
        val rendered = resizePreview?.takeIf { it.id == item.id } ?: item
        val cell = tileSize + gap
        val targetX = cell * rendered.column
        val targetY = cell * rendered.row
        val targetWidth = tileSize * rendered.width + gap * (rendered.width - 1)
        val targetHeight = tileSize * rendered.height + gap * (rendered.height - 1)
        // Springs instead of tweens: every retarget (a drag step or a resize step) starts from
        // the current position *and velocity*, so tiles squeeze and stretch non-linearly and
        // never restart from a standstill. The resizing tile tracks a bit tighter than its
        // reflowing neighbours.
        val resizing = resizingId == item.id
        // High stiffness on purpose: customization must feel instant, with the board
        // reflowing as fast as the finger moves.
        val animation = if (resizing) {
            spring<Dp>(dampingRatio = 0.8f, stiffness = 600f)
        } else {
            spring<Dp>(dampingRatio = 0.85f, stiffness = 650f)
        }
        val x by animateDpAsState(targetX, animation, label = "tileX")
        val y by animateDpAsState(targetY, animation, label = "tileY")
        val width by animateDpAsState(targetWidth, animation, label = "tileWidth")
        val height by animateDpAsState(targetHeight, animation, label = "tileHeight")
        return Modifier
            .offset(x = x, y = y)
            .graphicsLayer {
                // Gentle stretch on the tile under the resize handle while the spring settles;
                // neighbouring tiles reflow through the same spring, producing the squeeze.
                val stretch = if (resizing) 1.02f else 1f
                scaleX = stretch
                scaleY = stretch
            }
            .size(width = width, height = height)
            .then(if (dragging == item.id) Modifier.graphicsLayer {
                translationX = dragX
                translationY = dragY
                alpha = 0.86f
                scaleX = 1.03f
                scaleY = 1.03f
            } else Modifier)
            .then(if (customizing) Modifier.pointerInput(item.id, placements) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = item.id
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                        val targetColumn = clampColumn((item.column + dragX / cellPx).roundToInt(), item.width)
                        val targetRow = clampRow((item.row + dragY / cellPx).roundToInt(), item.height)
                        val target = placements.firstOrNull { it.id != item.id &&
                            targetColumn in it.column until it.column + it.width &&
                            targetRow in it.row until it.row + it.height }
                        hoverJob?.cancel()
                        if (target != null) {
                            hoverJob = boardScope.launch {
                                // A deliberate two-second dwell over another tile packs it into
                                // the next vacant slot before the drag is released.
                                delay(2000)
                                if (dragging == item.id) {
                                    onPlacementsChange(MetroTileLayout.move(placements, target.id, target.column, target.row, isLandscape))
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        hoverJob?.cancel()
                        val targetColumn = clampColumn((item.column + dragX / cellPx).roundToInt(), item.width)
                        val targetRow = clampRow((item.row + dragY / cellPx).roundToInt(), item.height)
                        onPlacementsChange(MetroTileLayout.move(placements, item.id, targetColumn, targetRow, isLandscape))
                        dragging = null
                    },
                    onDragCancel = { hoverJob?.cancel(); dragging = null },
                )
            } else Modifier)
    }
    @Composable
    fun resizeHandle(item: MetroTilePlacement, horizontal: Int, vertical: Int): Modifier =
        if (!customizing) Modifier else {
            val cell = tileSize + gap
            val rendered = resizePreview?.takeIf { it.id == item.id } ?: item
            val rawX = cell * rendered.column + if (horizontal > 0) tileSize * rendered.width + gap * (rendered.width - 1) - 18.dp else 0.dp
            val rawY = cell * rendered.row + if (vertical > 0) tileSize * rendered.height + gap * (rendered.height - 1) - 18.dp else 0.dp
            // Handles ride the same spring as the tile so they never visibly detach from it.
            val springSpec = spring<Dp>(dampingRatio = 0.85f, stiffness = 650f)
            val hx by animateDpAsState(rawX, springSpec, label = "handleX")
            val hy by animateDpAsState(rawY, springSpec, label = "handleY")
            Modifier
                .offset(x = hx, y = hy)
                .size(18.dp)
                .drawBehind { drawRect(Color.White.copy(alpha = 0.92f)) }
                .pointerInput(item.id, placements) {
                    var totalX = 0f
                    var totalY = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalX = 0f
                            totalY = 0f
                            resizingId = item.id
                            resizePreview = item
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            totalX += amount.x
                            totalY += amount.y
                            val deltaX = (totalX / (tileSize + gap).toPx()).roundToInt()
                            val deltaY = (totalY / (tileSize + gap).toPx()).roundToInt()
                            // The constrained axis has a hard cap; the free axis can grow without limit.
                            val widthMax = if (isLandscape) Int.MAX_VALUE else (cap - item.column).coerceAtLeast(1)
                            val heightMax = if (isLandscape) (cap - item.row).coerceAtLeast(1) else Int.MAX_VALUE
                            val width = (if (horizontal > 0) item.width + deltaX else item.width - deltaX).coerceIn(1, widthMax)
                            val height = (if (vertical > 0) item.height + deltaY else item.height - deltaY).coerceIn(1, heightMax)
                            val column = clampColumn(if (horizontal > 0) item.column else item.column + item.width - width, width)
                            val row = clampRow(if (vertical > 0) item.row else item.row + item.height - height, height)
                            val candidate = item.copy(column = column, row = row, width = width, height = height)
                            if (MetroTileLayout.canPlace(placements, candidate, isLandscape)) {
                                resizePreview = candidate
                            }
                        },
                        onDragEnd = {
                            resizePreview?.let { preview ->
                                onPlacementsChange(MetroTileLayout.resizeRect(
                                    placements, preview.id, preview.column, preview.row,
                                    preview.width, preview.height, isLandscape,
                                ))
                            }
                            resizePreview = null
                            resizingId = null
                        },
                        onDragCancel = {
                            resizePreview = null
                            resizingId = null
                        },
                    )
                }
        }
    /* The layout engine owns every rectangle, making a custom phone grid impossible to overlap. */
    fun placement(id: String) = resizePreview?.takeIf { it.id == id } ?: placements.first { it.id == id }
    Box(modifier = Modifier.fillMaxSize()) {
        if (MetroTileLayout.isVisible(MetroTileLayout.Magisk)) {
            MagiskTile(modifier = place(placement(MetroTileLayout.Magisk)), navigationIndex = 0, onClick = onMagiskClick)
        }
        // Modules 1x1 and Apps 1x1 to the right of Magisk
        if (MetroTileLayout.isVisible(MetroTileLayout.Modules)) {
            ModuleTile(modifier = place(placement(MetroTileLayout.Modules)), navigationIndex = 2, modules = modules, onClick = onModulesClick)
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Apps)) {
            AppTile(modifier = place(placement(MetroTileLayout.Apps)), navigationIndex = 3, apps = rootApps, onClick = onAppsClick)
        }
        // Settings 2x1
        if (MetroTileLayout.isVisible(MetroTileLayout.Settings)) {
            SettingsTile(modifier = place(placement(MetroTileLayout.Settings)), navigationIndex = 4, onClick = onSettingsClick)
        }
        // Logs 1x2 (tall, right column)
        if (MetroTileLayout.isVisible(MetroTileLayout.Logs)) {
            LogTile(modifier = place(placement(MetroTileLayout.Logs)), navigationIndex = 5, logs = importantLogs, onClick = onLogsClick)
        }
        // Contributor 2x1
        if (MetroTileLayout.isVisible(MetroTileLayout.Contributors)) {
            ContributorTile(modifier = place(placement(MetroTileLayout.Contributors)), navigationIndex = 6, onClick = onContributorsClick)
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Sponsor)) {
            SponsorTile(modifier = place(placement(MetroTileLayout.Sponsor)), navigationIndex = 7, label = stringResource(R.string.metro_sponsor)) {
                viewModel.onLinkPressed(SUPPORT_URL)
            }
        }
        customTiles.forEachIndexed { index, tile ->
            val placement = placements.firstOrNull { it.id == tile.id } ?: return@forEachIndexed
            // Custom tiles are theme citizens: they ride the Apps role accent and change with
            // the theme, exactly like built-in tiles. Only the custom-theme palette can
            // recolor them (through the per-role custom colors).
            val accent = LocalMetroPalette.current.apps
            CustomTile(
                modifier = place(placement),
                tile = tile,
                accent = accent,
                navigationIndex = MetroTileCount + index,
            )
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Support)) {
            SponsorTile(modifier = place(placement(MetroTileLayout.Support)), navigationIndex = 8, label = stringResource(R.string.metro_support)) {
                viewModel.onLinkPressed(SUPPORT_URL)
            }
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Donate)) {
            SponsorTile(modifier = place(placement(MetroTileLayout.Donate)), navigationIndex = 9, label = stringResource(R.string.metro_donate)) {
                viewModel.onLinkPressed(SUPPORT_URL)
            }
        }
        // Four visible corner handles give each tile direct, spatial resize affordances.
        val displayPlacements = resizePreview?.let { preview ->
            placements.map { if (it.id == preview.id) preview else it }
        } ?: placements
        displayPlacements.forEach { item ->
            for (horizontal in listOf(-1, 1)) for (vertical in listOf(-1, 1)) {
                Box(modifier = resizeHandle(item, horizontal, vertical))
            }
        }
    }
}

@Composable
private fun CustomTile(
    modifier: Modifier,
    tile: MetroCustomTile,
    accent: com.topjohnwu.magisk.ui.theme.MetroAccent,
    navigationIndex: Int,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val color = accent.color
    val foreground = accent.onColor
    MetroTile(
        modifier = modifier,
        color = color,
        navigationIndex = navigationIndex,
        onClick = {
            val shortcut = MetroMagiskShortcuts.routeFor(tile.packageName)
            when {
                shortcut != null -> navigator.push(shortcut)
                tile.groupMembers.size > 1 -> {
                    // A group opens as its own secondary Metro destination listing every member app.
                    navigator.push(Route.TileGroup(tile.id))
                }
                else -> {
                    context.packageManager.getLaunchIntentForPackage(tile.packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(it)
                    }
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = tile.title,
                color = foreground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tile.ticker.isNotBlank()) {
                AutoRollingList(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp),
                    items = tile.ticker.split('\n', '|').map(String::trim).filter(String::isNotBlank),
                    visibleItems = 2,
                    emptyText = "",
                    textColor = foreground,
                )
            }
            if (tile.groupMembers.size > 1) {
                Text(
                    text = "${tile.groupMembers.size} apps",
                    color = foreground.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** A tile group opens as its own secondary Metro destination listing every member app. */

@Composable
private fun MetroTile(
    modifier: Modifier = Modifier,
    color: Color,
    navigationIndex: Int = 0,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val navigator = LocalMetroTileNavigator.current
    val flyProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (navigator?.leaving == true) 1f else 0f,
        animationSpec = tween(
            durationMillis = 280,
            delayMillis = (MetroTileCount - 1 - navigationIndex).coerceAtLeast(0) * 45,
        ),
        label = "metroTileFly",
    )
    // The transform must wrap background/clip too; otherwise only tile content flies while the
    // colored block remains stationary.
    val enterProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (navigator?.entering == true) 1f else 0f,
        animationSpec = tween(280, navigationIndex.coerceAtLeast(0) * 45),
        label = "metroTileEnter",
    )
    val tileModifier = modifier
        .graphicsLayer {
            val scale = if (pressed && onClick != null) 0.96f else 1f
            scaleX = scale
            scaleY = scale
            alpha = (if (pressed && onClick != null) 0.88f else 1f) *
                (1f - flyProgress) * (1f - enterProgress)
            translationX = -size.width * (flyProgress + enterProgress)
            translationY = -size.height * (flyProgress + enterProgress)
            rotationZ = -8f * flyProgress
        }
        .clip(RectangleShape)
        .background(color)
        .then(
            if (onClick != null) Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (navigator != null) navigator.navigate(navigationIndex, onClick)
                    else onClick()
                },
            ) else Modifier
        )
        .padding(8.dp)

    Box(modifier = tileModifier, content = content)
}

@Composable
private fun MagiskTile(
    modifier: Modifier,
    navigationIndex: Int,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.magisk
    MetroTile(
        modifier = modifier,
        color = accent.color,
        navigationIndex = navigationIndex,
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
    navigationIndex: Int,
    modules: List<LocalModule>,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.modules
    MetroTile(modifier = modifier, color = accent.color, navigationIndex = navigationIndex, onClick = onClick) {
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
    navigationIndex: Int,
    apps: List<String>,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.apps
    MetroTile(modifier = modifier, color = accent.color, navigationIndex = navigationIndex, onClick = onClick) {
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
private fun SettingsTile(modifier: Modifier, navigationIndex: Int, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.settings
    MetroTile(modifier = modifier, color = accent.color, navigationIndex = navigationIndex, onClick = onClick) {
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
private fun ContributorTile(modifier: Modifier, navigationIndex: Int, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.contributors
    MetroTile(
        modifier = modifier,
        color = accent.color,
        navigationIndex = navigationIndex,
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
private fun LogTile(modifier: Modifier, navigationIndex: Int, logs: List<String>, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.logs
    MetroTile(modifier = modifier, color = accent.color, navigationIndex = navigationIndex, onClick = onClick) {
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
                delay(2600L)
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
            val rowHeight = maxHeight / visibleItems
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                userScrollEnabled = false,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 1.dp),
            ) {
                itemsIndexed(rollingItems) { index, value ->
                    val offset = kotlin.math.abs(index - listState.firstVisibleItemIndex)
                    val itemAlpha = when (offset) {
                        0 -> 1f
                        1 -> 0.82f
                        else -> 0.58f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = value,
                            color = textColor.copy(alpha = itemAlpha),
                            fontSize = 11.sp,
                            fontWeight = if (offset == 0) FontWeight.Medium else FontWeight.Normal,
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
private fun SponsorTile(modifier: Modifier, navigationIndex: Int, label: String, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.sponsor
    MetroTile(
        modifier = modifier,
        color = accent.color,
        navigationIndex = navigationIndex,
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
