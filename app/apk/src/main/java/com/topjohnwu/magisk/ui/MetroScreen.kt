package com.topjohnwu.magisk.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MagiskUiState(
    val magiskVersion: String = "Checking...",
    val isRooted: Boolean = false,
    val modulesCount: Int = 0,
    val appsCount: Int = 0,
    val denyListCount: Int = 0
)

class MagiskViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MagiskUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchMagiskStatus()
    }

    private fun fetchMagiskStatus() {
        viewModelScope.launch {
            // NOTE: placeholder functions — replace with real implementations
            val version = "unknown"
            val rootStatus = false
            val modulesCount = 0
            val appsCount = 0
            val denyListCount = 0

            _uiState.value = _uiState.value.copy(
                magiskVersion = version,
                isRooted = rootStatus,
                modulesCount = modulesCount,
                appsCount = appsCount,
                denyListCount = denyListCount
            )
        }
    }
}

object WallpaperColorExtractor {
    fun extractDominantColor(drawable: Drawable): Int? {
        val bitmap = drawable.toBitmap()
        return Palette.from(bitmap).generate().getDominantColor(0)
    }

    private fun Drawable.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}

class SettingsViewModel : ViewModel() {
    private val _isMonetEnabled = MutableStateFlow(false)
    val isMonetEnabled = _isMonetEnabled.asStateFlow()

    fun toggleMonetEnabled() {
        viewModelScope.launch {
            _isMonetEnabled.value = !_isMonetEnabled.value
        }
    }
}
