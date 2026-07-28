package com.lifebench.app.ui.screens.fit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import com.lifebench.app.service.FocusService
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
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.util.AlarmScheduler
import com.lifebench.app.util.CalcUtil
import com.lifebench.app.util.NotificationUtil
import com.lifebench.app.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * ===== 健身饮食专区：枢纽 + 番茄钟 + 睡眠 + 记账 + 饮食菜谱 + 健身计划 =====
 */

// ——— 健身饮食枢纽 ———
@Composable
fun FitHubScreen(nav: NavController) {
    val entries = listOf(
        ToolEntry("番茄钟专注", Icons.Filled.Alarm, Routes.FOCUS),
        ToolEntry("睡眠记录", Icons.Filled.Bedtime, Routes.SLEEP),
        ToolEntry("收支记账", Icons.Filled.AccountBalanceWallet, Routes.ACCOUNT),
        ToolEntry("饮食菜谱", Icons.Filled.Restaurant, Routes.DIET),
        ToolEntry("健身计划", Icons.Filled.FitnessCenter, Routes.FITNESS),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("健身饮食")
        Spacer(Modifier.height(Dimen.s12))
        entries.forEach { e ->
            AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s12), onClick = { nav.navigate(e.route) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(e.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Dimen.s12))
                    Text(e.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}

private data class ToolEntry(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String)

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
    var noise by remember { mutableStateOf("无") }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("番茄钟专注", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s16))
        // 大圆环进度
        val total = if (phase == "专注") focusMin * 60 else breakMin * 60
        val progress = if (total > 0) 1f - (remaining.toFloat() / total) else 0f
        Box(Modifier.fillMaxWidth().padding(vertical = Dimen.s12), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = progress.coerceIn(0f, 1f), strokeWidth = 12.dp, modifier = Modifier.size(220.dp),
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
            Row { listOf("无", "雨声", "咖啡馆", "森林").forEach { n ->
                FilterChip(selected = noise == n, onClick = { noise = n }, label = { Text(n) }, modifier = Modifier.padding(end = 4.dp))
            } }
            Text("（放入 res/raw 音频后可在源码 FocusScreen 启用播放，详见使用说明书）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var alarmOn by remember { mutableStateOf(false) }
    var alarmTime by remember { mutableStateOf("07:00") }

    val suggestion = remember(recent) { CalcUtil.sleepSuggestion(recent) }
    val lineData = recent.reversed().map { it.durationMin.toFloat() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("睡眠作息", showBack = true, onBack = { nav.popBackStack() })
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
                    Repo.sleep.insert(SleepEntity(date = TimeUtil.dayKey(sleepTs), sleepTime = sleepTs, wakeTime = wakeTs, durationMin = dur))
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }
            }, icon = Icons.Filled.Save)
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("起床闹钟 $alarmTime", modifier = Modifier.weight(1f))
                Switch(alarmOn, onCheckedChange = {
                    alarmOn = it
                    val (h, m) = alarmTime.split(":").map { it.toInt() }
                    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH,1) }
                    if (it) AlarmScheduler.schedule(context, AlarmScheduler.Alarm(900001, cal.timeInMillis, "起床闹钟", "该起床啦，新的一天加油！"))
                    else AlarmScheduler.cancel(context, 900001)
                })
            }
            Button(onClick = {
                val (h, m) = alarmTime.split(":").map { it.toInt() }
                TimePickerDialog(context, { _, hh, mm -> alarmTime = "%02d:%02d".format(hh, mm) }, h, m, true).show()
            }) { Text("设置时间") }
        }
        Spacer(Modifier.height(Dimen.s12))
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
                Text("本月收入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥%.2f".format(income), style = MaterialTheme.typography.titleLarge, color = LocalExtraColors.current.success)
            }
            AppCard(Modifier.weight(1f).padding(start = Dimen.s6)) {
                Text("本月支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥%.2f".format(expense), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("月度预算 ¥%.0f ${if (overBudget) "· 已超支！" else ""}".format(budget),
                color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = (expense / budget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
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
            val palette = listOf(Color(0xFF4F8A8B), Color(0xFFE0A899), Color(0xFF6BBF73), Color(0xFF8A7FB0), Color(0xFFE2A53B), Color(0xFFD8695F), Color(0xFF7FB5B5))
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
                AppCard(Modifier.padding(bottom = Dimen.s8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.category, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text((if (a.type == 1) "+" else "-") + "¥%.2f".format(a.amount), color = if (a.type == 1) LocalExtraColors.current.success else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { scope.launch { Repo.account.delete(a) } }) { Icon(Icons.Filled.Delete, null) }
                    }
                    if (a.note.isNotEmpty()) Text(a.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                val expenseCats = listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗") + customCats
                val incomeCats = listOf("工资", "理财", "红包", "其他") + customCats
                FlowRow(Modifier.fillMaxWidth()) {
                    (if (type == 0) expenseCats else incomeCats).forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }, modifier = Modifier.padding(end = 4.dp, bottom = 4.dp))
                    }
                }
                Spacer(Modifier.height(Dimen.s4))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newCat, { newCat = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("自定义分类") })
                    IconButton(onClick = {
                        val n = newCat.trim()
                        if (n.isNotBlank()) { scope.launch { Repo.settings.addCustomCategory(n) }; newCat = "" }
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

    val totalCal = meals.sumOf { it.calories }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("饮食与菜谱", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("今日热量摄入", style = MaterialTheme.typography.titleMedium)
            Text("$totalCal kcal", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
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
        meals.forEach { m -> AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(m.foodName, modifier = Modifier.weight(1f)); Text("${m.calories} kcal") } } }
        if (meals.isEmpty()) EmptyState("今天还没吃饭记录哦")
        Spacer(Modifier.height(Dimen.s12))
        SectionTitle("  我的菜谱库")
        Spacer(Modifier.height(Dimen.s8))
        recipes.forEach { r ->
            AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { Repo.recipe.update(r.copy(favorite = !r.favorite)) } }) { Icon(if (r.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = if (r.favorite) LocalExtraColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { scope.launch { Repo.recipe.delete(r) } }) { Icon(Icons.Filled.Delete, null) }
                }
                Text("食材：${r.ingredients.take(40)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (recipes.isEmpty()) EmptyState("菜谱库还是空的，添加一个家常菜吧")
        Spacer(Modifier.height(Dimen.s24))
    }
    if (showMeal) MealAddDialog(onDismiss = { showMeal = false }, onSave = { mt, name, cal -> scope.launch { Repo.diet.insert(DietLogEntity(date = today, mealType = mt, foodName = name, calories = cal)); showMeal = false } })
    if (showRecipe) RecipeAddDialog(onDismiss = { showRecipe = false }, onSave = { name, ing, steps, fav -> scope.launch { Repo.recipe.insert(RecipeEntity(name = name, ingredients = ing, steps = steps, favorite = fav)); showRecipe = false } })
}

@Composable
private fun MealAddDialog(onDismiss: () -> Unit, onSave: (Int, String, Int) -> Unit) {
    var mt by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var cal by remember { mutableStateOf("") }
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

// ——— 个性化健身计划 ———
@Composable
fun FitnessScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val plan by Repo.fitnessPlan.observeAll().collectAsStateWithLifecycle(emptyList())
    val profile = remember { mutableStateOf<FitnessProfileEntity?>(null) }
    var showProfile by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { profile.value = Repo.fitnessProfile.get() }

    val todayStats = plan.filter { it.dayIndex == Calendar.getInstance().get(Calendar.DAY_OF_WEEK) % 7 }.let { p -> p.sumOf { it.calories } to p.sumOf { it.durationMin } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("健身计划", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("今日训练统计", style = MaterialTheme.typography.titleMedium)
            Text("时长 ${todayStats.second} 分 · 消耗 ${todayStats.first} kcal", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton(if (profile.value == null) "填写资料生成计划" else "重新生成 7 天计划", onClick = { showProfile = true }, icon = Icons.Filled.Refresh)
        }
        Spacer(Modifier.height(Dimen.s12))
        plan.groupBy { it.dayIndex }.toSortedMap().forEach { (day, items) ->
            AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s12)) {
                Text("第 ${day + 1} 天${if (day == 6) "（恢复日）" else ""}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                items.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { Repo.fitnessPlan.update(a.copy(done = !a.done)) } }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = a.done, onCheckedChange = { scope.launch { Repo.fitnessPlan.update(a.copy(done = it)) } })
                        Text(a.actionName, modifier = Modifier.weight(1f))
                        Text(if (a.reps > 0) "${a.sets}组×${a.reps}次" else "${a.durationMin}分", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (plan.isEmpty()) EmptyState("生成计划后这里会显示 7 天训练安排")
        Spacer(Modifier.height(Dimen.s24))
    }
    if (showProfile) ProfileDialog(onDismiss = { showProfile = false }, initial = profile.value, onSave = { p ->
        scope.launch {
            Repo.fitnessPlan.clear()
            Repo.fitnessProfile.upsert(p)
            CalcUtil.generateFitnessPlan(p).forEach { Repo.fitnessPlan.insert(it) }
            profile.value = p
            showProfile = false
            Toast.makeText(context, "已生成 7 天计划", Toast.LENGTH_SHORT).show()
        }
    })
}

@Composable
private fun ProfileDialog(onDismiss: () -> Unit, initial: FitnessProfileEntity?, onSave: (FitnessProfileEntity) -> Unit) {
    var height by remember { mutableStateOf(initial?.height?.toString() ?: "170") }
    var weight by remember { mutableStateOf(initial?.weight?.toString() ?: "60") }
    var level by remember { mutableStateOf(initial?.level ?: "初级") }
    var goal by remember { mutableStateOf(initial?.goal ?: "减脂") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = {
        onSave(FitnessProfileEntity(height = height.toIntOrNull() ?: 170, weight = weight.toIntOrNull() ?: 60, level = level, goal = goal))
    }) { Text("生成") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text("健身资料") }, text = {
            Column {
                OutlinedTextField(height, { height = it }, label = { Text("身高(cm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(weight, { weight = it }, label = { Text("体重(kg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("运动基础"); Row { listOf("初级", "中级", "高级").forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l) }, modifier = Modifier.padding(end = 4.dp)) } }
                Text("目标"); Row { listOf("减脂", "增肌", "塑形").forEach { g -> FilterChip(selected = goal == g, onClick = { goal = g }, label = { Text(g) }, modifier = Modifier.padding(end = 4.dp)) } }
            }
        })
}
