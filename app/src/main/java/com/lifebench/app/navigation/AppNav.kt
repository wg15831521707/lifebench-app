package com.lifebench.app.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.AnimatedNavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifebench.app.ui.screens.brain.*
import com.lifebench.app.ui.screens.fit.*
import com.lifebench.app.ui.screens.home.HomeScreen
import com.lifebench.app.ui.screens.profile.ProfileScreen
import com.lifebench.app.ui.screens.tools.*

/**
 * 全局导航：底部五大主导航 + 各子页面路由注册。
 * - 五大主导航（首页/工具/训练/健身/我的）在 NavBar 切换，单栈避免重复入栈。
 * - 子页面通过顶栏返回键 popBackStack 回到所属枢纽。
 * - 仅在顶级路由显示底部导航栏，进入子页自动隐藏，保证沉浸与空间。
 */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // 顶级路由：显示底部导航；其余子页隐藏。
    val showBottom = currentRoute in BottomTabs.map { it.route }

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
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        AnimatedNavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(pad),
            // 页面过渡：进入自右滑入淡入，退出向左滑出淡出；返回反之，时长 280ms。
            enterTransition = {
                fadeIn(animationSpec = tween(280)) +
                    slideInHorizontally(animationSpec = tween(280)) { fullWidth -> fullWidth / 6 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(280)) +
                    slideOutHorizontally(animationSpec = tween(280)) { fullWidth -> -fullWidth / 6 }
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(280)) +
                    slideInHorizontally(animationSpec = tween(280)) { fullWidth -> -fullWidth / 6 }
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(280)) +
                    slideOutHorizontally(animationSpec = tween(280)) { fullWidth -> fullWidth / 6 }
            }
        ) {
            // —— 五大主导航 ——
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.TOOLS) { ToolsHubScreen(nav) }
            composable(Routes.BRAIN) { BrainHubScreen(nav) }
            composable(Routes.FIT) { FitHubScreen(nav) }
            composable(Routes.PROFILE) { ProfileScreen(nav) }

            // —— 生活工具子页 ——
            composable(Routes.TODO) { TodoScreen(nav) }
            composable(Routes.STEPS) { StepsScreen(nav) }
            composable(Routes.PASSWORD) { PasswordScreen(nav) }
            composable(Routes.NOTE) { NoteScreen(nav) }
            composable(Routes.ANNIVERSARY) { AnniversaryScreen(nav) }
            composable(Routes.WEATHER) { WeatherScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }

            // —— 健身饮食子页 ——
            composable(Routes.FOCUS) { FocusScreen(nav) }
            composable(Routes.SLEEP) { SleepScreen(nav) }
            composable(Routes.ACCOUNT) { AccountScreen(nav) }
            composable(Routes.DIET) { DietScreen(nav) }
            composable(Routes.FITNESS) { FitnessScreen(nav) }

            // —— 脑力与速读 ——
            composable(Routes.SCHULTE) { SchulteScreen(nav) }
            composable(Routes.SPEED_READ) { SpeedReadScreen(nav) }
            composable("brain_train/{category}") { back ->
                val cat = back.arguments?.getString("category") ?: "专注力"
                BrainTrainScreen(nav, cat)
            }
        }
    }
}
