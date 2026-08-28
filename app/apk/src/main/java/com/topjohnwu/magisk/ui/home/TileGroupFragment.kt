package com.topjohnwu.magisk.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.graphics.Color
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
import androidx.navigation.fragment.navArgs
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseFragment
import com.topjohnwu.magisk.arch.viewModel
import com.topjohnwu.magisk.databinding.FragmentTileGroupBinding
import com.topjohnwu.magisk.ui.anim.MetroFlipItem
import com.topjohnwu.magisk.ui.theme.MagisKubeTheme
import com.topjohnwu.magisk.ui.theme.MetroAccent
import com.topjohnwu.magisk.ui.theme.MetroAccentRole
import com.topjohnwu.magisk.ui.theme.MetroColors

/** Pure presentation host; the tile contents come from [MetroCustomTiles]. */
class TileGroupViewModel : com.topjohnwu.magisk.arch.BaseViewModel()

/**
 * A tile group opens as its own secondary Metro destination listing every member app, never as a
 * floating list over the Start screen and never as an empty surface.
 */
class TileGroupFragment : BaseFragment<FragmentTileGroupBinding>() {

    override val layoutRes = R.layout.fragment_tile_group
    override val viewModel by viewModel<TileGroupViewModel>()
    override val metroAccentRole = MetroAccentRole.APPS

    private val args by navArgs<TileGroupFragmentArgs>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.tileGroupCompose.setContent {
            MagisKubeTheme {
                TileGroupScreen(args.tileId)
            }
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        activity?.title = getString(R.string.metro_tile_group)
    }
}

@Composable
private fun TileGroupScreen(tileId: String) {
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

    val accentColor = tile?.color
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: MaterialTheme.colorScheme.primary
    val accent = MetroAccent(
        color = accentColor,
        onColor = if (accentColor.luminance() > 0.45f) Color.Black else Color.White,
    )

    val view = LocalView.current
    LaunchedEffect(view, accent.color) {
        (view.context as? android.app.Activity)?.window?.let { window ->
            window.statusBarColor = accent.color.toArgb()
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars =
                accent.color.luminance() > 0.5f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
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
