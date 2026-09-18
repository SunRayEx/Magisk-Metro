package com.topjohnwu.magisk.ui.flash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.ui.component.rememberExternalStoragePermissionLauncher
import com.topjohnwu.magisk.ui.terminal.TerminalScreen
import com.topjohnwu.magisk.ui.theme.LocalMetroPalette
import com.topjohnwu.magisk.core.R as CoreR

@Composable
fun FlashScreen(
    viewModel: FlashViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flashState by viewModel.flashState.collectAsStateWithLifecycle()
    val showReboot by viewModel.showReboot.collectAsStateWithLifecycle()
    val finished = flashState != FlashViewModel.State.FLASHING
    val saveLog = rememberExternalStoragePermissionLauncher {
        viewModel.saveLog()
    }

    val statusText = when (flashState) {
        FlashViewModel.State.FLASHING -> stringResource(CoreR.string.flashing)
        FlashViewModel.State.SUCCESS -> stringResource(CoreR.string.done)
        FlashViewModel.State.FAILED -> stringResource(CoreR.string.failure)
    }

    // Metro design language: no top bar. A large accent header carries the title and status;
    // the save/reboot actions sit inline beside it, the terminal fills the space underneath.
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(CoreR.string.flash_screen_title),
                    color = LocalMetroPalette.current.magisk.color,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
            }
            if (finished) {
                IconButton(onClick = saveLog) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = stringResource(CoreR.string.menuSaveLog),
                    )
                }
            }
            if (flashState == FlashViewModel.State.SUCCESS && showReboot) {
                IconButton(onClick = { viewModel.restartPressed() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restart),
                        contentDescription = stringResource(CoreR.string.reboot),
                    )
                }
            }
        }
        TerminalScreen(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            onEmulatorCreated = { viewModel.onEmulatorCreated(it) },
        )
    }
}
