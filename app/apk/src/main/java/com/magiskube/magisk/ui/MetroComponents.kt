#!/usr/bin/env kotlin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- 静态磁贴：用于 Magisk 主状态 ---
@Composable
fun StaticMetroTile(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.padding(4.dp),
        color = backgroundColor,
        shape = RectangleShape // 经典的直角设计
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

// --- 动态磁贴：用于其他功能块 ---
@Composable
fun DynamicMetroTile(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    frontContent: @Composable BoxScope.() -> Unit,
    backContent: @Composable BoxScope.() -> Unit
) {
    var rotated by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (rotated) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "TileFlip"
    )

    Surface(
        modifier = modifier
            .padding(4.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable { rotated = !rotated },
        color = backgroundColor,
        shape = RectangleShape
    ) {
        if (rotation <= 90f) {
            Box(Modifier.fillMaxSize()) { frontContent() }
        } else {
            // 背面翻转补偿，确保文字不镜像
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                backContent()
            }
        }
    }
}

// 1. 【静态】左上角 Magisk 状态
@Composable
fun MagiskMainTile(modifier: Modifier, uiState: MagiskUiState) {
    StaticMetroTile(
        modifier = modifier,
        backgroundColor = Color(0xFF00897B) // 你的设计稿绿色
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Magisk ${uiState.magiskVersion}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("[${if (uiState.isRooted) "enable" else "disable"}]", fontSize = 16.sp, color = Color.Black)

            Spacer(Modifier.weight(1f))

            val statusStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black, fontWeight = FontWeight.Bold)
            Text("Root Status : ${if (uiState.isRooted) "Yes" else "No"}", style = statusStyle)
            Text("Zygisk Status : Yes", style = statusStyle) // 你可以根据实际情况更新
            Text("Ramdisk Status : Yes", style = statusStyle) // 你可以根据实际情况更新
        }
    }
}

// 2. 【动态】统计磁贴 (Modules / Apps)
@Composable
fun StatTile(title: String, count: String, items: List<String>, color: Color, modifier: Modifier) {
    DynamicMetroTile(
        modifier = modifier,
        backgroundColor = color,
        frontContent = {
            Column(Modifier.padding(12.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                items.forEach { Text(it, fontSize = 12.sp, color = Color.Black) }
                Spacer(Modifier.weight(1f))
                Text(count, modifier = Modifier.align(Alignment.End), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        },
        backContent = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("点击管理 $title", color = Color.Black)
            }
        }
    )
}

@Composable
fun MetroScreen(uiState: MagiskUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // 还原设计稿黑色背景
            .padding(4.dp)
    ) {
        // 第一行：Magisk状态(大) + Modules/Apps(小)
        Row(modifier = Modifier.weight(1.5f).fillMaxWidth()) {
            MagiskMainTile(Modifier.weight(2f), uiState) // 静态
            Column(Modifier.weight(1f)) {
                StatTile("Modules", "${uiState.modulesCount}", listOf("LSPosed", "Tricky Store"), Color(0xFF1976D2), Modifier.weight(1f))
                StatTile("Apps", "${uiState.appsCount}", listOf("IceBox", "Scene"), Color(0xFFD32F2F), Modifier.weight(1f))
            }
        }

        // 第二行：DenyList + Logs
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            DynamicMetroTile(
                modifier = Modifier.weight(2f),
                backgroundColor = Color(0xFFFBC02D),
                frontContent = { Text("DenyList", Modifier.padding(16.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                backContent = { Text("Switch to SuList", Modifier.padding(16.dp), color = Color.Black) }
            )
            DynamicMetroTile(
                modifier = Modifier.weight(1f),
                backgroundColor = Color.White,
                frontContent = {
                    Column(Modifier.padding(8.dp)) {
                        Text("Logs", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                backContent = { Text("清除日志", Modifier.padding(8.dp), color = Color.Black) }
            )
        }

        // 第三行：Contributor (紫色)
        Row(modifier = Modifier.weight(0.8f).fillMaxWidth()) {
            DynamicMetroTile(
                modifier = Modifier.fillMaxSize(),
                backgroundColor = Color(0xFF9C27B0),
                frontContent = {
                    Column(Modifier.padding(16.dp)) {
                        Text("Contributor", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("topjohnwu", color = Color.Black)
                        Text("vvb2060", color = Color.Black)
                    }
                },
                backContent = { Text("查看致谢名单", Modifier.padding(16.dp), color = Color.Black) }
            )
        }
    }
}

