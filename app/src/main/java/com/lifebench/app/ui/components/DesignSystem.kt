package com.lifebench.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifebench.app.ui.theme.Dimen

/**
 * 设计系统落地组件（对应 lifebench-redesign.html 的层级与质感方案）。
 * 全部基于 MaterialTheme 语义色 + 已有 RingProgress / AppCard，避免写死色值，跨主题预设通用。
 */

/** 品牌渐变笔刷：浅 → 主色 → 深，靠 compositeOver 推导明暗，不写死玫瑰粉，任何预设都和谐。 */
@Composable
private fun brandBrush(): Brush {
    val p = MaterialTheme.colorScheme.primary
    return Brush.linearGradient(
        0.0f to p.compositeOver(Color.White.copy(alpha = 0.14f)),
        0.5f to p,
        1.0f to p.compositeOver(Color.Black.copy(alpha = 0.22f)),
        start = Offset(0.12f, 0f),
        end = Offset(0.9f, 1f)
    )
}

/**
 * 首页 Hero 锚点（设计系统第一层级）：渐变面板承载问候 + 日期 + 头像 + 当日专注环形，
 * 把页面视觉重心拉到最顶端。文字统一白色，靠渐变主色兜底对比；环形与主色面板形成「强调面板」权重。
 */
@Composable
fun HeroCard(
    greeting: String,
    date: String,
    avatarText: String,
    focusMin: Int,
    focusTarget: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (focusTarget > 0) (focusMin.toFloat() / focusTarget).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(Dimen.cardRadius + 6.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            .background(brandBrush(), RoundedCornerShape(Dimen.cardRadius + 6.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(date, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(avatarText, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingProgress(
                    progress = progress,
                    modifier = Modifier.size(62.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f),
                    strokeWidth = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$focusMin", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("今日专注", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    Row {
                        Text("$focusMin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.alignByBaseline())
                        Text(" 分钟", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.alignByBaseline())
                        Text(" · 目标 $focusTarget", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.alignByBaseline())
                    }
                }
            }
        }
    }
}

/**
 * 区块标题（设计系统统一分组）：左侧 4dp 主色圆角竖条 + 标题，右侧可选「查看 ›」操作。
 * 替代各页散落的 Row(Text + TextButton)，建立一致的视觉分组与留白。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    moreLabel: String? = null,
    onMore: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimen.s16, vertical = Dimen.s4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 4.dp, height = 15.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (moreLabel != null && onMore != null) {
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onMore,
                contentPadding = PaddingValues(horizontal = Dimen.s4, vertical = Dimen.s2)
            ) {
                Text(moreLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * 个人中心头部：渐变圆形头像 + 昵称 + 副标题（如「已坚持 X 天」），与品牌色呼应。
 */
@Composable
fun ProfileHeader(
    name: String,
    subtitle: String,
    avatarText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimen.s16, vertical = Dimen.s4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).background(brandBrush(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(avatarText, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 概览小指标（4 列网格用）：大数字（主色）+ 标签，统一统计展示。 */
@Composable
fun StatBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 通用渐变强调面板（专注页「今日专注」汇总等用）：圆角 + 主色柔化投影，承载白色内容。
 */
@Composable
fun GradientPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(Dimen.cardRadius + 6.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            .background(brandBrush(), RoundedCornerShape(Dimen.cardRadius + 6.dp))
            .padding(18.dp)
    ) {
        Column(content = content)
    }
}
