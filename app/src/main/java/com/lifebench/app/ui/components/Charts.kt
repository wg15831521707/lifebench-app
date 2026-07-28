package com.lifebench.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** 环形饼图：用于收支分类结构。center 挖空显示总额。 */
@Composable
fun PieChart(
    items: List<Pair<String, Double>>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surface
    val total = items.sumOf { it.second }.toFloat().coerceAtLeast(0.0001f)
    Canvas(modifier = modifier.size(170.dp)) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        var start = -90f
        items.forEachIndexed { i, item ->
            val sweep = (item.second.toFloat() / total) * 360f
            drawArc(
                color = colors.getOrElse(i) { Color.Gray },
                startAngle = start, sweepAngle = sweep, useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            start += sweep
        }
        // 中心挖空，形成环形
        drawCircle(color = surface, radius = radius * 0.55f, center = center)
    }
}

/** 柱状图：用于周收支等。 */
@Composable
fun BarChart(
    items: List<Pair<String, Double>>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val max = (items.maxOfOrNull { it.second } ?: 1.0).toFloat().coerceAtLeast(1f)
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val n = items.size.coerceAtLeast(1)
        val gap = size.width / n
        val barW = (gap * 0.5f).coerceAtLeast(6.dp.toPx())
        items.forEachIndexed { i, item ->
            val h = (item.second.toFloat() / max) * (size.height * 0.85f)
            val x = gap * i + (gap - barW) / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

/** 折线图：用于睡眠波动、专注力趋势等。 */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val max = values.maxOrNull()!!.coerceAtLeast(1f)
    val min = values.minOrNull()!!.coerceAtMost(0f)
    val span = (max - min).coerceAtLeast(1f)
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val n = values.size.coerceAtLeast(2)
        val stepX = size.width / (n - 1)
        val toY: (Float) -> Float = { v ->
            size.height - ((v - min) / span) * size.height * 0.85f - size.height * 0.075f
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
