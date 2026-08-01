package com.topjohnwu.magisk.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Windows Phone style "flip" entrance/exit motion. Content pivots in around its top edge with a
 * non-linear ease-out, matching the tile turnstile effect. Items are staggered by [index] so a
 * list resolves top-to-bottom on enter and reverses on exit.
 */

/** WP-like ease-out (fast start, gentle settle). */
val MetroEaseOut = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

private const val FlipDurationMs = 360
private const val StaggerMs = 45L
private const val AnimatedItems = 8

/** Shared transition signal for content hosted by the secondary Metro page. */
object MetroPageExit {
    val state = androidx.compose.runtime.mutableStateOf(false)
    var active: Boolean
        get() = state.value
        set(value) { state.value = value }
}

@Composable
fun MetroFlipItem(
    index: Int,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible, MetroPageExit.active) {
        if (visible && !MetroPageExit.active) {
            delay(index.coerceAtMost(AnimatedItems - 1).coerceAtLeast(0) * StaggerMs)
            progress.animateTo(1f, tween(FlipDurationMs, easing = MetroEaseOut))
        } else {
            delay(index.coerceAtMost(AnimatedItems - 1).coerceAtLeast(0) * StaggerMs)
            progress.animateTo(0f, tween(FlipDurationMs, easing = MetroEaseOut))
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            rotationX = -90f * (1f - p)
            translationX = if (MetroPageExit.active) (1f - p) * 96f * density else 0f
            alpha = p
            transformOrigin = TransformOrigin(0.5f, 0f)
            cameraDistance = 16f * density
        }
    ) {
        content()
    }
}
