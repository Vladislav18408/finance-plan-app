package com.vladk.financeplan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.ui.theme.chartGrid
import com.vladk.financeplan.ui.theme.chartMuted
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Shared rules for every chart here: thin marks, 4dp rounded bar ends on the baseline,
// 2dp surface gaps between filled segments, hairline grid, muted axis text, and text never
// coloured with the series colour (identity comes from the mark or the legend swatch).

private val AxisWidth = 44.dp

/** Round axis ticks: 0, step, 2·step … covering [maxValue] with about three gridlines. */
private fun niceTicks(maxValue: Double): List<Double> {
    val m = if (maxValue <= 0.0) 10.0 else maxValue
    val raw = m / 3.0
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val step = (if (norm <= 1) 1.0 else if (norm <= 2) 2.0 else if (norm <= 5) 5.0 else 10.0) * mag
    val top = ceil(m / step) * step
    return generateSequence(0.0) { it + step }.takeWhile { it <= top + step / 2 }.toList()
}

private fun DrawScope.yGrid(
    ticks: List<Double>,
    top: Float,
    plotH: Float,
    left: Float,
    grid: Color,
    tm: TextMeasurer,
    style: TextStyle,
    axisFormat: (Double) -> String,
) {
    val maxTick = ticks.last()
    ticks.forEach { t ->
        val y = top + plotH - (t / maxTick * plotH).toFloat()
        drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
        val layout = tm.measure(axisFormat(t), style)
        drawText(layout, topLeft = Offset(left - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f))
    }
}

/**
 * Vertical bars, one per label (days of a week, months …). Tapping a bar selects it; the value of the
 * selected bar (or of the biggest bar) is written above it. [reference] draws a dashed guide line.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    color: Color,
    format: (Double) -> String,
    axisFormat: (Double) -> String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    highlight: Int? = null,
    reference: Double? = null,
    referenceLabel: String? = null,
    height: Dp = 180.dp,
) {
    val tm = rememberTextMeasurer()
    val muted = chartMuted()
    val grid = chartGrid()
    val ink = MaterialTheme.colorScheme.onSurface
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val strongAxis = axisStyle.copy(color = ink, fontWeight = FontWeight.Bold)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = ink, fontWeight = FontWeight.SemiBold)
    val n = values.size.coerceAtLeast(1)

    val tap = if (onSelect != null) {
        Modifier.pointerInput(n, selected) {
            detectTapGestures { o ->
                val left = AxisWidth.toPx()
                if (o.x < left) return@detectTapGestures
                val i = ((o.x - left) / ((size.width - left) / n)).toInt().coerceIn(0, n - 1)
                onSelect(if (i == selected) null else i)
            }
        }
    } else Modifier

    Canvas(modifier.fillMaxWidth().height(height).then(tap)) {
        val left = AxisWidth.toPx()
        val topPad = 22.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - topPad - bottomPad
        val baseY = topPad + plotH
        val ticks = niceTicks(max(values.maxOrNull() ?: 0.0, reference ?: 0.0))
        val top = ticks.last()
        fun y(v: Double) = baseY - (v / top * plotH).toFloat()

        yGrid(ticks, topPad, plotH, left, grid, tm, axisStyle, axisFormat)

        val slot = plotW / n
        val bw = min(slot * 0.56f, 36.dp.toPx())
        values.forEachIndexed { i, v ->
            val x0 = left + slot * i + (slot - bw) / 2
            if (v > 0) {
                val h = baseY - y(v)
                val r = min(4.dp.toPx(), h)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x0, top = y(v), right = x0 + bw, bottom = baseY,
                            topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        ),
                    )
                }
                val dim = selected != null && selected != i
                drawPath(path, color.copy(alpha = if (dim) 0.35f else 1f))
            }
            labels.getOrNull(i)?.let { label ->
                val layout = tm.measure(label, if (i == highlight) strongAxis else axisStyle)
                drawText(layout, topLeft = Offset(left + slot * i + (slot - layout.size.width) / 2, baseY + 4.dp.toPx()))
            }
        }

        if (reference != null && reference > 0) {
            val ry = y(reference)
            drawLine(
                muted, Offset(left, ry), Offset(size.width, ry), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            referenceLabel?.let {
                val layout = tm.measure(it, axisStyle)
                drawText(layout, topLeft = Offset(size.width - layout.size.width, ry - layout.size.height - 2.dp.toPx()))
            }
        }

        // One value label only: the selected bar, else the biggest one.
        val labelIndex = selected ?: values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }
        labelIndex?.let { i ->
            val layout = tm.measure(format(values[i]), valueStyle)
            val cx = left + slot * i + slot / 2
            val x = (cx - layout.size.width / 2).coerceIn(left, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, y(values[i]) - layout.size.height - 3.dp.toPx()))
        }
    }
}

data class Slice(val label: String, val value: Double, val color: Color)

/** Donut with the total (or the tapped slice) in the middle. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerLabel: String,
    centerValue: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.sumOf { it.value }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(slices, selected) {
                detectTapGestures { o ->
                    if (total <= 0) return@detectTapGestures
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = min(size.width, size.height) / 2f - 16.dp.toPx()
                    val d = hypot(o.x - cx, o.y - cy)
                    if (d < r - 20.dp.toPx() || d > r + 20.dp.toPx()) {
                        onSelect(null); return@detectTapGestures
                    }
                    val angle = ((atan2(o.y - cy, o.x - cx) * 180 / PI) + 90 + 360) % 360
                    var acc = 0.0
                    val i = slices.indexOfFirst { s -> acc += s.value / total * 360; angle <= acc }
                    onSelect(if (i == selected) null else i.takeIf { it >= 0 })
                }
            },
        ) {
            val r = min(size.width, size.height) / 2f - 16.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2, r * 2)
            if (total <= 0) {
                drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(24.dp.toPx()))
                return@Canvas
            }
            val gap = if (slices.size > 1) (2.dp.toPx() / r * 180f / PI.toFloat()) else 0f
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = (s.value / total * 360).toFloat()
                val isSel = i == selected
                val dim = selected != null && !isSel
                drawArc(
                    color = s.color.copy(alpha = if (dim) 0.35f else 1f),
                    startAngle = start + gap / 2,
                    sweepAngle = max(0.5f, sweep - gap),
                    useCenter = false,
                    topLeft = tl,
                    size = sz,
                    style = Stroke(if (isSel) 30.dp.toPx() else 24.dp.toPx(), cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Text(
                centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(centerValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

data class LineSeries(
    val label: String,
    val points: List<Double?>,
    val color: Color,
    val dashed: Boolean = false,
)

/**
 * Line chart over a common x axis (days of a month). Series are drawn in list order, so put the
 * main series last. Tap or drag to move the crosshair; the caller shows the read-out for [selected].
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    xLabel: (Int) -> String,
    axisFormat: (Double) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    val tm = rememberTextMeasurer()
    val grid = chartGrid()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = chartMuted())
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val n = series.maxOfOrNull { it.points.size }?.coerceAtLeast(2) ?: 2

    fun indexAt(x: Float, width: Float, left: Float): Int =
        (((x - left) / (width - left)) * (n - 1)).roundToInt().coerceIn(0, n - 1)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(n) {
                detectTapGestures { o -> onSelect(indexAt(o.x, size.width.toFloat(), AxisWidth.toPx())) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onSelect(indexAt(o.x, size.width.toFloat(), AxisWidth.toPx())) },
                ) { change, _ -> onSelect(indexAt(change.position.x, size.width.toFloat(), AxisWidth.toPx())) }
            },
    ) {
        val left = AxisWidth.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val plotW = size.width - left
        val plotH = size.height - topPad - bottomPad
        val baseY = topPad + plotH
        val maxV = series.flatMap { it.points }.filterNotNull().maxOrNull() ?: 0.0
        val ticks = niceTicks(maxV)
        val top = ticks.last()
        fun x(i: Int) = left + plotW * i / (n - 1)
        fun y(v: Double) = baseY - (v / top * plotH).toFloat()

        yGrid(ticks, topPad, plotH, left, grid, tm, axisStyle, axisFormat)
        listOf(0, (n - 1) / 3, 2 * (n - 1) / 3, n - 1).distinct().forEach { i ->
            val layout = tm.measure(xLabel(i), axisStyle)
            val lx = (x(i) - layout.size.width / 2).coerceIn(left, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(lx, baseY + 4.dp.toPx()))
        }

        selected?.let { i -> drawLine(grid.copy(alpha = 1f), Offset(x(i), topPad), Offset(x(i), baseY), 1.dp.toPx()) }

        series.forEach { s ->
            val path = Path()
            var started = false
            s.points.forEachIndexed { i, v ->
                if (v == null) {
                    started = false
                } else if (!started) {
                    path.moveTo(x(i), y(v)); started = true
                } else {
                    path.lineTo(x(i), y(v))
                }
            }
            drawPath(
                path, s.color,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
                ),
            )
        }

        fun marker(i: Int, v: Double, c: Color) {
            drawCircle(ring, radius = 6.dp.toPx(), center = Offset(x(i), y(v)))
            drawCircle(c, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
        }
        if (selected != null) {
            series.forEach { s -> s.points.getOrNull(selected)?.let { marker(selected, it, s.color) } }
        } else {
            // End-of-line marker on the main (last) series.
            series.lastOrNull()?.let { s ->
                val i = s.points.indexOfLast { it != null }
                if (i >= 0) marker(i, s.points[i]!!, s.color)
            }
        }
    }
}

data class Segment(val label: String, val value: Double, val color: Color)

/** One horizontal bar split into parts, with 2dp gaps between the parts. */
@Composable
fun StackedBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    val visible = segments.filter { it.value > 0 }
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (visible.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
    ) {
        visible.forEachIndexed { i, s ->
            if (i > 0) Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(s.value.toFloat()).fillMaxHeight().background(s.color))
        }
    }
}

data class LegendItem(val label: String, val color: Color, val line: Boolean = false, val dashed: Boolean = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.line) {
                    Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
                        drawLine(
                            item.color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = if (item.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                        )
                    }
                } else {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(item.color))
                }
                Spacer(Modifier.width(6.dp))
                Text(item.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A category/group row with a horizontal magnitude bar: emoji, name, amount, share. */
@Composable
fun BarRow(
    emoji: String,
    title: String,
    value: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trailingNote: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        EmojiAvatar(emoji, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
                }
                if (trailingNote != null) {
                    Text(
                        trailingNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).width(36.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
