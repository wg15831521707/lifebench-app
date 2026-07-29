package com.lifebench.app.ui.screens.fit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import com.lifebench.app.service.FocusService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.entity.*
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.ChartPalette
import com.lifebench.app.ui.components.MetricLine
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.util.CalcUtil
import com.lifebench.app.util.NotificationUtil
import com.lifebench.app.util.TimeUtil
import com.lifebench.app.util.WhiteNoisePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView

/**
 * ===== 健身饮食专区：枢纽 + 番茄钟 + 睡眠 + 记账 + 饮食菜谱 + 健身计划 =====
 */

// ——— 训练计划数据模型（存 DataStore JSON，免 Room 迁移）———
private data class TrainingAction(
    val name: String,
    val muscle: String,
    val sets: Int,
    val reps: Int,
    val videoUrl: String = ""
)
private data class TrainingPlan(
    val name: String,
    val days: List<String>,
    val note: String,
    val actions: List<TrainingAction>
)

private val PLAN_LIST_TYPE = object : com.google.gson.reflect.TypeToken<List<TrainingPlan>>() {}.type
private fun parsePlans(json: String): List<TrainingPlan> = try {
    com.google.gson.Gson().fromJson(json, PLAN_LIST_TYPE) ?: emptyList()
} catch (_: Exception) { emptyList() }
private fun plansToJson(list: List<TrainingPlan>): String = com.google.gson.Gson().toJson(list)
private suspend fun savePlans(list: List<TrainingPlan>) = Repo.settings.setTrainingPlansJson(plansToJson(list))

// 周几顺序与真实星期映射、派生计算
private val WEEK_ORDER = listOf("一", "二", "三", "四", "五", "六", "日")
private fun todayWeekday(): String {
    val cal = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=周日..7=周六
    return WEEK_ORDER[(cal - 2 + 7) % 7]
}
private fun plansForDay(plans: List<TrainingPlan>, k: String): List<TrainingPlan> = plans.filter { k in it.days }
private fun actionsForDay(plans: List<TrainingPlan>, k: String): List<TrainingAction> = plansForDay(plans, k).flatMap { it.actions }
private fun muscleLabelFor(plans: List<TrainingPlan>): String {
    val ms = plans.flatMap { it.actions.map { a -> a.muscle } }.filter { it.isNotBlank() }.toSet()
    return if (ms.isNotEmpty()) ms.joinToString("+") else plans.firstOrNull()?.name ?: "训练"
}
private fun weekTrainingDays(plans: List<TrainingPlan>): Int = WEEK_ORDER.count { plansForDay(plans, it).isNotEmpty() }
private fun weeklyKcal(plans: List<TrainingPlan>): Int = ((plans.sumOf { it.actions.size } * 80) / 10) * 10
private fun streakDays(plans: List<TrainingPlan>): Int {
    val today = todayWeekday()
    var count = 0
    val idx = WEEK_ORDER.indexOf(today)
    for (i in 0 until 7) {
        val k = WEEK_ORDER[(idx - i + 7) % 7]
        if (plansForDay(plans, k).isNotEmpty()) count++ else break
    }
    return count
}

// 三分化动作池（生成计划时按器材过滤）
private data class PoolAction(val name: String, val muscle: String, val equip: String, val sets: Int, val reps: Int)
private val ACTIONS_POOL = listOf(
    PoolAction("哑铃卧推", "胸", "哑铃", 4, 10),
    PoolAction("俯卧撑", "胸", "徒手", 4, 12),
    PoolAction("哑铃飞鸟", "胸", "哑铃", 3, 12),
    PoolAction("绳索下压", "三头", "器械", 3, 12),
    PoolAction("臂屈伸", "三头", "徒手", 3, 12),
    PoolAction("杠铃划船", "背", "杠铃", 4, 10),
    PoolAction("引体向上", "背", "徒手", 4, 8),
    PoolAction("哑铃弯举", "二头", "哑铃", 3, 12),
    PoolAction("弹力带弯举", "二头", "弹力带", 3, 15),
    PoolAction("深蹲", "腿", "杠铃", 4, 10),
    PoolAction("箭步蹲", "腿", "徒手", 3, 12),
    PoolAction("腿举", "腿", "器械", 4, 12),
    PoolAction("哑铃推举", "肩", "哑铃", 3, 12),
    PoolAction("侧平举", "肩", "哑铃", 3, 15),
    PoolAction("平板支撑", "核心", "徒手", 3, 0)
)
private fun buildThreeSplit(equip: Set<String>): List<TrainingPlan> {
    fun pick(groups: List<String>) = ACTIONS_POOL.filter { it.muscle in groups && it.equip in equip }
        .map { TrainingAction(it.name, it.muscle, it.sets, it.reps) }
    fun mk(name: String, days: List<String>, acts: List<TrainingAction>): TrainingPlan {
        val a = if (acts.isEmpty()) listOf(TrainingAction("徒手${name}训练", if (name == "推") "胸" else if (name == "拉") "背" else "腿", 3, 12)) else acts
        return TrainingPlan(name, days, "", a)
    }
    return listOf(
        mk("推", listOf("一", "四"), pick(listOf("胸", "三头"))),
        mk("拉", listOf("二", "五"), pick(listOf("背", "二头"))),
        mk("腿", listOf("三", "六"), pick(listOf("腿", "肩")))
    )
}

// 问候语 / 日期提示 / 激励语
private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when { h < 6 -> "凌晨好"; h < 12 -> "早上好"; h < 14 -> "中午好"; h < 18 -> "下午好"; else -> "晚上好" }
}
private fun dateHint(plans: List<TrainingPlan>, curDay: String): String {
    val today = todayWeekday()
    return if (curDay == today) {
        if (actionsForDay(plans, today).isNotEmpty()) "今天有训练计划" else "今天休息，好好恢复"
    } else "周$curDay"
}
private val MOTIVATIONS = listOf(
    "今天也要动起来 💪", "坚持就是胜利，练一组是一组", "身体是革命的本钱，开始吧",
    "一点点进步，也是进步", "给自己一个流汗的理由", "自律给你自由", "今天不练，明天后悔"
)
private fun motivationText(day: String): String = MOTIVATIONS[WEEK_ORDER.indexOf(day).coerceIn(0, MOTIVATIONS.lastIndex)]

// 品牌绿（与效果图一致）
private val BRAND_GREEN = Color(0xFFB6E946)
private val BRAND_GREEN2 = Color(0xFF9DD63B)

// ——— 番茄钟专注 ———
@Composable
fun FocusScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var focusMin by remember { mutableStateOf(25) }
    var breakMin by remember { mutableStateOf(5) }
    var totalCycles by remember { mutableStateOf(4) }
    var phase by remember { mutableStateOf("专注") }
    var remaining by remember { mutableStateOf(focusMin * 60) }
    var running by remember { mutableStateOf(false) }
    var cyclesDone by remember { mutableStateOf(0) }
    val whiteNoiseSetting by Repo.settings.whiteNoise.collectAsStateWithLifecycle("无")
    var noise by remember { mutableStateOf(whiteNoiseSetting) }
    var showSetting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    // 计时循环：仅在 running 为 true 时运行；暂停即取消，恢复即重启（从当前 remaining 继续）
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        try {
            while (running && remaining > 0) {
                delay(1000)
                remaining -= 1
            }
            if (running && remaining <= 0) {
                val planned = if (phase == "专注") focusMin else breakMin
                val start = System.currentTimeMillis() - planned * 60_000L
                Repo.focus.insert(FocusSessionEntity(startTime = start, endTime = System.currentTimeMillis(), plannedMin = planned, type = phase, interrupted = false))
                NotificationUtil.notify(context, NotificationUtil.CH_FOCUS, if (phase == "专注") 2001 else 2002,
                    if (phase == "专注") "专注完成 🎉" else "休息结束",
                    if (phase == "专注") "完成一轮专注，去休息一下吧" else "休息结束，继续专注")
                if (phase == "专注") { cyclesDone += 1; phase = "休息"; remaining = breakMin * 60; message = "专注完成！点击开始休息" }
                else { phase = "专注"; remaining = focusMin * 60; message = "休息结束！点击继续专注" }
            }
        } finally { running = false }
    }

    // 运行期间启动前台服务，保证锁屏/后台依旧计时；停止（暂停/结束/离开页面）时一并停止服务。
    DisposableEffect(running) {
        if (running) {
            try { context.startForegroundService(Intent(context, FocusService::class.java)) } catch (_: Exception) { }
        }
        onDispose {
            try { context.stopService(Intent(context, FocusService::class.java)) } catch (_: Exception) { }
        }
    }
    DisposableEffect(Unit) { onDispose { WhiteNoisePlayer.stop() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("番茄钟专注", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s16))
        // 大圆环进度
        val total = if (phase == "专注") focusMin * 60 else breakMin * 60
        val progress = if (total > 0) 1f - (remaining.toFloat() / total) else 0f
        Box(Modifier.fillMaxWidth().padding(vertical = Dimen.s12), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, strokeWidth = 12.dp, modifier = Modifier.size(220.dp),
                    color = if (phase == "专注") MaterialTheme.colorScheme.primary else LocalExtraColors.current.success)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phase, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%02d:%02d".format(remaining / 60, remaining % 60), style = MaterialTheme.typography.displayMedium)
                    Text("已完成 $cyclesDone / $totalCycles 轮", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (message.isNotEmpty()) {
            Text(message, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Dimen.s16))
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimen.s16), horizontalArrangement = Arrangement.spacedBy(Dimen.s12)) {
            val startText = if (!running) "开始" else "暂停"
            PrimaryButton(startText, onClick = {
                if (running) running = false else { message = ""; running = true }
            }, modifier = Modifier.weight(1f), icon = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow)
            OutlinedButton(onClick = {
                running = false; phase = "专注"; remaining = focusMin * 60; cyclesDone = 0; message = ""
            }, modifier = Modifier.weight(1f)) { Text("重置") }
        }
        Spacer(Modifier.height(Dimen.s12))
        // 白噪音选择
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("白噪音背景音", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            FlowRow(Modifier.fillMaxWidth()) { listOf("无", "雨声", "森林", "海浪", "咖啡馆").forEach { n ->
                FilterChip(selected = noise == n, onClick = { noise = n; WhiteNoisePlayer.play(n); scope.launch { Repo.settings.setWhiteNoise(n) } }, label = { Text(n) }, modifier = Modifier.padding(end = 4.dp, bottom = 4.dp))
            } }
            Text("点击即可播放 / 切换背景白噪音（离线合成，无需联网）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("专注 ${focusMin}分 · 休息 ${breakMin}分 · ${totalCycles} 轮", modifier = Modifier.weight(1f))
                TextButton(onClick = { showSetting = true }) { Text("设置") }
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
    if (showSetting) {
        AlertDialog(onDismissRequest = { showSetting = false }, confirmButton = { TextButton(onClick = {
            if (!running) remaining = if (phase == "专注") focusMin * 60 else breakMin * 60
            showSetting = false
        }) { Text("确定") } }, title = { Text("番茄钟设置") }, text = {
            Column {
                Text("单次专注：${focusMin} 分", style = MaterialTheme.typography.bodyLarge)
                Slider(focusMin.toFloat(), { focusMin = it.toInt().coerceIn(1, 120) }, valueRange = 1f..120f, steps = 119)
                Text("休息：${breakMin} 分", style = MaterialTheme.typography.bodyLarge)
                Slider(breakMin.toFloat(), { breakMin = it.toInt().coerceIn(1, 60) }, valueRange = 1f..60f, steps = 59)
                Text("循环轮数：${totalCycles} 轮", style = MaterialTheme.typography.bodyLarge)
                Slider(totalCycles.toFloat(), { totalCycles = it.toInt().coerceIn(1, 20) }, valueRange = 1f..20f, steps = 19)
            }
        })
    }
}

// ——— 睡眠记录 ———
@Composable
fun SleepScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val recent by Repo.sleep.observeRecent().collectAsStateWithLifecycle(emptyList())
    var sleepTs by remember { mutableStateOf(System.currentTimeMillis() - 8 * 3600_000) } // 默认昨晚 23:00
    var wakeTs by remember { mutableStateOf(System.currentTimeMillis()) }                  // 今早 07:00
    val suggestion = remember(recent) { CalcUtil.sleepSuggestion(recent) }
    val lineData = recent.reversed().map { it.durationMin.toFloat() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("睡眠作息", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            val avgMin = if (recent.isEmpty()) 0 else recent.map { it.durationMin }.average().toInt()
            MetricLine(icon = Icons.Filled.Bedtime, label = "近一周平均睡眠",
                value = if (recent.isEmpty()) "暂无" else TimeUtil.formatDuration(avgMin),
                valueColor = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("记录昨晚睡眠", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Button(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = sleepTs }
                TimePickerDialog(context, { _, h, m -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); sleepTs = cal.timeInMillis }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }) { Text("入睡 ${TimeUtil.formatClock(sleepTs)}") }
            Spacer(Modifier.height(Dimen.s8))
            Button(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = wakeTs }
                TimePickerDialog(context, { _, h, m -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); wakeTs = cal.timeInMillis }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }) { Text("起床 ${TimeUtil.formatClock(wakeTs)}") }
            Spacer(Modifier.height(Dimen.s8))
            val dur = TimeUtil.sleepDurationMin(sleepTs, wakeTs)
            Text("睡眠时长：${TimeUtil.formatDuration(dur)}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("保存记录", onClick = {
                scope.launch {
                    Repo.sleep.upsertByDate(SleepEntity(date = TimeUtil.dayKey(sleepTs), sleepTime = sleepTs, wakeTime = wakeTs, durationMin = dur))
                    Toast.makeText(context, "已保存（按入睡日期覆盖，不会重复记录）", Toast.LENGTH_SHORT).show()
                }
            }, icon = Icons.Filled.Save)
        }
        Spacer(Modifier.height(Dimen.s12))
        if (recent.isNotEmpty()) {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Text("近一周睡眠记录", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                recent.reversed().forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Dimen.s6), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${TimeUtil.formatMonthDay(r.sleepTime)} 入睡 ${TimeUtil.formatClock(r.sleepTime)}", style = MaterialTheme.typography.bodyMedium)
                            Text("${TimeUtil.formatMonthDay(r.wakeTime)} 起床 ${TimeUtil.formatClock(r.wakeTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(TimeUtil.formatDuration(r.durationMin), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s12))
        }
        if (recent.isNotEmpty()) {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Text("近一周睡眠时长（分钟）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                LineChart(lineData, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Dimen.s12))
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Text("睡眠改善建议", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                Text(suggestion, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}

// ——— 收支记账 ———
@Composable
fun AccountScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val all by Repo.account.observeAll().collectAsStateWithLifecycle(emptyList())
    val budget by Repo.settings.monthlyBudget.collectAsStateWithLifecycle(2000.0)
    var showAdd by remember { mutableStateOf(false) }
    var chartType by remember { mutableStateOf("饼图") } // 饼图/柱状

    val (ms, me) = TimeUtil.monthRange()
    val monthItems = all.filter { it.date in ms..me }
    val income = monthItems.filter { it.type == 1 }.sumOf { it.amount }
    val expense = monthItems.filter { it.type == 0 }.sumOf { it.amount }
    val overBudget = expense > budget

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("收支记账", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        Row(Modifier.padding(horizontal = Dimen.s16)) {
            AppCard(Modifier.weight(1f).padding(end = Dimen.s6)) {
                MetricLine(icon = Icons.Filled.Paid, label = "本月收入", value = "¥%.2f".format(income), valueColor = LocalExtraColors.current.success)
            }
            AppCard(Modifier.weight(1f).padding(start = Dimen.s6)) {
                MetricLine(icon = Icons.Filled.CreditCard, label = "本月支出", value = "¥%.2f".format(expense), valueColor = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("月度预算 ¥%.0f ${if (overBudget) "· 已超支！" else ""}".format(budget),
                color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (expense / budget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("收支结构", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Row { listOf("饼图", "柱状").forEach { t -> FilterChip(selected = chartType == t, onClick = { chartType = t }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp)) } }
            }
            Spacer(Modifier.height(Dimen.s8))
            val cats = monthItems.filter { it.type == 0 }.groupBy { it.category }.mapValues { it.value.sumOf { a -> a.amount } }
            val pieData = cats.map { it.key to it.value }
            val palette = ChartPalette
            if (pieData.isEmpty()) Text("本月暂无支出分类数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (chartType == "饼图") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PieChart(pieData, palette, Modifier.size(150.dp))
                    Spacer(Modifier.width(Dimen.s12))
                    Column { pieData.forEachIndexed { i, (c, v) -> Text("${c}：¥%.2f".format(v), color = palette.getOrElse(i) { Color.Gray }) } }
                }
            } else {
                BarChart(pieData, MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Dimen.s16).heightIn(max = 360.dp)) {
            items(monthItems.sortedByDescending { it.date }, key = { it.id }) { a ->
                var showDel by remember { mutableStateOf(false) }
                AppCard(Modifier.padding(bottom = Dimen.s8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.category, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text((if (a.type == 1) "+" else "-") + "¥%.2f".format(a.amount), color = if (a.type == 1) LocalExtraColors.current.success else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
                    }
                    if (a.note.isNotEmpty()) Text(a.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showDel) ConfirmDeleteDialog(message = "确定删除这笔「${a.category}」记账记录吗？", onDismiss = { showDel = false }) { scope.launch { Repo.account.delete(a) } }
            }
        }
        Spacer(Modifier.height(Dimen.s16))
        PrimaryButton("＋ 记一笔", onClick = { showAdd = true }, modifier = Modifier.padding(horizontal = Dimen.s16))
        Spacer(Modifier.height(Dimen.s24))
    }
    if (showAdd) AccountAddDialog(onDismiss = { showAdd = false }, onSave = { type, cat, amount, note, date ->
        scope.launch {
            val before = Repo.account.sumByType(0, ms, me)
            Repo.account.insert(AccountEntity(type = type, category = cat, amount = amount, note = note, date = TimeUtil.dayKey(date)))
            val after = Repo.account.sumByType(0, ms, me)
            if (before <= budget && after > budget) {
                NotificationUtil.notify(context, NotificationUtil.CH_BUDGET, 3001, "预算提醒", "本月支出已超预算 ¥%.0f".format(budget))
            }
            showAdd = false
        }
    })
}

@Composable
private fun AccountAddDialog(onDismiss: () -> Unit, onSave: (Int, String, Double, String, Long) -> Unit) {
    var type by remember { mutableStateOf(0) } // 0 支出 1 收入
    var cat by remember { mutableStateOf(if (type == 0) "餐饮" else "工资") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var newCat by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = {
        val amt = amount.toDoubleOrNull() ?: return@TextButton
        if (cat.isBlank()) return@TextButton
        onSave(type, cat, amt, note, date)
    }) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text("记一笔") },
        text = {
            Column {
                Row { listOf("支出" to 0, "收入" to 1).forEach { (t, v) -> FilterChip(selected = type == v, onClick = { type = v; cat = if (v == 0) "餐饮" else "工资" }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp)) } }
                Spacer(Modifier.height(Dimen.s8))
                Text("分类")
                val customCats by Repo.settings.customCategories.collectAsStateWithLifecycle(emptyList())
                val allCats = (if (type == 0) listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗") else listOf("工资", "理财", "红包", "其他")) + customCats
                FlowRow(Modifier.fillMaxWidth()) {
                    allCats.forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }, modifier = Modifier.padding(end = 4.dp, bottom = 4.dp))
                    }
                }
                Spacer(Modifier.height(Dimen.s4))
                if (customCats.isNotEmpty()) {
                    Text("自定义分类（点 ✕ 可删除）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth()) {
                        customCats.forEach { c ->
                            AssistChip(
                                onClick = { cat = c },
                                label = { Text(c) },
                                trailingIcon = { Icon(Icons.Filled.Close, null, modifier = Modifier.clickable { scope.launch { Repo.settings.removeCustomCategory(c) } }.size(16.dp)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(Dimen.s4))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newCat, { newCat = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("自定义分类") })
                    IconButton(onClick = {
                        val n = newCat.trim()
                        if (n.isNotBlank()) {
                            if (n in allCats) Toast.makeText(context, "分类「$n」已存在", Toast.LENGTH_SHORT).show()
                            else { scope.launch { Repo.settings.addCustomCategory(n) }; newCat = "" }
                        }
                    }) { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(Dimen.s8))
                OutlinedTextField(amount, { amount = it }, label = { Text("金额") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val cal = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, d -> cal.set(y, m, d); date = cal.timeInMillis }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }) { Text("日期：${TimeUtil.formatDate(date)}") }
            }
        })
}

// ——— 饮食记录 + 菜谱 ———
@Composable
fun DietScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = TimeUtil.dayKey()
    val meals by Repo.diet.observeByDate(today).collectAsStateWithLifecycle(emptyList())
    val recipes by Repo.recipe.observeAll().collectAsStateWithLifecycle(emptyList())
    var showMeal by remember { mutableStateOf(false) }
    var showRecipe by remember { mutableStateOf(false) }
    var editMeal by remember { mutableStateOf<DietLogEntity?>(null) }

    val totalCal = meals.sumOf { it.calories }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("饮食与菜谱", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            MetricLine(icon = Icons.Filled.Restaurant, label = "今日热量摄入", value = "$totalCal kcal", valueColor = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Dimen.s8))
            Row { listOf("早餐" to 0, "午餐" to 1, "晚餐" to 2).forEach { (t, v) ->
                val c = meals.filter { it.mealType == v }.sumOf { it.calories }
                FilterChip(selected = false, onClick = { showMeal = true }, label = { Text("$t $c kcal") }, modifier = Modifier.padding(end = 4.dp))
            } }
        }
        Spacer(Modifier.height(Dimen.s12))
        Row(Modifier.padding(horizontal = Dimen.s16)) { PrimaryButton("打卡三餐", onClick = { showMeal = true }, modifier = Modifier.weight(1f)); Spacer(Modifier.width(Dimen.s8)); PrimaryButton("新建菜谱", onClick = { showRecipe = true }, modifier = Modifier.weight(1f)) }
        Spacer(Modifier.height(Dimen.s12))
        SectionTitle("  今日饮食")
        Spacer(Modifier.height(Dimen.s8))
        meals.forEach { m ->
            var showDel by remember { mutableStateOf(false) }
            AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8), onClick = { editMeal = m }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.foodName, fontWeight = FontWeight.SemiBold)
                        Text("${m.calories} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
                }
            }
            if (showDel) ConfirmDeleteDialog(message = "确定删除「${m.foodName}」这条饮食记录吗？", onDismiss = { showDel = false }) { scope.launch { Repo.diet.delete(m) } }
        }
        if (meals.isEmpty()) EmptyState("今天还没吃饭记录哦")
        Spacer(Modifier.height(Dimen.s12))
        SectionTitle("  我的菜谱库")
        Spacer(Modifier.height(Dimen.s8))
        recipes.forEach { r ->
            var showDel by remember { mutableStateOf(false) }
            AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { Repo.recipe.update(r.copy(favorite = !r.favorite)) } }) { Icon(if (r.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = if (r.favorite) LocalExtraColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
                }
                Text("食材：${r.ingredients.take(40)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showDel) ConfirmDeleteDialog(message = "确定删除菜谱「${r.name}」吗？", onDismiss = { showDel = false }) { scope.launch { Repo.recipe.delete(r) } }
        }
        if (recipes.isEmpty()) EmptyState("菜谱库还是空的，添加一个家常菜吧")
        Spacer(Modifier.height(Dimen.s24))
    }
    val mealToEdit = editMeal
    if (showMeal || mealToEdit != null) {
        MealAddDialog(initial = mealToEdit, onDismiss = { showMeal = false; editMeal = null }, onSave = { mt, name, cal ->
            scope.launch {
                if (mealToEdit != null) Repo.diet.update(mealToEdit.copy(mealType = mt, foodName = name, calories = cal))
                else Repo.diet.insert(DietLogEntity(date = today, mealType = mt, foodName = name, calories = cal))
                showMeal = false; editMeal = null
            }
        })
    }
    if (showRecipe) RecipeAddDialog(onDismiss = { showRecipe = false }, onSave = { name, ing, steps, fav -> scope.launch { Repo.recipe.insert(RecipeEntity(name = name, ingredients = ing, steps = steps, favorite = fav)); showRecipe = false } })
}

@Composable
private fun MealAddDialog(initial: DietLogEntity? = null, onDismiss: () -> Unit, onSave: (Int, String, Int) -> Unit) {
    var mt by remember { mutableStateOf(initial?.mealType ?: 0) }
    var name by remember { mutableStateOf(initial?.foodName ?: "") }
    var cal by remember { mutableStateOf((initial?.calories ?: "").toString()) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { val c = cal.toIntOrNull() ?: 0; if (name.isBlank()) return@TextButton; onSave(mt, name, c) }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }, title = { Text("打卡饮食") }, text = {
            Column {
                Text("餐次"); Row { listOf("早餐" to 0, "午餐" to 1, "晚餐" to 2).forEach { (t, v) -> FilterChip(selected = mt == v, onClick = { mt = v }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp)) } }
                Spacer(Modifier.height(Dimen.s8))
                OutlinedTextField(name, { name = it }, label = { Text("食物名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cal, { cal = it }, label = { Text("热量(kcal)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        })
}

@Composable
private fun RecipeAddDialog(onDismiss: () -> Unit, onSave: (String, String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ing by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var fav by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { if (name.isBlank()) return@TextButton; onSave(name, ing, steps, fav) }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }, title = { Text("新建菜谱") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("菜名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ing, { ing = it }, label = { Text("食材（每行一个）") }, modifier = Modifier.fillMaxWidth().height(80.dp), maxLines = 4)
                OutlinedTextField(steps, { steps = it }, label = { Text("步骤（每行一步）") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fav, { fav = it }); Text("收藏到常用") }
            }
        })
}

// ——— 健身训练（新）：枢纽 + 手动添加 + 生成计划 + 开始训练 ———
@Composable
fun FitnessScreen(nav: NavController) {
    var view by remember { mutableStateOf<String?>(null) }
    when (view) {
        "add" -> ManualAddView(onBack = { view = null })
        "generate" -> GeneratePlanView(onBack = { view = null })
        "train" -> TrainingSessionScreen(nav, onBack = { view = null })
        else -> FitnessMainView(
            onAdd = { view = "add" },
            onGenerate = { view = "generate" },
            onTrain = { view = "train" }
        )
    }
}

@Composable
private fun FitnessMainView(onAdd: () -> Unit, onGenerate: () -> Unit, onTrain: () -> Unit) {
    val plansJson by Repo.settings.trainingPlansJson.collectAsStateWithLifecycle("[]")
    val plans = parsePlans(plansJson)
    var curDay by remember { mutableStateOf(todayWeekday()) }
    var showSheet by remember { mutableStateOf(false) }

    val dayActions = actionsForDay(plans, curDay)
    var doneKeys by remember { mutableStateOf(setOf<String>()) }
    val doneCount = dayActions.count { "${curDay}:${it.name}" in doneKeys }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 顶部问候 + 添加按钮
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimen.s16, vertical = Dimen.s8), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(TimeUtil.formatMonthDay(System.currentTimeMillis()) + " · " + dateHint(plans, curDay),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(46.dp).background(BRAND_GREEN, CircleShape).clickable { showSheet = true },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null, Modifier.size(26.dp), tint = Color(0xFF111418))
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        HeroCard(plans, curDay, doneCount, dayActions.size, onTrain)
        Spacer(Modifier.height(Dimen.s12))
        WeekPlanner(plans, curDay) { curDay = it; doneKeys = setOf() }
        Spacer(Modifier.height(Dimen.s12))
        SectionTitle("  今日动作")
        Spacer(Modifier.height(Dimen.s8))
        if (dayActions.isEmpty()) {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(motivationText(curDay), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Dimen.s8))
                    TextButton(onClick = onAdd) { Text("＋ 添加训练计划") }
                }
            }
        } else {
            dayActions.forEach { a ->
                val key = "${curDay}:${a.name}"
                AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = key in doneKeys, onCheckedChange = { doneKeys = if (it) doneKeys + key else doneKeys - key })
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.SemiBold,
                                color = if (key in doneKeys) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Text("${a.muscle} · ${a.sets}组 × ${a.reps}次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        StatRow(plans)
        Spacer(Modifier.height(Dimen.s24))
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Dimen.s16, vertical = Dimen.s16)) {
                Text("添加训练计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Dimen.s12))
                AppCard(Modifier.padding(bottom = Dimen.s12).clickable { showSheet = false; onAdd() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Dimen.s12))
                        Text("📝 手动添加计划", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AppCard(Modifier.clickable { showSheet = false; onGenerate() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Dimen.s12))
                        Text("✨ 生成计划（三分化）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Dimen.s8))
            }
        }
    }
}

@Composable
private fun HeroCard(plans: List<TrainingPlan>, day: String, done: Int, total: Int, onTrain: () -> Unit) {
    val has = total > 0
    Card(Modifier.padding(horizontal = Dimen.s16), shape = RoundedCornerShape(Dimen.cardRadius),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(BRAND_GREEN, BRAND_GREEN2))).padding(Dimen.s16)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DAY $day", color = Color(0xFF111418), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Dimen.s4))
                        Text(muscleLabelFor(plansForDay(plans, day)), color = Color(0xFF1B1F23), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Dimen.s4))
                        Text("${total} 个动作${if (has) " · 约 ${total * 8} 分钟" else ""}", color = Color(0xFF1B1F23), style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f },
                            strokeWidth = 8.dp, modifier = Modifier.size(64.dp),
                            color = Color(0xFF111418), trackColor = Color(0x33111418))
                        Text("$done/$total", color = Color(0xFF111418), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                if (has) {
                    Spacer(Modifier.height(Dimen.s12))
                    Button(onClick = onTrain, modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(Dimen.btnRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp), tint = Color(0xFF111418))
                        Spacer(Modifier.width(6.dp))
                        Text("开始训练", color = Color(0xFF111418), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekPlanner(plans: List<TrainingPlan>, curDay: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dimen.s16),
        horizontalArrangement = Arrangement.spacedBy(Dimen.s8)) {
        WEEK_ORDER.forEach { k ->
            val has = plansForDay(plans, k).isNotEmpty()
            val selected = k == curDay
            Column(
                Modifier.width(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable { onPick(k) }.padding(vertical = Dimen.s12),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(k, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Dimen.s4))
                Text(if (has) muscleLabelFor(plansForDay(plans, k)) else "无",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatRow(plans: List<TrainingPlan>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimen.s16), horizontalArrangement = Arrangement.spacedBy(Dimen.s12)) {
        StatCard("本周训练", "${weekTrainingDays(plans)} 天", Icons.Filled.DateRange, Modifier.weight(1f))
        StatCard("预计消耗", "${weeklyKcal(plans)} kcal", Icons.Filled.Whatshot, Modifier.weight(1f))
        StatCard("连续打卡", "${streakDays(plans)} 天", Icons.Filled.EmojiEvents, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Dimen.s6))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ——— 手动添加计划 ———
private class ActionRowState(name: String = "", muscle: String = "", sets: String = "3", reps: String = "12") {
    val name = mutableStateOf(name)
    val muscle = mutableStateOf(muscle)
    val sets = mutableStateOf(sets)
    val reps = mutableStateOf(reps)
}

@Composable
private fun ManualAddView(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val plansJson by Repo.settings.trainingPlansJson.collectAsStateWithLifecycle("[]")
    val plans = parsePlans(plansJson)
    var planName by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<String>()) }
    val rows = remember { mutableStateListOf(ActionRowState(), ActionRowState()) }
    var note by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("添加训练计划", showBack = true, onBack = onBack)
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            OutlinedTextField(planName, { planName = it }, label = { Text("计划名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(Dimen.s8))
            Text("训练日（可多选）")
            FlowRow(Modifier.fillMaxWidth()) {
                WEEK_ORDER.forEach { d ->
                    FilterChip(selected = d in selectedDays, onClick = { selectedDays = if (d in selectedDays) selectedDays - d else selectedDays + d },
                        label = { Text(d) }, modifier = Modifier.padding(end = 4.dp, bottom = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("动作列表", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            rows.forEachIndexed { i, r ->
                ActionRowEditor(r, onDelete = { rows.removeAt(i) })
                Spacer(Modifier.height(Dimen.s8))
            }
            TextButton(onClick = { rows.add(ActionRowState()) }) { Text("＋ 添加动作") }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Dimen.s12))
        PrimaryButton("保存计划", onClick = {
            if (planName.isBlank()) { Toast.makeText(context, "请填写计划名称", Toast.LENGTH_SHORT).show(); return@PrimaryButton }
            if (selectedDays.isEmpty()) { Toast.makeText(context, "请至少选择一个训练日", Toast.LENGTH_SHORT).show(); return@PrimaryButton }
            val acts = rows.filter { it.name.value.isNotBlank() }.map {
                TrainingAction(name = it.name.value.trim(), muscle = it.muscle.value.ifBlank { "其他" },
                    sets = it.sets.value.toIntOrNull() ?: 3, reps = it.reps.value.toIntOrNull() ?: 12)
            }
            scope.launch {
                savePlans(plans + TrainingPlan(planName.trim(), selectedDays.toList(), note.trim(), acts))
                Toast.makeText(context, "已保存计划", Toast.LENGTH_SHORT).show()
                onBack()
            }
        }, modifier = Modifier.padding(horizontal = Dimen.s16), icon = Icons.Filled.Save)
        Spacer(Modifier.height(Dimen.s16))
        SectionTitle("  我的计划")
        Spacer(Modifier.height(Dimen.s8))
        if (plans.isEmpty()) EmptyState("还没有任何计划")
        else plans.forEachIndexed { i, p ->
            var showDel by remember { mutableStateOf(false) }
            AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text("训练日：${p.days.joinToString(" · ")} · ${p.actions.size} 个动作",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            if (showDel) ConfirmDeleteDialog(
                message = "确定删除计划「${p.name}」吗？此操作仅删除这一个计划。",
                onDismiss = { showDel = false }
            ) {
                scope.launch {
                    savePlans(plans.filterIndexed { index, _ -> index != i })
                    Toast.makeText(context, "已删除计划", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}

@Composable
private fun ActionRowEditor(state: ActionRowState, onDelete: () -> Unit) {
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(state.name.value, { state.name.value = it }, label = { Text("动作名称") }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(Dimen.s6))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(state.muscle.value, { state.muscle.value = it }, label = { Text("部位") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(Dimen.s8))
            OutlinedTextField(state.sets.value, { state.sets.value = it }, label = { Text("组") }, modifier = Modifier.width(64.dp), singleLine = true)
            Spacer(Modifier.width(Dimen.s8))
            OutlinedTextField(state.reps.value, { state.reps.value = it }, label = { Text("次") }, modifier = Modifier.width(64.dp), singleLine = true)
        }
    }
}

// ——— 生成计划（三分化）———
@Composable
private fun GeneratePlanView(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val plansJson by Repo.settings.trainingPlansJson.collectAsStateWithLifecycle("[]")
    val plans = parsePlans(plansJson)
    val equipments = listOf("哑铃", "杠铃", "弹力带", "徒手", "器械", "瑜伽垫")
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("生成训练计划", showBack = true, onBack = onBack)
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("选择你可用的器材", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Text("将按推 / 拉 / 腿三分化生成计划，只含你能做的动作。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimen.s8))
            FlowRow(Modifier.fillMaxWidth()) {
                equipments.forEach { e ->
                    FilterChip(selected = e in selected, onClick = { selected = if (e in selected) selected - e else selected + e },
                        label = { Text(e) }, modifier = Modifier.padding(end = 4.dp, bottom = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        PrimaryButton("生成计划", onClick = {
            scope.launch {
                savePlans(plans + buildThreeSplit(selected.ifEmpty { setOf("徒手") }))
                Toast.makeText(context, "已生成三分化计划", Toast.LENGTH_SHORT).show()
                onBack()
            }
        }, modifier = Modifier.padding(horizontal = Dimen.s16), icon = Icons.Filled.AutoAwesome)
        Spacer(Modifier.height(Dimen.s12))
        TextButton(onClick = { showClear = true }, modifier = Modifier.padding(horizontal = Dimen.s16)) {
            Text("🗑 清空所有计划", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Dimen.s24))
    }
    if (showClear) ConfirmDeleteDialog(
        message = "确定要清空全部训练计划吗？此操作不可撤销。",
        title = "清空确认",
        onDismiss = { showClear = false }
    ) {
        scope.launch {
            savePlans(emptyList())
            Toast.makeText(context, "已清空所有计划", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
}

// ——— 开始训练（跟练会话）———
@Composable
fun TrainingSessionScreen(nav: NavController, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val plansJson by Repo.settings.trainingPlansJson.collectAsStateWithLifecycle("[]")
    val plans = parsePlans(plansJson)
    val day = todayWeekday()
    val acts = actionsForDay(plans, day)
    val back: () -> Unit = onBack ?: { nav.popBackStack() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("开始训练 · 周$day", showBack = true, onBack = back)
        Spacer(Modifier.height(Dimen.s12))
        if (acts.isEmpty()) {
            EmptyState("今天没有安排训练，去添加或生成计划吧")
        } else {
            acts.forEachIndexed { i, a ->
                AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s12)) {
                    Text("${i + 1}. ${a.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${a.muscle} · ${if (a.reps > 0) "${a.sets}组 × ${a.reps}次" else "${a.sets}组"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimen.s8))
                    Text(actionGuide(a), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Dimen.s8))
                    TrainingVideoPlayer(a.videoUrl)
                }
            }
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("完成训练 🎉", onClick = {
                Toast.makeText(context, "训练完成，棒！", Toast.LENGTH_SHORT).show()
                back()
            }, modifier = Modifier.padding(horizontal = Dimen.s16), icon = Icons.Filled.Check)
            Spacer(Modifier.height(Dimen.s24))
        }
    }
}

private fun actionGuide(a: TrainingAction): String = when (a.muscle) {
    "胸" -> "沉肩夹紧肩胛，慢下快上，感受胸肌收缩。"
    "背" -> "挺胸收腹，用背部发力带动手臂，避免耸肩。"
    "腿" -> "膝盖对准脚尖，臀部后坐，核心收紧。"
    "肩" -> "小重量控制，避免耸肩代偿。"
    "三头" -> "大臂贴紧身体，仅小臂伸展。"
    "二头" -> "肘部固定，靠二头发力弯举。"
    "核心" -> "收紧腹部，保持呼吸均匀。"
    else -> "控制节奏，注意呼吸：发力呼气、还原吸气。"
}

@Composable
private fun TrainingVideoPlayer(url: String) {
    if (url.isBlank()) {
        Box(Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PlayCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Dimen.s6))
                Text("暂无跟练视频，按文字指导训练", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    AndroidView(factory = { ctx ->
        android.widget.VideoView(ctx).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            try {
                setVideoURI(android.net.Uri.parse(url))
                setMediaController(android.widget.MediaController(ctx))
                start()
            } catch (_: Exception) { }
        }
    }, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)))
}
