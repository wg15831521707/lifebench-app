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

// ——— 健身计划：记录动作 + 运动规划 ———
@Composable
fun FitnessScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val plan by Repo.fitnessPlan.observeAll().collectAsStateWithLifecycle(emptyList())
    val today = TimeUtil.dayKey()
    val records = plan.filter { it.date == today }
    val templates = plan.filter { it.date == 0L }
    var showAddRecord by remember { mutableStateOf(false) }
    var showAddPlan by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<FitnessPlanEntity?>(null) }

    val todayCal = records.sumOf { it.calories }
    val todayDur = records.sumOf { it.durationMin }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("健身计划", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            MetricLine(icon = Icons.Filled.FitnessCenter, label = "今日消耗", value = "$todayCal kcal", valueColor = LocalExtraColors.current.success)
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("今日动作统计", style = MaterialTheme.typography.titleMedium)
            Text("动作 ${records.size} 个 · 时长 ${todayDur} 分 · 消耗 ${todayCal} kcal", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("＋ 记录今日动作", onClick = { showAddRecord = true }, icon = Icons.Filled.Add)
        }
        Spacer(Modifier.height(Dimen.s12))
        SectionTitle("  今日动作")
        Spacer(Modifier.height(Dimen.s8))
        records.sortedByDescending { it.id }.forEach { a ->
            FitnessItem(a,
                onEdit = { editItem = a },
                onDelete = { scope.launch { Repo.fitnessPlan.delete(a) } },
                onToggle = { scope.launch { Repo.fitnessPlan.update(a.copy(done = it)) } })
        }
        if (records.isEmpty()) EmptyState("今天还没记录动作，点击上方按钮打卡")
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("健身计划（模板）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAddPlan = true }) { Text("＋ 添加") }
            }
        }
        Spacer(Modifier.height(Dimen.s8))
        templates.forEach { a ->
            FitnessItem(a,
                onEdit = { editItem = a },
                onDelete = { scope.launch { Repo.fitnessPlan.delete(a) } },
                onToggle = { scope.launch { Repo.fitnessPlan.update(a.copy(done = it)) } })
        }
        if (templates.isEmpty()) EmptyState("还没有健身计划，添加你想练的动作吧")
        Spacer(Modifier.height(Dimen.s24))
    }

    val edit = editItem
    if (showAddRecord) AddFitnessDialog(onDismiss = { showAddRecord = false }, onSave = { name, sets, reps, dur, cal ->
        scope.launch { Repo.fitnessPlan.insert(FitnessPlanEntity(dayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK), actionName = name, sets = sets, reps = reps, durationMin = dur, calories = cal, date = today)); showAddRecord = false }
    })
    if (showAddPlan) AddFitnessDialog(onDismiss = { showAddPlan = false }, onSave = { name, sets, reps, dur, cal ->
        scope.launch { Repo.fitnessPlan.insert(FitnessPlanEntity(dayIndex = 0, actionName = name, sets = sets, reps = reps, durationMin = dur, calories = cal, date = 0)); showAddPlan = false }
    })
    if (edit != null) AddFitnessDialog(initial = edit, onDismiss = { editItem = null }, onSave = { name, sets, reps, dur, cal ->
        scope.launch { Repo.fitnessPlan.update(edit.copy(actionName = name, sets = sets, reps = reps, durationMin = dur, calories = cal)); editItem = null }
    })
}

@Composable
private fun FitnessItem(e: FitnessPlanEntity, onEdit: () -> Unit, onDelete: () -> Unit, onToggle: (Boolean) -> Unit) {
    var showDel by remember { mutableStateOf(false) }
    AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8), onClick = onEdit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = e.done, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text(e.actionName, fontWeight = FontWeight.SemiBold, color = if (e.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                val detail = if (e.reps > 0) "${e.sets}组 × ${e.reps}次" else "${e.durationMin}分钟"
                Text(detail + if (e.calories > 0) " · ${e.calories} kcal" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null) }
            IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
        }
    }
    if (showDel) ConfirmDeleteDialog(message = "确定删除动作「${e.actionName}」吗？", onDismiss = { showDel = false }) { onDelete() }
}

@Composable
private fun AddFitnessDialog(initial: FitnessPlanEntity? = null, onDismiss: () -> Unit, onSave: (String, Int, Int, Int, Int) -> Unit) {
    var name by remember { mutableStateOf(initial?.actionName ?: "") }
    var mode by remember { mutableStateOf(if ((initial?.reps ?: 0) > 0) 0 else 1) } // 0 组次 / 1 时长
    var sets by remember { mutableStateOf((initial?.sets ?: 3).toString()) }
    var reps by remember { mutableStateOf((initial?.reps ?: 12).toString()) }
    var dur by remember { mutableStateOf((initial?.durationMin ?: 30).toString()) }
    var cal by remember { mutableStateOf((initial?.calories ?: 0).toString()) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = {
        val n = name.trim()
        if (n.isBlank()) return@TextButton
        val s = sets.toIntOrNull() ?: 0
        val r = reps.toIntOrNull() ?: 0
        val d = dur.toIntOrNull() ?: 0
        val c = cal.toIntOrNull() ?: 0
        onSave(n, if (mode == 0) s else 0, if (mode == 0) r else 0, if (mode == 1) d else 0, c)
    }) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text(if (initial == null) "添加动作" else "编辑动作") }, text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("动作名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(Dimen.s8))
                Text("记录方式")
                Row { listOf("组数×次数" to 0, "时长(分钟)" to 1).forEach { (t, v) -> FilterChip(selected = mode == v, onClick = { mode = v }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp)) } }
                Spacer(Modifier.height(Dimen.s8))
                if (mode == 0) {
                    Row { OutlinedTextField(sets, { sets = it }, label = { Text("组数") }, modifier = Modifier.weight(1f), singleLine = true); Spacer(Modifier.width(Dimen.s8)); OutlinedTextField(reps, { reps = it }, label = { Text("每组次数") }, modifier = Modifier.weight(1f), singleLine = true) }
                } else {
                    OutlinedTextField(dur, { dur = it }, label = { Text("时长(分钟)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Spacer(Modifier.height(Dimen.s8))
                OutlinedTextField(cal, { cal = it }, label = { Text("消耗热量(kcal，可选)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        })
}
