package com.topjohnwu.magisk.ui.pivot

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import kotlinx.coroutines.launch

/** A top-level Metro destination selected from the home tile board. */
enum class PivotSection {
    MAGISK, APPS, LOGS, MODULES, SETTINGS;

    companion object {
        fun fromName(name: String?): PivotSection =
            entries.firstOrNull { it.name == name } ?: APPS
    }
}

@Composable
fun MetroPivotScreen(
    superuserVM: SuperuserViewModel,
    logVM: LogViewModel,
    moduleVM: ModuleViewModel,
    settingsVM: SettingsViewModel,
    homeVM: HomeViewModel,
    initialSection: PivotSection,
) {
    val palette = LocalMetroPalette.current
    val view = LocalView.current
    val sections = PivotSection.entries
    val pagerState = rememberPagerState(initialPage = initialSection.ordinal) { sections.size }
    val headerState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeSection = sections[pagerState.currentPage]
    val entrance = remember { Animatable(0f) }
    val accent = when (activeSection) {
        PivotSection.MAGISK -> palette.magisk
        PivotSection.APPS -> palette.apps
        PivotSection.LOGS -> palette.logs
        PivotSection.MODULES -> palette.modules
        PivotSection.SETTINGS -> palette.settings
    }

    // The top-level pager is the single source of truth. This keeps a tap, header drag, and a
    // content swipe in lockstep and lets users return to a neighbouring Metro section naturally.
    LaunchedEffect(pagerState.currentPage) {
        headerState.animateScrollToItem(pagerState.currentPage)
    }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 300, delayMillis = 90))
    }
    LaunchedEffect(accent) {
        (view.context as? Activity)?.window?.let { window ->
            window.statusBarColor = accent.color.toArgb()
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars =
                accent.onColor.luminance() < 0.5f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .graphicsLayer {
                alpha = entrance.value
                translationX = (1f - entrance.value) * 120f * density
            },
    ) {
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
                            }.coerceIn(0, sections.lastIndex)
                            dragDistance = 0f
                            if (target != pagerState.currentPage) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        },
                        onDragCancel = { dragDistance = 0f },
                    )
                }
                .padding(start = 24.dp, end = 28.dp, top = 12.dp, bottom = 8.dp),
        ) {
            sections.forEachIndexed { index, section ->
                val selected = pagerState.currentPage == index
                item(key = section.name) {
                    Text(
                        text = when (section) {
                            PivotSection.MAGISK -> stringResource(R.string.metro_magisk_manager)
                            PivotSection.APPS -> stringResource(R.string.metro_apps)
                            PivotSection.LOGS -> stringResource(R.string.metro_logs)
                            PivotSection.MODULES -> stringResource(R.string.metro_modules)
                            PivotSection.SETTINGS -> stringResource(R.string.metro_settings)
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else 0.42f),
                        fontSize = if (selected) 30.sp else 24.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Light,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            when (sections[page]) {
                PivotSection.MAGISK -> MagiskSection(homeVM)
                PivotSection.APPS -> AppsSection(superuserVM)
                PivotSection.LOGS -> LogsSection(logVM)
                PivotSection.MODULES -> ModulesSection(moduleVM)
                PivotSection.SETTINGS -> SettingsSection(settingsVM)
            }
        }
    }
}
