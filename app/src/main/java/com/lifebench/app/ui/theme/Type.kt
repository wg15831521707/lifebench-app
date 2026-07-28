package com.lifebench.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifebench.app.R

/**
 * 字族定义（字体资源见 res/font）。
 * - DisplayFont：Space Grotesk，几何无衬线，用于标题/数字，辨识度高、有科技感。
 * - TextFont：Plus Jakarta Sans，人文无衬线，用于正文/说明，长文易读。
 * 两者均为可变字体（单文件含完整字重轴），Compose 会按 TextStyle 的 weight 自动取轴。
 */
val DisplayFont = FontFamily(Font(R.font.space_grotesk))
val TextFont = FontFamily(Font(R.font.plus_jakarta_sans))

/**
 * 字体规格。fontScale 由设置中心调节（0.85×~1.3×），全树生效。
 * 标题类统一 DisplayFont，正文类统一 TextFont，形成稳定的「标题 vs 正文」对比。
 */
fun buildTypography(scale: Float): Typography {
    val s = scale
    return Typography(
        displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = (28 * s).sp, lineHeight = (34 * s).sp),
        titleLarge    = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = (20 * s).sp),
        titleMedium   = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = (16 * s).sp),
        bodyLarge     = TextStyle(fontFamily = TextFont, fontSize = (16 * s).sp),
        bodyMedium    = TextStyle(fontFamily = TextFont, fontSize = (14 * s).sp),
        labelLarge    = TextStyle(fontFamily = TextFont, fontSize = (14 * s).sp),
        bodySmall     = TextStyle(fontFamily = TextFont, fontSize = (12 * s).sp),
    )
}
