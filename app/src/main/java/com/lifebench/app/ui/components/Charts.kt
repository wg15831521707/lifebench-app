package com.lifebench.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 轻量自绘图表（不依赖第三方图表库，保证离线、轻量、可控）。
 * 含：环形饼图 PieChart、柱状图 BarChart、折线图 LineChart。均做了除零/空数据安全处理。
 */

/** 环形饼图：用于收支分类结构。center 挖空显示总额，首次出现时整体扫入生长。 */
@Composable
fun PieChart(
    items: List<Pair<String, Double>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    gapDeg: Float = 2f,
    centerContent: @Composable BoxScope.() -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val total = items.sumOf { it.second }.toFloat().coerceAtLeast(0.0001f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            var start = -90f
            items.forEachIndexed { i, item ->
                val frac = (item.second.toFloat() / total)
                val sweep = (frac * 360f - gapDeg).coerceAtLeast(0f) * progress.value
                drawArc(
                    color = colors.getOrElse(i) { Color.Gray },
                    startAngle = start, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
                start += frac * 360f
            }
            // 中心挖空，形成环形
            drawCircle(color = scheme.surface, radius = radius * 0.58f, center = center)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = centerContent)
    }
}

/** 柱状图：用于周收支等。首次出现时逐根错峰生长（0→目标高度）。 */
@Composable
fun BarChart(
    items: List<Pair<String, Double>>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val max = (items.maxOfOrNull { it.second } ?: 1.0).toFloat().coerceAtLeast(1f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val n = items.size.coerceAtLeast(1)
        val gap = size.width / n
        val barW = (gap * 0.5f).coerceAtLeast(6.dp.toPx())
        items.forEachIndexed { i, item ->
            val full = (item.second.toFloat() / max) * (size.height * 0.85f)
            val localStart = i * 0.10f
            val local = ((progress.value - localStart) / (1f - localStart)).coerceIn(0f, 1f)
            val h = full * local
            val x = gap * i + (gap - barW) / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h.coerceAtLeast(0.001f)),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

/** 折线图：用于睡眠波动、专注力趋势等。可绘制一条目标基准虚线。 */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    target: Float? = null,
    targetLabel: String? = null
) {
    if (values.isEmpty()) return
    val targetVal = target ?: Float.MAX_VALUE
    val max = maxOf(values.maxOrNull()!!, targetVal).coerceAtLeast(1f)
    val min = values.minOrNull()!!.coerceAtMost(0f)
    val span = (max - min).coerceAtLeast(1f)
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val n = values.size.coerceAtLeast(2)
        val stepX = size.width / (n - 1)
        val toY: (Float) -> Float = { v ->
            size.height - ((v - min) / span) * size.height * 0.85f - size.height * 0.075f
        }
        // 目标基准虚线
        if (target != null) {
            val yT = toY(target)
            val dash = 8.dp.toPx(); val gap = 6.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Color.Gray.copy(alpha = 0.7f),
                    start = Offset(x, yT), end = Offset(minOf(x + dash, size.width), yT),
                    strokeWidth = 2.dp.toPx()
                )
                x += dash + gap
            }
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = toY(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 3.dp.toPx()))
        values.forEachIndexed { i, v ->
            drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(stepX * i, toY(v)))
        }
    }
}
