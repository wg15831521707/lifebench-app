package com.lifebench.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 预算进度条（超预算感知）：
 * - 进度从 0 缓动生长到 ratio∈[0,1]；
 * - ratio>1（超预算）时整体切换为 error 色，配合 PulseBadge 强调；
 * - 轨道用 surfaceVariant，承袭主题深浅模式。
 */
@Composable
fun BudgetProgress(
    ratio: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    overColor: Color = MaterialTheme.colorScheme.error,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 10.dp
) {
    val over = ratio > 1f
    val target = ratio.coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        anim.animateTo(target, tween(900, easing = FastOutSlowInEasing))
    }
    LinearProgressIndicator(
        progress = { anim.value },
        modifier = modifier.fillMaxWidth().height(height),
        color = if (over) overColor else color,
        trackColor = trackColor,
    )
}
