package com.lifebench.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效时长与曲线集中管理：页面切换 300ms、卡片弹簧微动效、列表错峰入场。
 */
val NavTransition = tween<Float>(300)
val DialogTransition = tween<Float>(250)
val CardPressSpring = spring<Float>(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)
val ListItemStagger = 80 // 每个列表项错峰入场间隔(ms)
