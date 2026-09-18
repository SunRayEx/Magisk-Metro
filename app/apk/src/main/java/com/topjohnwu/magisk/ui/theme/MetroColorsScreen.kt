package com.topjohnwu.magisk.ui.theme

import android.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.ui.anim.MetroEaseOut
import com.topjohnwu.magisk.ui.home.MetroUiState
import java.util.Locale

/**
 * The custom-theme color editor. One HEX field per Metro accent role; the value resolves through
 * the exact same [MetroColors.customAccent] pipeline the Start board uses, so the swatch beside a
 * field always previews the color that role will actually paint.
 *
 * Saving validates every field first (an invalid one is highlighted and the dialog stays open),
 * then writes [Config.metroCustomColors], switches the active theme to Custom, invalidates the
 * board and recreates the activity so the whole UI re-inflates with the new palette.
 */
@Composable
fun MetroColorsScreen(
    onApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = LocalMetroPalette.current.settings

    // One editable value per role, seeded from the persisted config.
    val fields = remember {
        mutableStateMapOf<MetroAccentRole, String>().apply {
            MetroAccentRole.entries.forEach { role ->
                put(role, MetroColors.customHex(role, context))
            }
        }
    }
    var invalidRole by remember { mutableStateOf<MetroAccentRole?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Metro entrance: slide/fade in like the other secondary screens.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(260, easing = MetroEaseOut))
    }

    fun isValid(hex: String) = runCatching { Color.parseColor(hex) }.isSuccess

    fun save() {
        val bad = MetroAccentRole.entries.firstOrNull { !isValid(fields[it].orEmpty()) }
        if (bad != null) {
            invalidRole = bad
            return
        }
        saving = true
        MetroColors.saveCustomColors(fields.mapValues { it.value.trim() })
        Config.metroCustomTheme = true
        MetroUiState.invalidate()
        onApplied()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .graphicsLayer {
                alpha = entrance.value
                translationX = (1f - entrance.value) * 24.dp.toPx()
            },
    ) {
        Text(
            text = stringResource(R.string.metro_custom_colors),
            color = accent.color,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Text(
            text = stringResource(R.string.metro_custom_colors_summary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 16.dp, vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(MetroAccentRole.entries, key = { it.name }) { role ->
                ColorRow(
                    role = role,
                    value = fields[role].orEmpty(),
                    isError = invalidRole == role,
                    onValueChange = {
                        fields[role] = it.uppercase(Locale.ROOT)
                        if (invalidRole == role && isValid(it)) invalidRole = null
                    },
                )
            }
        }

        Button(
            onClick = { save() },
            enabled = !saving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.color,
                contentColor = accent.onColor,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(android.R.string.ok),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun ColorRow(
    role: MetroAccentRole,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    // Live preview through the real pipeline; falls back to the typed value so an unsaved edit is
    // visible too.
    val preview = remember(value) {
        runCatching { Color.parseColor(value) }
            .getOrElse { MetroColors.customAccent(context, role) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (isError) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(14.dp),
                ) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(preview)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = role.name.lowercase().replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.metro_invalid_hex)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
