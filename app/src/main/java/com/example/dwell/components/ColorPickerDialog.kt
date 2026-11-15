package com.example.dwell.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit

private val BASIC_SWATCHES: List<Color> = listOf(
    Color(0xFF0A84FF), // Blue
    Color(0xFF38BDF8), // Sky
    Color(0xFF14B8A6), // Teal
    Color(0xFF34C759), // Green
    Color(0xFF84CC16), // Lime
    Color(0xFFFACC15), // Yellow
    Color(0xFFFF9F0A), // Orange
    Color(0xFFFF453A), // Red
    Color(0xFFF43F5E), // Rose
    Color(0xFFFF2D92), // Pink
    Color(0xFFA855F7), // Purple
    Color(0xFF6366F1)  // Indigo
)

private const val CUSTOM_SWATCH_COUNT = 6
private const val PREF_CUSTOM_COLORS = "custom_accent_colors"

// Filled circle with a faint, thin greyish outline
@Composable
fun ColorDot(color: Color, size: Int, selected: Boolean = false) {
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else outline,
                shape = CircleShape
            )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentColorPickerDialog(
    current: Int?,
    darkMode: Boolean,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val builtIn = if (darkMode) darkColorScheme().primary else lightColorScheme().primary

    val initialColor = current?.let { Color(it) } ?: builtIn
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }

    var useDefault by rememberSaveable { mutableStateOf(current == null) }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var sat by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var value by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }

    var customColors by remember {
        mutableStateOf(loadCustomColors(prefs))
    }

    val activeColor = if (useDefault) builtIn else Color.hsv(hue, sat, value)
    val activeArgb = activeColor.toArgb()
    val r = (activeArgb shr 16) and 0xFF
    val g = (activeArgb shr 8) and 0xFF
    val b = activeArgb and 0xFF
    val hexString = "#%02X%02X%02X".format(r, g, b)

    fun selectColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        useDefault = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ---- Header + Default theme chip ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FilterChip(
                        selected = useDefault,
                        onClick = {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(builtIn.toArgb(), hsv)
                            hue = hsv[0]
                            sat = hsv[1]
                            value = hsv[2]
                            useDefault = true
                        },
                        label = { Text("Theme Default") },
                        leadingIcon = {
                            ColorDot(color = builtIn, size = 14)
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ---- 2D Spectrum Pad (Hue x Saturation) ----
                SpectrumPad(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { newHue, newSat ->
                        hue = newHue
                        sat = newSat
                        useDefault = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                )

                Spacer(Modifier.height(12.dp))

                // ---- Brightness / Luminance Slider ----
                ValueSliderBar(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onValueChange = { newValue ->
                        value = newValue
                        useDefault = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                Spacer(Modifier.height(16.dp))

                // ---- Live Preview Swatch + Hex / RGB Readout ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Split preview showing previous vs new color
                    Row(
                        modifier = Modifier
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(initialColor)
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(activeColor)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hexString,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "R $r  ·  G $g  ·  B $b",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Basic Colors ----
                Text(
                    text = "Basic colors",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 6
                ) {
                    BASIC_SWATCHES.forEach { swatch ->
                        val isSelected = !useDefault && activeArgb == swatch.toArgb()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(swatch)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectColor(swatch) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Custom Colors ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Custom colors",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap + to save current",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until CUSTOM_SWATCH_COUNT) {
                        val savedArgb = customColors.getOrNull(i)
                        val isSelected = !useDefault && savedArgb != null && activeArgb == savedArgb
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (savedArgb != null) Color(savedArgb)
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = savedArgb != null) {
                                    if (savedArgb != null) selectColor(Color(savedArgb))
                                }
                        )
                    }

                    // Add to Custom Colors button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable {
                                val updated = (listOf(activeArgb) + customColors.filter { it != activeArgb })
                                    .take(CUSTOM_SWATCH_COUNT)
                                customColors = updated
                                saveCustomColors(prefs, updated)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Save custom color",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ---- Dialog Actions ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onPick(if (useDefault) null else activeArgb)
                        }
                    ) {
                        Text("Apply", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectrumPad(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                fun updateFromOffset(pos: Offset) {
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val newHue = (pos.x / w).coerceIn(0f, 1f) * 360f
                    val newSat = 1f - (pos.y / h).coerceIn(0f, 1f)
                    onChange(newHue, newSat)
                }
                updateFromOffset(down.position)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.pressed) {
                        change.consume()
                        updateFromOffset(change.position)
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        // Horizontal Hue spectrum
        drawRect(brush = Brush.horizontalGradient(rainbowColors))
        // Vertical Saturation fade (top = 1f saturated, bottom = 0f white)
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.White)))
        // Value / brightness darkening overlay
        drawRect(color = Color.Black.copy(alpha = (1f - value).coerceIn(0f, 1f)))

        // Crosshair / ring selector
        val cx = (hue / 360f).coerceIn(0f, 1f) * size.width
        val cy = (1f - sat.coerceIn(0f, 1f)) * size.height
        val center = Offset(cx, cy)

        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = 11.dp.toPx(),
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun ValueSliderBar(
    hue: Float,
    sat: Float,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val fullBrightColor = remember(hue, sat) { Color.hsv(hue, sat, 1f) }
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, outlineColor, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun updateFromX(x: Float) {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onValueChange((x / w).coerceIn(0f, 1f))
                    }
                    updateFromX(down.position.x)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, fullBrightColor)
            )
        )

        val radius = size.height / 2f - 2.dp.toPx()
        val minX = size.height / 2f
        val maxX = (size.width - size.height / 2f).coerceAtLeast(minX)
        val cx = minX + value.coerceIn(0f, 1f) * (maxX - minX)
        val center = Offset(cx, size.height / 2f)

        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = radius + 1.dp.toPx(),
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun loadCustomColors(prefs: android.content.SharedPreferences): List<Int> {
    val raw = prefs.getString(PREF_CUSTOM_COLORS, null) ?: return emptyList()
    return raw.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .take(CUSTOM_SWATCH_COUNT)
}

private fun saveCustomColors(prefs: android.content.SharedPreferences, colors: List<Int>) {
    prefs.edit {
        putString(PREF_CUSTOM_COLORS, colors.joinToString(","))
    }
}
