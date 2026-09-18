package com.topjohnwu.magisk.ui.module

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
fun ActionScreen(
    viewModel: ActionViewModel,
    actionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val finished = actionState != ActionViewModel.State.RUNNING
    val saveLog = rememberExternalStoragePermissionLauncher {
        viewModel.saveLog()
    }

    // Metro design language: no top bar. A large accent header carries the title and the
    // action buttons sit inline beside it; the terminal fills the space underneath.
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = actionName,
                color = LocalMetroPalette.current.modules.color,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (finished) {
                IconButton(onClick = saveLog) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = stringResource(CoreR.string.menuSaveLog),
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
