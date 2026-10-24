package com.topjohnwu.magisk.ui.home

import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.topjohnwu.magisk.ui.anim.MetroEaseOut
import com.topjohnwu.magisk.ui.metro.LocalMetroRootSize
import com.topjohnwu.magisk.ui.metro.LocalMetroTransition
import com.topjohnwu.magisk.ui.metro.MetroTileSnapshot
import com.topjohnwu.magisk.ui.metro.metroTilePose
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.ui.theme.MetroWallpaper
import com.topjohnwu.magisk.ui.theme.MetroTileGlass
import com.topjohnwu.magisk.ui.theme.metroScreenBackground
import com.topjohnwu.magisk.ui.navigation.LocalNavigator
import com.topjohnwu.magisk.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    onMagiskClick: () -> Unit,
) {
    // Ground truth for "the Start screen is on top". The back stack is a SnapshotStateList,
    // so reading its top entry here is reactive and this composable recomposes on every push
    // and pop.
    val navigator = LocalNavigator.current
    val isCurrentRoute = navigator.backStack.lastOrNull() === Route.Main
    val transition = LocalMetroTransition.current

    // The one-shot cold-start intro; never replays after rotation because the played flag
    // is rememberSaveable at the activity level.
    LaunchedEffect(isCurrentRoute) {
        if (isCurrentRoute) transition?.maybeBeginIntro()
    }

    // The pivot tints the status bar with the section accent; returning to the Start screen
    // must clear it again, exactly like returning from the browser (sponsor tile) does.
    val view = LocalView.current
    val boardBackground = MaterialTheme.colorScheme.background
    LaunchedEffect(view, boardBackground, isCurrentRoute) {
        if (isCurrentRoute) {
            (view.context as? android.app.Activity)?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = boardBackground.toArgb()
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars =
                    boardBackground.luminance() > 0.5f
            }
        }
    }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    // A wide screen (landscape phone or tablet) scrolls sideways; a tall screen scrolls down.
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || isTablet
    val customTiles = remember(MetroUiState.version) { MetroCustomTiles.load() }

    val modules by produceState<List<LocalModule>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            // Read off the main thread: the module filesystem is served by a root service that
            // recycles with the idle-timeout shell, and awaiting it on the main thread throws
            // instead of blocking until the service is back.
            if (LocalModule.loaded()) {
                runCatching { LocalModule.installed() }.getOrDefault(emptyList())
            } else emptyList()
        }
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
    // The scroll must NOT sit on this BoxWithConstraints: a scrollable container measures
    // its content with an unbounded free axis, which would make maxHeight/maxWidth report
    // Infinity and blow up the board size below. The viewport is measured here and the
    // scroll is applied to the board wrapper instead.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .metroScreenBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val cap = MetroTileLayout.fixedSpan(isLandscape)
        val constrainedMax = if (isLandscape) maxHeight else maxWidth
        val tileSize = (constrainedMax - gap * (cap + 1)) / cap
        val freeViewport = if (isLandscape) maxWidth else maxHeight
        // Whole cells that fit on the free axis. The board is padded to at least this many
        // cells so a short board still fills the screen, and is free to grow past it once a
        // tile is added or dragged beyond the edge.
        val visibleFreeExtent = ((freeViewport - gap) / (tileSize + gap)).toInt().coerceAtLeast(1)
        val customIds = customTiles.map { it.id }
        val customWidths = customTiles.associate { it.id to (if (it.groupMembers.size > 1) 2 else 1) }
        // Tile positions are kept exactly as the user left them in every mode; nothing is
        // squeezed back into the visible rows when customization ends.
        var placements by remember(cap, isLandscape, MetroUiState.version, customIds, customWidths) {
            mutableStateOf(MetroTileLayout.load(isLandscape, customIds, customWidths))
        }
        // The board is exactly as long as its last tile and never shorter than the viewport,
        // so the page can scroll to a newly added tile and stops there.
        val freeExtent = MetroTileLayout.usedFreeExtent(placements, isLandscape)
            .coerceAtLeast(visibleFreeExtent)
        fun persist(next: List<MetroTilePlacement>) {
            placements = MetroTileLayout.save(next, isLandscape)
        }
        val boardWidth = if (isLandscape) tileSize * freeExtent + gap * (freeExtent - 1) else tileSize * cap + gap * (cap - 1)
        val boardHeight = if (isLandscape) tileSize * cap + gap * (cap - 1) else tileSize * freeExtent + gap * (freeExtent - 1)
        // The board is the scroll content: it is sized to exactly the tiles it holds, so the
        // scroll range covers every tile and stops at the last one instead of coasting on.
        // Saveable: returning from a secondary page must land on the same scroll offset,
        // or the board visibly reflows to the top under the returning tile.
        val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        Box(
            modifier = Modifier.then(
                if (isLandscape) Modifier.horizontalScroll(scrollState)
                else Modifier.verticalScroll(scrollState),
            ),
        ) {
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
    // Reading-order stagger for the cold-start intro; a fraction of the shared progress.
    fun introDelay(id: String): Float {
        val item = placements.firstOrNull { it.id == id } ?: return 0f
        return ((item.row * 4 + item.column) / 24f).coerceIn(0f, 0.45f)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (MetroTileLayout.isVisible(MetroTileLayout.Magisk)) {
            MagiskTile(
                modifier = place(placement(MetroTileLayout.Magisk)),
                introDelay = introDelay(MetroTileLayout.Magisk),
                onClick = onMagiskClick,
            )
        }
        // Modules 1x1 and Apps 1x1 to the right of Magisk
        if (MetroTileLayout.isVisible(MetroTileLayout.Modules)) {
            ModuleTile(
                modifier = place(placement(MetroTileLayout.Modules)),
                introDelay = introDelay(MetroTileLayout.Modules),
                modules = modules,
                onClick = onModulesClick,
            )
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Apps)) {
            AppTile(
                modifier = place(placement(MetroTileLayout.Apps)),
                introDelay = introDelay(MetroTileLayout.Apps),
                apps = rootApps,
                onClick = onAppsClick,
            )
        }
        // Settings 2x1
        if (MetroTileLayout.isVisible(MetroTileLayout.Settings)) {
            SettingsTile(
                modifier = place(placement(MetroTileLayout.Settings)),
                introDelay = introDelay(MetroTileLayout.Settings),
                onClick = onSettingsClick,
            )
        }
        // Logs 1x2 (tall, right column)
        if (MetroTileLayout.isVisible(MetroTileLayout.Logs)) {
            LogTile(
                modifier = place(placement(MetroTileLayout.Logs)),
                introDelay = introDelay(MetroTileLayout.Logs),
                logs = importantLogs,
                onClick = onLogsClick,
            )
        }
        // Contributor 2x1
        if (MetroTileLayout.isVisible(MetroTileLayout.Contributors)) {
            ContributorTile(
                modifier = place(placement(MetroTileLayout.Contributors)),
                introDelay = introDelay(MetroTileLayout.Contributors),
                onClick = onContributorsClick,
            )
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Sponsor)) {
            SponsorTile(
                modifier = place(placement(MetroTileLayout.Sponsor)),
                introDelay = introDelay(MetroTileLayout.Sponsor),
                tileId = MetroTileLayout.Sponsor,
                label = stringResource(R.string.metro_sponsor),
            ) {
                viewModel.onLinkPressed(SUPPORT_URL)
            }
        }
        customTiles.forEachIndexed { index, tile ->
            val placement = resizePreview?.takeIf { it.id == tile.id }
                ?: placements.firstOrNull { it.id == tile.id } ?: return@forEachIndexed
            // Custom tiles are theme citizens: they ride the Apps role accent and change with
            // the theme, exactly like built-in tiles. Only the custom-theme palette can
            // recolor them (through the per-role custom colors).
            val accent = LocalMetroPalette.current.apps
            CustomTile(
                modifier = place(placement),
                tile = tile,
                accent = accent,
                introDelay = introDelay(tile.id),
            )
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Support)) {
            SponsorTile(
                modifier = place(placement(MetroTileLayout.Support)),
                introDelay = introDelay(MetroTileLayout.Support),
                tileId = MetroTileLayout.Support,
                label = stringResource(R.string.metro_support),
            ) {
                viewModel.onLinkPressed(SUPPORT_URL)
            }
        }
        if (MetroTileLayout.isVisible(MetroTileLayout.Donate)) {
            SponsorTile(
                modifier = place(placement(MetroTileLayout.Donate)),
                introDelay = introDelay(MetroTileLayout.Donate),
                tileId = MetroTileLayout.Donate,
                label = stringResource(R.string.metro_donate),
            ) {
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
    introDelay: Float,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val color = accent.color
    val foreground = accent.onColor
    MetroTile(
        modifier = modifier,
        tileId = tile.id,
        transitionTitle = tile.title,
        color = color,
        onColor = foreground,
        introDelay = introDelay,
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
                    rowHeight = ListRowCompact,
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

/**
 * One Start tile. All transition motion (press lift, fly-out swirl, return swirl, intro
 * tilt) is derived inside the graphics layer from the shared [MetroTransitionController]
 * state; taps go through the controller, which locks immediately and only runs the
 * navigation action once the leave choreography has finished.
 */
@Composable
private fun MetroTile(
    modifier: Modifier = Modifier,
    tileId: String,
    transitionTitle: String,
    color: Color,
    onColor: Color,
    introDelay: Float = 0f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = LocalMetroTransition.current
    val rootSize = LocalMetroRootSize.current
    val density = LocalDensity.current
    val wallpaperOn = MetroWallpaper.active
    // Live root-space rect of this tile: feeds the fly-out pose math, the glass fill and
    // the return-target tracking (so the ghost always shrinks onto the real position,
    // whatever the layout, orientation or scroll happened to be).
    var tileRect by remember { mutableStateOf<Rect?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    // The tile's own pixels, recorded on demand into a Picture: at tap time the next
    // draw pass captures the tile as-is (glass, text, lists); a Picture survives the
    // route swap, so the overlay can replay the tile itself after the push. Placed
    // OUTERMOST so the capture is untransformed.
    val tilePicture = remember { android.graphics.Picture() }
    // Capture generation: each launch bumps the controller's counter, and every visible
    // tile re-records its pixels on the next draw pass and registers itself for the
    // full-board playback that covers the instantly-opened page.
    var recordedGeneration by remember { mutableIntStateOf(-1) }
    // Instant touch-down feedback: the press-in starts the moment the finger lands (the
    // controller's Press phase deepens it further once the click dispatches).
    val pressedNow by interactionSource.collectIsPressedAsState()
    val instantPress by animateFloatAsState(
        targetValue = if (pressedNow) 1f else 0f,
        animationSpec = tween(90, easing = MetroEaseOut),
        label = "instantPress",
    )
    // Evaluated in composition every animation frame: the parameter-based graphicsLayer
    // below then applies it. (The lambda-based layer block does not invalidate per frame
    // on this device, which made every tile pose invisible.)
    val pose = metroTilePose(transition, tileId, introDelay, tileRect, rootSize)
    val pressScale = 1f - 0.07f * instantPress

    Box(
        modifier = modifier
            .drawWithCache {
                onDrawWithContent {
                    val controller = transition
                    if (controller != null && recordedGeneration != controller.captureGeneration) {
                        recordedGeneration = controller.captureGeneration
                        val w = size.width.toInt().coerceAtLeast(1)
                        val h = size.height.toInt().coerceAtLeast(1)
                        val recording = tilePicture.beginRecording(w, h)
                        val previous = drawContext.canvas
                        drawContext.canvas = androidx.compose.ui.graphics.Canvas(recording)
                        try {
                            drawContent()
                        } finally {
                            drawContext.canvas = previous
                            tilePicture.endRecording()
                        }
                        val rect = tileRect
                        if (rect != null) {
                            controller.registerVisual(tileId, tilePicture, rect, introDelay)
                        }
                    }
                    drawContent()
                }
            }
            .onGloballyPositioned { coordinates ->
                tileRect = Rect(
                    offset = coordinates.positionInRoot(),
                    size = Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat(),
                    ),
                )
            }
            .graphicsLayer(
                alpha = pose.alpha,
                scaleX = pose.scaleX * pressScale,
                scaleY = pose.scaleY * pressScale,
                translationX = pose.translationX,
                translationY = pose.translationY,
                rotationX = pose.rotationX,
                rotationY = pose.rotationY,
                rotationZ = pose.rotationZ,
                cameraDistance = 13f * density.density,
                shadowElevation = pose.shadowElevation * density.density,
                transformOrigin = pose.transformOrigin,
            )
            .clip(RectangleShape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        val action = onClick
                        val controller = transition
                        if (controller == null || !controller.canLaunch()) {
                            // No coordinator (e.g. previews) or a transition is already
                            // running: never queue a second navigation.
                            if (controller == null) action()
                            return@clickable
                        }
                        controller.requestLaunch(
                            MetroTileSnapshot(
                                id = tileId,
                                title = transitionTitle,
                                bounds = tileRect ?: Rect(0f, 0f, 0f, 0f),
                                picture = tilePicture,
                            ),
                        ) { action() }
                    },
                ) else Modifier,
            ),
    ) {
        if (wallpaperOn) {
            // Frosted glass over the shared wallpaper: the tile content reads in WHITE on
            // top of the photo (the theme palette is overridden locally for legibility;
            // the tile's own accent survives only as the whisper tint inside the glass).
            val white = com.topjohnwu.magisk.ui.theme.MetroAccent(Color.White, Color.Black)
            val glassPalette = com.topjohnwu.magisk.ui.theme.MetroPalette(
                magisk = white, modules = white, apps = white, settings = white,
                contributors = white, logs = white, sponsor = white,
            )
            MetroTileGlass(color = color, tileRect = tileRect)
            CompositionLocalProvider(LocalMetroPalette provides glassPalette) {
                Box(modifier = Modifier.fillMaxSize().padding(8.dp), content = content)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(color))
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), content = content)
        }
    }
}

@Composable
private fun MagiskTile(
    modifier: Modifier,
    introDelay: Float,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.magisk
    MetroTile(
        modifier = modifier,
        tileId = MetroTileLayout.Magisk,
        transitionTitle = stringResource(R.string.metro_install),
        color = accent.color,
        onColor = accent.onColor,
        introDelay = introDelay,
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
    introDelay: Float,
    modules: List<LocalModule>,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.modules
    MetroTile(
        modifier = modifier,
        tileId = MetroTileLayout.Modules,
        transitionTitle = stringResource(R.string.metro_modules),
        color = accent.color,
        onColor = accent.onColor,
        introDelay = introDelay,
        onClick = onClick,
    ) {
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
                rowHeight = ListRowCompact,
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
    introDelay: Float,
    apps: List<String>,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.apps
    MetroTile(
        modifier = modifier,
        tileId = MetroTileLayout.Apps,
        transitionTitle = stringResource(R.string.metro_apps),
        color = accent.color,
        onColor = accent.onColor,
        introDelay = introDelay,
        onClick = onClick,
    ) {
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
                rowHeight = ListRowCompact,
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
private fun SettingsTile(modifier: Modifier, introDelay: Float, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.settings
    MetroTile(
        modifier = modifier,
        tileId = MetroTileLayout.Settings,
        transitionTitle = stringResource(R.string.metro_settings),
        color = accent.color,
        onColor = accent.onColor,
        introDelay = introDelay,
        onClick = onClick,
    ) {
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
private fun ContributorTile(modifier: Modifier, introDelay: Float, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.contributors
    MetroTile(
        modifier = modifier,
        tileId = MetroTileLayout.Contributors,
        transitionTitle = stringResource(R.string.metro_contributor),
        color = accent.color,
        onColor = accent.onColor,
        introDelay = introDelay,
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
private fun LogTile(modifier: Modifier, introDelay: Float, logs: List<String>, onClick: () -> Unit) {
    val accent = LocalMetroPalette.current.logs
    MetroTile(
        modifier = modifier,
        tileId = MetroTileLayout.Logs,
        transitionTitle = stringResource(R.string.metro_logs),
        color = accent.color,
        onColor = accent.onColor,
        introDelay = introDelay,
        onClick = onClick,
    ) {
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
                rowHeight = ListRowDense,
                textAlign = TextAlign.Start,
                emptyText = stringResource(R.string.metro_no_warnings),
                textColor = accent.onColor,
            )
        }
    }
}

/** Compact row heights for the rolling lists. Rows never stretch: whatever height the tile
 * has, the list packs in as many of these fixed-height rows as fit, so a tile that is pulled
 * taller fills the extra space with more items instead of just spreading the same few out.
 * The logs tile is denser because it has no big count number to make room for. */
private val ListRowCompact = 34.dp
private val ListRowDense = 22.dp
private const val ListMinRows = 2
private const val ListMaxRows = 32

@Composable
private fun AutoRollingList(
    modifier: Modifier,
    items: List<String>,
    rowHeight: Dp,
    textAlign: TextAlign = TextAlign.Start,
    emptyText: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier = modifier) {
        // The capacity is derived from the height this list actually has, so it grows in real
        // time while the tile is being resized. Rows shrink only when even the minimum would
        // not fit, so a small tile still shows a couple of items.
        val rowH = rowHeight.coerceAtMost(maxHeight / ListMinRows)
        val visibleItems = (maxHeight / rowH).toInt().coerceIn(ListMinRows, ListMaxRows)
        val canRoll = items.size > visibleItems
        val rollingItems = remember(items, visibleItems) {
            if (canRoll) List(items.size * 3) { items[it % items.size] } else items
        }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                userScrollEnabled = false,
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
                            .height(rowH)
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
private fun SponsorTile(
    modifier: Modifier,
    introDelay: Float,
    tileId: String,
    label: String,
    onClick: () -> Unit,
) {
    val accent = LocalMetroPalette.current.sponsor
    MetroTile(
        modifier = modifier,
        tileId = tileId,
        transitionTitle = label,
        color = accent.color,
        onColor = accent.onColor,
        introDelay = introDelay,
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
