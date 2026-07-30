package com.lifebench.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/** 底部三大主导航栏目（首页 / 工具 / 我的）。 */
data class TabItem(val route: String, val label: String, val icon: ImageVector)

val BottomTabs = listOf(
    TabItem(Routes.HOME, "首页", Icons.Filled.Home),
    TabItem(Routes.TOOLS, "工具", Icons.Filled.Widgets),
    TabItem(Routes.PROFILE, "我的", Icons.Filled.Person),
)
