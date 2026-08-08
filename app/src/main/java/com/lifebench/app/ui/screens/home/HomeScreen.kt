package com.lifebench.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.entity.TodoEntity
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.AppCard
import com.lifebench.app.ui.components.ConfirmDeleteDialog
import com.lifebench.app.ui.components.HeroCard
import com.lifebench.app.ui.components.MetricLine
import com.lifebench.app.ui.components.SectionHeader
import com.lifebench.app.ui.components.ToolMeta
import com.lifebench.app.ui.components.ToolTile
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.ui.theme.LocalQuadrantColors
import com.lifebench.app.ui.theme.ThemeMode
import com.lifebench.app.util.CalcUtil
import com.lifebench.app.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 首页数据仪表盘：聚合今日专注、睡眠概况、本周收支，并提供快捷入口。
 *
 * 性能要点（针对「工作台内容增多后卡顿、不跟手」）：
 * 1) 整页用 LazyColumn 虚拟化，只合成/测量可视区；
 * 2) 各区块（习惯打卡 / 待办四象限 / 已完成 / 全部工具）**自带流订阅**：
 *    某条数据变化（如勾选一个待办）只重排受影响区块，不触发整页 LazyColumn 重排；
 * 3) 滚动列表的 item 上**不挂 reveal() 等入场动画**——这类 LaunchedEffect(Unit)+graphicsLayer
 *    动画会在 item 进出视野时反复重播、每帧占用主线程，是「滑动不跟手」的直接元凶。
 *    首页为长滚动页，故去掉逐条入场动效，保流畅优先。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")
    val recentSleep by Repo.sleep.observeRecent().collectAsStateWithLifecycle(emptyList())

    // 仅首页顶部 Hero/概览所需的轻量流（随计时或记账变化，频率低，不驱动整页高频重排）
    val now = System.currentTimeMillis()
    val dayStart = TimeUtil.dayKey(now)
    val focusMin by Repo.focus.focusMinutesBetweenFlow(dayStart, now + 1)
        .collectAsStateWithLifecycle(initialValue = 0)
    val cal = remember { Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY } }
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    val weekStart = TimeUtil.dayKey(cal.timeInMillis)
    val weekEnd = weekStart + 7L * 86_400_000
    val weekExpense by Repo.account.sumByTypeFlow(0, weekStart, weekEnd)
        .collectAsStateWithLifecycle(initialValue = 0.0)

    val lastSleep = recentSleep.firstOrNull()
    val sleepHoursText = lastSleep?.let { CalcUtil.fmtSleep(it.durationMin) } ?: "未记录"
    val sleepQuality = lastSleep?.quality?.let {
        when (it) { 1 -> "差"; 2 -> "中"; 3 -> "好"; else -> null }
    }

    // 今日日期
    val todayCal = remember { Calendar.getInstance() }
    val year = todayCal.get(Calendar.YEAR)
    val weekday = SimpleDateFormat("EEEE", Locale.CHINA).format(todayCal.time)
    val dateStr = "${todayCal.get(Calendar.MONTH) + 1}月${todayCal.get(Calendar.DAY_OF_MONTH)}日"
    val hour = todayCal.get(Calendar.HOUR_OF_DAY)
    val greetWord = when {
        hour < 5 -> "夜深了"; hour < 11 -> "早上好"; hour < 14 -> "中午好"
        hour < 18 -> "下午好"; hour < 22 -> "晚上好"; else -> "夜深了"
    }
    val greetText = "$greetWord，王浩"
    // 每日一语：以「日期」为随机种子，当天稳定、跨天换新
    val daySeed = (todayCal.get(Calendar.YEAR) * 10000L
            + (todayCal.get(Calendar.MONTH) + 1) * 100L
            + todayCal.get(Calendar.DAY_OF_MONTH)).toLong()
    val quote = DAILY_QUOTES[kotlin.random.Random(daySeed).nextInt(DAILY_QUOTES.size)]

    val toolRows = remember { homeToolMetas.chunked(2) }

    LazyColumn(Modifier.fillMaxSize()) {
        // 主题切换已移入 HeroCard 右上角（替换原头像徽章），无需独立顶栏
        item { Spacer(Modifier.height(Dimen.s8)) }
        item {
            HeroCard(
                greeting = greetText,
                date = "$year 年 · $weekday · $dateStr",
                focusMin = focusMin,
                focusTarget = 120,
                themeMode = themeMode,
                onThemeToggle = {
                    val next = when (themeMode) {
                        "SYSTEM" -> "LIGHT"; "LIGHT" -> "DARK"; else -> "SYSTEM"
                    }
                    scope.launch { Repo.settings.setThemeMode(next) }
                },
                modifier = Modifier.padding(horizontal = Dimen.s16)
            )
        }

        item { Spacer(Modifier.height(Dimen.s12)) }
        item { HomeQuoteCard(quote = quote, year = year, weekday = weekday, dateStr = dateStr) }

        item { Spacer(Modifier.height(Dimen.s12)) }
        item {
            HomeOverviewSection(
                focusMin = focusMin,
                sleepHoursText = sleepHoursText,
                sleepQuality = sleepQuality,
                weekExpense = weekExpense,
                nav = nav
            )
        }

        item { Spacer(Modifier.height(Dimen.s16)) }
        item { HomeStreakSection(nav = nav) }

        item { Spacer(Modifier.height(Dimen.s16)) }
        item { HomeQuadrantSection(nav = nav, scope = scope) }

        item { Spacer(Modifier.height(Dimen.s16)) }
        item { HomeArchivedSection(scope = scope) }

        item { Spacer(Modifier.height(Dimen.s16)) }
        // 全部工具：复用 ToolTile（与专注/工具页一致的双列卡片风格），9 个工具全部可达
        item { SectionHeader("全部工具") }
        item { Spacer(Modifier.height(Dimen.s8)) }
        itemsIndexed(toolRows) { idx, rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimen.s16),
                horizontalArrangement = Arrangement.spacedBy(Dimen.s12)
            ) {
                rowItems.forEach { meta ->
                    Box(Modifier.weight(1f)) { ToolTile(meta) { nav.navigate(meta.route) } }
                }
                // 奇数最后一个单元格单独成行时，右侧补占位保持左对齐
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            if (idx < toolRows.lastIndex) Spacer(Modifier.height(Dimen.s12))
        }
        item { Spacer(Modifier.height(Dimen.s8)) }
    }
}

/** 每日一语卡片。 */
@Composable
private fun HomeQuoteCard(quote: Pair<String, String>, year: Int, weekday: String, dateStr: String) {
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
}

/** 今日概览：今日专注 + 睡眠概况 + 本周支出 三块 KPI。 */
@Composable
private fun HomeOverviewSection(
    focusMin: Int,
    sleepHoursText: String,
    sleepQuality: String?,
    weekExpense: Double,
    nav: NavController
) {
    Column {
        SectionHeader("今日概览", moreLabel = "查看", onMore = { nav.navigate(Routes.FOCUS) })
        Spacer(Modifier.height(Dimen.s8))
        // 统计胶囊：Row 用 IntrinsicSize.Min 让两盒等高
        Row(
            Modifier.padding(horizontal = Dimen.s16).height(IntrinsicSize.Min)
        ) {
            MetricCapsule(
                icon = Icons.Filled.PlayArrow, label = "今日专注", value = "${focusMin} 分",
                actionText = "开始专注", onAction = { nav.navigate(Routes.FOCUS) },
                modifier = Modifier.weight(1f).padding(end = Dimen.s6).fillMaxHeight()
            )
            MetricCapsule(
                icon = Icons.Filled.Bedtime, label = "睡眠概况", value = sleepHoursText,
                actionText = "去记录", onAction = { nav.navigate(Routes.SLEEP) },
                modifier = Modifier.weight(1f).padding(start = Dimen.s6).fillMaxHeight(),
                // 把「差/中/好」质量 chip 放到 value 下方独立一行，避免「5h3m」被截成「5h...」
                quality = sleepQuality
            )
        }
        Spacer(Modifier.height(Dimen.s12))
        MetricCapsule(
            icon = Icons.Filled.AccountBalanceWallet, label = "本周支出", value = "¥%.1f".format(weekExpense),
            valueColor = MaterialTheme.colorScheme.error, actionText = "记账", onAction = { nav.navigate(Routes.ACCOUNT) },
            modifier = Modifier.padding(horizontal = Dimen.s16)
        )
    }
}

/** 首页习惯连续打卡区：自带流订阅，仅在 habits/allCheckIns 变化时重排本区，不波及整页。 */
@Composable
private fun HomeStreakSection(nav: NavController) {
    val habits by Repo.habit.observeActiveHabits().collectAsStateWithLifecycle(emptyList())
    val allCheckIns by Repo.habit.observeAllCheckIns().collectAsStateWithLifecycle(emptyList())
    if (habits.isEmpty()) return
    // 派生量：仅 habits / allCheckIns 变化时才重算，避免无关 flow 触发时重复计算长列表
    val streakInfo = remember(habits, allCheckIns) {
        val todayKey = TimeUtil.dayKey()
        val byHabit = allCheckIns.groupBy { it.habitId }.mapValues { m -> m.value.map { it.date }.toSet() }
        val longest = habits.maxOfOrNull { computeStreak(byHabit[it.id] ?: emptySet()) } ?: 0
        val milestone = when {
            longest >= 365 -> 365
            longest >= 100 -> 365
            longest >= 30 -> 100
            longest >= 7 -> 30
            else -> 7
        }
        val progress = (longest.toFloat() / milestone).coerceIn(0f, 1f)
        val remain = (milestone - longest).coerceAtLeast(0)
        val todayChecked = allCheckIns.count { it.date == todayKey }
        StreakInfo(longest, milestone, progress, remain, todayChecked)
    }
    Column {
        Spacer(Modifier.height(Dimen.s12))
        SectionHeader("习惯打卡", moreLabel = "管理", onMore = { nav.navigate(Routes.HABIT) })
        Spacer(Modifier.height(Dimen.s8))
        HomeStreakCard(
            info = streakInfo,
            habitsSize = habits.size
        )
    }
}

/** 首页习惯连续打卡卡（对应模板 .streak 布局）。 */
@Composable
private fun HomeStreakCard(info: StreakInfo, habitsSize: Int) {
    AppCard(Modifier.padding(horizontal = Dimen.s16)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            // 左：最长连续大数字
            Surface(
                shape = RoundedCornerShape(Dimen.s12),
                color = LocalExtraColors.current.success.copy(alpha = 0.12f),
                modifier = Modifier.width(84.dp).fillMaxHeight()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Dimen.s12),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("${info.longest}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LocalExtraColors.current.success)
                    Text("最长连续 (天)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Dimen.s12))
            // 右：今日打卡 / 里程碑 + 进度条 + 提示
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimen.s8)) {
                    Surface(
                        shape = RoundedCornerShape(Dimen.s8), color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(Dimen.s8)) {
                            Text("${info.todayChecked} / $habitsSize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("今日打卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(Dimen.s8), color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(Dimen.s8)) {
                            Text("${info.milestone}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("里程碑 (天)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(Dimen.s8))
                LinearProgressIndicator(
                    progress = { info.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = LocalExtraColors.current.success,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(Dimen.s6))
                Text(
                    if (info.longest >= info.milestone) "已达成 ${info.milestone} 天连续目标！"
                    else "再坚持 ${info.remain} 天，解锁 ${info.milestone} 天连续 🎉",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 待办四象限区：自带 todos 流订阅，勾选待办只重排本区，不波及整页。 */
@Composable
private fun HomeQuadrantSection(nav: NavController, scope: CoroutineScope) {
    val todos by Repo.todo.observeActive().collectAsStateWithLifecycle(emptyList())
    Column {
        SectionHeader("待办四象限", moreLabel = "管理", onMore = { nav.navigate(Routes.TODO) })
        Spacer(Modifier.height(Dimen.s8))
        TodoQuadrantGrid(
            todos = todos,
            onCellClick = { nav.navigate(Routes.TODO) },
            onCheck = { t -> scope.launch { Repo.todo.update(t.copy(done = true, archived = true)) } }
        )
    }
}

/**
 * 统计胶囊：图标芯片 + 指标行 + 行动按钮，统一首页三项数据概览的视觉。
 * `trailing` 会在 value 文本右侧追加一个小型徽章（如睡眠质量"好/中/差"），避免长文本被截断。
 */
@Composable
private fun MetricCapsule(
    icon: ImageVector,
    label: String,
    value: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    quality: String? = null
) {
    AppCard(modifier = modifier) {
        MetricLine(icon = icon, label = label, value = value, valueColor = valueColor)
        // 质量 chip 单独一行：避免睡眠时长文本与 chip 同行挤压导致 ellipsis。
        if (quality != null) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SleepQualityChip(quality)
                Spacer(Modifier.width(Dimen.s6))
                Text("近一晚质量", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Dimen.s4))
        TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionText) }
    }
}

/** 睡眠质量小徽章：好=success，中=warning，差=error，与全局语义色一致。 */
@Composable
private fun SleepQualityChip(q: String) {
    val (bg, fg) = when (q) {
        "好" -> LocalExtraColors.current.success.copy(alpha = 0.18f) to LocalExtraColors.current.success
        "中" -> LocalExtraColors.current.warning.copy(alpha = 0.20f) to LocalExtraColors.current.warning
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Text(
            q,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
                // 简化空态：仅一行轻提示「暂无任务 · 立即处理/安排时间/…」。未恢复「＋ 点此添加任务」。
                Text("暂无任务", fontSize = 12.sp, color = info.text.copy(alpha = 0.72f))
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

/** 已完成列表区：自带 archived 流订阅；用普通 Column 渲染（已完成数量通常很少，且本区重排不影响整页）。 */
@Composable
private fun HomeArchivedSection(scope: CoroutineScope) {
    val archived by Repo.todo.observeArchived().collectAsStateWithLifecycle(emptyList())
    if (archived.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Dimen.s12))
        Text("  已完成（${archived.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Dimen.s16))
        Spacer(Modifier.height(Dimen.s8))
        archived.forEach { t ->
            key(t.id) {
                var showDel by remember { mutableStateOf(false) }
                AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.title, modifier = Modifier.weight(1f),
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { scope.launch { Repo.todo.update(t.copy(done = false, archived = false)) } }) { Text("恢复") }
                        IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
                    }
                }
                if (showDel) ConfirmDeleteDialog(message = "确定删除已完成的待办「${t.title}」吗？", onDismiss = { showDel = false }) { scope.launch { Repo.todo.delete(t) } }
            }
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

/** 首页习惯连续打卡派生数据（仅在 habits / allCheckIns 变化时才重算）。 */
private data class StreakInfo(
    val longest: Int,
    val milestone: Int,
    val progress: Float,
    val remain: Int,
    val todayChecked: Int,
)

/** 首页「全部工具」：与专注/工具页一致的双列卡片（ToolTile），全部工具可达。 */
private val homeToolMetas = listOf(
    ToolMeta("番茄钟", "专注计时", Icons.Filled.Alarm, Routes.FOCUS, 0),
    ToolMeta("待办", "四象限，分清轻重缓急", Icons.Filled.Checklist, Routes.TODO, 0),
    ToolMeta("记账", "随手记，掌控每一笔", Icons.Filled.AccountBalanceWallet, Routes.ACCOUNT, 1),
    ToolMeta("睡眠", "作息记录", Icons.Filled.Bedtime, Routes.SLEEP, 2),
    ToolMeta("饮食", "三餐记录", Icons.Filled.Restaurant, Routes.DIET, 1),
    ToolMeta("舒尔特", "训练专注力与反应", Icons.Filled.GridView, Routes.SCHULTE, 2),
    ToolMeta("笔记", "灵感随时记录", Icons.Filled.Note, Routes.NOTE, 0),
    ToolMeta("密码箱", "加密保管账号密码", Icons.Filled.Lock, Routes.PASSWORD, 3),
    ToolMeta("纪念日", "重要日子不错过", Icons.Filled.Celebration, Routes.ANNIVERSARY, 1),
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
