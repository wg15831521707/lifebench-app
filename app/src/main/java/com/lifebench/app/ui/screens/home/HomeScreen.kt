package com.lifebench.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebench.app.data.entity.TodoEntity
import com.lifebench.app.ui.theme.LocalQuadrantColors
import com.lifebench.app.ui.theme.LocalExtraColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.AppCard
import com.lifebench.app.ui.components.AppTopBar
import com.lifebench.app.ui.components.ConfirmDeleteDialog
import com.lifebench.app.ui.components.MetricLine
import com.lifebench.app.ui.components.chipTint
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
    val habits by Repo.habit.observeActiveHabits().collectAsStateWithLifecycle(emptyList())
    val allCheckIns by Repo.habit.observeAllCheckIns().collectAsStateWithLifecycle(emptyList())
    val todayKey = TimeUtil.dayKey()
    val todayHabitChecked = allCheckIns.count { it.date == todayKey }
    val byHabit = allCheckIns.groupBy { it.habitId }.mapValues { m -> m.value.map { it.date }.toSet() }
    val longestStreak = habits.maxOfOrNull { computeStreak(byHabit[it.id] ?: emptySet()) } ?: 0
    val streakMilestone = when {
        longestStreak >= 365 -> 365
        longestStreak >= 100 -> 365
        longestStreak >= 30 -> 100
        longestStreak >= 7 -> 30
        else -> 7
    }
    val streakProgress = (longestStreak.toFloat() / streakMilestone).coerceIn(0f, 1f)
    val streakRemain = (streakMilestone - longestStreak).coerceAtLeast(0)

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
    // 每日一语：以「日期」为随机种子，当天稳定、跨天换新（不按天序号循环，池大小无关）
    val daySeed = (cal.get(Calendar.YEAR) * 10000L
            + (cal.get(Calendar.MONTH) + 1) * 100L
            + cal.get(Calendar.DAY_OF_MONTH)).toLong()
    val quote = DAILY_QUOTES[kotlin.random.Random(daySeed).nextInt(DAILY_QUOTES.size)]

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
        // 每日一语
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.FormatQuote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(Dimen.s12))
                Column(Modifier.weight(1f)) {
                    Text("每日一语", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("$year 年 · $weekday · $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Dimen.s12))
            Text("「${quote.first}」", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(quote.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(Dimen.s12))

        // 统计胶囊：今日专注 + 睡眠
        Row(Modifier.padding(horizontal = Dimen.s16)) {
            MetricCapsule(
                icon = Icons.Filled.PlayArrow, label = "今日专注", value = "${focusMin} 分",
                actionText = "开始专注", onAction = { nav.navigate(Routes.FOCUS) },
                modifier = Modifier.weight(1f).padding(end = Dimen.s6)
            )
            MetricCapsule(
                icon = Icons.Filled.Bedtime, label = "睡眠概况", value = sleepText,
                actionText = "去记录", onAction = { nav.navigate(Routes.SLEEP) },
                modifier = Modifier.weight(1f).padding(start = Dimen.s6)
            )
        }

        Spacer(Modifier.height(Dimen.s12))

        // 本周支出
        MetricCapsule(
            icon = Icons.Filled.AccountBalanceWallet, label = "本周支出", value = "¥%.1f".format(weekExpense),
            valueColor = MaterialTheme.colorScheme.error, actionText = "记账", onAction = { nav.navigate(Routes.ACCOUNT) },
            modifier = Modifier.padding(horizontal = Dimen.s16)
        )

        // 首页习惯连续打卡入口
        if (habits.isNotEmpty()) {
            Spacer(Modifier.height(Dimen.s12))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimen.s16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("习惯打卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { nav.navigate(Routes.HABIT) }) { Text("管理") }
            }
            Spacer(Modifier.height(Dimen.s8))
            Row(Modifier.padding(horizontal = Dimen.s16)) {
                MetricCapsule(
                    icon = Icons.Filled.CheckCircle, label = "今日打卡", value = "$todayHabitChecked / ${habits.size}",
                    valueColor = MaterialTheme.colorScheme.primary, actionText = "去打卡", onAction = { nav.navigate(Routes.HABIT) },
                    modifier = Modifier.weight(1f).padding(end = Dimen.s6)
                )
                MetricCapsule(
                    icon = Icons.Filled.Whatshot, label = "最长连续", value = "$longestStreak 天",
                    valueColor = LocalExtraColors.current.success, actionText = "查看", onAction = { nav.navigate(Routes.HABIT) },
                    modifier = Modifier.weight(1f).padding(start = Dimen.s6)
                )
            }
            Spacer(Modifier.height(Dimen.s8))
            // 连续天数进度条：最长连续天数向里程碑（7/30/100/365）递进
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Whatshot, contentDescription = null, tint = LocalExtraColors.current.success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("连续打卡目标", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("$longestStreak / $streakMilestone 天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { streakProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = LocalExtraColors.current.success,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (longestStreak >= streakMilestone) "已达成 $streakMilestone 天连续目标！"
                    else "再坚持 $streakRemain 天，解锁 $streakMilestone 天连续",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Dimen.s16))
        // 待办四象限「田」字格
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimen.s16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("待办四象限", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { nav.navigate(Routes.TODO) }) { Text("管理") }
        }
        Spacer(Modifier.height(Dimen.s8))
        TodoQuadrantGrid(
            todos = todos,
            onCellClick = { nav.navigate(Routes.TODO) },
            onCheck = { t -> scope.launch { Repo.todo.update(t.copy(done = true, archived = true)) } }
        )

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
        // 快捷跳转：两大枢纽
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimen.s16),
            horizontalArrangement = Arrangement.spacedBy(Dimen.s12)
        ) {
            HubShortcut("专注空间", Icons.Filled.Spa, "番茄钟 · 睡眠 · 饮食 · 习惯", Routes.FOCUS_HUB, nav, Modifier.weight(1f), 0)
            HubShortcut("工具箱", Icons.Filled.Widgets, "待办 · 记账 · 笔记 · 更多", Routes.TOOLS, nav, Modifier.weight(1f), 1)
        }

        Spacer(Modifier.height(Dimen.s16))
        Text("  全部工具", style = MaterialTheme.typography.titleMedium,
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
                val (c, t) = chipTint(i)
                Column(
                    Modifier.clickable { nav.navigate(e.route) }.padding(Dimen.s8),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = c,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(e.icon, contentDescription = e.label, tint = t)
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

/**
 * 统计胶囊：图标芯片 + 指标行 + 行动按钮，统一首页三项数据概览的视觉。
 */
@Composable
private fun MetricCapsule(
    icon: ImageVector,
    label: String,
    value: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    AppCard(modifier = modifier) {
        MetricLine(icon = icon, label = label, value = value, valueColor = valueColor)
        Spacer(Modifier.height(Dimen.s8))
        TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionText) }
    }
}

/**
 * 待办四象限「田」字格：2×2 布局、子卡间 12dp 间隔形成十字留白，
 * 每格独立语义背景色，一眼可读「该先做什么」。
 */
@Composable
private fun TodoQuadrantGrid(
    todos: List<TodoEntity>,
    onCellClick: () -> Unit,
    onCheck: (TodoEntity) -> Unit
) {
    val q = LocalQuadrantColors.current
    val cells = listOf(
        QuadrantInfo(0, "紧急 · 重要", "立即处理", q.q1Bg, q.q1Accent, q.q1Text),
        QuadrantInfo(1, "重要 · 不急", "安排时间", q.q2Bg, q.q2Accent, q.q2Text),
        QuadrantInfo(2, "紧急 · 不重要", "可委托", q.q3Bg, q.q3Accent, q.q3Text),
        QuadrantInfo(3, "不急 · 不重要", "尽量删减", q.q4Bg, q.q4Accent, q.q4Text),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimen.s16)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimen.s12)) {
            QuadrantCell(cells[0], todos, onCellClick, onCheck, Modifier.weight(1f))
            QuadrantCell(cells[1], todos, onCellClick, onCheck, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Dimen.s12))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimen.s12)) {
            QuadrantCell(cells[2], todos, onCellClick, onCheck, Modifier.weight(1f))
            QuadrantCell(cells[3], todos, onCellClick, onCheck, Modifier.weight(1f))
        }
    }
}

/** 单个象限子卡：语义底色 + 标题与计数 + 最多 3 条任务预览 + 行动提示。 */
@Composable
private fun QuadrantCell(
    info: QuadrantInfo,
    todos: List<TodoEntity>,
    onCellClick: () -> Unit,
    onCheck: (TodoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = todos.filter { it.quadrant == info.index }.take(3)
    val count = todos.count { it.quadrant == info.index }
    Surface(
        modifier = modifier
            .heightIn(min = 132.dp)
            .clickable { onCellClick() },
        shape = RoundedCornerShape(Dimen.s8),
        color = info.bg
    ) {
        Column(Modifier.padding(Dimen.s12)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(info.accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(info.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = info.text)
                Spacer(Modifier.weight(1f))
                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = info.text.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(10.dp))
            if (items.isEmpty()) {
                Text("暂无任务", fontSize = 12.sp, color = info.text.copy(alpha = 0.5f))
            } else {
                items.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = false,
                            onCheckedChange = { onCheck(t) },
                            modifier = Modifier.size(18.dp),
                            colors = CheckboxDefaults.colors(uncheckedColor = info.accent, checkedColor = info.accent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.title,
                            fontSize = 12.5.sp,
                            color = info.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(info.hint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = info.text.copy(alpha = 0.75f))
        }
    }
}

/** 象限展示数据。 */
private data class QuadrantInfo(
    val index: Int,
    val label: String,
    val hint: String,
    val bg: Color,
    val accent: Color,
    val text: Color
)

/** 首页枢纽快捷卡：大图标芯片 + 标题 + 副标题，一键进入对应主导航。 */
@Composable
private fun HubShortcut(
    label: String, icon: ImageVector, desc: String, route: String,
    nav: NavController, modifier: Modifier = Modifier, accent: Int
) {
    val (c, t) = chipTint(accent)
    AppCard(modifier = modifier, onClick = { nav.navigate(route) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = c, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = t, modifier = Modifier.size(24.dp)) }
            }
            Spacer(Modifier.width(Dimen.s12))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class Quick(val label: String, val icon: ImageVector, val route: String)
private val quickEntries = listOf(
    Quick("番茄钟", Icons.Filled.Alarm, Routes.FOCUS),
    Quick("待办", Icons.Filled.Checklist, Routes.TODO),
    Quick("记账", Icons.Filled.AccountBalanceWallet, Routes.ACCOUNT),
    Quick("睡眠", Icons.Filled.Bedtime, Routes.SLEEP),
    Quick("饮食", Icons.Filled.Restaurant, Routes.DIET),
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

/** 计算连续打卡天数：从今天（或昨天，若今天未打卡）往前连续计数。 */
private fun computeStreak(dates: Set<Long>): Int {
    if (dates.isEmpty()) return 0
    var cursor = TimeUtil.dayKey()
    if (cursor !in dates) {
        cursor = TimeUtil.dayKey(cursor - 86_400_000L)
        if (cursor !in dates) return 0
    }
    var streak = 0
    while (cursor in dates) {
        streak++
        cursor = TimeUtil.dayKey(cursor - 86_400_000L)
    }
    return streak
}

// 每日一语（中 + 英），约 30 条。以日期为种子随机选取：当天稳定、跨天换新。
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
    "你怎样度过一天，就怎样度过一生。" to "How you spend your days is how you spend your life.",
    "不积跬步，无以至千里。" to "Without accumulating small steps, one cannot reach a thousand miles.",
    "凡是过往，皆为序章。" to "What's past is prologue.",
    "滴水穿石，不是力量大，而是功夫深。" to "Dripping water hollows out stone, not through force but persistence.",
    "你现在偷的懒，都会变成未来的难。" to "The laziness of today becomes the hardship of tomorrow.",
    "掌控时间的人，掌控人生。" to "He who controls his time controls his life.",
    "真正的平静，来自内心的秩序。" to "True calm comes from inner order.",
    "与其抱怨黑暗，不如提灯前行。" to "Instead of complaining about the dark, carry a lantern forward.",
    "把时间花在重要的事上。" to "Spend your time on what matters.",
    "习惯决定性格，性格决定命运。" to "Character is destiny, shaped by habit.",
    "不要为打翻的牛奶哭泣。" to "Don't cry over spilled milk.",
    "做正确的事，而非容易的事。" to "Do the right thing, not the easy thing.",
    "专注当下，是最好的修行。" to "Presence is the best practice.",
    "慢就是快，少即是多。" to "Slow is fast; less is more.",
    "每一次坚持，都是对懒惰的胜利。" to "Every persistence is a win over laziness.",
    "目标清晰，路自通畅。" to "With a clear goal, the path opens.",
    "休息，是为了走更远的路。" to "Rest is to go further.",
    "你现在做的事，正在定义未来的你。" to "What you do now defines the future you.",
    "勇敢的人先享受世界。" to "The brave enjoy the world first.",
    "让优秀成为一种习惯。" to "Make excellence a habit.",
)
