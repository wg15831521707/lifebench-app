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
 * ===== 舒尔特方格（v1.2 起唯一保留的训练模块）=====
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

    /** 本局结束：核算效率分并入库、弹报告。 */
    fun finish() {
        if (!running) return
        running = false
        val eff = CalcUtil.schulteEfficiency(size, elapsed, errors)
        val r = SchulteResultEntity(size = size, timeMs = elapsed, errors = errors, efficiency = eff, mode = mode)
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
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(Dimen.s16))
            SectionTitle("  成绩趋势（效率分）")
            Spacer(Modifier.height(Dimen.s8))
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                val data = history.takeLast(20).reversed().map { it.efficiency }
                LineChart(data, MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
    val f = finished
    if (f != null) {
        AlertDialog(
            onDismissRequest = { finished = null },
            confirmButton = { TextButton(onClick = { finished = null }) { Text("知道了") } },
            title = { Text("训练完成 🎉") },
            text = {
                Text("规格：${f.size}×${f.size}\n用时：${f.timeMs / 1000} 秒\n错误：${f.errors} 次\n模式：${f.mode}\n效率分：%.1f".format(f.efficiency))
            }
        )
    }
}

// ——— 舒尔特方格（唯一保留的训练模块）———

