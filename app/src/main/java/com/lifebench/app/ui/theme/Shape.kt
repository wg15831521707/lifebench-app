package com.lifebench.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 统一形状令牌：圆角与 Dimension 中的圆角常量保持一致，供 MaterialTheme 使用。
 * 业务组件如需特殊圆角，仍优先引用 Dimen.cardRadius / btnRadius 等以集中管理。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),   // 按钮、输入框
    large = RoundedCornerShape(16.dp),    // 卡片
    extraLarge = RoundedCornerShape(20.dp) // 弹窗
)
