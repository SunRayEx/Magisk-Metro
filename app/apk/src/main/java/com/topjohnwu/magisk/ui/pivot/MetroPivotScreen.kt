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
import com.topjohnwu.magisk.ui.anim.MetroEaseOut
import com.topjohnwu.magisk.ui.anim.MetroPageExit
import com.topjohnwu.magisk.ui.deny.DenyListViewModel
import com.topjohnwu.magisk.ui.home.HomeViewModel
import com.topjohnwu.magisk.ui.log.LogViewModel
import com.topjohnwu.magisk.ui.module.ModuleViewModel
import com.topjohnwu.magisk.ui.settings.SettingsViewModel
import com.topjohnwu.magisk.ui.superuser.SuperuserViewModel
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import kotlin.math.roundToInt
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
    denyListVM: DenyListViewModel,
    showDenyListInitially: Boolean,
    initialSection: PivotSection,
) {
    val palette = LocalMetroPalette.current
    val view = LocalView.current
    val sections = PivotSection.entries
    val pagerState = rememberPagerState(initialPage = initialSection.ordinal) { sections.size }
    val headerState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeSection = sections[pagerState.currentPage]
    // Publish the visible section so the hosting fragment (and every dialog raised through it)
    // tints itself with the accent of the tile that opened this pivot.
    MetroPivotState.section.value = activeSection
    val entrance = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    // Leaving the pivot plays the entrance backwards: the page slides back out to the right
    // and fades while the Start board behind flies its tiles back in.
    LaunchedEffect(MetroPageExit.active) {
        if (MetroPageExit.active) {
            exit.animateTo(1f, tween(durationMillis = 300, easing = MetroEaseOut))
        } else {
            exit.snapTo(0f)
        }
    }
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
            // Blend between section accents instead of snapping when pages settle.
            android.animation.ValueAnimator.ofArgb(window.statusBarColor, accent.color.toArgb()).apply {
                duration = 200
                addUpdateListener { window.statusBarColor = it.animatedValue as Int }
            }.start()
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
                alpha = entrance.value * (1f - exit.value)
                translationX = (1f - entrance.value) * 120f * density + exit.value * 120f * density
            },
    ) {
        LazyRow(
            state = headerState,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // The page follows the finger live, so the titles scale, fade and flow
                    // during the drag exactly like they do while swiping the content.
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            pagerState.dispatchRawDelta(-amount)
                        },
                        onDragEnd = {
                            val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .roundToInt().coerceIn(0, sections.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                        onDragCancel = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage) }
                        },
                    )
                }
                .padding(start = 24.dp, end = 28.dp, top = 12.dp, bottom = 8.dp),
        ) {
            sections.forEachIndexed { index, section ->
                val distance = kotlin.math.abs(index - pagerState.currentPage - pagerState.currentPageOffsetFraction)
                val selected = distance < 0.5f
                item(key = section.name) {
                    Text(
                        text = when (section) {
                            PivotSection.MAGISK -> stringResource(R.string.metro_install)
                            PivotSection.APPS -> stringResource(R.string.metro_apps)
                            PivotSection.LOGS -> stringResource(R.string.metro_logs)
                            PivotSection.MODULES -> stringResource(R.string.metro_modules)
                            PivotSection.SETTINGS -> stringResource(R.string.metro_settings)
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 1f - distance.coerceIn(0f, 1f) * 0.58f),
                        fontSize = (30f - distance.coerceIn(0f, 1f) * 6f).sp,
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
                PivotSection.APPS -> AppsSection(superuserVM, denyListVM, showDenyListInitially)
                PivotSection.LOGS -> LogsSection(logVM)
                PivotSection.MODULES -> ModulesSection(moduleVM)
                PivotSection.SETTINGS -> SettingsSection(settingsVM)
            }
        }
    }
}
