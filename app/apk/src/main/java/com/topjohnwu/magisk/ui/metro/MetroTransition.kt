package com.topjohnwu.magisk.ui.metro

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.topjohnwu.magisk.ui.anim.MetroDiveEase
import com.topjohnwu.magisk.ui.anim.MetroEaseOut
import com.topjohnwu.magisk.ui.anim.MetroFlyEase
import com.topjohnwu.magisk.ui.navigation.Navigator
import com.topjohnwu.magisk.ui.theme.MetroWallpaperBackdrop
import com.topjohnwu.magisk.ui.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Windows Phone 10 style home <-> secondary-page transition coordinator.
 *
 * The tapped tile never morphs. The choreography per the spec:
 *
 *  Leaving home:  Press (the tile presses in and holds) -> Leaving (the other tiles
 *                 page-turn fully OFF SCREEN one by one in reading order while the tapped
 *                 tile simply fades to transparent in place) -> push -> Arriving (the
 *                 page fades in over the now-empty board).
 *  Returning:    ReturnPrepare (the page fades out) -> pop -> Returning (the tapped tile
 *                 fades back to its original look first, then the other tiles flip back
 *                 in one at a time, in the same reading order).
 *  External app: Press -> Leaving -> launch intent -> ExternalLaunch (everything quietly
 *                 restores beneath the opened app).
 *
 * The pushed route is learned by diffing the Navigator before/after the launch action, so
 * every tile (built-in, custom, group, shortcut, sponsor link) funnels through the same
 * machine without knowing anything about routes. Returns are driven through
 * [Navigator.popInterceptor], so system back, in-page back buttons and programmatic pops
 * all play the same animation.
 */
sealed interface MetroTransitionPhase {
    data object Idle : MetroTransitionPhase
    data object Intro : MetroTransitionPhase
    data class Press(val tileId: String) : MetroTransitionPhase
    data class Leaving(val tileId: String) : MetroTransitionPhase
    data class Arriving(val tileId: String) : MetroTransitionPhase
    data class ReturnPrepare(val tileId: String) : MetroTransitionPhase
    data class Returning(val tileId: String) : MetroTransitionPhase
    data class ExternalLaunch(val tileId: String) : MetroTransitionPhase
}

/** Everything needed to carry the tapped tile through a transition. [picture] is the
 *  tile's OWN recorded pixels (glass, text, lists) and [bounds] its root-space rect at tap
 *  time; together they let the tile itself — never a colour block — keep morphing across
 *  the route swap, and land back exactly where it came from. */
class MetroTileSnapshot(
    val id: String,
    val title: String,
    val bounds: Rect,
    val picture: android.graphics.Picture?,
)

/** Shared, non-linear timing for every phase. Durations live here only. */
private val MetroReverseEase = Easing { fraction ->
    1f - MetroEaseOut.transform(1f - fraction)
}

// The return path is intentionally softer than the enter dive: less front-loaded motion
// keeps the tile from snapping into place while still preserving the same destination rect.
private val MetroReturnDiveEase = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

object MetroTransitionSpec {
    /** Phase A: the tapped tile is pressed in (animated to ~0.80 scale) and holds. */
    const val PRESS_MS = 345

    /** Phase B: other tiles page-turn off screen one by one in reading order. Each tile's
     *  own flight occupies [TILE_FLIGHT_SPAN] of this window; starts are staggered. */
    const val OTHERS_OUT_MS = 1400

    /** The tapped tile MORPHS (non-uniform stretch) from its own rect to the full screen;
     *  it stays fully opaque — the transparency starts only after the push, over the
     *  already-open destination page. */
    const val ZOOM_DELAY_MS = 335
    const val ZOOM_MS = 900

    /** The tile turns transparent while it finishes growing; the page is already open
     *  beneath it, so this reads as the tile dissolving into the page mid-zoom. */
    const val OVERLAY_FADE_MS = 700

    /** The destination page fades in over the emptied board (its own page shell). */
    const val PAGE_APPEAR_MS = 470

    /** After the leave choreography ends on a blank screen, the page slides in from the
     *  right across the full screen width. */
    const val PAGE_ENTER_SLIDE_MS = 480

    /** Returning: the page reverses the enter slide before the board comes back. */
    const val PAGE_EXIT_MS = 432

    /** Return: the fullscreen tile capture fades in WHILE the page reverses its enter
     *  slide, so the handover remains a continuous crossfade. */
    const val RETURN_TAKEOVER_MS = 220

    /** Return: the tapped tile fades back to its original look first, then the other
     *  tiles flip back in one at a time, in the same reading order. */
    const val RETURN_MS = 2400

    /** Fraction of [RETURN_MS] at which the tapped tile is fully opaque again. */
    const val RETURN_TILE_IN_END = 0.52f

    /** Fraction of the return timeline at which the other tiles start flipping back in. */
    const val RETURN_OTHERS_START = 0.52f

    const val RESTORE_MS = 680

    /** How long the pressed tile hovers waiting for the destination's first rendered
     *  frame before the choreography proceeds anyway (safety fallback). */
    const val PAGE_READY_TIMEOUT_MS = 900
    const val INTRO_MS = 1100

    /** Fraction of a shared phase window that ONE tile's flip occupies (the rest is the
     *  stagger between consecutive tiles — the "one by one" domino feel). */
    const val TILE_FLIGHT_SPAN = 0.42f
}

/** One captured board tile for the full-board playback after the instant push. */
class MetroTileVisual(
    val id: String,
    val picture: android.graphics.Picture,
    val rect: Rect,
    val introDelay: Float,
)

/** Internal command envelope for the composition-driven sequence runner. */
private sealed interface MetroTransitionCommand {
    data class Launch(val tile: MetroTileSnapshot, val action: () -> Unit) : MetroTransitionCommand
    data class Return(val tile: MetroTileSnapshot) : MetroTransitionCommand
    data object Intro : MetroTransitionCommand
}

class MetroTransitionController(
    private val context: Context,
    playedIntro: Boolean = false,
) {
    var phase by mutableStateOf<MetroTransitionPhase>(MetroTransitionPhase.Idle)
        private set
    var sourceTile by mutableStateOf<MetroTileSnapshot?>(null)
        private set

    /** 0..1 shared progress of the other-tiles fly-out. */
    var othersOut by mutableFloatStateOf(0f)
        private set

    /** 0..1 press-in progress of the tapped tile during the Press phase. */
    var pressIn by mutableFloatStateOf(0f)
        private set

    /** 0..1 morph of the tapped tile from its own rect to the full screen (opaque). */
    var tileZoom by mutableFloatStateOf(0f)
        private set

    /** Opacity of the fullscreen tile capture while the open page shows through it. */
    var overlayAlpha by mutableFloatStateOf(1f)
        private set

    /** 0..1 exit progress applied to the visible secondary page. */
    var pageExit by mutableFloatStateOf(0f)
        private set

    /** 0..1 return progress: tile fade-back + other tiles flipping home. */
    var returnIn by mutableFloatStateOf(0f)
        private set

    /** 0..1 cold-start board intro progress. */
    var introProgress by mutableFloatStateOf(0f)
        private set

    /**
     * Every visible tile's captured pixels, registered right after a tap. The push is
     * nearly immediate (instant response), so the whole leave choreography (press,
     * cascade, morph) replays from THIS list on the overlay above the already-open page,
     * which stays covered until the animation completes.
     */
    var boardVisuals by mutableStateOf<List<MetroTileVisual>>(emptyList())
        private set

    /** Bumped on every launch; visible tiles re-record their pixels when it changes. */
    var captureGeneration by mutableIntStateOf(0)
        private set

    /**
     * The destination page reports its first rendered frame here; the leave
     * choreography waits on it so the zoom never outruns the page. CONFLATED: only the
     * latest signal matters, and a stray report while nobody waits is dropped.
     */
    private val pageReady = Channel<Unit>(Channel.CONFLATED)

    var onIntroPlayed: (() -> Unit)? = null

    /** The route this controller's leave transition pushed, until its return completes. */
    private var launchedRoute: Any? = null

    /**
     * Compose animations suspend on a MonotonicFrameClock, which only composition-launched
     * coroutines carry. The controller must outlive every NavDisplay entry, so it cannot
     * own the coroutines itself: sequences are enqueued here and pumped by [pump], which the
     * activity root calls from a LaunchedEffect — composition scope, composition lifetime.
     */
    private val commands = Channel<MetroTransitionCommand>(Channel.UNLIMITED)
    private var navigator: Navigator? = null
    private var introPlayed = playedIntro

    fun bind(navigator: Navigator) {
        this.navigator = navigator
        navigator.popInterceptor = { top -> interceptPop(top) }
    }

    /** Suspends pumping transitions; call once from an activity-root LaunchedEffect. */
    suspend fun CoroutineScope.pump() {
        for (command in commands) {
            try {
                when (command) {
                    is MetroTransitionCommand.Launch -> runLaunch(this, command)
                    is MetroTransitionCommand.Return -> runReturn(this, command)
                    MetroTransitionCommand.Intro -> runIntro()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // A transition must never wedge the UI in a half-flown state.
                resetAll()
            }
        }
    }

    /** True while a transition is in flight; the board uses it to swallow extra taps. */
    fun canLaunch(): Boolean = phase == MetroTransitionPhase.Idle

    /**
     * Tile tap entry point. The phase lock is taken synchronously (before the command is
     * even queued) so a same-frame second tap can never enqueue a second transition; the
     * navigation action runs only once the leave choreography completes.
     */
    fun requestLaunch(tile: MetroTileSnapshot, action: () -> Unit) {
        if (!canLaunch()) return
        sourceTile = tile
        boardVisuals = emptyList()
        captureGeneration += 1
        phase = MetroTransitionPhase.Press(tile.id)
        commands.trySend(MetroTransitionCommand.Launch(tile, action))
    }

    /** Called by the destination page's shell once its first frame has rendered. */
    fun reportPageRendered() {
        pageReady.trySend(Unit)
    }

    /** Called by each visible tile's draw pass right after [captureGeneration] bumps. */
    fun registerVisual(
        id: String,
        picture: android.graphics.Picture,
        rect: Rect,
        introDelay: Float,
    ) {
        if (phase == MetroTransitionPhase.Idle || phase is MetroTransitionPhase.Intro) return
        if (boardVisuals.any { it.id == id }) return
        boardVisuals = boardVisuals + MetroTileVisual(id, picture, rect, introDelay)
    }

    /** Cold-start board intro; plays once per activity (rotation-safe via [playedIntro]). */
    fun maybeBeginIntro() {
        if (phase != MetroTransitionPhase.Idle || introPlayed) return
        introPlayed = true
        onIntroPlayed?.invoke()
        phase = MetroTransitionPhase.Intro
        commands.trySend(MetroTransitionCommand.Intro)
    }

    /**
     * Installed as [Navigator.popInterceptor]: the secondary page a tile opened pops
     * through the return choreography; anything else pops untouched.
     */
    private fun interceptPop(top: Any): Boolean = when {
        // Busy: swallow repeat backs so no double navigation happens mid-transition.
        phase is MetroTransitionPhase.Press ||
            phase is MetroTransitionPhase.Leaving ||
            phase is MetroTransitionPhase.Arriving ||
            phase is MetroTransitionPhase.ExternalLaunch ||
            phase is MetroTransitionPhase.ReturnPrepare ||
            phase is MetroTransitionPhase.Returning -> true

        phase == MetroTransitionPhase.Idle &&
            launchedRoute != null && top == launchedRoute -> {
            beginReturn()
            true
        }

        else -> false
    }

    private fun beginReturn() {
        val tile = sourceTile ?: return
        if (launchedRoute == null) return
        launchedRoute = null
        phase = MetroTransitionPhase.ReturnPrepare(tile.id)
        // The returning tile starts invisible on the board; the fullscreen capture morphs
        // back onto it (see the overlay), then the real tile takes over.
        tileZoom = 0f
        overlayAlpha = 0f
        pageExit = 0f
        commands.trySend(MetroTransitionCommand.Return(tile))
    }

    private suspend fun runLaunch(scope: CoroutineScope, command: MetroTransitionCommand.Launch) {
        val tile = command.tile
        if (animatorScale() == 0f) {
            // System animations disabled: jump to the end states and navigate at once.
            command.action()
            launchedRoute = null
            resetAll()
            return
        }
        // Phase A starts immediately: the tapped tile presses in (the overlay mirrors it
        // once the push has happened). One or two frames later every visible tile has
        // re-recorded its pixels for the board playback.
        val pressAnim = scope.launch {
            Animatable(0f).apply {
                animateTo(1f, tween(MetroTransitionSpec.PRESS_MS, easing = MetroEaseOut)) {
                    pressIn = value
                }
            }
        }
        delay(50)
        // Clear a signal left by an earlier page before opening the destination. The new
        // page reports its first frame synchronously from its own composition lifecycle.
        pageReady.tryReceive()
        // Instant response: the destination opens NOW. It stays completely covered by the
        // board playback until the choreography finishes, then fades in.
        val before = navigator?.current()
        command.action()
        launchedRoute = navigator?.current()?.takeIf { it !== before && it != Route.Main }
        // Hover wait: the tile stays pressed while the destination page loads beneath the
        // playback overlay; the first frame after the action releases the choreography.
        if (launchedRoute != null) {
            withTimeoutOrNull(MetroTransitionSpec.PAGE_READY_TIMEOUT_MS.toLong()) {
                pageReady.receive()
            }
        }
        // Phase B (replayed on the overlay above the open page): the board flips off
        // screen one page at a time while the tapped tile MORPHS to the exact fullscreen
        // rect — opaque, its own stretched content, the page hidden beneath.
        phase = MetroTransitionPhase.Leaving(tile.id)
        val others = scope.launch { animateOthersTo(1f, MetroTransitionSpec.OTHERS_OUT_MS) }
        val morph = scope.launch {
            delay(MetroTransitionSpec.ZOOM_DELAY_MS.toLong())
            animateTileZoomTo(1f, MetroTransitionSpec.ZOOM_MS)
        }
        pressAnim.join()
        others.join()
        morph.join()
        if (launchedRoute == null) {
            // An external app/window took over: quietly restore the board beneath it.
            phase = MetroTransitionPhase.ExternalLaunch(tile.id)
            animateTileZoomTo(0f, MetroTransitionSpec.RESTORE_MS)
            animateOthersTo(0f, MetroTransitionSpec.RESTORE_MS)
        }
        resetProgress()
    }

    private suspend fun runReturn(scope: CoroutineScope, command: MetroTransitionCommand.Return) {
        val tile = command.tile
        if (animatorScale() == 0f) {
            navigator?.popImmediate()
            resetAll()
            return
        }
        // The page fades out while the fullscreen tile capture fades IN over it — a
        // crossfade handover instead of a hard cut, then the capture morphs home.
        // Fade the page out and bring the captured tile in at the same time. The page stays
        // composed until this handover is complete, so returning is a continuous crossfade.
        val takeover = scope.launch {
            animateOverlayAlphaTo(1f, MetroTransitionSpec.PAGE_EXIT_MS)
        }
        animatePageExitTo(1f, MetroTransitionSpec.PAGE_EXIT_MS)
        takeover.join()
        navigator?.popImmediate()
        // The board composes from scratch at the pop (data, glass painters, lists); give
        // that burst two frames to land before the morph starts, or the animation's
        // opening beats drop frames and read as stutter.
        withFrameNanos { }
        withFrameNanos { }
        // The board was hidden throughout ReturnPrepare, so it never renders a default
        // frame before the capture handoff. Both board poses and capture now read
        // [returnIn], with no fixed-timing page guess involved.
        phase = MetroTransitionPhase.Returning(tile.id)
        returnIn = 0f
        Animatable(0f).apply {
            // Keep the shared reverse timeline linear. Each pose applies the exact
            // time-reversed easing of its matching enter pose; easing this value again
            // would make the tile shrink with an unintended spring-like slowdown.
            animateTo(1f, tween(MetroTransitionSpec.RETURN_MS)) {
                returnIn = value
            }
        }
        resetAll()
    }

    private suspend fun runIntro() {
        try {
            Animatable(0f).apply {
                animateTo(1f, tween(MetroTransitionSpec.INTRO_MS, easing = MetroEaseOut)) {
                    introProgress = value
                }
            }
        } finally {
            introProgress = 1f
            if (phase is MetroTransitionPhase.Intro) phase = MetroTransitionPhase.Idle
        }
    }

    /** Progress-only reset: the leave completed, the pushed route is still on top. */
    private fun resetProgress() {
        phase = MetroTransitionPhase.Idle
        pressIn = 0f
        othersOut = 0f
        tileZoom = 0f
        overlayAlpha = 1f
        pageExit = 0f
        returnIn = 0f
        boardVisuals = emptyList()
    }

    private fun resetAll() {
        resetProgress()
        sourceTile = null
        launchedRoute = null
    }

    private fun animatorScale(): Float = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)

    private suspend fun animateOthersTo(target: Float, ms: Int) {
        Animatable(othersOut).apply {
            animateTo(target, tween(ms, easing = MetroEaseOut)) { othersOut = value }
        }
    }

    private suspend fun animateTileZoomTo(target: Float, ms: Int) {
        Animatable(tileZoom).apply {
            animateTo(target, tween(ms, easing = MetroDiveEase)) { tileZoom = value }
        }
    }

    private suspend fun animateOverlayAlphaTo(target: Float, ms: Int) {
        Animatable(overlayAlpha).apply {
            animateTo(target, tween(ms, easing = MetroEaseOut)) { overlayAlpha = value }
        }
    }

    private suspend fun animatePageExitTo(target: Float, ms: Int) {
        Animatable(pageExit).apply {
            animateTo(target, tween(ms, easing = MetroEaseOut)) { pageExit = value }
        }
    }
}

/** The compose root's px size; provided by [com.topjohnwu.magisk.ui.MainActivity]. */
val LocalMetroRootSize = staticCompositionLocalOf { IntSize.Zero }

val LocalMetroTransition = staticCompositionLocalOf<MetroTransitionController?> { null }

/**
 * Graphics transform of one board tile for the current phase. Evaluated IN COMPOSITION
 * (the caller reads the controller's snapshot states at its call site) and applied through
 * the parameter-based graphicsLayer, which updates on every recomposition — the reliable
 * per-frame path on devices where the lambda-based layer block does not invalidate per
 * animation frame.
 */
class MetroTilePose(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val rotationZ: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val shadowElevation: Float = 0f,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
)

/** Reading-order fraction of a tile (0 = first, 1 = last), derived from its intro delay. */
private fun orderFraction(introDelay: Float): Float = (introDelay / 0.45f).coerceIn(0f, 1f)

/**
 * One-by-one cascade: within the shared phase window, a tile's own flight occupies only
 * [MetroTransitionSpec.TILE_FLIGHT_SPAN]; its start is staggered by reading order, so the
 * board leaves (and returns) like dominoes — one tile clearly following the previous.
 */
private fun metroOrderStagger(progress: Float, order: Float): Float {
    val flight = MetroTransitionSpec.TILE_FLIGHT_SPAN
    val start = order * (1f - flight)
    return ((progress - start) / flight).coerceIn(0f, 1f)
}

/**
 * The tapped tile's MORPH pose at progress [z] (0 = resting on the board, 1 = exactly the
 * fullscreen rect): a non-uniform stretch (形变) anchored at the tile's top-left corner,
 * which simultaneously slides to the screen origin. At [z] = 1 the tile's rect IS the
 * screen rect, so the fullscreen capture taken there lines up pixel-perfectly. The tile
 * stays fully opaque during the morph — transparency happens only after the push, over
 * the already-open page.
 */
private fun metroMorphPose(z: Float, tile: Rect?, rootSize: IntSize): MetroTilePose {
    val p = z.coerceIn(0f, 1f)
    if (p <= 0f) return MetroTilePose()
    if (tile == null || rootSize.width <= 0 || rootSize.height <= 0) {
        return MetroTilePose()
    }
    val e = MetroDiveEase.transform(p)
    val scaleX = 1f + (rootSize.width / tile.width - 1f) * e
    val scaleY = 1f + (rootSize.height / tile.height - 1f) * e
    return MetroTilePose(
        // 从开始放大就同步渐变透明: the transparency starts with the zoom itself, so the
        // page (open since the instant push, loading on its own) shows through the
        // still-growing tile for the whole flight.
        alpha = 1f - e,
        scaleX = scaleX,
        scaleY = scaleY,
        translationX = -tile.left * e,
        translationY = -tile.top * e,
        transformOrigin = TransformOrigin(0f, 0f),
    )
}

fun metroTilePose(
    controller: MetroTransitionController?,
    tileId: String,
    introDelay: Float,
    tileRect: Rect?,
    rootSize: IntSize,
): MetroTilePose {
    if (controller == null) return MetroTilePose()
    val src = controller.sourceTile
    val order = orderFraction(introDelay)
    return when (val p = controller.phase) {
        is MetroTransitionPhase.Press -> if (p.tileId == tileId) {
            // 压陷并悬停: the tile presses in clearly (~0.80) and holds before the cascade.
            val press = controller.pressIn
            MetroTilePose(
                scaleX = 1f - 0.20f * press,
                scaleY = 1f - 0.20f * press,
                shadowElevation = 20f * press,
            )
        } else MetroTilePose()

        is MetroTransitionPhase.Leaving,
        is MetroTransitionPhase.Arriving,
        is MetroTransitionPhase.ExternalLaunch -> if (src != null) {
            if (src.id == tileId) {
                // 本体放大并形变为全屏: the real tile morphs, opaque, content and all.
                metroMorphPose(controller.tileZoom, tileRect, rootSize)
            } else {
                // 翻页式外旋: tiles flip fully OFF SCREEN toward the upper-left, one by one.
                metroPageTurnPoseAt(
                    metroOrderStagger(controller.othersOut, order), tileRect,
                )
            }
        } else MetroTilePose()

        is MetroTransitionPhase.Returning -> if (src != null) {
            if (src.id == tileId) {
                // 退回时渐变为原型: the fullscreen capture morphs back onto the tile
                // (overlay); the real tile fades in beneath it and takes over on landing.
                val handoff = ((controller.returnIn - 0.96f) / 0.04f).coerceIn(0f, 1f)
                MetroTilePose(
                    // Crossfade only after the capture has settled on the source rect. Both
                    // layers then share the same geometry, so the final handoff has no blank
                    // frame or double flash.
                    alpha = handoff,
                )
            } else {
                // The tapped tile is home; the board then flips back in one tile at a time.
                val flight = MetroTransitionSpec.TILE_FLIGHT_SPAN
                val span = 1f - MetroTransitionSpec.RETURN_OTHERS_START
                val start = MetroTransitionSpec.RETURN_OTHERS_START + order * (span - flight)
                val t = 1f - (((controller.returnIn - start) / flight).coerceIn(0f, 1f))
                metroPageTurnPoseReverseAt(t, tileRect)
            }
        } else MetroTilePose()

        is MetroTransitionPhase.Intro -> {
            val e = MetroEaseOut.transform(
                ((controller.introProgress - introDelay) / (1f - introDelay)).coerceIn(0f, 1f),
            )
            MetroTilePose(
                alpha = e,
                scaleX = 0.9f + 0.1f * e,
                scaleY = 0.9f + 0.1f * e,
                rotationY = (1f - e) * -20f,
            )
        }

        MetroTransitionPhase.Idle -> MetroTilePose()
        // The home board can be recomposed immediately after popImmediate(), while the
        // exiting page is still in its return handoff. Keep every live tile hidden until
        // Returning starts so no default pose flashes through the capture layer.
        is MetroTransitionPhase.ReturnPrepare -> MetroTilePose(alpha = 0f)
    }
}

/**
 * The page-turn pose at parameter [t] (0 = rest, 1 = gone): the tile pivots around its
 * top-left "spine", tumbles toward the upper-left, shrinks and fades — and at t = 1 it is
 * fully OUTSIDE the screen (the travel is derived from the tile's own on-screen position,
 * so tiles scrolled anywhere on the board still exit completely).
 */
private fun metroPageTurnPoseAt(t: Float, tile: Rect?): MetroTilePose {
    val e = MetroFlyEase.transform(t.coerceIn(0f, 1f))
    val exitX = (tile?.left ?: 0f) + (tile?.width ?: 0f) * 1.05f
    val exitY = (tile?.top ?: 0f) + (tile?.height ?: 0f) * 1.05f
    return metroPageTurnPoseWithProgress(e, exitX, exitY)
}

/** The same page-turn path traversed from fully off-screen back to rest. */
private fun metroPageTurnPoseReverseAt(t: Float, tile: Rect?): MetroTilePose {
    val progress = t.coerceIn(0f, 1f)
    val e = 1f - MetroFlyEase.transform(1f - progress)
    val exitX = (tile?.left ?: 0f) + (tile?.width ?: 0f) * 1.05f
    val exitY = (tile?.top ?: 0f) + (tile?.height ?: 0f) * 1.05f
    return metroPageTurnPoseWithProgress(e, exitX, exitY)
}

private fun metroPageTurnPoseWithProgress(e: Float, exitX: Float, exitY: Float): MetroTilePose {
    return MetroTilePose(
        translationX = -exitX * e,
        translationY = -exitY * e,
        rotationZ = -10f * e,
        rotationX = 10f * e,
        rotationY = -38f * e,
        scaleX = 1f - 0.22f * e,
        scaleY = 1f - 0.22f * e,
        alpha = (1f - 1.12f * e).coerceIn(0f, 1f),
        transformOrigin = TransformOrigin(0f, 0f),
    )
}

/**
 * Shared secondary-page shell: a quick appear fade when pushed (the "page appears as the
 * tile dissolves" half of the transition) plus the coordinated exit fade on return.
 */
@Composable
fun Modifier.metroPageShell(): Modifier {
    val controller = LocalMetroTransition.current
    // A page composed while a leave transition is in flight is the transition's target:
    // it stays parked OFF-SCREEN right while the choreography plays; when the leave ends
    // on a blank screen, the whole page slides in from the right.
    val entersAfterTransition = remember {
        val phase = controller?.phase
        phase is MetroTransitionPhase.Press ||
            phase is MetroTransitionPhase.Leaving ||
            phase is MetroTransitionPhase.ExternalLaunch
    }
    val appear = remember { Animatable(0f) }
    val slideInWidthPx = LocalMetroRootSize.current.width.toFloat()
    LaunchedEffect(Unit) {
        // First rendered frame = the page has loaded: release the hovering tile.
        withFrameNanos { }
        controller?.reportPageRendered()
        if (entersAfterTransition && controller != null) {
            androidx.compose.runtime.snapshotFlow { controller.phase }
                .first { it is MetroTransitionPhase.Idle }
            appear.animateTo(1f, tween(MetroTransitionSpec.PAGE_ENTER_SLIDE_MS, easing = MetroEaseOut))
        } else {
            appear.animateTo(1f, tween(MetroTransitionSpec.PAGE_APPEAR_MS, easing = MetroEaseOut))
        }
    }
    // Read every animated value in COMPOSITION and apply them through the parameter-based
    // graphicsLayer: on this device the lambda-based layer block does not invalidate per
    // frame, which made the page's exit fade stutter in steps instead of animating.
    val appearValue = appear.value
    val exit = controller?.pageExit ?: 0f
    // Keep the exiting page composed while it reverses the exact full-width slide used
    // when it entered. The page remains visible underneath the returning tile capture
    // until the handoff is complete.
    return Modifier.graphicsLayer(
        alpha = appearValue * (1f - exit),
        translationX = if (entersAfterTransition) {
            (1f - appearValue) * slideInWidthPx + exit * slideInWidthPx
        } else {
            exit * slideInWidthPx
        },
    )
}

/**
 * The transition stage above the instantly-opened destination page. After a tap the page
 * is pushed within a couple of frames (instant response) but stays COVERED: this overlay
 * replays the captured board — wallpaper backdrop plus every tile's own pixels — running
 * the press, the one-by-one page-turn cascade and the tapped tile's fullscreen morph.
 * Only when the choreography completes does the overlay fade, revealing the page that has
 * been open underneath the whole time. On return it morphs the tapped tile's capture back
 * onto its live rect and hands over to the real board.
 */
@Composable
fun MetroTileContinuum(
    controller: MetroTransitionController,
    modifier: Modifier = Modifier,
) {
    val src = controller.sourceTile ?: return
    val phase = controller.phase
    if (phase is MetroTransitionPhase.Idle || phase is MetroTransitionPhase.Intro) return
    val rootSize = LocalMetroRootSize.current
    if (rootSize.width == 0 || rootSize.height == 0) return
    val density = LocalDensity.current
    val rootRect = Rect(0f, 0f, rootSize.width.toFloat(), rootSize.height.toFloat())

    when (phase) {
        is MetroTransitionPhase.Press,
        is MetroTransitionPhase.Leaving,
        is MetroTransitionPhase.ExternalLaunch -> {
            // Tile playback on a TRANSPARENT stage: the page is open underneath and
            // loads on its own — the flying tiles simply pass over it, and the tapped
            // tile fades as it grows, so the page shows through from early on.
            Box(modifier = modifier.fillMaxSize()) {
                controller.boardVisuals.forEach { visual ->
                    val pose = metroTilePose(
                        controller, visual.id, visual.introDelay, visual.rect, rootSize,
                    )
                    TileReplay(
                        picture = visual.picture,
                        rect = visual.rect,
                        pose = pose,
                        density = density,
                    )
                }
            }
        }

        is MetroTransitionPhase.Arriving -> {}

        is MetroTransitionPhase.ReturnPrepare,
        is MetroTransitionPhase.Returning -> {
            // 退回时渐变为原型: the capture crossfades in over the exiting page
            // (ReturnPrepare), then morphs from fullscreen back onto the tile's live rect
            // and hands over to the real board on landing.
            val picture = src.picture ?: return
            val p = if (phase is MetroTransitionPhase.Returning) {
                (controller.returnIn / MetroTransitionSpec.RETURN_TILE_IN_END).coerceIn(0f, 1f)
            } else 0f
            val handoff = if (phase is MetroTransitionPhase.Returning) {
                ((controller.returnIn - 0.96f) / 0.04f).coerceIn(0f, 1f)
            } else 0f
            val rect = lerp(rootRect, src.bounds, MetroReturnDiveEase.transform(p))
            if (controller.overlayAlpha <= 0.01f) return
            // 缩小的同时渐变透明: the capture fades out WHILE it shrinks home, crossfading
            // with the real tile (which fades in underneath at the same rate) — the exact
            // mirror of the leave's zoom-and-fade, with no opaque handover anywhere.
            TileReplay(
                picture = picture,
                rect = rect,
                pose = MetroTilePose(
                    // Return from a fully transparent capture: colour appears as the capture
                    // shrinks along the exact reverse path, then crossfades to the live tile.
                    alpha = controller.overlayAlpha * MetroReturnDiveEase.transform(p) *
                        (1f - handoff),
                ),
                density = density,
                modifier = modifier,
            )
        }
    }
}

/** Draws one captured tile at [rect] with the transition [pose] applied. */
@Composable
private fun TileReplay(
    picture: android.graphics.Picture,
    rect: Rect,
    pose: MetroTilePose,
    density: Density,
    modifier: Modifier = Modifier,
) {
    if (pose.alpha <= 0.01f) return
    Box(
        modifier = modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .layout { measurable, _ ->
                val w = rect.width.roundToInt().coerceAtLeast(1)
                val h = rect.height.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .graphicsLayer(
                alpha = pose.alpha,
                scaleX = pose.scaleX,
                scaleY = pose.scaleY,
                translationX = pose.translationX,
                translationY = pose.translationY,
                rotationX = pose.rotationX,
                rotationY = pose.rotationY,
                rotationZ = pose.rotationZ,
                cameraDistance = 13f * density.density,
                shadowElevation = pose.shadowElevation * density.density,
                transformOrigin = pose.transformOrigin,
            )
            .drawBehind {
                if (picture.width > 0 && picture.height > 0) {
                    val canvas = drawContext.canvas.nativeCanvas
                    canvas.save()
                    canvas.scale(
                        size.width / picture.width,
                        size.height / picture.height,
                    )
                    canvas.drawPicture(picture)
                    canvas.restore()
                }
            },
    )
}
