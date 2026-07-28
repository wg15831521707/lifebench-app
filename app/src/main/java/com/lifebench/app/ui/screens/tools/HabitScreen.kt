package com.lifebench.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lifebench.app.data.Repo
import com.lifebench.app.data.entity.HabitCheckInEntity
import com.lifebench.app.data.entity.HabitEntity
import com.lifebench.app.navigation.Routes
import com.lifebench.app.ui.components.*
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.HabitDotPalette
import com.lifebench.app.ui.theme.LocalExtraColors
import com.lifebench.app.ui.theme.habitDotColor
import com.lifebench.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ===== 习惯打卡模块：概览指标 + GitHub 风格年度热力图 + 习惯列表（今日一键打卡）=====
 */

@Composable
fun HabitScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val habits by Repo.habit.observeActiveHabits().collectAsStateWithLifecycle(emptyList())
    val allCheckIns by Repo.habit.observeAllCheckIns().collectAsStateWithLifecycle(emptyList())
    val heatRaw by Repo.habit.observeHeatmap().collectAsStateWithLifecycle(emptyList())
    val heatMap = remember(heatRaw) { heatRaw.associate { it.date to it.cnt } }
    val today = TimeUtil.dayKey()
    // 每个习惯的打卡日期集合，用于连续天数与「今日是否已打卡」计算
    val byHabit = remember(allCheckIns) { allCheckIns.groupBy { it.habitId }.mapValues { m -> m.value.map { it.date }.toSet() } }
    val todayChecked = allCheckIns.count { it.date == today }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AppTopBar("习惯打卡", showBack = true, onBack = { nav.popBackStack() }) },
        floatingActionButton = { AddFloating { showAdd = true } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Dimen.s12))
            Row(Modifier.padding(horizontal = Dimen.s16)) {
                AppCard(Modifier.weight(1f).padding(end = Dimen.s6)) {
                    MetricLine(icon = Icons.Filled.CheckCircle, label = "今日打卡", value = "$todayChecked / ${habits.size}",
                        valueColor = MaterialTheme.colorScheme.primary)
                }
                AppCard(Modifier.weight(1f).padding(start = Dimen.s6)) {
                    MetricLine(icon = Icons.Filled.Whatshot, label = "累计打卡", value = "${allCheckIns.size} 次",
                        valueColor = LocalExtraColors.current.success)
                }
            }
            Spacer(Modifier.height(Dimen.s12))
            AppCard(Modifier.padding(horizontal = Dimen.s16)) {
                Text("近一年打卡热力图", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Dimen.s8))
                HabitHeatmap(heatMap)
            }
            Spacer(Modifier.height(Dimen.s12))
            SectionTitle("  我的习惯")
            Spacer(Modifier.height(Dimen.s8))
            habits.forEach { h ->
                val dates = byHabit[h.id] ?: emptySet()
                HabitRow(
                    h = h,
                    checkedToday = today in dates,
                    streak = computeStreak(dates),
                    onToggleToday = { scope.launch {
                        if (today in dates) Repo.habit.deleteCheckIn(HabitCheckInEntity(habitId = h.id, date = today))
                        else Repo.habit.insertCheckIn(HabitCheckInEntity(habitId = h.id, date = today))
                    } },
                    onDelete = { scope.launch {
                        Repo.habit.deleteCheckInsByHabit(h.id)
                        Repo.habit.deleteHabit(h)
                    } }
                )
            }
            if (habits.isEmpty()) EmptyState("还没有习惯，点 + 添加一个想坚持的事")
            Spacer(Modifier.height(Dimen.s24))
        }
    }

    if (showAdd) HabitAddDialog(
        onDismiss = { showAdd = false },
        onSave = { name, icon, colorIndex ->
            scope.launch { Repo.habit.insertHabit(HabitEntity(name = name, icon = icon, colorIndex = colorIndex)); showAdd = false }
        }
    )
}

/**
 * 习惯行：彩色图标芯片（HabitDotPalette）+ 名称 + 连续天数 + 今日一键打卡。
 */
@Composable
private fun HabitRow(
    h: HabitEntity,
    checkedToday: Boolean,
    streak: Int,
    onToggleToday: () -> Unit,
    onDelete: () -> Unit
) {
    var showDel by remember { mutableStateOf(false) }
    val milestone = streakMilestone(streak)
    val progress = (streak.toFloat() / milestone).coerceIn(0f, 1f)
    AppCard(Modifier.padding(horizontal = Dimen.s16).padding(bottom = Dimen.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = habitDotColor(h.colorIndex).copy(alpha = 0.16f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(h.icon, fontSize = 20.sp) }
            }
            Spacer(Modifier.width(Dimen.s12))
            Column(Modifier.weight(1f)) {
                Text(h.name, fontWeight = FontWeight.SemiBold)
                Text("连续打卡 $streak 天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Dimen.s6))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(Dimen.s2))
                Text(
                    if (streak >= milestone) "已达成 $milestone 天连续 🎉"
                    else "再坚持 ${milestone - streak} 天解锁 $milestone 天",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleToday) {
                Icon(
                    if (checkedToday) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (checkedToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showDel = true }) { Icon(Icons.Filled.Delete, null) }
        }
    }
    if (showDel) ConfirmDeleteDialog(
        message = "确定删除习惯「${h.name}」吗？相关打卡记录会一并清除。",
        onDismiss = { showDel = false }
    ) { onDelete() }
}

/** 计算连续打卡天数：从今天（或昨天，若今天未打卡）往前连续计数。 */
private fun computeStreak(dates: Set<Long>): Int {
    if (dates.isEmpty()) return 0
    var cursor = TimeUtil.dayKey()
    if (cursor !in dates) {
        cursor = TimeUtil.dayKey(cursor - 86_400_000L)
        if (cursor !in dates) return 0
    }
    var streak = 0
    while (cursor in dates) {
        streak++
        cursor = TimeUtil.dayKey(cursor - 86_400_000L)
    }
    return streak
}

/** 连续天数里程碑：7→30→100→365，给人「下一目标」的节奏感（与首页一致）。 */
private fun streakMilestone(streak: Int): Int = when {
    streak < 7 -> 7
    streak < 30 -> 30
    streak < 100 -> 100
    streak < 365 -> 365
    else -> 365
}

@Composable
private fun HabitAddDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("✅") }
    var colorIndex by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { if (name.isBlank()) return@TextButton; onSave(name, icon, colorIndex) }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text("新建习惯") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("习惯名称") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimen.s8))
                OutlinedTextField(icon, { icon = it }, label = { Text("图标 emoji") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimen.s8))
                Text("主题色", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Dimen.s6))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitDotPalette.forEachIndexed { i, c ->
                        val sel = colorIndex == i
                        Surface(
                            shape = CircleShape, color = c,
                            modifier = Modifier.size(30.dp)
                                .clickable { colorIndex = i }
                                .then(if (sel) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        ) {}
                    }
                }
            }
        }
    )
}
