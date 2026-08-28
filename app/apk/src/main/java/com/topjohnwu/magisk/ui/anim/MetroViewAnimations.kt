package com.topjohnwu.magisk.ui.anim

import android.view.View
import android.view.animation.PathInterpolator

/** View-system counterpart of the Compose Metro flip transition. */
object MetroViewAnimations {

    private const val DurationMs = 320L
    private const val InitialRotation = -30f
    private val EaseOut = PathInterpolator(0.1f, 0.9f, 0.2f, 1f)

    /** Enters around the horizontal centre, turning from -30° to its resting position. */
    fun flipIn(view: View) {
        view.cameraDistance = 24f * view.resources.displayMetrics.density
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.alpha = 0f
        view.rotationY = InitialRotation
        view.animate()
            .alpha(1f)
            .rotationY(0f)
            .setDuration(DurationMs)
            .setInterpolator(EaseOut)
            .start()
    }
}