package com.lifebench.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 待办四象限语义色板（首页「田」字格专用）。
 * 浅色用柔彩底 + 深色字（对比度 ≥ 4.5:1，满足 WCAG AA）；
 * 深色用沉稳底 + 浅色字，保证暗色模式下同样清晰可读。
 * 四格含义：
 *  Q1 重要且紧急（立即做）/ Q2 重要不紧急（计划做）/
 *  Q3 不重要但紧急（委托）/ Q4 不重要不紧急（删减）。
 */
data class QuadrantPalette(
    val q1Bg: Color, val q1Accent: Color, val q1Text: Color,
    val q2Bg: Color, val q2Accent: Color, val q2Text: Color,
    val q3Bg: Color, val q3Accent: Color, val q3Text: Color,
    val q4Bg: Color, val q4Accent: Color, val q4Text: Color,
)

/** 浅色模式：柔彩背景 + 深色字。 */
val LightQuadrantColors = QuadrantPalette(
    q1Bg = Color(0xFFFFF1F0), q1Accent = Color(0xFFE5484D), q1Text = Color(0xFF7A1216),
    q2Bg = Color(0xFFEEF2FF), q2Accent = Color(0xFF4F46E5), q2Text = Color(0xFF1E1B4B),
    q3Bg = Color(0xFFFFF7EB), q3Accent = Color(0xFFD97706), q3Text = Color(0xFF7C2D12),
    q4Bg = Color(0xFFECFDF5), q4Accent = Color(0xFF059669), q4Text = Color(0xFF064E3B),
)

/** 深色模式：低饱和沉稳底 + 浅色字。 */
val DarkQuadrantColors = QuadrantPalette(
    q1Bg = Color(0xFF3A2123), q1Accent = Color(0xFFFF7A7E), q1Text = Color(0xFFF6D6D7),
    q2Bg = Color(0xFF21244A), q2Accent = Color(0xFFA5ACFF), q2Text = Color(0xFFE0E3FF),
    q3Bg = Color(0xFF3A2C16), q3Accent = Color(0xFFF0A23C), q3Text = Color(0xFFF6E2C4),
    q4Bg = Color(0xFF13312A), q4Accent = Color(0xFF4FD89A), q4Text = Color(0xFFCFF6E6),
)

/** 全局可读的四象限色板，页面内通过 LocalQuadrantColors.current 取用。 */
val LocalQuadrantColors = compositionLocalOf { LightQuadrantColors }
