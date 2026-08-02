package com.lifebench.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifebench.app.ui.theme.Dimen

/**
 * 数字滚动（count-up）：从 0 缓动到目标值，金额/指标变化时会从当前值继续滚动。
 * format 默认保留两位小数整数化；传入自定义 lambda 可输出 ¥/%/时长等任意格式。
 */
@Composable
fun CountUpText(
    value: Double,
    modifier: Modifier = Modifier,
    format: (Double) -> String = { "%.0f".format(it) },
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = Color.Unspecified
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) {
        anim.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    Text(format(anim.value.toDouble()), modifier, style = style, color = color)
}

/**
 * 揭示动效 Modifier 扩展：首次出现时按 index 错峰淡入 + 轻微上移，营造层级递进。
 * 纯 transform/alpha，60fps 友好；属轻量入场反馈，无需用户手动触发。
 */
@Composable
fun Modifier.reveal(
    index: Int = 0,
    delayPerItem: Int = 70,
    offsetY: Dp = 14.dp,
    durationMs: Int = 420
): Modifier {
    val alpha = remember { Animatable(0f) }
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        val d = index * delayPerItem
        alpha.animateTo(1f, tween(durationMs, delayMillis = d, easing = FastOutSlowInEasing))
        slide.animateTo(0f, tween(durationMs + 80, delayMillis = d, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        this.alpha = alpha.value
        translationY = slide.value * offsetY.toPx()
    }
}

/**
 * 脉冲徽标（超预算提醒等）：透明度在 0.55↔1 间无限呼吸，吸引注意但不刺眼。
 */
@Composable
fun PulseBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.error
) {
    val infinite = rememberInfiniteTransition()
    val a by infinite.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.alpha(a)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = Dimen.s8, vertical = Dimen.s4)
        )
    }
}
