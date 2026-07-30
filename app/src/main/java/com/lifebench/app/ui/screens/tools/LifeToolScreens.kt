package com.lifebench.app.ui.screens.tools


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.lifebench.app.service.FocusService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.lifebench.app.util.AlarmScheduler
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
/** 睡眠质量文案/配色：0 未评 1 差 2 中 3 好。 */
private fun qualityLabel(q: Int) = when (q) { 1 -> "差"; 2 -> "中"; 3 -> "好"; else -> "未评" }
@Composable
private fun qualityColor(q: Int) = when (q) {
    1 -> MaterialTheme.colorScheme.error
    2 -> MaterialTheme.colorScheme.tertiary
    3 -> LocalExtraColors.current.success
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
/** 就寝提醒通知 id 固定值，避免与闹钟冲突。 */
private const val SLEEP_REMIND_ID = 9001

@Composable
fun SleepScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val recent by Repo.sleep.observeRecent().collectAsStateWithLifecycle(emptyList())
    val targetMin by Repo.settings.sleepTargetMin.collectAsStateWithLifecycle(480)
    val remindMin by Repo.settings.sleepRemindMin.collectAsStateWithLifecycle(-1)

    // —— 一键记时间：基于 upsertByDate 按入睡日去重 ——
    var editQuality by remember { mutableStateOf(0) }        // 表单用：保存时的质量
    var editing by remember { mutableStateOf<SleepEntity?>(null) }
    var showQualityDialog by remember { mutableStateOf<SleepEntity?>(null) }

    // 今天的入睡日 dayKey
    val todaySleepDay = TimeUtil.dayKey()
    val todayRec = remember(recent, todaySleepDay) { recent.firstOrNull { it.date == todaySleepDay } }

    // 一键记「我睡觉啦」：记录入睡时刻（按今天入睡日去重）
    fun markSleep() {
        val now = System.currentTimeMillis()
        scope.launch {
            val existing = Repo.sleep.getByDate(todaySleepDay)
            val wake = existing?.wakeTime ?: now
            val dur = TimeUtil.sleepDurationMin(now, wake)
            Repo.sleep.upsertByDate(
                SleepEntity(date = todaySleepDay, sleepTime = now, wakeTime = wake, durationMin = dur,
                    quality = existing?.quality ?: 0)
            )
            Toast.makeText(context, "已记录入睡时间 ${TimeUtil.formatClock(now)}", Toast.LENGTH_SHORT).show()
        }
    }
    // 一键记「我起床啦」：补起床时刻并核算时长
    fun markWake() {
        val now = System.currentTimeMillis()
        scope.launch {
            val existing = Repo.sleep.getByDate(todaySleepDay)
            if (existing == null) {
                // 没记入睡：以今早 1 点前估算入睡（兜底，避免时长异常）
                val estSleep = now - 60L * 60_000L * 6 // 估 6 小时前
                val dur = TimeUtil.sleepDurationMin(estSleep, now)
                Repo.sleep.upsertByDate(
                    SleepEntity(date = todaySleepDay, sleepTime = estSleep, wakeTime = now, durationMin = dur, quality = 0)
                )
                Toast.makeText(context, "未记录入睡，已按 6 小时前估算并保存", Toast.LENGTH_SHORT).show()
            } else {
                val dur = TimeUtil.sleepDurationMin(existing.sleepTime, now)
                Repo.sleep.update(existing.copy(wakeTime = now, durationMin = dur))
                Toast.makeText(context, "已记录起床时间 ${TimeUtil.formatClock(now)}，时长 ${TimeUtil.formatDuration(dur)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveForm(sleepTs: Long, wakeTs: Long, quality: Int) {
        val dur = TimeUtil.sleepDurationMin(sleepTs, wakeTs)
        scope.launch {
            Repo.sleep.upsertByDate(
                SleepEntity(date = TimeUtil.dayKey(sleepTs), sleepTime = sleepTs, wakeTime = wakeTs,
                    durationMin = dur, quality = quality)
            )
            Toast.makeText(context, "已保存（按入睡日期覆盖，不会重复记录）", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("睡眠作息", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))

        // —— 顶部：近一周平均 + 目标达标率环形 ——
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            val avgMin = if (recent.isEmpty()) 0 else recent.map { it.durationMin }.average().toInt()
            val rate = if (targetMin > 0) (avgMin.toFloat() / targetMin).coerceIn(0f, 1.3f) else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).weight(0.4f), contentAlignment = Alignment.Center) {
                    RingProgress(progress = rate, color = MaterialTheme.colorScheme.primary)
                    Text(if (recent.isEmpty()) "暂无" else "${TimeUtil.formatDuration(avgMin)}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).padding(start = Dimen.s12)) {
                    Text("近一周平均睡眠", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (recent.isEmpty()) "开始记录吧" else "达标率 ${ (rate * 100).toInt() }%",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("目标 ${TimeUtil.formatDuration(targetMin)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Dimen.s12))

        // —— 一键记录按钮（我睡觉啦 / 我起床啦）——
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("快速记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Row(Modifier.fillMaxWidth()) {
                val slept = todayRec != null
                Button(onClick = { markSleep() }, modifier = Modifier.weight(1f).padding(end = Dimen.s6).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (slept) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (slept) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    )) {
                    Icon(Icons.Filled.Bedtime, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😴 我睡觉啦", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (slept) Text("已记 ${TimeUtil.formatClock(todayRec!!.sleepTime)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val woke = todayRec?.wakeTime?.let { it > todayRec.sleepTime } ?: false
                Button(onClick = { markWake() }, modifier = Modifier.weight(1f).padding(start = Dimen.s6).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (woke) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (woke) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    )) {
                    Icon(Icons.Filled.WbSunny, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌞 我起床啦", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (woke) Text("已记 ${TimeUtil.formatClock(todayRec!!.wakeTime)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("一键记昨晚（23:00→07:00）", onClick = {
                val sleepTs = TimeUtil.dayKey() - 60L * 60_000L // 昨晚 23:00（dayKey 是今 0 点，往前 1h）
                val wakeTs = TimeUtil.dayKey() + 7L * 60 * 60_000L // 今 07:00
                saveForm(sleepTs, wakeTs, editQuality)
            }, icon = Icons.Filled.HistoryEdu)
        }
        Spacer(Modifier.height(Dimen.s12))

        // —— 手动表单（可设质量）——
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("手动记录（含睡眠质量）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            var sleepTs by remember { mutableStateOf(System.currentTimeMillis() - 8 * 3600_000) }
            var wakeTs by remember { mutableStateOf(System.currentTimeMillis()) }
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
            // 质量选择（差/中/好）
            Text("睡眠质量", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = Dimen.s4)) {
                listOf(1 to "差", 2 to "中", 3 to "好").forEach { (qv, qn) ->
                    FilterChip(selected = editQuality == qv, onClick = { editQuality = qv },
                        label = { Text(qn) }, modifier = Modifier.padding(end = Dimen.s6))
                }
            }
            Spacer(Modifier.height(Dimen.s8))
            val dur = TimeUtil.sleepDurationMin(sleepTs, wakeTs)
            Text("睡眠时长：${TimeUtil.formatDuration(dur)}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("保存记录", onClick = { saveForm(sleepTs, wakeTs, editQuality) }, icon = Icons.Filled.Save)
        }
        Spacer(Modifier.height(Dimen.s12))

        // —— 本周 vs 上周对比 ——
        if (recent.isNotEmpty()) {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                val (thisWeek, lastWeek) = remember(recent) {
                    val now = System.currentTimeMillis()
                    val tw = recent.filter { it.date >= TimeUtil.dayKey() - 6L * 86_400_000L }
                    val lw = recent.filter { it.date in (TimeUtil.dayKey() - 13L * 86_400_000L) until (TimeUtil.dayKey() - 6L * 86_400_000L) }
                    val avg = { list: List<SleepEntity> -> if (list.isEmpty()) 0 else list.map { it.durationMin }.average().toInt() }
                    avg(tw) to avg(lw)
                }
                Text("本周 vs 上周平均", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("本周", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (thisWeek == 0) "—" else TimeUtil.formatDuration(thisWeek),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("上周", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (lastWeek == 0) "—" else TimeUtil.formatDuration(lastWeek),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s12))
        }

        // —— 近一周记录列表（可编辑质量/删除）——
        if (recent.isNotEmpty()) {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Text("近一周睡眠记录", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                recent.reversed().forEach { r ->
                    val q = r.quality
                    Row(Modifier.fillMaxWidth().padding(vertical = Dimen.s6), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${TimeUtil.formatMonthDay(r.sleepTime)}（${TimeUtil.formatClock(r.sleepTime)}→${TimeUtil.formatClock(r.wakeTime)}）",
                                style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("时长 ${TimeUtil.formatDuration(r.durationMin)}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(Dimen.s8))
                                Surface(shape = RoundedCornerShape(Dimen.s6), color = qualityColor(q).copy(alpha = 0.15f)) {
                                    Text("质量 ${qualityLabel(q)}", style = MaterialTheme.typography.bodySmall,
                                        color = qualityColor(q), modifier = Modifier.padding(horizontal = Dimen.s6, vertical = 2.dp))
                                }
                            }
                        }
                        IconButton(onClick = { showQualityDialog = r }) {
                            Icon(Icons.Filled.Star, "评质量", tint = if (q > 0) LocalExtraColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            scope.launch { Repo.sleep.delete(r); Toast.makeText(context, "已删除该记录", Toast.LENGTH_SHORT).show() }
                        }) {
                            Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s12))
        }

        // —— 折线图（带目标基准线）——
        if (recent.isNotEmpty()) {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Text("近一周睡眠时长（分钟）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                LineChart(recent.reversed().map { it.durationMin.toFloat() }, MaterialTheme.colorScheme.primary,
                    target = targetMin.toFloat(), targetLabel = "目标")
            }
            Spacer(Modifier.height(Dimen.s12))
        }

        // —— 建议（按档位高亮）——
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            val level = remember(recent) {
                val s = CalcUtil.sleepSuggestion(recent)
                when {
                    s.contains("偏少") -> "少"
                    s.contains("波动") -> "波动"
                    s.contains("偏多") -> "多"
                    else -> "好"
                }
            }
            val (icon, tint, bg) = when (level) {
                "少" -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
                "波动" -> Triple(Icons.Filled.Sync, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
                "多" -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                else -> Triple(Icons.Filled.CheckCircle, LocalExtraColors.current.success, LocalExtraColors.current.success.copy(alpha = 0.12f))
            }
            Text("睡眠改善建议", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Row(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(Dimen.s8)).padding(Dimen.s8),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Dimen.s8))
                Text(CalcUtil.sleepSuggestion(recent), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Dimen.s12))

        // —— 目标与就寝提醒设置 ——
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("目标 & 就寝提醒", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            var targetH by remember { mutableStateOf(targetMin / 60) }
            var targetM by remember { mutableStateOf(targetMin % 60) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("目标睡眠时长", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Button(onClick = {
                    TimePickerDialog(context, { _, h, m -> targetH = h; targetM = m; scope.launch { Repo.settings.setSleepTargetMin(h * 60 + m) } },
                        targetH, targetM, true).show()
                }) { Text("${targetH}h${targetM}m") }
            }
            Spacer(Modifier.height(Dimen.s8))
            val notifyOn by Repo.settings.notificationEnabled.collectAsStateWithLifecycle(true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("就寝提醒", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                var rh by remember { mutableStateOf(if (remindMin < 0) 22 else remindMin / 60) }
                var rm by remember { mutableStateOf(if (remindMin < 0) 30 else remindMin % 60) }
                Button(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        rh = h; rm = m
                        val minOfDay = h * 60 + m
                        scope.launch {
                            Repo.settings.setSleepRemindMin(minOfDay)
                            if (notifyOn) scheduleSleepReminder(context, minOfDay)
                            Toast.makeText(context, "已设就寝提醒 ${"%02d:%02d".format(h, m)}", Toast.LENGTH_SHORT).show()
                        }
                    }, rh, rm, true).show()
                }) { Text(if (remindMin < 0) "未设置" else "${rh}:${"%02d".format(rm)}") }
                Spacer(Modifier.width(Dimen.s8))
                if (remindMin >= 0) {
                    TextButton(onClick = {
                        scope.launch {
                            Repo.settings.setSleepRemindMin(-1)
                            AlarmScheduler.cancel(context, SLEEP_REMIND_ID)
                        }
                    }) { Text("关闭", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }

    // 质量评分弹窗
    if (showQualityDialog != null) {
        val rec = showQualityDialog!!
        AlertDialog(onDismissRequest = { showQualityDialog = null },
            title = { Text("评价睡眠质量") },
            text = {
                Row(Modifier.fillMaxWidth()) {
                    listOf(1 to "差", 2 to "中", 3 to "好").forEach { (qv, qn) ->
                        FilterChip(selected = rec.quality == qv, onClick = {
                            scope.launch { Repo.sleep.update(rec.copy(quality = qv)); showQualityDialog = null }
                        }, label = { Text(qn) }, modifier = Modifier.padding(end = Dimen.s6))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = null }) { Text("完成") } })
    }
}

/** 环形进度：用于睡眠达标率。 */
@Composable
private fun RingProgress(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        drawArc(color = color.copy(alpha = 0.18f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
            style = Stroke(width = stroke))
        drawArc(color = color, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            style = Stroke(width = stroke))
    }
}

/** 调度就寝提醒（当天或次日该时刻的精确闹钟）。 */
private fun scheduleSleepReminder(context: Context, minOfDay: Int) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minOfDay / 60); set(Calendar.MINUTE, minOfDay % 60)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
    }
    AlarmScheduler.schedule(context, AlarmScheduler.Alarm(
        id = SLEEP_REMIND_ID, triggerAt = cal.timeInMillis,
        title = "该休息啦 🌙", text = "已到就寝提醒时间，放下手机，准备入睡吧。"
    ))
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

