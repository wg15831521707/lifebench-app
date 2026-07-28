package com.lifebench.app.ui.screens.tools

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.WeatherDemo
import com.lifebench.app.data.entity.*
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.util.AlarmScheduler
import com.lifebench.app.util.BackupUtil
import com.lifebench.app.util.CalcUtil
import com.lifebench.app.util.CryptoUtil
import com.lifebench.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.util.*

import android.Manifest
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lifebench.app.data.remote.WeatherData
import com.lifebench.app.data.remote.WeatherRepository

/**
 * ===== 生活工具专区：枢纽 + 待办/步数/密码/笔记/纪念日/天气/设置 =====
 */

// ——— 工具枢纽 ———
@Composable
fun ToolsHubScreen(nav: NavController) {
    val entries = listOf(
        ToolEntry("待办备忘录", Icons.Filled.Checklist, Routes.TODO),
        ToolEntry("每日步数", Icons.Filled.DirectionsWalk, Routes.STEPS),
        ToolEntry("密码保险箱", Icons.Filled.Lock, Routes.PASSWORD),
        ToolEntry("随手笔记", Icons.Filled.Note, Routes.NOTE),
        ToolEntry("纪念日倒计时", Icons.Filled.Celebration, Routes.ANNIVERSARY),
        ToolEntry("天气预报", Icons.Filled.Cloud, Routes.WEATHER),
        ToolEntry("全局设置", Icons.Filled.Settings, Routes.SETTINGS),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("生活工具")
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

private data class ToolEntry(val label: String, val icon: ImageVector, val route: String)

// ——— 待办备忘录 ———
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
                items(list, key = { it.id }) { item ->
                    AppCard(Modifier.padding(bottom = Dimen.s12)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.done, onCheckedChange = {
                                scope.launch { Repo.todo.update(item.copy(done = it, archived = it)) }
                            })
                            Column(Modifier.weight(1f).clickable { editItem = item }) {
                                Text(item.title, fontWeight = FontWeight.SemiBold,
                                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                if (item.dueTime != null)
                                    Text("到期 ${TimeUtil.formatHM(item.dueTime)} · ${item.repeatMode}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val dot = when (item.priority) { 2 -> MaterialTheme.colorScheme.error; 1 -> LocalExtraColors.current.warning; else -> MaterialTheme.colorScheme.outline }
                            Box(Modifier.size(10.dp).background(dot, CircleShape))
                            Spacer(Modifier.width(Dimen.s4))
                            IconButton(onClick = { scope.launch { Repo.todo.delete(item); AlarmScheduler.cancel(context, item.id.toInt()) } }) {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (list.isEmpty()) item { EmptyState(if (tab == 0) "暂无进行中的待办" else "暂无已完成事项") }
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

@Composable
private fun TodoEditDialog(initial: TodoEntity?, onDismiss: () -> Unit, onSave: (TodoEntity) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var priority by remember { mutableStateOf(initial?.priority ?: 1) }
    var dueTime by remember { mutableStateOf(initial?.dueTime) }
    var repeat by remember { mutableStateOf(initial?.repeatMode ?: "永不") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            if (title.isBlank()) return@TextButton
            onSave(initial?.copy(title = title, note = note, priority = priority, dueTime = dueTime, repeatMode = repeat)
                ?: TodoEntity(title = title, note = note, priority = priority, dueTime = dueTime, repeatMode = repeat))
        }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text(if (initial == null) "新建待办" else "编辑待办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimen.s8))
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimen.s8))
                Text("优先级")
                Row { listOf("低" to 0, "中" to 1, "高" to 2).forEach { (t, v) ->
                    FilterChip(selected = priority == v, onClick = { priority = v }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp))
                } }
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

// ——— 每日步数 ———
@Composable
fun StepsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val week by Repo.step.observeWeek().collectAsStateWithLifecycle(emptyList())
    var todaySteps by remember { mutableStateOf(0) }
    var sensorAvailable by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent?) {
                val total = e?.values?.firstOrNull()?.toLong() ?: return
                scope.launch {
                    var base = Repo.settings.stepBaseline.first()
                    if (base < 0) { base = total; Repo.settings.setStepBaseline(base) }
                    val steps = (total - base).coerceAtLeast(0).toInt()
                    todaySteps = steps
                    Repo.step.upsert(StepEntity(TimeUtil.dayKey(), steps, CalcUtil.stepCalories(steps)))
                }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        else sensorAvailable = false
        onDispose { sm?.unregisterListener(listener) }
    }

    val data = week.map { TimeUtil.formatMonthDay(it.date) to it.steps.toDouble() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("每日步数", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("今日步数", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$todaySteps", style = MaterialTheme.typography.displayMedium)
            Text("消耗约 ${CalcUtil.stepCalories(todaySteps)} kcal", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!sensorAvailable) {
            Spacer(Modifier.height(Dimen.s8))
            Text("  本设备无计步传感器，可在设置中手动记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Dimen.s16))
        }
        Spacer(Modifier.height(Dimen.s16))
        SectionTitle("本周步数")
        Spacer(Modifier.height(Dimen.s8))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) { BarChart(data, MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(Dimen.s24))
    }
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
            items(items, key = { it.id }) { p ->
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
                        IconButton(onClick = { scope.launch { Repo.password.delete(p) } }) { Icon(Icons.Filled.Delete, null) }
                    }
                }
            }
            if (items.isEmpty()) item { EmptyState("还没有密码条目，点击 + 添加") }
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
            items(items, key = { it.id }) { n ->
                AppCard(Modifier.padding(bottom = Dimen.s12), onClick = { edit = n }) {
                    Text(n.title, fontWeight = FontWeight.SemiBold)
                    Text(n.content.take(60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("分类：${n.category} · ${TimeUtil.formatHM(n.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (items.isEmpty()) item { EmptyState("还没有笔记，记一笔吧") }
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
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (title.isBlank()) return@TextButton; onSave(title, content, cat) }) { Text("保存") } },
        dismissButton = { Row { if (initial != null) TextButton(onDelete) { Text("删除") }; TextButton(onDismiss) { Text("取消") } } },
        title = { Text(if (initial==null) "新建笔记" else "编辑笔记") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text("内容（支持多行）") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 8)
                OutlinedTextField(cat, { cat = it }, label = { Text("分类") }, modifier = Modifier.fillMaxWidth())
            }
        })
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
            items(items, key = { it.id }) { a ->
                val days = TimeUtil.daysUntil(a.date)
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
                        IconButton(onClick = { scope.launch { Repo.anniversary.delete(a); AlarmScheduler.cancel(context, a.id.toInt()) } }) { Icon(Icons.Filled.Delete, null) }
                    }
                }
            }
            if (items.isEmpty()) item { EmptyState("添加重要的日子，准时提醒") }
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

// ——— 天气预报（真实 API：Open-Meteo，无需 Key；断网回退本地缓存/演示）———
@Composable
fun WeatherScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<WeatherData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var source by remember { mutableStateOf("") }
    val popular = listOf("北京", "上海", "广州", "成都", "深圳", "杭州", "武汉", "西安")

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (loc != null) scope.launch { refresh(lat = loc.latitude, lon = loc.longitude, city = "当前位置") }
            else scope.launch { refresh(city = popular.first()) }
        } else scope.launch { refresh(city = popular.first()) }
    }

    suspend fun refresh(city: String? = null, lat: Double? = null, lon: Double? = null) {
        loading = true
        val cached = WeatherRepository.loadCached(context)
        if (cached != null) { data = cached; source = "离线缓存" }
        val r = runCatching { WeatherRepository.load(context, city = city, lat = lat, lon = lon) }.getOrNull()
        if (r != null) { data = r.data; source = r.source }
        loading = false
    }

    LaunchedEffect(Unit) { refresh(city = popular.first()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("天气预报", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        Row(Modifier.padding(horizontal = Dimen.s16), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("输入城市，如 北京") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Dimen.s8))
            Button(onClick = { scope.launch { refresh(city = input.ifBlank { popular.first() }) } }) { Text("查询") }
            IconButton(onClick = { locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "定位")
            }
        }
        Spacer(Modifier.height(Dimen.s8))
        Row(Modifier.padding(horizontal = Dimen.s16), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            popular.forEach { c -> FilterChip(selected = false, onClick = { input = c; scope.launch { refresh(city = c) } }, label = { Text(c) }) }
        }
        Spacer(Modifier.height(Dimen.s8))
        if (source.isNotEmpty()) {
            val warn = source.contains("缓存") || source.contains("演示")
            Row(Modifier.padding(horizontal = Dimen.s16)) {
                AssistChip(
                    onClick = {},
                    label = { Text("数据来源：$source", color = if (warn) LocalExtraColors.current.warning else MaterialTheme.colorScheme.primary) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (warn) LocalExtraColors.current.warning.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            Spacer(Modifier.height(Dimen.s8))
        }
        if (loading && data == null) {
            Box(Modifier.fillMaxWidth().padding(Dimen.s32), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        data?.let { w ->
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(WeatherDemo.iconOf(w.now.condition), fontSize = 48.dp.value.sp)
                    Spacer(Modifier.width(Dimen.s12))
                    Column {
                        Text("${w.now.temp}°", style = MaterialTheme.typography.displayMedium)
                        Text("${w.now.city} · ${w.now.condition} · 体感${w.now.feel}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Dimen.s8))
                Text("空气质量 AQI ${w.now.aqi}（${w.now.aqiLevel}） · 紫外线 ${w.now.uv} · 湿度 ${w.now.humidity}% · ${w.now.wind}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Dimen.s8))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimen.s8)) {
                    w.now.indexes.forEach { (k, v) -> AssistChip(onClick = {}, label = { Text("$k：$v") }) }
                }
            }
            Spacer(Modifier.height(Dimen.s16))
            SectionTitle("未来 7 天")
            Spacer(Modifier.height(Dimen.s8))
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimen.s8)) {
                    w.days.forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(d.label, style = MaterialTheme.typography.bodySmall)
                            Text(WeatherDemo.iconOf(d.condition), fontSize = 20.dp.value.sp)
                            Text("${d.high}°", fontWeight = FontWeight.SemiBold)
                            Text("${d.low}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s16))
            SectionTitle("逐小时")
            Spacer(Modifier.height(Dimen.s8))
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimen.s12)) {
                    w.hours.forEach { h ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(h.label, style = MaterialTheme.typography.bodySmall)
                            Text("🌫️", fontSize = 18.dp.value.sp)
                            Text("${h.temp}°", fontWeight = FontWeight.SemiBold)
                            Text("${h.pop}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(h.wind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s24))
        }
    }
}

// ——— 全局设置中心 ———
@Composable
fun SettingsScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeMode by Repo.settings.themeMode.collectAsStateWithLifecycle("SYSTEM")
    val fontScale by Repo.settings.fontScale.collectAsStateWithLifecycle(1.0f)
    val notify by Repo.settings.notificationEnabled.collectAsStateWithLifecycle(true)
    val sound by Repo.settings.soundEnabled.collectAsStateWithLifecycle(true)
    val budget by Repo.settings.monthlyBudget.collectAsStateWithLifecycle(2000.0)
    val lock by Repo.settings.appLockEnabled.collectAsStateWithLifecycle(false)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppTopBar("全局设置", showBack = true, onBack = { nav.popBackStack() })
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("外观")
            Spacer(Modifier.height(Dimen.s8))
            Text("主题模式")
            Row { listOf("SYSTEM" to "跟随系统","LIGHT" to "浅色","DARK" to "深色").forEach { (v, t) ->
                FilterChip(selected = themeMode==v, onClick = { scope.launch { Repo.settings.setThemeMode(v) } }, label = { Text(t) }, modifier = Modifier.padding(end = 4.dp))
            } }
            Spacer(Modifier.height(Dimen.s8))
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
            var bText by remember { mutableStateOf(budget.toString()) }
            OutlinedTextField(bText, { bText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { bText.toDoubleOrNull()?.let { scope.launch { Repo.settings.setMonthlyBudget(it) }; Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() } }) { Text("保存预算") }
        }
        Spacer(Modifier.height(Dimen.s12))
        AppCard(Modifier.padding(horizontal = Dimen.s16)) {
            Text("数据备份与权限")
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("导出全部备份", onClick = { scope.launch { val p = BackupUtil.exportAll(context); Toast.makeText(context, "已导出：$p", Toast.LENGTH_LONG).show() } }, icon = Icons.Filled.FileDownload)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("导入备份恢复", onClick = { scope.launch {
                context.filesDir.listFiles { _, n -> n.endsWith(".json") }?.sortedByDescending { it.lastModified() }?.firstOrNull()?.let { f ->
                    val r = BackupUtil.importAll(context, f); Toast.makeText(context, if (r) "恢复成功" else "恢复失败", Toast.LENGTH_SHORT).show()
                } ?: Toast.makeText(context, "未找到备份文件", Toast.LENGTH_SHORT).show()
            } }, icon = Icons.Filled.FileUpload)
            Spacer(Modifier.height(Dimen.s8))
            PrimaryButton("打开系统权限设置", onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }, icon = Icons.Filled.OpenInNew)
        }
        Spacer(Modifier.height(Dimen.s24))
    }
}
