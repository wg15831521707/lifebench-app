package com.lifebench.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 图表分类配色板（饼图 / 柱状图 / 图例）。
 *
 * 设计约束：
 * - 以品牌翡翠绿为首，搭配可区分、明度对比充分的同类色，浅色/深色模式通用；
 * - 颜色均为中明度，叠加深/浅背景与白色描边时仍清晰，避免旧版「青+桃」的低对比残留；
 * - 共 7 色，分类数超出时循环取色（pieData 通常 ≤ 7 类）。
 */
val ChartPalette = listOf(
    Color(0xFF1A9C84), // 翡翠绿（品牌主色）
    Color(0xFFE2A53B), // 琥珀
    Color(0xFF4C8BF5), // 蓝
    Color(0xFF9B7FD0), // 紫
    Color(0xFFE0786E), // 珊瑚
    Color(0xFF2DD4BF), // 青绿
    Color(0xFF7C8BA1), // 石板灰
)

/**
 * 习惯圆点配色板（8 色）。用于习惯列表的彩色图标芯片，与品牌色系协调、彼此可区分。
 * index 越界时取模循环，保证任意 colorIndex 都安全。
 */
val HabitDotPalette = listOf(
    Color(0xFF1A9C84), // 翡翠绿
    Color(0xFF4C8BF5), // 蓝
    Color(0xFFE2A53B), // 琥珀
    Color(0xFFE0786E), // 珊瑚
    Color(0xFF9B7FD0), // 紫
    Color(0xFF2DD4BF), // 青绿
    Color(0xFFF2749A), // 玫红
    Color(0xFF7C8BA1), // 石板灰
)

/** 按索引安全取习惯圆点色。 */
fun habitDotColor(index: Int): Color = HabitDotPalette.getOrElse(index.coerceAtLeast(0)) { HabitDotPalette.first() }
