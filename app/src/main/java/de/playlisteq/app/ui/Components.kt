package de.playlisteq.app.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.playlisteq.app.model.Bands

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Senkrechter Regler für ein EQ-Band. Doppeltippen setzt auf 0 dB zurück. */
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = Bands.MIN_DB,
    max: Float = Bands.MAX_DB,
) {
    val onChange by rememberUpdatedState(onValueChange)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline

    Canvas(
        modifier
            .pointerInput(min, max) {
                fun toValue(y: Float): Float {
                    val pad = 12.dp.toPx()
                    val t = ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    return max - t * (max - min)
                }
                detectVerticalDragGestures(
                    onDragStart = { onChange(toValue(it.y)) },
                ) { change, _ ->
                    change.consume()
                    onChange(toValue(change.position.y))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onChange(0f) })
            }
    ) {
        val pad = 12.dp.toPx()
        val cx = size.width / 2
        val top = pad
        val bottom = size.height - pad
        fun yOf(v: Float) = top + (max - v) / (max - min) * (bottom - top)

        drawLine(track, Offset(cx, top), Offset(cx, bottom), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        val y0 = yOf(0f)
        val yv = yOf(value.coerceIn(min, max))
        drawLine(outline, Offset(cx - 9.dp.toPx(), y0), Offset(cx + 9.dp.toPx(), y0), strokeWidth = 1.dp.toPx())
        drawLine(primary, Offset(cx, y0), Offset(cx, yv), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(primary, radius = 9.dp.toPx(), center = Offset(cx, yv))
    }
}

/** Kleine Vorschau der EQ-Kurve. */
@Composable
fun EqCurve(gains: List<Float>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val n = gains.size
        if (n < 2) return@Canvas
        val mid = size.height / 2
        val amp = mid - 4.dp.toPx()
        drawLine(outline.copy(alpha = 0.4f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        val path = Path()
        gains.forEachIndexed { i, g ->
            val x = i * size.width / (n - 1)
            val y = mid - g / Bands.MAX_DB * amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Einfache Auswahlliste. */
@Composable
fun <T> Dropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedText, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(optionText(o)) }, onClick = { onSelect(o); open = false })
                }
            }
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

fun fmtDb(v: Float): String = (if (v > 0) "+" else "") + (if (v % 1f == 0f) "${v.toInt()}" else "$v")
