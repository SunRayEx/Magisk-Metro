package com.topjohnwu.magisk.ui.home

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.ui.anim.MetroEaseOut
import com.topjohnwu.magisk.ui.anim.MetroFlipItem
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette

@Composable
internal fun TileGroupScreen(tileId: String) {
    val context = LocalContext.current
    val tile = remember(tileId) { MetroCustomTiles.load().firstOrNull { it.id == tileId } }
    val members = tile?.groupMembers.orEmpty().distinct()

    val apps by produceState<Map<String, String>>(emptyMap(), members) {
        val pm = context.packageManager
        value = members.associateWith { packageName ->
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
        }
    }

    // The group page wears the Apps role accent of the active theme, like its tile does.
    val accent = com.topjohnwu.magisk.ui.theme.LocalMetroPalette.current.apps

    val view = LocalView.current
    LaunchedEffect(view, accent.color) {
        (view.context as? android.app.Activity)?.window?.let { window ->
            window.statusBarColor = accent.color.toArgb()
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars =
                accent.color.luminance() > 0.5f
        }
    }

    // Full Metro entrance: the page slides in from the right and settles while its rows
    // flip in underneath.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 260, easing = MetroEaseOut))
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
        Text(
            text = tile?.title ?: stringResource(R.string.metro_tile_group),
            color = accent.color,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 28.dp, top = 18.dp, bottom = 6.dp),
        )
        Text(
            text = stringResource(R.string.metro_tile_group_members, members.size),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, end = 28.dp, bottom = 10.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(members) { index, packageName ->
                MetroFlipItem(index = index, visible = true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(it)
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(34.dp)
                                .background(accent.color),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = apps[packageName] ?: packageName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = packageName,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(R.string.metro_tile_group_open),
                            color = accent.color,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (members.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.metro_tile_group_empty),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}
