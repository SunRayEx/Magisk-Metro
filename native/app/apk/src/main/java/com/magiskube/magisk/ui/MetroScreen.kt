#!/usr/bin/env kotlin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.yourapp.MagiskViewModel
import com.example.yourapp.Module
import com.example.yourapp.MagiskUiState
import com.example.yourapp.MagiskViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MetroScreen(viewModel: MagiskViewModel = viewModel(), navController: NavController) {
    val uiState by viewModel.uiState.collectAsState()

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
                StatTile("Modules", "${uiState.modulesCount}", uiState.modules.map { it.name }, Color(0xFF1976D2), Modifier.weight(1f), navController, "module_detail")
                StatTile("Apps", "${uiState.appsCount}", uiState.rootedApps, Color(0xFFD32F2F), Modifier.weight(1f), navController, "app_detail")
            }
        }

        // 第二行：DenyList + Logs
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            DynamicMetroTile(
                modifier = Modifier.weight(2f),
                backgroundColor = Color(0xFFFBC02D),
                frontContent = { Text("DenyList", Modifier.padding(16.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black) },
                backContent = { Text("Switch to SuList", Modifier.padding(16.dp), color = Color.Black) },
                navController = navController,
                navigateTo = "deny_list_detail"
            )
            DynamicMetroTile(
                modifier = Modifier.weight(1f),
                backgroundColor = Color.White,
                frontContent = {
                    Column(Modifier.padding(8.dp)) {
                        Text("Logs", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                backContent = { Text("清除日志", Modifier.padding(8.dp), color = Color.Black) },
                navController = navController,
                navigateTo = "log_detail"
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
                backContent = { Text("查看致谢名单", Modifier.padding(16.dp), color = Color.Black) },
                navController = navController,
                navigateTo = "contributor_detail"
            )
        }
    }
}

// 动态磁贴
@Composable
fun DynamicMetroTile(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    frontContent: @Composable BoxScope.() -> Unit,
    backContent: @Composable BoxScope.() -> Unit,
    navController: NavController,
    navigateTo: String
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
            .clickable {
                if (rotated) {
                    navController.navigate(navigateTo)
                } else {
                    rotated = true
                }
            },
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

// 统计磁贴
@Composable
fun StatTile(
    title: String,
    count: String,
    items: List<String>,
    color: Color,
    modifier: Modifier,
    navController: NavController,
    navigateTo: String
) {
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
        },
        navController = navController,
        navigateTo = navigateTo
    )
}

// Magisk 主状态磁贴
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

// 静态磁贴
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MagiskUiState(
    val magiskVersion: String = "Checking...",
    val isRooted: Boolean = false,
    val modulesCount: Int = 0,
    val appsCount: Int = 0,
    val denyListCount: Int = 0,
    val modules: List<Module> = emptyList(),
    val rootedApps: List<String> = emptyList()
)

data class Module(val name: String, val path: String)

class MagiskViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MagiskUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchMagiskStatus()
    }

    private fun fetchMagiskStatus() {
        viewModelScope.launch {
            val version = getMagiskVersion()
            val rootStatus = checkRootAccess()
            val modulesCount = countModules()
            val appsCount = countApps()
            val denyListCount = countDenyList()
            val modules = getModules()
            val rootedApps = getRootedApps()

            _uiState.value = _uiState.value.copy(
                magiskVersion = version,
                isRooted = rootStatus,
                modulesCount = modulesCount,
                appsCount = appsCount,
                denyListCount = denyListCount,
                modules = modules,
                rootedApps = rootedApps
            )
        }
    }

    private fun getMagiskVersion(): String {
        // 实际实现
        return "23.0"
    }

    private fun checkRootAccess(): Boolean {
        // 实际实现
        return true
    }

    private fun countModules(): Int {
        // 实际实现
        return 5
    }

    private fun countApps(): Int {
        // 实际实现
        return 10
    }

    private fun countDenyList(): Int {
        // 实际实现
        return 2
    }

    private fun getModules(): List<Module> {
        val modulesDir = File("/data/adb/modules")
        val modules = mutableListOf<Module>()

        if (modulesDir.exists() && modulesDir.isDirectory) {
            for (file in modulesDir.listFiles()) {
                if (file.isDirectory) {
                    val moduleProp = File(file, "module.prop")
                    if (moduleProp.exists() && moduleProp.isFile) {
                        val name = moduleProp.readLines().firstOrNull { it.startsWith("name=") }?.substringAfter("=")
                        if (name != null) {
                            modules.add(Module(name, file.absolutePath))
                        }
                    }
                }
            }
        }

        return modules
    }

    private fun getRootedApps(): List<String> {
        // 实际实现
        return listOf("App1", "App2", "App3")
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YourAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MetroScreen(viewModel())
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewMainActivity() {
    YourAppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MetroScreen(viewModel())
        }
    }
}
