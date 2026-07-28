package com.lifebench.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 主题模式：跟随系统 / 浅色 / 深色，由设置中心持久化。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 额外语义色（Material3 默认 scheme 未包含 success/warning 等，统一在此扩展，避免硬编码）。 */
data class ExtraColors(val success: Color, val warning: Color, val whiteNoise: Color, val onSurfaceVariant: Color)
val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(SuccessLight, WarningLight, WhiteNoiseLight, OnSurfaceVariantLight)
}

/** 依据预设与深浅模式构建浅色色板（中性色全局统一，仅主/次色随预设）。 */
private fun buildLight(p: ThemePreset) = lightColorScheme(
    primary = p.primaryLight,
    onPrimary = Color.White,
    primaryContainer = p.primaryContainerLight,
    onPrimaryContainer = OnSurfaceLight,
    secondary = p.secondaryLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
)

/** 依据预设与深浅模式构建深色色板。 */
private fun buildDark(p: ThemePreset) = darkColorScheme(
    primary = p.primaryDark,
    onPrimary = Color(0xFF10201F),
    primaryContainer = p.primaryContainerDark,
    onPrimaryContainer = OnSurfaceDark,
    secondary = p.secondaryDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
)

/**
 * 全局主题包裹：根据 themeMode 选择深浅，依据 preset 选择主/次色；注入字体缩放与额外色。
 * 所有页面最外层统一调用，切换即时生效且无需重启。
 */
@Composable
fun LifeBenchTheme(
    preset: ThemePreset = DefaultPreset,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (dark) buildDark(preset) else buildLight(preset)
    val extra = if (dark) {
        ExtraColors(SuccessDark, WarningDark, WhiteNoiseDark, OnSurfaceVariantDark)
    } else {
        ExtraColors(SuccessLight, WarningLight, WhiteNoiseLight, OnSurfaceVariantLight)
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildTypography(fontScale),
            shapes = AppShapes,
            content = content,
        )
    }
}
