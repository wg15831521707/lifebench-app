package com.lifebench.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.WeatherDemo
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.AppCard
import com.lifebench.app.ui.components.AppTopBar
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.ThemeMode
import com.lifebench.app.util.CalcUtil
import com.lifebench.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.util.*

/**
 * 首页数据仪表盘：聚合今日专注、睡眠概况、本周收支、运动步数、精简天气，并提供快捷入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    var focusMin by remember { mutableStateOf(0) }
    var weekExpense by remember { mutableStateOf(0.0) }
    val recentSleep by Repo.sleep.observeRecent().collectAsStateWithLifecycle(emptyList())
    val weekSteps by Repo.step.observeWeek().collectAsStateWithLifecycle(emptyList())
    val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")

    val now = System.currentTimeMillis()
    LaunchedEffect(Unit) {
        val dayStart = TimeUtil.dayKey(now)
        focusMin = Repo.focus.focusMinutesBetween(dayStart, now)
        // 本周一 0 点 ~ 下周一 0 点
        val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        TimeUtil.dayKey(cal.timeInMillis).also { ws ->
            val we = ws + 7L * 86_400_000
            weekExpense = Repo.account.sumByType(0, ws, we)
        }
    }

    val weather = remember { WeatherDemo.current() }
    val todaySteps = weekSteps.firstOrNull()?.steps ?: 0
    val lastSleep = recentSleep.firstOrNull()
    val sleepText = lastSleep?.let { "${CalcUtil.fmtSleep(it.durationMin)}" } ?: "未记录"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar(
            title = "工作台",
            actions = {
                IconButton(onClick = {
                    val next = when (themeMode) {
                        "SYSTEM" -> "LIGHT"; "LIGHT" -> "DARK"; else -> "SYSTEM"
                    }
                    scope.launch { Repo.settings.setThemeMode(next) }
                }) {
                    Icon(
                        if (themeMode == "DARK") Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "主题切换"
                    )
                }
            }
        )

        Spacer(Modifier.height(Dimen.s12))

        // 天气精简卡
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WeatherDemo.iconOf(weather.condition), fontSize = 32.dp.value.sp)
                Spacer(Modifier.width(Dimen.s12))
                Column(Modifier.weight(1f)) {
                    Text(weather.city, fontWeight = FontWeight.SemiBold)
                    Text("${weather.temp}° ${weather.condition} · 体感${weather.feel}°",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { nav.navigate(Routes.WEATHER) }) { Text("详情") }
            }
        }

        Spacer(Modifier.height(Dimen.s12))

        // 今日专注 + 睡眠
        Row(Modifier.padding(horizontal = Dimen.s16)) {
            AppCard(Modifier.weight(1f).padding(end = Dimen.s6)) {
                Text("今日专注", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${focusMin} 分", style = MaterialTheme.typography.displayMedium)
                TextButton(onClick = { nav.navigate(Routes.FOCUS) }) { Text("开始专注") }
            }
            AppCard(Modifier.weight(1f).padding(start = Dimen.s6)) {
                Text("睡眠概况", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sleepText, style = MaterialTheme.typography.displayMedium)
                TextButton(onClick = { nav.navigate(Routes.SLEEP) }) { Text("去记录") }
            }
        }

        Spacer(Modifier.height(Dimen.s12))

        // 本周收支 + 步数
        Row(Modifier.padding(horizontal = Dimen.s16)) {
            AppCard(Modifier.weight(1f).padding(end = Dimen.s6)) {
                Text("本周支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥%.1f".format(weekExpense), style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { nav.navigate(Routes.ACCOUNT) }) { Text("记账") }
            }
            AppCard(Modifier.weight(1f).padding(start = Dimen.s6)) {
                Text("今日步数", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$todaySteps", style = MaterialTheme.typography.displayMedium)
                TextButton(onClick = { nav.navigate(Routes.STEPS) }) { Text("详情") }
            }
        }

        Spacer(Modifier.height(Dimen.s16))
        Text("  快捷入口", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimen.s16))

        Spacer(Modifier.height(Dimen.s8))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.s16).heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(Dimen.s8),
            horizontalArrangement = Arrangement.spacedBy(Dimen.s8)
        ) {
            items(quickEntries.size) { i ->
                val e = quickEntries[i]
                Column(
                    Modifier.clickable { nav.navigate(e.route) }.padding(Dimen.s8),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(e.icon, contentDescription = e.label, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(e.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}

private data class Quick(val label: String, val icon: ImageVector, val route: String)
private val quickEntries = listOf(
    Quick("番茄钟", Icons.Filled.Alarm, Routes.FOCUS),
    Quick("待办", Icons.Filled.Checklist, Routes.TODO),
    Quick("记账", Icons.Filled.AccountBalanceWallet, Routes.ACCOUNT),
    Quick("睡眠", Icons.Filled.Bedtime, Routes.SLEEP),
    Quick("饮食", Icons.Filled.Restaurant, Routes.DIET),
    Quick("健身", Icons.Filled.FitnessCenter, Routes.FITNESS),
    Quick("舒尔特", Icons.Filled.GridView, Routes.SCHULTE),
    Quick("速读", Icons.Filled.Speed, Routes.SPEED_READ),
    Quick("笔记", Icons.Filled.Note, Routes.NOTE),
    Quick("密码箱", Icons.Filled.Lock, Routes.PASSWORD),
    Quick("纪念日", Icons.Filled.Celebration, Routes.ANNIVERSARY),
    Quick("步数", Icons.Filled.DirectionsWalk, Routes.STEPS),
)

// 小工具：睡眠时长展示
private fun CalcUtil.fmtSleep(min: Int): String {
    val h = min / 60; val m = min % 60
    return "${h}h${m}m"
}
