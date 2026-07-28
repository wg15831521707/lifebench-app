package com.lifebench.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体规格。fontScale 由设置中心调节（0.85×~1.3×），全树生效。
 */
fun buildTypography(scale: Float): Typography {
    val s = scale
    return Typography(
        displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = (28 * s).sp, lineHeight = (34 * s).sp),
        titleLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = (20 * s).sp),
        titleMedium   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = (16 * s).sp),
        bodyLarge     = TextStyle(fontSize = (16 * s).sp),
        bodyMedium    = TextStyle(fontSize = (14 * s).sp),
        labelLarge    = TextStyle(fontSize = (14 * s).sp),
        bodySmall     = TextStyle(fontSize = (12 * s).sp),
    )
}
