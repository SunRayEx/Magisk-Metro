package com.MagisKube.magisk.ui.settings

import android.os.Bundle
import android.view.View
import com.MagisKube.magisk.R
import androidx.fragment.app.Fragment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import com.MagisKube.magisk.core.R as CoreR

class SettingsFragment : Fragment(R.layout.fragment_settings_md2) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val composeView = view as ComposeView
        composeView.setContent {
            SettingsScreen()
        }
    }

    @Composable
    fun SettingsScreen() {
        val settingsViewModel: SettingsViewModel = viewModel()
        val isMonetEnabled by settingsViewModel.isMonetEnabled.collectAsState()

        SettingsContent(isMonetEnabled, { settingsViewModel.toggleMonetEnabled() })
    }

    @Composable
    fun SettingsContent(isMonetEnabled: Boolean, onToggleMonet: () -> Unit) {
        // 这里可以添加你的设置 UI 组件
        // 例如使用 Switch 来控制 Monet 取色功能
        // 示例代码：
        /*
        var monetEnabled by remember { mutableStateOf(isMonetEnabled) }
        Switch(
            checked = monetEnabled,
            onCheckedChange = {
                monetEnabled = it
                onToggleMonet()
            },
            label = { Text(text = stringResource(id = R.string.monet_color_extraction)) }
        )
        */
    }
}
