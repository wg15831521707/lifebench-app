package com.lifebench.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 环形进度（睡眠达标率 / 专注进度等）。
 * - 自带 0→progress 缓动生长动画（首次出现或从 0 变化都会播放）；
 * - 支持叠加在圆心的内容槽（如时长文字、百分比）；
 * - 父容器需通过 modifier 给定尺寸（如 Modifier.size(72.dp)），Canvas 自动填充。
 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.18f),
    strokeWidth: Dp = 10.dp,
    startAngle: Float = -90f,
    content: (@Composable () -> Unit)? = null
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val r = (size.minDimension - sw) / 2
            val c = Offset(size.width / 2, size.height / 2)
            drawArc(trackColor, startAngle, 360f, false, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, startAngle, 360f * anim.value, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        if (content != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}
