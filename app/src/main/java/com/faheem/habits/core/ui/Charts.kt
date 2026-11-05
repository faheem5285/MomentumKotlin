@file:Suppress(
    "FunctionNaming",
    "TooManyFunctions",
    "LongParameterList",
    "MagicNumber",
    "CyclomaticComplexMethod",
    "MatchingDeclarationName",
    "ReturnCount"
)

package com.faheem.habits.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** A named value used by the donut, legend and bar chart helpers. */
@Immutable
data class ChartSlice(val label: String, val value: Float, val color: Color)

/** Neon series palette, ordered so neighbouring slices stay distinguishable. */
val ChartPalette = listOf(
    Color(0xFF22D3EE),
    Color(0xFF8B5CF6),
    Color(0xFFF472B6),
    Color(0xFF34D399),
    Color(0xFFFBBF24),
    Color(0xFF60A5FA),
    Color(0xFFFB7185),
    Color(0xFFA78BFA),
    Color(0xFF2DD4BF),
    Color(0xFFF59E0B)
)

fun chartColor(index: Int): Color = ChartPalette[Math.floorMod(index, ChartPalette.size)]

/** Builds slices from a map while keeping the ordering stable. */
fun chartSlices(values: Map<String, Float>, limit: Int = ChartPalette.size): List<ChartSlice> =
    values.entries
        .sortedByDescending { it.value }
        .take(limit)
        .mapIndexed { index, entry -> ChartSlice(entry.key, entry.value, chartColor(index)) }

@Composable
private fun ChartEmpty(message: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun DrawScope.gridLines(rows: Int, color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
    repeat(rows) { index ->
        val y = size.height * (index + 1) / (rows + 1)
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
            pathEffect = dash
        )
    }
}

private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val midX = (previous.x + current.x) / 2f
        path.cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
    }
    return path
}

/**
 * Glowing line/area chart.
 *
 * Values are plotted in list order; the y-axis always includes zero so short and
 * tall series stay comparable between cards. Set [normalized] for data that is
 * already a 0..1 fraction.
 */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 140.dp,
    showArea: Boolean = true,
    showDots: Boolean = true,
    normalized: Boolean = false,
    emptyMessage: String = "Not enough data yet"
) {
    if (values.isEmpty()) {
        ChartEmpty(emptyMessage, modifier)
        return
    }
    val reduce = LocalReduceEffects.current
    var emerged by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { emerged = true }
    val progress by animateFloatAsState(if (reduce || emerged) 1f else 0f, tween(700), label = "lineChart")
    val low = if (normalized) 0f else min(values.min(), 0f)
    val high = if (normalized) 1f else max(values.max(), 1f)
    val span = (high - low).takeIf { it > 0f } ?: 1f
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    Column(modifier) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = "Line chart with ${values.size} points" }
        ) {
            gridLines(3, gridColor)
            val inset = 6.dp.toPx()
            val usableWidth = size.width - inset * 2
            val usableHeight = size.height - inset * 2
            val stepX = if (values.size > 1) usableWidth / (values.size - 1) else 0f
            val points = values.mapIndexed { index, value ->
                val fraction = ((value - low) / span).coerceIn(0f, 1f)
                Offset(
                    x = inset + if (values.size == 1) usableWidth / 2f else stepX * index,
                    y = inset + usableHeight * (1f - fraction * progress)
                )
            }
            val line = buildSmoothPath(points)
            if (showArea) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, size.height - inset)
                    lineTo(points.first().x, size.height - inset)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0.02f)),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
            drawPath(line, color.copy(alpha = 0.30f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(line, color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
            if (showDots) {
                points.forEachIndexed { index, point ->
                    val isLast = index == points.lastIndex
                    drawCircle(color.copy(alpha = 0.22f), if (isLast) 7.dp.toPx() else 4.5.dp.toPx(), point)
                    drawCircle(if (isLast) Color.White else color, if (isLast) 3.4.dp.toPx() else 2.2.dp.toPx(), point)
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { label ->
                    Text(
                        label,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Compact line without axes, for tiles and summary rows. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = true
) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    val low = values.min()
    val high = values.max()
    val span = (high - low).takeIf { it > 0f } ?: 1f
    androidx.compose.foundation.Canvas(modifier) {
        val inset = 2.dp.toPx()
        val stepX = (size.width - inset * 2) / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(
                inset + stepX * index,
                inset + (size.height - inset * 2) * (1f - (value - low) / span)
            )
        }
        val line = buildSmoothPath(points)
        if (filled) {
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent))
            )
        }
        drawPath(line, color.copy(alpha = 0.28f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White, 2.4.dp.toPx(), points.last())
    }
}

/**
 * Vertical bar chart with rounded neon bars and an optional highlighted column.
 * The tallest value always fills the plot so small series stay readable.
 */
@Composable
fun BarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary),
    highlightIndex: Int? = null,
    height: Dp = 150.dp,
    showValues: Boolean = true,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    emptyMessage: String = "No data yet"
) {
    if (values.isEmpty() || values.all { it <= 0f }) {
        ChartEmpty(emptyMessage, modifier)
        return
    }
    val top = values.max().takeIf { it > 0f } ?: 1f
    val maxBar = (height - 30.dp).coerceAtLeast(20.dp)
    Row(modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { index, value ->
            val fraction = (value / top).coerceIn(0f, 1f)
            val animated by animateFloatAsState(fraction, tween(650), label = "bar$index")
            val color = colors[Math.floorMod(index, colors.size)]
            val selected = highlightIndex == index
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (showValues) {
                    Text(
                        valueFormatter(value),
                        fontSize = 9.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(if (values.size > 12) 10.dp else 16.dp)
                        .height((maxBar * animated).coerceAtLeast(3.dp))
                        .shadow(
                            if (selected) 14.dp else 8.dp,
                            RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp),
                            clip = false,
                            ambientColor = color.copy(alpha = if (selected) 0.9f else 0.55f),
                            spotColor = color.copy(alpha = 0.5f)
                        )
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    color,
                                    color.copy(alpha = 0.62f),
                                    color.copy(alpha = 0.30f)
                                )
                            )
                        )
                )
                if (labels.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        labels.getOrElse(index) { "" },
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Horizontal ranked bars, handy for “top apps” style lists. */
@Composable
fun HorizontalBarList(
    items: List<ChartSlice>,
    modifier: Modifier = Modifier,
    maxItems: Int = 5,
    valueFormatter: (Float) -> String = { it.toInt().toString() }
) {
    if (items.isEmpty()) {
        ChartEmpty("Nothing recorded yet", modifier)
        return
    }
    val shown = items.take(maxItems)
    val top = shown.maxOf { it.value }.takeIf { it > 0f } ?: 1f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        shown.forEach { slice ->
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(slice.color)
                            .shadow(6.dp, CircleShape, ambientColor = slice.color, spotColor = slice.color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.label,
                        Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(valueFormatter(slice.value), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((slice.value / top).coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(slice.color, slice.color.copy(alpha = 0.55f))
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Donut chart with a soft neon bloom. Shares the same slice model as the legend.
 * Values do not need to be normalised.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    centerValue: String? = null,
    centerLabel: String? = null,
    thickness: Dp = 20.dp,
    gapDegrees: Float = 3.5f,
    emptyMessage: String = "No data yet"
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    if (slices.isEmpty() || total <= 0f) {
        ChartEmpty(emptyMessage, modifier)
        return
    }
    val reduce = LocalReduceEffects.current
    var emerged by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { emerged = true }
    val animated by animateFloatAsState(if (reduce || emerged) 1f else 0f, tween(800), label = "donut")
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .semantics { contentDescription = "Donut chart with ${slices.size} segments" }
        ) {
            val stroke = thickness.toPx()
            val diameter = min(size.width, size.height) - stroke * 2.4f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke)
            )
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value / total) * animated
                val visible = (sweep - gapDegrees).coerceAtLeast(1f)
                drawArc(
                    color = slice.color.copy(alpha = 0.26f),
                    startAngle = start,
                    sweepAngle = visible,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke * 2f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = slice.color,
                    startAngle = start,
                    sweepAngle = visible,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                start += sweep
            }
        }
        if (centerValue != null || centerLabel != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                centerValue?.let {
                    Text(it, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                }
                centerLabel?.let {
                    Text(it, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** Colour key for a chart, showing value and share for each slice. */
@Composable
fun ChartLegend(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    showPercent: Boolean = true,
    maxRows: Int = 5
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.take(maxRows).forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(slice.color)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    slice.label,
                    Modifier.weight(1f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showPercent) {
                    Text(
                        "${((slice.value / total) * 100f).toInt()}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(valueFormatter(slice.value), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Single horizontal stacked bar, useful for composition summaries. */
@Composable
fun StackedBar(
    segments: List<ChartSlice>,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat()
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track.copy(alpha = 0.55f)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (total <= 0f) {
            Box(Modifier.fillMaxWidth().height(height))
        } else {
            segments.forEach { segment ->
                Box(
                    Modifier
                        .weight((segment.value / total).coerceAtLeast(0.001f))
                        .height(height)
                        .clip(RoundedCornerShape(height / 2))
                        .background(
                            Brush.verticalGradient(
                                listOf(segment.color, segment.color.copy(alpha = 0.6f))
                            )
                        )
                )
            }
        }
    }
}

/** Week-style heatmap; each inner list is a column. Null means “not scheduled”. */
@Composable
fun HeatmapGrid(
    columns: List<List<Float?>>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    cellSize: Dp = 16.dp,
    spacing: Dp = 4.dp
) {
    if (columns.isEmpty()) {
        ChartEmpty("No history yet", modifier)
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
        columns.forEach { column ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                column.forEach { value ->
                    Box(
                        Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    value == null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                    value <= 0f -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> color.copy(alpha = 0.22f + 0.78f * value.coerceIn(0f, 1f))
                                }
                            )
                    )
                }
            }
        }
    }
}

/**
 * Simple radar/spider chart for comparing up to eight dimensions.
 * Each series is drawn as a glowing polygon.
 */
@Composable
fun RadarChart(
    axes: List<String>,
    series: List<Pair<String, List<Float>>>,
    modifier: Modifier = Modifier,
    colors: List<Color> = ChartPalette,
    height: Dp = 210.dp
) {
    if (axes.size < 3 || series.isEmpty()) {
        ChartEmpty("Not enough data yet", modifier)
        return
    }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Radar chart comparing ${series.size} series" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - 14.dp.toPx()
        val count = axes.size
        repeat(4) { ring ->
            val r = radius * (ring + 1) / 4f
            val polygon = Path()
            for (index in 0 until count) {
                val angle = (-90f + 360f * index / count) * (Math.PI / 180f)
                val x = center.x + kotlin.math.cos(angle).toFloat() * r
                val y = center.y + kotlin.math.sin(angle).toFloat() * r
                if (index == 0) polygon.moveTo(x, y) else polygon.lineTo(x, y)
            }
            polygon.close()
            drawPath(polygon, gridColor, style = Stroke(1f))
        }
        for (index in 0 until count) {
            val angle = (-90f + 360f * index / count) * (Math.PI / 180f)
            drawLine(
                gridColor,
                center,
                Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * radius,
                    center.y + kotlin.math.sin(angle).toFloat() * radius
                ),
                strokeWidth = 1f
            )
        }
        series.forEachIndexed { seriesIndex, (_, values) ->
            val color = colors[Math.floorMod(seriesIndex, colors.size)]
            val points = (0 until count).map { index ->
                val fraction = values.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                val angle = (-90f + 360f * index / count) * (Math.PI / 180f)
                Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * radius * fraction,
                    center.y + kotlin.math.sin(angle).toFloat() * radius * fraction
                )
            }
            val path = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
            drawPath(path, color.copy(alpha = 0.16f))
            drawPath(path, color.copy(alpha = 0.35f), style = Stroke(6.dp.toPx()))
            drawPath(path, color, style = Stroke(2.dp.toPx()))
            points.forEach { drawCircle(Color.White.copy(alpha = 0.9f), 2.2.dp.toPx(), it) }
        }
    }
}

/** Compact horizontal composition bar with legend text, used inside cards. */
@Composable
fun CompositionBar(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { it.toInt().toString() }
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StackedBar(slices, height = 12.dp)
        Row(Modifier.fillMaxWidth()) {
            slices.forEach { slice ->
                Row(
                    Modifier.weight((slice.value.coerceAtLeast(0.01f))),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(slice.color)
                    )
                    Spacer(Modifier.width(5.dp))
                    Column {
                        Text(
                            slice.label,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            valueFormatter(slice.value),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Small “no data” box used by callers that want a fixed height. */
@Composable
fun ChartPlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
