package com.lifebench.app.ui.screens.tools

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.entity.*
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.GradientPanel
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.ui.theme.LocalQuadrantColors
import com.lifebench.app.ui.theme.ThemePresets
import com.lifebench.app.util.AlarmScheduler
import com.lifebench.app.util.BackupUtil
import com.lifebench.app.util.CryptoUtil
import com.lifebench.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.util.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/**
 * ===== 生活工具专区：枢纽 + 待办/密码/笔记/纪念日/设置 =====
 */

// ——— 工具枢纽（重设计：分类双列网格）———

/** 区块标题（带语义色小图标）。 */
@Composable
private fun HubSectionHeader(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = Dimen.s4)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Dimen.s6))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ToolsHubScreen(nav: NavController) {
    val efficiency = listOf(
        ToolMeta("待办备忘录", "四象限，分清轻重缓急", Icons.Filled.Checklist, Routes.TODO, 0),
        ToolMeta("收支记账", "随手记，掌控每一笔", Icons.Filled.AccountBalanceWallet, Routes.ACCOUNT, 1),
        ToolMeta("舒尔特方格", "训练专注力与反应", Icons.Filled.GridOn, Routes.SCHULTE, 2),
    )
    val safety = listOf(
        ToolMeta("密码保险箱", "加密保管账号密码", Icons.Filled.Lock, Routes.PASSWORD, 3),
        ToolMeta("随手笔记", "灵感随时记录", Icons.Filled.Note, Routes.NOTE, 0),
        ToolMeta("纪念日倒计时", "重要日子不错过", Icons.Filled.Celebration, Routes.ANNIVERSARY, 1),
    )
    val discover = listOf(
        ToolMeta("抖音热榜", "热门视频一键直达抖音", Icons.Filled.SmartDisplay, Routes.DOUYIN_WALL, 1),
    )
    Scaffold(topBar = { AppTopBar("工具") }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = Dimen.s16),
            verticalArrangement = Arrangement.spacedBy(Dimen.s12),
            horizontalArrangement = Arrangement.spacedBy(Dimen.s12),
            contentPadding = PaddingValues(vertical = Dimen.s12)
        ) {
            items(1, span = { GridItemSpan(2) }) { HubSectionHeader("效率工具", Icons.Filled.FlashOn) }
            items(efficiency) { ToolTile(it) { nav.navigate(it.route) } }
            items(1, span = { GridItemSpan(2) }) { HubSectionHeader("安全与记录", Icons.Filled.VerifiedUser) }
            items(safety) { ToolTile(it) { nav.navigate(it.route) } }
            items(1, span = { GridItemSpan(2) }) { HubSectionHeader("发现", Icons.Filled.Explore) }
            items(discover) { ToolTile(it) { nav.navigate(it.route) } }
        }
    }
}

// ——— 专注枢纽（番茄钟 / 睡眠 / 饮食 / 习惯，含实时状态）———
private data class FocusMeta(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val accent: Int,
)

private fun fmtSleep(min: Int): String {
    val h = min / 60; val m = min % 60
    return if (h > 0) "${h}h${m}m" else "${m}m"
}

/** 预算金额展示：整数去小数（如 2000），非整数保留两位（如 1999.5）。 */
private fun formatBudget(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v)

@Composable
fun FocusHubScreen(nav: NavController) {
    var focusMin by remember { mutableStateOf(0) }
    val recentSleep by Repo.sleep.observeRecent().collectAsStateWithLifecycle(emptyList())
    val diets by Repo.diet.observeByDate(TimeUtil.dayKey()).collectAsStateWithLifecycle(emptyList())
    val habits by Repo.habit.observeActiveHabits().collectAsStateWithLifecycle(emptyList())
    val checkIns by Repo.habit.observeAllCheckIns().collectAsStateWithLifecycle(emptyList())
    val todayKey = TimeUtil.dayKey()
    val todayChecked = checkIns.count { it.date == todayKey }
    LaunchedEffect(Unit) {
        focusMin = Repo.focus.focusMinutesBetween(TimeUtil.dayKey(), System.currentTimeMillis())
    }
    val sleepMin = recentSleep.firstOrNull()?.durationMin ?: 0
    val metas = listOf(
        FocusMeta("番茄钟", Icons.Filled.Alarm, Routes.FOCUS, 0),
        FocusMeta("睡眠记录", Icons.Filled.Bedtime, Routes.SLEEP, 2),
        FocusMeta("饮食菜谱", Icons.Filled.Restaurant, Routes.DIET, 1),
        FocusMeta("习惯打卡", Icons.Filled.Repeat, Routes.HABIT, 3),
    )
    Scaffold(topBar = { AppTopBar("专注") }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = Dimen.s16),
            verticalArrangement = Arrangement.spacedBy(Dimen.s12),
            horizontalArrangement = Arrangement.spacedBy(Dimen.s12),
            contentPadding = PaddingValues(vertical = Dimen.s12)
        ) {
            items(1, span = { GridItemSpan(2) }) {
                // 顶部焦点面板：渐变强调面板承载今日专注总时长（设计系统「强调面板」层级）
                GradientPanel {
                    Text("今日专注", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text("$focusMin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.alignByBaseline())
                        Text(" 分钟", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.alignByBaseline())
                        Text(" · 目标 120", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.alignByBaseline())
                    }
                }
            }
            items(1, span = { GridItemSpan(2) }) { HubSectionHeader("今日状态", Icons.Filled.Insights) }
            items(1) { FocusStatCard(metas[0], "${focusMin} 分", "今日专注时长", nav) }
            items(1) { FocusStatCard(metas[1], fmtSleep(sleepMin), "昨晚睡眠", nav) }
            items(1) { FocusStatCard(metas[2], "${diets.size} 餐", "今日已记录", nav) }
            items(1) { FocusStatCard(metas[3], "$todayChecked/${habits.size}", "今日打卡", nav) }
            items(1, span = { GridItemSpan(2) }) {
                PrimaryButton("开始一次专注", onClick = { nav.navigate(Routes.FOCUS) }, icon = Icons.Filled.PlayArrow)
            }
        }
    }
}

/** 专注状态卡：图标芯片 + 实时数值 + 标签 + 右箭头，点按进入对应子页。 */
@Composable
private fun FocusStatCard(meta: FocusMeta, value: String, sub: String, nav: NavController) {
    val (container, tint) = chipTint(meta.accent)
    AppCard(onClick = { nav.navigate(meta.route) }) {
        Surface(
            shape = RoundedCornerShape(12.dp), color = container,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(meta.icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(Dimen.s8))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimen.s6))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meta.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

// ——— 待办备忘录（科维四象限）———
private val QUADRANT_LABELS = listOf("重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急")

@Composable
fun TodoScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val active by Repo.todo.observeActive().collectAsStateWithLifecycle(emptyList())
    val archived by Repo.todo.observeArchived().collectAsStateWithLifecycle(emptyList())
    val list = if (tab == 0) active else archived
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<TodoEntity?>(null) }

    Scaffold(
        topBar = { AppTopBar("待办备忘录", showBack = true, onBack = { nav.popBackStack() }) },
        floatingActionButton = { AddFloating { showAdd = true } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("进行中") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("已完成") })
            }
            LazyColumn(Modifier.fillMaxSize().padding(Dimen.s16)) {
                if (tab == 0) {
                    for (q in 0..3) {
                        val qs = list.filter { it.quadrant == q }
                        if (qs.isNotEmpty()) {
                            items(1) { QuadrantSectionHeader(q) }
                            items(qs, key = { it.id }) { item ->
                                TodoRow(item,
                                    onToggle = { scope.launch { Repo.todo.update(item.copy(done = it, archived = it)) } },
                                    onEdit = { editItem = item },
                                    onDelete = { scope.launch { Repo.todo.delete(item); AlarmScheduler.cancel(context, item.id.toInt()) } })
                            }
                        }
                    }
                    if (list.isEmpty()) items(1) { EmptyState("暂无进行中的待办，点 + 添加") }
                } else {
                    items(list, key = { it.id }) { item ->
                        TodoRow(item,
                            onToggle = { scope.launch { Repo.todo.update(item.copy(done = it, archived = it)) } },
                            onEdit = { editItem = item },
                            onDelete = { scope.launch { Repo.todo.delete(item); AlarmScheduler.cancel(context, item.id.toInt()) } })
                    }
                    if (list.isEmpty()) items(1) { EmptyState("暂无已完成事项") }
                }
            }
        }
    }
    val editing = editItem
    if (showAdd || editing != null) {
        TodoEditDialog(
            initial = editing,
            onDismiss = { showAdd = false; editItem = null },
            onSave = { e ->
                scope.launch {
                    val id = if (e.id == 0L) Repo.todo.insert(e) else { Repo.todo.update(e); e.id }
                    if (e.dueTime != null && e.dueTime > System.currentTimeMillis()) {
                        AlarmScheduler.schedule(context, AlarmScheduler.Alarm(id.toInt(), e.dueTime, "待办提醒", e.title))
                    }
                    showAdd = false; editItem = null
                }
            }
        )
    }
}

/**
 * 待办分区标题：带对应象限语义强调色圆点，与首页「田」字格呼应，一眼定位象限。
 */
@Composable
private fun QuadrantSectionHeader(q: Int) {
    val qc = LocalQuadrantColors.current
    val accent = when (q) {
        0 -> qc.q1Accent; 1 -> qc.q2Accent; 2 -> qc.q3Accent; else -> qc.q4Accent
    }
    Row(Modifier.padding(bottom = Dimen.s4), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(Dimen.s8))
        Text(QUADRANT_LABELS[q], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TodoRow(item: TodoEntity, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDel by remember { mutableStateOf(false) }
    AppCard(Modifier.padding(bottom = Dimen.s12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.done, onCheckedChange = onToggle)
            Column(Modifier.weight(1f).clickable { onEdit() }) {
                Text(item.title, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (item.dueTime != null)
                    Text("到期 ${TimeUtil.formatHM(item.dueTime)} · ${item.repeatMode}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (showDel) ConfirmDeleteDialog(message = "确定删除待办「${item.title}」吗？", onDismiss = { showDel = false }) { onDelete() }
}

@Composable
private fun TodoEditDialog(initial: TodoEntity?, onDismiss: () -> Unit, onSave: (TodoEntity) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var quadrant by remember { mutableStateOf(initial?.quadrant ?: 2) }
    var dueTime by remember { mutableStateOf(initial?.dueTime) }
    var repeat by remember { mutableStateOf(initial?.repeatMode ?: "永不") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            if (title.isBlank()) return@TextButton
            onSave(initial?.copy(title = title, note = note, quadrant = quadrant, dueTime = dueTime, repeatMode = repeat)
                ?: TodoEntity(title = title, note = note, quadrant = quadrant, dueTime = dueTime, repeatMode = repeat))
        }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text(if (initial == null) "新建待办" else "编辑待办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimen.s8))
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimen.s8))
                Text("所属象限（科维四象限）")
                FlowRow(Modifier.fillMaxWidth()) {
                    QUADRANT_LABELS.forEachIndexed { q, label ->
                        FilterChip(selected = quadrant == q, onClick = { quadrant = q }, label = { Text(label) }, modifier = Modifier.padding(end = 4.dp, bottom = 4.dp))
                    }
                }
                Spacer(Modifier.height(Dimen.s8))
                Text("重复")
                Row { listOf("永不","每天","每周","每月","每年").forEach { r ->
                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r) }, modifier = Modifier.padding(end = 4.dp))
                } }
                Spacer(Modifier.height(Dimen.s8))
                Button(onClick = {
                    val cal = Calendar.getInstance().apply { dueTime?.let { timeInMillis = it } }
                    DatePickerDialog(context, { _, y, m, d -> cal.set(y, m, d); TimePickerDialog(context, { _, hh, mm ->
                        cal.set(Calendar.HOUR_OF_DAY, hh); cal.set(Calendar.MINUTE, mm); dueTime = cal.timeInMillis
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show() }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text(if (dueTime == null) "设置到期时间" else "到期 ${TimeUtil.formatHM(dueTime!!)}") }
            }
        }
    )
}

// ——— 密码保险箱 ———
@Composable
fun PasswordScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val items by Repo.password.observeAll().collectAsStateWithLifecycle(emptyList())
    val lockEnabled by Repo.settings.appLockEnabled.collectAsStateWithLifecycle(false)
    var unlocked by remember { mutableStateOf(!lockEnabled) }
    var pin by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    if (!unlocked) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("请输入应用锁口令（演示口令：0000）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s12))
            OutlinedTextField(pin, { pin = it }, label = { Text("口令") }, singleLine = true)
            Spacer(Modifier.height(Dimen.s12))
            Button(onClick = { if (pin == "0000") unlocked = true else Toast.makeText(context, "口令错误", Toast.LENGTH_SHORT).show() }) { Text("解锁") }
        }
        return
    }

    Scaffold(
        topBar = { AppTopBar("密码保险箱", showBack = true, onBack = { nav.popBackStack() }) },
        floatingActionButton = { AddFloating { showAdd = true } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(Dimen.s16)) {
            items(1) {
                AppCard(Modifier.padding(bottom = Dimen.s12)) {
                    MetricLine(icon = Icons.Filled.Lock, label = "密码条目", value = "${items.size} 条")
                }
            }
            items(items, key = { it.id }) { p ->
                var showDel by remember { mutableStateOf(false) }
                AppCard(Modifier.padding(bottom = Dimen.s12)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            val real = CryptoUtil.decrypt(p.passwordEnc)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText(p.title, real))
                            Toast.makeText(context, "已复制密码", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("${p.group} · ${p.title}", fontWeight = FontWeight.SemiBold)
                            Text("账号：${p.account}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("密码：••••••", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
                    }
                }
                if (showDel) ConfirmDeleteDialog(message = "确定删除密码条目「${p.title}」吗？", onDismiss = { showDel = false }) { scope.launch { Repo.password.delete(p) } }
            }
            if (items.isEmpty()) items(1) { EmptyState("还没有密码条目，点击 + 添加") }
        }
    }
    if (showAdd) PasswordAddDialog(onDismiss = { showAdd = false }, onSave = { g, t, a, pw, n ->
        scope.launch { Repo.password.insert(PasswordEntity(group = g, title = t, account = a, passwordEnc = CryptoUtil.encrypt(pw), note = n)) }
        showAdd = false
    })
}

@Composable
private fun PasswordAddDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var g by remember { mutableStateOf("网站") }
    var t by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = {
        if (t.isBlank() || pw.isBlank()) return@TextButton
        onSave(g, t, a, pw, n)
    }) { Text("保存") } }, dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text("新增密码条目") },
        text = {
            Column {
                Text("类型"); Row { listOf("网站","软件","银行卡").forEach { r -> FilterChip(selected = g==r, onClick={g=r}, label={Text(r)}, modifier = Modifier.padding(end=4.dp)) } }
                Spacer(Modifier.height(Dimen.s8))
                OutlinedTextField(t, { t = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(a, { a = it }, label = { Text("账号") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pw, { pw = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(n, { n = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
            }
        })
}

// ——— 随手笔记 ———
@Composable
fun NoteScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val items by Repo.note.observeAll().collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = { AppTopBar("随手笔记", showBack = true, onBack = { nav.popBackStack() }) },
        floatingActionButton = { AddFloating { showAdd = true } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(Dimen.s16)) {
            items(1) {
                AppCard(Modifier.padding(bottom = Dimen.s12)) {
                    MetricLine(icon = Icons.Filled.Note, label = "笔记数量", value = "${items.size} 篇")
                }
            }
            items(items, key = { it.id }) { n ->
                AppCard(Modifier.padding(bottom = Dimen.s12), onClick = { edit = n }) {
                    Text(n.title, fontWeight = FontWeight.SemiBold)
                    Text(n.content.take(60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("分类：${n.category} · ${TimeUtil.formatHM(n.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (items.isEmpty()) items(1) { EmptyState("还没有笔记，记一笔吧") }
        }
    }
    if (showAdd || edit != null) NoteEditDialog(initial = edit, onDismiss = { showAdd=false; edit=null }, onSave = { title, content, cat ->
        scope.launch {
            if (edit != null) Repo.note.update(edit!!.copy(title = title, content = content, category = cat, updatedAt = System.currentTimeMillis()))
            else Repo.note.insert(NoteEntity(title = title, content = content, category = cat))
            showAdd=false; edit=null
        }
    }, onDelete = { scope.launch { edit?.let { Repo.note.delete(it) }; showAdd=false; edit=null } })
}

@Composable
private fun NoteEditDialog(initial: NoteEntity?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "默认") }
    var showDel by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (title.isBlank()) return@TextButton; onSave(title, content, cat) }) { Text("保存") } },
        dismissButton = { Row { if (initial != null) TextButton(onClick = { showDel = true }) { Text("删除") }; TextButton(onDismiss) { Text("取消") } } },
        title = { Text(if (initial==null) "新建笔记" else "编辑笔记") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text("内容（支持多行）") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8)
                OutlinedTextField(cat, { cat = it }, label = { Text("分类") }, modifier = Modifier.fillMaxWidth())
            }
        }
    )
    if (showDel) ConfirmDeleteDialog(message = "确定删除这条笔记吗？", onDismiss = { showDel = false }) { onDelete() }
}

// ——— 纪念日倒计时 ———
@Composable
fun AnniversaryScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val items by Repo.anniversary.observeAll().collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AppTopBar("纪念日倒计时", showBack = true, onBack = { nav.popBackStack() }) },
        floatingActionButton = { AddFloating { showAdd = true } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(Dimen.s16)) {
            items(1) {
                AppCard(Modifier.padding(bottom = Dimen.s12)) {
                    MetricLine(icon = Icons.Filled.Celebration, label = "纪念日", value = "${items.size} 个", valueColor = MaterialTheme.colorScheme.primary)
                }
            }
            items(items, key = { it.id }) { a ->
                val days = TimeUtil.daysUntil(a.date)
                var showDel by remember { mutableStateOf(false) }
                AppCard(Modifier.padding(bottom = Dimen.s12)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.icon, fontSize = 28.dp.value.sp)
                        Spacer(Modifier.width(Dimen.s12))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.SemiBold)
                            Text(TimeUtil.formatDate(a.date) + if (a.repeatYearly) " · 每年" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val near = days in 0..7
                        Text((if (days >= 0) "还有 ${days} 天" else "已过 ${-days} 天"),
                            color = if (near) LocalExtraColors.current.warning else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
                    }
                }
                if (showDel) ConfirmDeleteDialog(message = "确定删除纪念日「${a.name}」吗？", onDismiss = { showDel = false }) { scope.launch { Repo.anniversary.delete(a); AlarmScheduler.cancel(context, a.id.toInt()) } }
            }
            if (items.isEmpty()) items(1) { EmptyState("添加重要的日子，准时提醒") }
        }
    }
    if (showAdd) AnniversaryAddDialog(onDismiss = { showAdd = false }, onSave = { name, date, yearly, icon ->
        scope.launch {
            val id = Repo.anniversary.insert(AnniversaryEntity(name = name, date = date, repeatYearly = yearly, icon = icon))
            if (date > System.currentTimeMillis()) AlarmScheduler.schedule(context, AlarmScheduler.Alarm(id.toInt(), date, "纪念日提醒", name))
            showAdd = false
        }
    })
}

@Composable
private fun AnniversaryAddDialog(onDismiss: () -> Unit, onSave: (String, Long, Boolean, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var yearly by remember { mutableStateOf(true) }
    var icon by remember { mutableStateOf("🎉") }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (name.isBlank()) return@TextButton; onSave(name, date, yearly, icon) }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text("新增纪念日") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(icon, { icon = it }, label = { Text("图标 emoji") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = date }
                    DatePickerDialog(context, { _, y, m, d -> cal.set(y,m,d); date = cal.timeInMillis }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text("日期：${TimeUtil.formatDate(date)}") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(yearly, { yearly = it }); Text("每年重复") }
            }
        })
}

// ——— 全局设置中心 ———
@Composable
fun SettingsScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")
    val fontScale by Repo.settings.fontScale.collectAsStateWithLifecycle(1.0f)
    val presetId by Repo.settings.themePreset.collectAsStateWithLifecycle("pink")
    val notify by Repo.settings.notificationEnabled.collectAsStateWithLifecycle(true)
    val sound by Repo.settings.soundEnabled.collectAsStateWithLifecycle(true)
    val budget by Repo.settings.monthlyBudget.collectAsStateWithLifecycle(2000.0)
    val lock by Repo.settings.appLockEnabled.collectAsStateWithLifecycle(false)

    // 备份导出/导入：用系统文件选择器（SAF）自定义位置，不再藏在本机私有目录
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = BackupUtil.exportToUri(context, uri)
            Toast.makeText(context, if (ok) "已保存到你选择的位置（如 Download 文件夹）" else "导出失败", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = BackupUtil.importFromUri(context, uri)
            Toast.makeText(context, if (ok) "恢复成功" else "恢复失败（文件格式不正确）", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("全局设置", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s8))
            Text("主题模式")
            Spacer(Modifier.height(Dimen.s4))
            Row { listOf("SYSTEM" to "跟随系统","LIGHT" to "浅色","DARK" to "深色").forEach { (v, t) ->
                FilterChip(selected = themeMode==v, onClick = { scope.launch { Repo.settings.setThemeMode(v) } }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp))
            } }
            Spacer(Modifier.height(Dimen.s12))
            Text("主题色彩（一键换肤，立即生效）")
            Spacer(Modifier.height(Dimen.s8))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePresets.forEach { p ->
                    val selected = presetId == p.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { scope.launch { Repo.settings.setThemePreset(p.id) } }) {
                        Surface(
                            shape = CircleShape, color = p.primaryLight,
                            modifier = Modifier.size(38.dp).then(if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        ) {}
                        Spacer(Modifier.height(3.dp))
                        Text(p.name, style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s12))
            Text("字体大小：${"%.2f".format(fontScale)}×")
            Slider(fontScale, { scope.launch { Repo.settings.setFontScale(it) } }, valueRange = 0.85f..1.3f)
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("通知提醒", modifier = Modifier.weight(1f))
                Switch(checked = notify, onCheckedChange = { scope.launch { Repo.settings.setNotificationEnabled(it) } })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("提示音效", modifier = Modifier.weight(1f))
                Switch(checked = sound, onCheckedChange = { scope.launch { Repo.settings.setSoundEnabled(it) } })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("应用锁", modifier = Modifier.weight(1f))
                Switch(checked = lock, onCheckedChange = { scope.launch { Repo.settings.setAppLockEnabled(it) } })
            }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("月度消费预算（元）")
            var bText by remember { mutableStateOf(formatBudget(budget)) }
            LaunchedEffect(budget) { bText = formatBudget(budget) }
            OutlinedTextField(bText, { bText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { bText.toDoubleOrNull()?.let { scope.launch { Repo.settings.setMonthlyBudget(it) }; Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() } }) { Text("保存预算") }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("数据备份与恢复", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.s4))
            Text("导出时由你选择保存位置，导入时由你选择备份文件，不再藏在本机私有目录。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("导出全部备份（自选位置）", onClick = {
                exportLauncher.launch("lifebench_backup_${System.currentTimeMillis()}.json")
            }, icon = Icons.Filled.FileDownload)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("导入备份恢复（自选文件）", onClick = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            }, icon = Icons.Filled.FileUpload)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("打开系统权限设置", onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }, icon = Icons.Filled.OpenInNew)
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}
