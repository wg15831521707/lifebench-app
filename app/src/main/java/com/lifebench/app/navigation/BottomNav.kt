package com.lifebench.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/** 底部四大主导航栏目（训练模块并入「工具」枢纽，不再单独占位）。 */
data class TabItem(val route: String, val label: String, val icon: ImageVector)

val BottomTabs = listOf(
    TabItem(Routes.HOME, "首页", Icons.Filled.Home),
    TabItem(Routes.TOOLS, "工具", Icons.Filled.Widgets),
    TabItem(Routes.FIT, "健身", Icons.Filled.FitnessCenter),
    TabItem(Routes.PROFILE, "我的", Icons.Filled.Person),
)
