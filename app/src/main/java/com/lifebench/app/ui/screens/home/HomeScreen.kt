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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.AppCard
import com.lifebench.app.ui.components.AppTopBar
import com.lifebench.app.ui.components.ConfirmDeleteDialog
import com.lifebench.app.ui.components.EmptyState
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.ThemeMode
import com.lifebench.app.util.CalcUtil
import com.lifebench.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 首页数据仪表盘：聚合今日专注、睡眠概况、本周收支，并提供快捷入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    var focusMin by remember { mutableStateOf(0) }
    var weekExpense by remember { mutableStateOf(0.0) }
    val recentSleep by Repo.sleep.observeRecent().collectAsStateWithLifecycle(emptyList())
    val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")
    val todos by Repo.todo.observeActive().collectAsStateWithLifecycle(emptyList())
    val archived by Repo.todo.observeArchived().collectAsStateWithLifecycle(emptyList())

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

    val lastSleep = recentSleep.firstOrNull()
    val sleepText = lastSleep?.let { "${CalcUtil.fmtSleep(it.durationMin)}" } ?: "未记录"

    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val weekday = SimpleDateFormat("EEEE", Locale.CHINA).format(cal.time)
    val dateStr = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    val quote = DAILY_QUOTES[cal.get(Calendar.DAY_OF_YEAR) % DAILY_QUOTES.size]

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
        // 年份 / 日期 / 每日一语
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("$year 年 · $weekday", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(dateStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimen.s8))
            Text("「${quote.first}」", fontWeight = FontWeight.SemiBold)
            Text(quote.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // 本周收支
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("本周支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("¥%.1f".format(weekExpense), style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { nav.navigate(Routes.ACCOUNT) }) { Text("记账") }
        }

        Spacer(Modifier.height(Dimen.s16))
        Text("  待办四象限", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimen.s16))

        Spacer(Modifier.height(Dimen.s8))
        if (todos.isEmpty()) {
            EmptyState("暂无进行中的待办，点下方「待办」添加", onAction = { nav.navigate(Routes.TODO) })
        } else {
            for (q in 0..3) {
                val qs = todos.filter { it.quadrant == q }
                if (qs.isNotEmpty()) {
                    Text("  ${QUADRANT_LABELS[q]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = Dimen.s16))
                    qs.forEach { t ->
                        AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = false, onCheckedChange = { scope.launch { Repo.todo.update(t.copy(done = true, archived = true)) } })
                                Column(Modifier.weight(1f).clickable { nav.navigate(Routes.TODO) }) {
                                    Text(t.title, fontWeight = FontWeight.SemiBold)
                                    if (t.dueTime != null) Text("到期 ${TimeUtil.formatHM(t.dueTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimen.s12))
        // 已完成列表：可删除 / 恢复未完成
        if (archived.isNotEmpty()) {
            Text("  已完成（${archived.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Dimen.s16))
            Spacer(Modifier.height(Dimen.s8))
            archived.forEach { t ->
                var showDel by remember { mutableStateOf(false) }
                AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, modifier = Modifier.weight(1f),
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { scope.launch { Repo.todo.update(t.copy(done = false, archived = false)) } }) { Text("恢复") }
                        IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
                    }
                }
                if (showDel) ConfirmDeleteDialog(message = "确定删除已完成的待办「${t.title}」吗？", onDismiss = { showDel = false }) { scope.launch { Repo.todo.delete(t) } }
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
    Quick("笔记", Icons.Filled.Note, Routes.NOTE),
    Quick("密码箱", Icons.Filled.Lock, Routes.PASSWORD),
    Quick("纪念日", Icons.Filled.Celebration, Routes.ANNIVERSARY),
)

// 小工具：睡眠时长展示
private fun CalcUtil.fmtSleep(min: Int): String {
    val h = min / 60; val m = min % 60
    return "${h}h${m}m"
}

// 科维四象限标签（与待办编辑一致）
private val QUADRANT_LABELS = listOf("重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急")

// 每日一语（中 + 英），按年内第几天轮换
private val DAILY_QUOTES = listOf(
    "今日事，今日毕。" to "Never put off till tomorrow what you can do today.",
    "千里之行，始于足下。" to "A journey of a thousand miles begins with a single step.",
    "自律给我自由。" to "Self-discipline sets you free.",
    "种一棵树最好的时间是十年前，其次是现在。" to "The best time to plant a tree was ten years ago; the second best is now.",
    "保持专注，持续进步。" to "Stay focused and keep improving.",
    "行动胜于空谈。" to "Action speaks louder than words.",
    "把简单的事做好就是不简单。" to "Doing simple things well is not simple at all.",
    "每天进步一点点。" to "Improve a little bit every single day.",
    "计划你的工作，工作你的计划。" to "Plan your work and work your plan.",
    "健康是人生第一财富。" to "Health is the first wealth in life.",
)
