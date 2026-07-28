package com.lifebench.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 多套主题色彩预设。每套定义主色/次色在浅色与深色模式下的取值，
 * 背景、表面、文字等中性色保持全局统一（见 Color.kt），仅主/次色随预设切换，
 * 既能一键换肤，又保证可读性。默认预设为「经典青」。
 */
data class ThemePreset(
    val id: String,
    val name: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val primaryContainerLight: Color,
    val primaryContainerDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
)

val ThemePresets = listOf(
    ThemePreset(
        id = "teal", name = "经典青",
        primaryLight = Color(0xFF1A9C84), primaryDark = Color(0xFF7FD0C2),
        primaryContainerLight = Color(0xFFDCF3EE), primaryContainerDark = Color(0xFF1F4A44),
        secondaryLight = Color(0xFFE0917A), secondaryDark = Color(0xFFD9A292),
    ),
    ThemePreset(
        id = "blue", name = "天空蓝",
        primaryLight = Color(0xFF2F6FE0), primaryDark = Color(0xFF82A9FF),
        primaryContainerLight = Color(0xFFDCE7FF), primaryContainerDark = Color(0xFF1A2C57),
        secondaryLight = Color(0xFF1FB6C9), secondaryDark = Color(0xFF4FC3D6),
    ),
    ThemePreset(
        id = "green", name = "薄荷绿",
        primaryLight = Color(0xFF1FA971), primaryDark = Color(0xFF5FCF9B),
        primaryContainerLight = Color(0xFFD2F5E4), primaryContainerDark = Color(0xFF14402F),
        secondaryLight = Color(0xFF8FCF6B), secondaryDark = Color(0xFF76B85A),
    ),
    ThemePreset(
        id = "orange", name = "晚霞橙",
        primaryLight = Color(0xFFEF7A2E), primaryDark = Color(0xFFFFA766),
        primaryContainerLight = Color(0xFFFCE0CC), primaryContainerDark = Color(0xFF5E2E12),
        secondaryLight = Color(0xFFF2A93B), secondaryDark = Color(0xFFD9912C),
    ),
    ThemePreset(
        id = "pink", name = "蔷薇粉",
        primaryLight = Color(0xFFD6497F), primaryDark = Color(0xFFFF8FB6),
        primaryContainerLight = Color(0xFFFFD9E6), primaryContainerDark = Color(0xFF5A1E35),
        secondaryLight = Color(0xFFC76BB0), secondaryDark = Color(0xFFB2579B),
    ),
    ThemePreset(
        id = "purple", name = "星空紫",
        primaryLight = Color(0xFF7A5CD6), primaryDark = Color(0xFFB39BF0),
        primaryContainerLight = Color(0xFFE7DEFF), primaryContainerDark = Color(0xFF2E2157),
        secondaryLight = Color(0xFF5BC8D6), secondaryDark = Color(0xFF46B0C0),
    ),
)

val DefaultPreset: ThemePreset = ThemePresets.first()

fun presetById(id: String): ThemePreset = ThemePresets.firstOrNull { it.id == id } ?: DefaultPreset
