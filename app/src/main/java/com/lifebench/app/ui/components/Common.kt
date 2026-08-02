package com.lifebench.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors

/**
 * 通用 UI 组件库：统一卡片、按钮、顶栏、空状态、加载，保证全局视觉一致。
 */

/** 页面顶栏：左标题 + 右侧操作（如主题切换）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (showBack) IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * 卡片视觉变体：
 * - SURFACE：常规内容卡。发丝级半透明描边（outline 令牌柔化）+ 1dp 轻投影，营造分层而非平铺；
 * - ACCENT：页面重心卡（如预算/重点指标）。主色容器底色 + 主色柔化投影，视觉权重最高。
 */
enum class CardVariant { SURFACE, ACCENT }

/** 统一卡片：圆角 16、内边距 16、点击可选。通过 variant 表达层级与质感。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.SURFACE,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimen.cardRadius)
    val containerColor = if (variant == CardVariant.ACCENT) scheme.primaryContainer else scheme.surface
    val border = if (variant == CardVariant.SURFACE) BorderStroke(1.dp, scheme.outline.copy(alpha = 0.45f)) else null
    val elevation = if (variant == CardVariant.ACCENT) 0.dp else 1.dp
    val accentShadow = if (variant == CardVariant.ACCENT) {
        Modifier.shadow(8.dp, shape, spotColor = scheme.primary.copy(alpha = 0.22f))
    } else Modifier
    Card(
        modifier = modifier.fillMaxWidth().then(accentShadow),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(
            Modifier.padding(Dimen.s16).then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier),
            content = content
        )
    }
}

/** 主按钮：高 48、圆角 12、按压反馈由 Material3 提供。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(Dimen.btnRadius),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** 区块标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

/** 空状态占位。 */
@Composable
fun EmptyState(text: String, onAction: (() -> Unit)? = null, actionText: String = "去添加") {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onAction != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

/**
 * 指标行：图标芯片 + 标签 + 大数字，用于首页统计胶囊与子页汇总卡片，保证全局一致。
 * valueColor 默认用 onSurface，可传语义色（如收入绿、支出红）。
 */
@Composable
fun MetricLine(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(Dimen.s8),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(Dimen.s12))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (valueContent != null) {
                    valueContent()
                } else {
                    Text(
                        value, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = valueColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(Dimen.s6))
                    trailing()
                }
            }
        }
    }
}

/** 加载指示。 */
@Composable
fun LoadingIndicator() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** 浮动添加按钮（FAB）。 */
@Composable
fun AddFloating(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(Icons.Filled.Add, contentDescription = "新增")
    }
}

/** 通用删除确认对话框：避免误删笔记/待办/密码/纪念日/记账/菜谱/饮食等条目。 */
@Composable
fun ConfirmDeleteDialog(
    message: String,
    onDismiss: () -> Unit = {},
    title: String = "删除确认",
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

/**
 * 工具/专注卡片图标芯片的循环配色：用已定义的语义色（主色 + success/warning + error 容器）叠加透明度，
 * 避免引用主题未定义的 tertiary/secondaryContainer 槽位（会回退成 Material 默认紫，破坏 teal 主色一致性）。
 * index 决定配色，保证同种工具颜色稳定。
 */
@Composable
fun chipTint(index: Int): Pair<Color, Color> {
    val ex = LocalExtraColors.current
    return when (((index % 4) + 4) % 4) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        1 -> ex.success.copy(alpha = 0.16f) to ex.success
        2 -> ex.warning.copy(alpha = 0.18f) to ex.warning
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
}
