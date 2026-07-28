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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.entity.SchulteResultEntity
import com.lifebench.app.data.entity.TrainingResultEntity
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.util.CalcUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ===== 脑力与速读专区：枢纽 + 舒尔特方格 + 通用脑力训练 + 速读训练 =====
 */

// ——— 脑力与速读枢纽 ———
@Composable
fun BrainHubScreen(nav: NavController) {
    val entries = listOf(
        BrainEntry("舒尔特方格", Icons.Filled.GridView, Routes.SCHULTE),
        BrainEntry("专注力训练", Icons.Filled.Visibility, "brain_train/专注力"),
        BrainEntry("记忆力训练", Icons.Filled.Memory, "brain_train/记忆力"),
        BrainEntry("逻辑思维", Icons.Filled.Extension, "brain_train/逻辑"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("脑力与速读")
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

private data class BrainEntry(val label: String, val icon: ImageVector, val route: String)

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

// ——— 通用脑力训练（专注力 / 记忆力 / 逻辑）———
@Composable
fun BrainTrainScreen(nav: NavController, category: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var phase by remember { mutableStateOf("idle") }      // idle / show / input / result
    var sequence by remember { mutableStateOf(listOf<Int>()) }
    var userInput by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0f) }
    var accuracy by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }

    val len = when (category) { "记忆力" -> 6; else -> 4 }

    /** 记忆力/专注力：生成数字序列并进入展示阶段。 */
    fun startRound() {
        sequence = List(len) { (0..9).random() }
        userInput = ""
        phase = "show"
    }

    // 展示 1.6 秒后自动进入输入阶段。
    LaunchedEffect(phase) {
        if (phase == "show") {
            delay(1600)
            phase = "input"
        }
    }

    fun submit() {
        val target = sequence.joinToString("")
        val correct = userInput == target
        val hit = if (correct) 1f else {
            val ok = userInput.mapIndexed { i, c ->
                if (i < sequence.size && c.digitToIntOrNull() == sequence[i]) 1 else 0
            }.sum()
            ok.toFloat() / sequence.size
        }
        accuracy = hit
        score = hit * 100f
        phase = "result"
        showResult = true
        scope.launch {
            Repo.training.insert(TrainingResultEntity(category = category, score = score, accuracy = accuracy, detail = "长度$len"))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("${category}训练", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("类型：$category", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Text(
                when (category) {
                    "记忆力" -> "记住短暂出现的数字序列，然后按顺序输入。序列越长越锻炼短时记忆。"
                    "专注力" -> "保持注意力，记住闪烁出现的数字并准确复述，训练抗干扰与专注。"
                    else -> "完成逻辑小测验，训练思维敏捷与运算准确性。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Dimen.s12))
        if (category == "逻辑") {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                LogicQuiz(onDone = { s, a ->
                    score = s; accuracy = a; showResult = true
                    scope.launch { Repo.training.insert(TrainingResultEntity(category = category, score = s, accuracy = a, detail = "算术")) }
                })
            }
        } else {
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                when (phase) {
                    "idle", "result" -> {
                        PrimaryButton("开始训练", onClick = { startRound() })
                    }
                    "show" -> {
                        Text(sequence.joinToString(" "), style = MaterialTheme.typography.displayMedium,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Dimen.s4))
                        Text("请记住上面的数字…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    "input" -> {
                        Text("请输入刚才的数字", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimen.s8))
                        OutlinedTextField(userInput, { userInput = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("序列") })
                        Spacer(Modifier.height(Dimen.s8))
                        PrimaryButton("提交", onClick = { submit() })
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimen.s24))
    }
    if (showResult) {
        AlertDialog(
            onDismissRequest = { showResult = false; phase = "idle" },
            confirmButton = { TextButton(onClick = { showResult = false; phase = "idle" }) { Text("完成") } },
            title = { Text("训练结果") },
            text = { Text("正确率：%.0f%%\n得分：%.0f".format(accuracy * 100, score)) }
        )
    }
}

/** 逻辑思维：5 道随机四则运算，结算正确率与得分。 */
@Composable
private fun LogicQuiz(onDone: (Float, Float) -> Unit) {
    var idx by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    val total = 5
    var input by remember { mutableStateOf("") }

    val q = remember(idx) {
        val a = (1..20).random()
        val b = (1..20).random()
        val op = listOf("+", "-", "×").random()
        when (op) {
            "+" -> "$a + $b" to a + b
            "-" -> "$a - $b" to a - b
            else -> "$a × $b" to a * b
        }
    }

    Column {
        Text("第 ${idx + 1}/$total 题：${q.first} = ?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimen.s8))
        OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("答案") })
        Spacer(Modifier.height(Dimen.s8))
        PrimaryButton("下一题", onClick = {
            if (input.toIntOrNull() == q.second) correctCount++
            input = ""
            idx++
            if (idx >= total) {
                val acc = correctCount.toFloat() / total
                onDone(acc * 100f, acc)
            }
        })
    }
}

