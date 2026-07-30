package com.lifebench.app.ui.screens.brain

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.entity.SchulteResultEntity
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.util.CalcUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ===== 舒尔特方格（v1.0.0 起唯一保留的训练模块）=====
 */

// ——— 舒尔特方格 ———
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchulteScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val nOptions = listOf(3, 4, 5, 6, 7, 8)              // 3~8 共 6 个规格，自动换行不再挤压 7×7 / 8×8
    var size by remember { mutableStateOf(5) }
    var mode by remember { mutableStateOf("继续") }       // 中断 / 继续
    var numbers by remember { mutableStateOf((1..size * size).toList()) }
    // 4 色循环（默认/绿/红/teal），与数字位置同步洗牌，强化训练辨识又不杂乱
    var cellColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    var phase by remember { mutableStateOf("idle") }       // idle | countdown | running | done
    var countdown by remember { mutableStateOf(0) }        // 3 → 2 → 1
    var expected by remember { mutableStateOf(1) }
    var errors by remember { mutableStateOf(0) }
    var startTime by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf<SchulteResultEntity?>(null) }
    var isNewRecord by remember { mutableStateOf(false) }
    var lastClicked by remember { mutableStateOf(-1) }
    val history by Repo.schulte.observeAll().collectAsStateWithLifecycle(emptyList())

    val palette = listOf(
        Color(0xFF1F2426),  // 默认（onSurface 黑）
        Color(0xFF6BBF73),  // 绿 - SuccessLight
        Color(0xFFD8695F),  // 红 - ErrorLight
        Color(0xFF1A9C84),  // teal - PrimaryLight（设计系统主色）
    )

    /** 重置本局：重新洗牌、归零计时与计数、刷新颜色。 */
    fun reset() {
        val ns = (1..size * size).toList().shuffled()
        numbers = ns
        // 错落分配（步长 7 与 3 错开，避免连续同色），保证每盘看起来新但不会全同色
        cellColors = List(ns.size) { palette[(it * 7 + 3) % 4] }
        phase = "idle"
        countdown = 0
        expected = 1
        errors = 0
        startTime = 0L
        elapsed = 0L
        finished = null
        lastClicked = -1
    }

    /** 点「开始」→ 启动 3-2-1 倒计时，倒计时归零后自动进入 running 并开始计时。 */
    fun startWithCountdown() {
        reset()
        countdown = 3
        phase = "countdown"
    }

    // 倒计时驱动：countdown>0 时每秒 -1，归零后切到 running 并启动秒表
    LaunchedEffect(phase, countdown) {
        if (phase != "countdown") return@LaunchedEffect
        if (countdown > 0) {
            delay(1000)
            countdown--
        } else {
            phase = "running"
            startTime = System.currentTimeMillis()
            elapsed = 0L
        }
    }

    // 计时循环：running 期间每 100ms 刷新用时；phase 离开 running 自动停。
    LaunchedEffect(phase) {
        if (phase != "running") return@LaunchedEffect
        while (phase == "running") {
            delay(100)
            elapsed = System.currentTimeMillis() - startTime
        }
    }

    /** 自然完成：写入记录、判断新纪录、弹报告。 */
    fun complete() {
        if (phase != "running") return
        phase = "done"
        val prevBest = history.filter { it.size == size }.minOfOrNull { it.timeMs }
        isNewRecord = prevBest == null || elapsed < prevBest
        val r = SchulteResultEntity(size = size, timeMs = elapsed, errors = errors, efficiency = 0f, mode = mode)
        finished = r
        scope.launch { Repo.schulte.insert(r) }
    }

    /** 取消/放弃：倒计时或 running 阶段都可触发；不写记录、不算最佳、不弹报告。 */
    fun cancel() {
        if (phase == "idle") return
        phase = "idle"
        finished = null
        isNewRecord = false
        expected = 1
        errors = 0
        elapsed = 0L
    }

    /** 点击格子：点中下一个数字→前进；点错→错误+1，中断模式直接结束。 */
    fun onCell(v: Int) {
        if (phase != "running") return
        if (v == expected) {
            lastClicked = v
            expected++
            if (expected > size * size) complete()
        } else {
            errors++
            if (mode == "中断") complete()
        }
    }

    val mins = (elapsed / 60000).toInt()
    val secs = ((elapsed / 1000) % 60).toInt()
    val timerText = "%02d:%02d".format(mins, secs)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("舒尔特方格", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("规格（数字 1 ~ n² 按升序点击）")
            Spacer(Modifier.height(Dimen.s4))
            // FlowRow 让 3~8 共 6 个 chip 在窄屏自动换行，解决 7×7 被挤成 3 行的 bug
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimen.s4),
                verticalArrangement = Arrangement.spacedBy(Dimen.s4),
                modifier = Modifier.fillMaxWidth()
            ) {
                nOptions.forEach { s ->
                    FilterChip(
                        selected = size == s,
                        onClick = { size = s; reset() },
                        label = { Text("${s}×${s}") }
                    )
                }
            }
            Spacer(Modifier.height(Dimen.s12))
            Text("点错处理")
            Spacer(Modifier.height(Dimen.s4))
            Row {
                listOf("继续", "中断").forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m) },
                        modifier = Modifier.padding(end = Dimen.s4)
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        // 计时区：左错误、中间 mm:ss 等宽 teal 大字、右下一个；居中突出计时。
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "错误 $errors",
                    modifier = Modifier.weight(1f),
                    color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    timerText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    "下一个：$expected / ${size * size}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        if (phase == "idle" || phase == "done") {
            PrimaryButton("开始", onClick = { startWithCountdown() }, modifier = Modifier.padding(horizontal = Dimen.s16))
        } else {
            OutlinedButton(
                onClick = { cancel() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.s16)
            ) {
                Text(if (phase == "countdown") "取消倒计时" else "放弃本局")
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = Dimen.s8
                val cell = (maxWidth - gap * (size - 1)) / size
                val gridHeight = cell * size + gap * (size - 1)
                Box(Modifier.fillMaxWidth().height(gridHeight)) {
                    // 倒计时覆盖在网格区域：3-2-1 全屏大数字，不显示 cell
                    if (phase == "countdown" && countdown > 0) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$countdown",
                                fontSize = 96.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(size),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            items(numbers.size) { idx ->
                                val v = numbers[idx]
                                val color = cellColors.getOrElse(idx) { MaterialTheme.colorScheme.onSurface }
                                Surface(
                                    shape = RoundedCornerShape(Dimen.s8),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.size(cell).clickable { onCell(v) }
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "$v",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 各规格最佳成绩：记录每个规格完成的最短用时（取代原效率分趋势图），并给星级即时反馈
        val bestBySize = history.groupBy { it.size }
            .mapValues { (_, list) -> list.minByOrNull { it.timeMs }!! }
            .toSortedMap()
        if (bestBySize.isNotEmpty()) {
            Spacer(Modifier.height(Dimen.s16))
            SectionTitle("  各规格最佳成绩")
            Spacer(Modifier.height(Dimen.s8))
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                bestBySize.forEach { (sz, best) ->
                    val stars = CalcUtil.schulteStars(sz, best.timeMs)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Dimen.s6),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${sz}×${sz}", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
                        Text(
                            "★".repeat(stars) + "☆".repeat(3 - stars),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(64.dp)
                        )
                        Text(
                            "最佳 %.1f 秒 · 错 %d".format(best.timeMs / 1000.0, best.errors),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
    val f = finished
    if (f != null) {
        val best = history.filter { it.size == f.size }.minOfOrNull { it.timeMs }
        val bestSec = best?.let { "%.1f".format(it / 1000.0) } ?: "—"
        AlertDialog(
            onDismissRequest = { finished = null },
            confirmButton = { TextButton(onClick = { finished = null }) { Text("知道了") } },
            title = { Text(if (isNewRecord) "新纪录！🏆" else "训练完成 🎉") },
            text = {
                Text(
                    "规格：${f.size}×${f.size}\n" +
                    "用时：%.1f 秒\n".format(f.timeMs / 1000.0) +
                    "错误：${f.errors} 次\n" +
                    "模式：${f.mode}\n" +
                    "本规格最佳：${bestSec} 秒"
                )
            }
        )
    }
}

// ——— 舒尔特方格（唯一保留的训练模块）———

