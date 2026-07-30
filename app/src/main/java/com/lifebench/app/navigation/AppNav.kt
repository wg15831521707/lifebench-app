package com.lifebench.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifebench.app.ui.screens.brain.*
import com.lifebench.app.ui.screens.home.HomeScreen
import com.lifebench.app.ui.screens.profile.ProfileScreen
import com.lifebench.app.ui.screens.tools.*

/**
 * 全局导航：底部四大主导航 + 各子页面路由注册。
 * - 四大主导航（首页/专注/工具/我的）在 NavBar 切换，单栈避免重复入栈。
 * - 子页面通过顶栏返回键 popBackStack 回到所属枢纽。
 * - 仅在顶级路由显示底部导航栏，进入子页自动隐藏，保证沉浸与空间。
 * - 选中态只改变图标/文字颜色，不显示背景色块，视觉反馈更深。
 * - 页面过渡：进入右滑入淡入，退出左滑出淡出；返回反之，时长 280ms。
 */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // 顶级路由：显示底部导航；其余子页隐藏。
    val showBottom = currentRoute in BottomTabs.map { it.route }

    // 统一的页面过渡动画（命名过渡参数挂到每个 composable 上，兼容 navigation-compose 2.7.x）
    val enterT: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(280)) + slideInHorizontally(animationSpec = tween(280)) { it / 6 }
    }
    val exitT: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(280)) + slideOutHorizontally(animationSpec = tween(280)) { -it / 6 }
    }
    val popEnterT: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(280)) + slideInHorizontally(animationSpec = tween(280)) { -it / 6 }
    }
    val popExitT: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(280)) + slideOutHorizontally(animationSpec = tween(280)) { it / 6 }
    }

    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    val currentRouteStr = backStack?.destination?.route
                    BottomTabs.forEach { tab ->
                        val selected = currentRouteStr == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    // 切换主导航时弹出到起点并保存状态，避免栈堆积
                                    popUpTo(nav.graph.startDestinationRoute ?: Routes.HOME) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            // 仅图标/文字变色，不显示背景色块
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(pad)
        ) {
            // —— 四大主导航 ——
            composable(Routes.HOME, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { HomeScreen(nav) }
            composable(Routes.FOCUS_HUB, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { FocusHubScreen(nav) }
            composable(Routes.TOOLS, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { ToolsHubScreen(nav) }
            composable(Routes.PROFILE, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { ProfileScreen(nav) }

            // —— 生活工具子页 ——
            composable(Routes.TODO, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { TodoScreen(nav) }
            composable(Routes.PASSWORD, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { PasswordScreen(nav) }
            composable(Routes.NOTE, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { NoteScreen(nav) }
            composable(Routes.ANNIVERSARY, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { AnniversaryScreen(nav) }
            composable(Routes.HABIT, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { HabitScreen(nav) }
            composable(Routes.SETTINGS, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { SettingsScreen(nav) }

            // —— 生活工具子页（番茄钟 / 睡眠 / 记账 / 饮食）——
            composable(Routes.FOCUS, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { FocusScreen(nav) }
            composable(Routes.SLEEP, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { SleepScreen(nav) }
            composable(Routes.ACCOUNT, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { AccountScreen(nav) }
            composable(Routes.DIET, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { DietScreen(nav) }

            // —— 舒尔特方格（专注力训练）——
            composable(Routes.SCHULTE, enterTransition = enterT, exitTransition = exitT, popEnterTransition = popEnterT, popExitTransition = popExitT) { SchulteScreen(nav) }
        }
    }
}
