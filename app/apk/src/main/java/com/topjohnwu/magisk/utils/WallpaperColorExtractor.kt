package com.topjohnwu.magisk.ui.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette

object WallpaperColorExtractor {

    fun extractDominantColor(drawable: Drawable): Int? {
        val bitmap = drawable.toBitmap()
        return Palette.from(bitmap).generate().getDominantColor(0)
    }

    private fun Drawable.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}