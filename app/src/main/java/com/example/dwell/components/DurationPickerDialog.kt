package com.example.dwell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ============================================================================
// Duration picker: three wheels (HH / MM / SS). Tap the chevrons or drag
// a number up/down; values wrap around.
// ============================================================================

@Composable
fun DurationPickerDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var h by remember { mutableIntStateOf(initialSeconds / 3600) }
    var m by remember { mutableIntStateOf(initialSeconds % 3600 / 60) }
    var s by remember { mutableIntStateOf(initialSeconds % 60) }
    val total = h * 3600 + m * 60 + s

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Focus time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WheelColumn(h, 24, "hr") { h = it }
                    WheelColumn(m, 60, "min") { m = it }
                    WheelColumn(s, 60, "sec") { s = it }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(enabled = total > 0, onClick = { onConfirm(total) }) { Text("Set") }
                }
            }
        }
    }
}

@Composable
private fun WheelColumn(value: Int, count: Int, label: String, onChange: (Int) -> Unit) {
    val stepPx = with(LocalDensity.current) { 28.dp.toPx() }
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    val tint = MaterialTheme.colorScheme.inverseOnSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(count, stepPx) {
            var acc = 0f
            detectVerticalDragGestures(
                onDragEnd = { acc = 0f },
                onDragCancel = { acc = 0f }
            ) { change, dy ->
                change.consume()
                acc += dy
                // drag up = increase, drag down = decrease
                while (acc <= -stepPx) {
                    currentOnChange((currentValue + 1) % count)
                    acc += stepPx
                }
                while (acc >= stepPx) {
                    currentOnChange((currentValue - 1 + count) % count)
                    acc -= stepPx
                }
            }
        }
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint.copy(alpha = 0.5f))
        IconButton(onClick = { onChange((value + 1) % count) }) {
            Icon(Icons.Default.KeyboardArrowUp, "Increase $label", tint = tint.copy(alpha = 0.6f))
        }
        Text(
            text = "%02d".format(value),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
        IconButton(onClick = { onChange((value - 1 + count) % count) }) {
            Icon(Icons.Default.KeyboardArrowDown, "Decrease $label", tint = tint.copy(alpha = 0.6f))
        }
    }
}

internal fun formatTime(ms: Long, countUp: Boolean = false): String {
    val totalSec = (if (countUp) ms / 1000 else (ms + 999) / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
