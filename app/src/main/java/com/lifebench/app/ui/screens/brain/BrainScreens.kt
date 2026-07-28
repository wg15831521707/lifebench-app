package com.lifebench.app.ui.screens.brain

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
@Composable
fun SchulteScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val nOptions = listOf(3, 4, 5, 6, 7, 8, 9)
    var size by remember { mutableStateOf(5) }
    var mode by remember { mutableStateOf("继续") }            // 中断 / 继续
    var numbers by remember { mutableStateOf((1..size * size).toList()) }
    var running by remember { mutableStateOf(false) }
    var expected by remember { mutableStateOf(1) }
    var errors by remember { mutableStateOf(0) }
    var startTime by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf<SchulteResultEntity?>(null) }
    var isNewRecord by remember { mutableStateOf(false) }
    var lastClicked by remember { mutableStateOf(-1) }
    val history by Repo.schulte.observeAll().collectAsStateWithLifecycle(emptyList())

    /** 重置本局：重新洗牌、归零计时与计数。 */
    fun reset() {
        numbers = (1..size * size).toList().shuffled()
        running = false
        expected = 1
        errors = 0
        startTime = 0L
        elapsed = 0L
        finished = null
        lastClicked = -1
    }

    // 计时循环：running 期间每 100ms 刷新用时；暂停/结束即停。
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        startTime = System.currentTimeMillis()
        elapsed = 0L
        while (running) {
            delay(100)
            elapsed = System.currentTimeMillis() - startTime
        }
    }

    /** 本局结束：记录用时与错误、比对个人最佳、入库并弹报告。 */
    fun finish() {
        if (!running) return
        running = false
        // 入库前用当前历史判断本局是否刷新该规格最短用时（efficiency 字段保留以兼容表结构，统一写 0）
        val prevBest = history.filter { it.size == size }.minOfOrNull { it.timeMs }
        isNewRecord = prevBest == null || elapsed < prevBest
        val r = SchulteResultEntity(size = size, timeMs = elapsed, errors = errors, efficiency = 0f, mode = mode)
        finished = r
        scope.launch { Repo.schulte.insert(r) }
    }

    /** 点击格子：点中下一个数字→前进；点错→错误+1，中断模式直接结束。 */
    fun onCell(v: Int) {
        if (!running) return
        if (v == expected) {
            lastClicked = v
            expected++
            if (expected > size * size) finish()
        } else {
            errors++
            if (mode == "中断") finish()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("舒尔特方格", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("规格（数字 1 ~ n² 按升序点击）")
            Spacer(Modifier.height(Dimen.s4))
            Row { nOptions.forEach { s -> FilterChip(selected = size == s, onClick = { size = s; reset() }, label = { Text("${s}×${s}") }, modifier = Modifier.padding(end = 4.dp)) } }
            Spacer(Modifier.height(Dimen.s8))
            Text("点错处理")
            Row { listOf("继续", "中断").forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }, modifier = Modifier.padding(end = 4.dp)) } }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("用时 %d 秒".format(elapsed / 1000), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("错误 $errors", color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Dimen.s4))
            Text("下一个：$expected / ${size * size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Dimen.s12))
        if (!running) {
            PrimaryButton(if (expected == 1) "开始" else "重新开始", onClick = { reset(); running = true }, modifier = Modifier.padding(horizontal = Dimen.s16))
        } else {
            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth().padding(horizontal = Dimen.s16)) { Text("提前结束") }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 4.dp
                val cell = (maxWidth - gap * (size - 1)) / size
                LazyVerticalGrid(
                    columns = GridCells.Fixed(size),
                    modifier = Modifier.fillMaxWidth().height(cell * size + gap * (size - 1)),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(numbers) { v ->
                        val isClicked = v == lastClicked
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isClicked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(cell).clickable { onCell(v) }
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("$v", style = MaterialTheme.typography.titleLarge,
                                    color = if (isClicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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

