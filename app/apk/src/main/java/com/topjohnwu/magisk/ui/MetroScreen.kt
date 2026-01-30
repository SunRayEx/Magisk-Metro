#!/usr/bin/env kotlin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    // 1. 定义状态流，UI 会监听这个变量
    private val _uiState = MutableStateFlow(MagiskUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // 初始化时开始获取数据
        fetchMagiskStatus()
    }

    private fun fetchMagiskStatus() {
        viewModelScope.launch {
            // --- 真实场景：在这里调用 Magisk 底层代码 ---
            val version = getMagiskVersion()
            val rootStatus = checkRootAccess()
            val modulesCount = countModules()
            val appsCount = countApps()
            val denyListCount = countDenyList()

            // 更新状态，UI 会自动刷新
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

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.accompanist.systemuicontroller.SystemUiController

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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _isMonetEnabled = MutableStateFlow(false)
    val isMonetEnabled = _isMonetEnabled.asStateFlow()

    fun toggleMonetEnabled() {
        viewModelScope.launch {
            _isMonetEnabled.value = !_isMonetEnabled.value
        }
    }
}
