package com.lifebench.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebench.app.data.entity.HabitCheckInEntity
import com.lifebench.app.data.entity.HabitEntity
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.ui.theme.habitDotColor
import com.lifebench.app.util.TimeUtil
import java.util.Calendar

/**
 * 单月热力图（上下滑动切月版）：同一时刻只显示一个月份，刷动或点箭头切换。
 * - 修复「太长」：不再平铺 12 个月，固定只显示当前月，整卡约 1 屏。
 * - 修复「看不出格子」：每个日期格加发丝级边框（outlineVariant），空格填充明显浅于背景，网格始终清晰。
 * - 修复「不知打卡了哪个习惯」：① 常驻「习惯图例」（每习惯色块+emoji+名）；② 点击已打卡日弹窗列出当天各习惯。
 * - 交互：上滑/下箭头看更早月份，下滑/上箭头看更新月份；月度切换带竖向滑入滑出动画。
 * - 切月手势与整页滚动互不抢占：拖拽切月期间通过 nestedScroll 拦截，外层 verticalScroll 不跟着滚，松手后恢复。
 * - 沿用 heatColor 强度色阶、今日高亮主色边框、未来日期置灰不可点、纯本地无网络。
 */
@Composable
fun HabitHeatmap(
    data: Map<Long, Int>,
    habits: List<HabitEntity> = emptyList(),
    checkInsByDay: Map<Long, List<HabitCheckInEntity>> = emptyMap(),
    modifier: Modifier = Modifier,
    monthsBack: Int = 11
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val todayKey = TimeUtil.dayKey()
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
    val cellGap = 4.dp
    val radius = 6.dp

    val months = remember(monthsBack) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val list = mutableListOf<MonthModel>()
        repeat(monthsBack + 1) {
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=周日
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val dayKeys = (1..daysInMonth).map { day ->
                cal.set(Calendar.DAY_OF_MONTH, day)
                TimeUtil.dayKey(cal.timeInMillis)
            }
            list.add(MonthModel(year, month, firstDow, dayKeys))
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, -1)
        }
        list
    }

    var sel by remember { mutableStateOf(0) }
    var sheetDay by remember { mutableStateOf<Long?>(null) }
    // 切月拖拽进行中标记：拖拽期间吞掉竖向滚动，外层整页 verticalScroll 不抢滚动
    var isDraggingMonth by remember { mutableStateOf(false) }
    val monthSwipeNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (isDraggingMonth && source == NestedScrollSource.Drag) available else Offset.Zero
            }
        }
    }
    val habitById = remember(habits) { habits.associateBy { it.id } }

    Column(modifier.fillMaxWidth()) {
        // 月份头：标题 + 本月标签 + 上下箭头（可发现 / 无障碍入口）
        val mo = months[sel]
        Row(
            Modifier.fillMaxWidth().padding(vertical = Dimen.s4, horizontal = Dimen.s4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${mo.year}年${mo.month + 1}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sel == 0) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = primary.copy(alpha = 0.15f)) {
                    Text(
                        "本月",
                        fontSize = 10.sp,
                        color = primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { if (sel > 0) sel-- },
                enabled = sel > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, "查看更新的月份", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = { if (sel < monthsBack) sel++ },
                enabled = sel < monthsBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, "查看更早的月份", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 日历区：上下拖拽切换月份（上滑看更早、下滑看更新）；拖拽期间拦截外层整页竖向滚动
        var dragAccum by remember { mutableStateOf(0f) }
        Box(
            Modifier.fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dragAccum += it },
                    onDragStarted = { isDraggingMonth = true },
                    onDragStopped = { velocity ->
                        if (velocity < -300f) sel = (sel + 1).coerceAtMost(monthsBack)   // 上滑看更早
                        else if (velocity > 300f) sel = (sel - 1).coerceAtLeast(0)        // 下滑看更新
                        isDraggingMonth = false
                    }
                )
                .nestedScroll(monthSwipeNestedScroll)
        ) {
            AnimatedContent(
                targetState = sel,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInVertically { h -> dir * h } + fadeIn()) togetherWith
                        (slideOutVertically { h -> -dir * h } + fadeOut())
                },
                label = "monthSwitch"
            ) { idx ->
                MonthCalendar(
                    month = months[idx],
                    data = data,
                    checkInsByDay = checkInsByDay,
                    primary = primary,
                    surfaceVariant = surfaceVariant,
                    outlineVariant = outlineVariant,
                    cellGap = cellGap,
                    radius = radius,
                    todayKey = todayKey,
                    weekLabels = weekLabels,
                    onDayClick = { sheetDay = it }
                )
            }
        }

        Spacer(Modifier.height(Dimen.s12))

        // 习惯图例：让用户知道「哪个颜色对应哪个习惯」
        if (habits.isNotEmpty()) {
            HabitLegend(habits = habits)
            Spacer(Modifier.height(Dimen.s8))
        }

        // 强度图例（少 → 多）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cellGap)
        ) {
            Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            repeat(5) { i ->
                Box(
                    Modifier.size(14.dp).clip(RoundedCornerShape(radius))
                        .background(heatColor(i - 1, primary, surfaceVariant))
                        .border(1.dp, outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(radius))
                )
            }
            Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // 当日明细：点击已打卡日，列出当天各习惯（色块 + emoji + 名）
    sheetDay?.let { dayKey ->
        val items = checkInsByDay[dayKey].orEmpty()
        AlertDialog(
            onDismissRequest = { sheetDay = null },
            confirmButton = { TextButton(onClick = { sheetDay = null }) { Text("关闭") } },
            title = { Text("${TimeUtil.formatDate(dayKey)} · 打卡 ${items.size} 个习惯") },
            text = {
                if (items.isEmpty()) {
                    Text("这一天没有打卡记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column {
                        items.forEach { ci ->
                            habitById[ci.habitId]?.let { h ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(14.dp)
                                            .background(habitDotColor(h.colorIndex), CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(h.icon, fontSize = 16.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(h.name, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        )
    }
}

/** 单月日历网格：星期表头 + 日期格（带边框）+ 点击看明细。 */
@Composable
private fun MonthCalendar(
    month: MonthModel,
    data: Map<Long, Int>,
    checkInsByDay: Map<Long, List<HabitCheckInEntity>>,
    primary: Color,
    surfaceVariant: Color,
    outlineVariant: Color,
    cellGap: Dp,
    radius: Dp,
    todayKey: Long,
    weekLabels: List<String>,
    onDayClick: (Long) -> Unit
) {
    val context = LocalContext.current
    Column {
        // 星期表头
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellGap)) {
            weekLabels.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(cellGap))
        // 日期网格
        val total = month.firstDow + month.dayKeys.size
        val rows = (total + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
            repeat(rows) { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                    repeat(7) { c ->
                        val pos = r * 7 + c
                        if (pos < month.firstDow || pos >= month.firstDow + month.dayKeys.size) {
                            Box(Modifier.weight(1f).aspectRatio(1f)) {}
                        } else {
                            val dayKey = month.dayKeys[pos - month.firstDow]
                            val count = data[dayKey] ?: 0
                            val future = dayKey > todayKey
                            val isToday = dayKey == todayKey
                            val hasCheckIn = checkInsByDay[dayKey]?.isNotEmpty() == true
                            val level = when {
                                future -> -1
                                count <= 0 -> 0
                                count == 1 -> 1
                                count == 2 -> 2
                                count == 3 -> 3
                                else -> 4
                            }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .clip(RoundedCornerShape(radius))
                                    .background(heatColor(level, primary, surfaceVariant))
                                    .border(1.dp, outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(radius))
                                    .then(if (isToday) Modifier.border(1.5.dp, primary, RoundedCornerShape(radius)) else Modifier)
                                    .clickable {
                                        when {
                                            future -> Toast.makeText(context, "未来日期", Toast.LENGTH_SHORT).show()
                                            hasCheckIn -> onDayClick(dayKey)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 习惯图例：每个习惯一行色卡（专属色 + emoji + 名），直观对应打卡方格。 */
@Composable
private fun HabitLegend(habits: List<HabitEntity>) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "习惯图例",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            habits.forEach { h ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(habitDotColor(h.colorIndex).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(Modifier.size(12.dp).background(habitDotColor(h.colorIndex), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(h.icon, fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(h.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private data class MonthModel(
    val year: Int,
    val month: Int,       // 0-11
    val firstDow: Int,    // 1 号是周几，0=周日
    val dayKeys: List<Long>
)

/** 热力图分级配色：基于品牌主色透明度，未来日期最浅置灰。 */
private fun heatColor(level: Int, primary: Color, surfaceVariant: Color): Color = when (level) {
    -1 -> surfaceVariant.copy(alpha = 0.12f)   // 未来（不可打卡）
    0 -> surfaceVariant.copy(alpha = 0.28f)    // 未打卡
    1 -> primary.copy(alpha = 0.30f)
    2 -> primary.copy(alpha = 0.55f)
    3 -> primary.copy(alpha = 0.80f)
    else -> primary
}
