package com.lifebench.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.util.TimeUtil
import java.util.Calendar

/**
 * 竖向堆叠月历热力图（折叠手风琴版）：展示最近 monthsBack+1 个月（含本月）。
 * - 当前月（列表首个）默认完整展开；历史月份折叠为单行摘要，点一下原地展开。
 * - 折叠态单行：月份标题 + 「打卡 X/Y 天」+ 一行迷你热度条，高度约 1 屏首屏即可见全部月份。
 * - 展开态沿用原竖向月历：完整星期表头 + 日期网格，今日高亮边框，点击提示「日期 + 次数」。
 * - 月头显示「2026年8月」并标注「本月」，年份一目了然（修复：原横向条只显示"M月"无法分辨年份）。
 * - 纯竖向滚动，无需左右拖动（修复：原 53 列横向条在手机上拖动不便）。
 */
@Composable
fun HabitHeatmap(
    data: Map<Long, Int>,
    modifier: Modifier = Modifier,
    monthsBack: Int = 11
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val context = LocalContext.current
    val cellGap = 4.dp
    val radius = 6.dp
    val todayKey = TimeUtil.dayKey()
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")

    // 预生成月份与每日 dayKey（仅月份范围变化时重建）
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

    // 折叠状态：index 0（当前月）默认展开，其余折叠
    val expanded = remember(monthsBack) {
        mutableStateListOf<Boolean>().apply {
            add(true)
            repeat(monthsBack) { add(false) }
        }
    }

    Column(modifier.fillMaxWidth()) {
        months.forEachIndexed { index, mo ->
            val isCurrent = index == 0
            val isExpanded = expanded[index]
            val checkedCount = mo.dayKeys.count { (data[it] ?: 0) > 0 }

            // 月份头（常驻，点击切换展开/收起）
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .clickable { expanded[index] = !expanded[index] }
                    .padding(vertical = Dimen.s8, horizontal = Dimen.s4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${mo.year}年${mo.month + 1}月",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isCurrent) {
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
                Text(
                    "打卡 $checkedCount/${mo.dayKeys.size} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
                val rotation = animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "chevron"
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation.value }
                )
            }

            // 折叠态：迷你热度条（单行，直观反映每日强度分布）
            if (!isExpanded) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Dimen.s4, horizontal = Dimen.s4),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    mo.dayKeys.forEach { dk ->
                        val count = data[dk] ?: 0
                        val level = when {
                            dk > todayKey -> -1
                            count <= 0 -> 0
                            count == 1 -> 1
                            count == 2 -> 2
                            count == 3 -> 3
                            else -> 4
                        }
                        Box(
                            Modifier.height(10.dp).weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(heatColor(level, primary, surfaceVariant))
                        )
                    }
                }
            }

            // 展开态：完整月历（带展开/收起动画）
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
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
                    val total = mo.firstDow + mo.dayKeys.size
                    val rows = (total + 6) / 7
                    Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                        repeat(rows) { r ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                                repeat(7) { c ->
                                    val pos = r * 7 + c
                                    if (pos < mo.firstDow || pos >= mo.firstDow + mo.dayKeys.size) {
                                        Box(Modifier.weight(1f).aspectRatio(1f)) {}
                                    } else {
                                        val dayKey = mo.dayKeys[pos - mo.firstDow]
                                        val count = data[dayKey] ?: 0
                                        val future = dayKey > todayKey
                                        val isToday = dayKey == todayKey
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
                                                .then(if (isToday) Modifier.border(1.5.dp, primary, RoundedCornerShape(radius)) else Modifier)
                                                .clickable {
                                                    val tip = if (future) "未来日期"
                                                    else "${TimeUtil.formatDate(dayKey)} · 打卡 $count 次"
                                                    Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Dimen.s12))
                }
            }

            // 月份之间间距
            Spacer(Modifier.height(Dimen.s12))
        }

        // 图例
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cellGap)
        ) {
            Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            repeat(5) { i ->
                Box(
                    Modifier.size(14.dp).clip(RoundedCornerShape(radius))
                        .background(heatColor(i - 1, primary, surfaceVariant))
                )
            }
            Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
