package com.topjohnwu.magisk.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

@Composable
fun textHolder(holder: TextHolder) = holder.getText(LocalResources.current)
