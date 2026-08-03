package com.lifebench.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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

/**
 * 分类柱状图：用于收支结构柱状形态。
 * 区别于 BarChart（单色）：每根柱子按 ChartPalette 取色，下方显示分类名 + 金额，
 * 错峰生长动画（每根 60ms 延迟），整体无 Canvas 重绘开销。
 */
@Composable
fun CategorizedBarChart(
    items: List<Pair<String, Double>>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val max = items.maxOf { it.second }.coerceAtLeast(0.0001)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth().height(168.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items.forEachIndexed { i, (label, value) ->
            val color = colors.getOrElse(i) { Color.Gray }
            val targetFrac = (value / max).toFloat().coerceIn(0f, 1f)
            // 每根柱子用独立的 Animatable，错峰启动
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay((i * 60L).coerceAtMost(400L))
                progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 数值标在柱顶
                Text(
                    "¥%.0f".format(value),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                // 柱身：背景轨道 + 按比例填充的彩色条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(trackColor),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(targetFrac.coerceAtLeast(0.01f) * progress.value.coerceAtLeast(0.01f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(color)
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 分类名
                Text(
                    label,
                    fontSize = 10.5.sp,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
