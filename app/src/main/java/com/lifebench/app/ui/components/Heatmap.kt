package com.lifebench.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.util.TimeUtil
import java.util.Calendar

/**
 * 竖向堆叠月历热力图：展示最近 monthsBack+1 个月（含本月），每个月一个标准月历（周日为首列）。
 * - 月头显示「2026年8月」并标注「本月」，年份一目了然（修复：原横向条只显示"M月"无法分辨年份）。
 * - 当前月位于最顶部，页面打开即见本月（修复：原横向条初始滚动在最旧周）。
 * - 纯竖向滚动，无需左右拖动（修复：原 53 列横向条在手机上拖动不便）。
 * - 单元格沿用品牌主色分级，今日高亮边框，点击提示「日期 + 次数」。
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

    // 预生成月份与每日 dayKey（与打卡数据无关，仅在月份范围变化时重建）
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

    Column(modifier.then(modifier).fillMaxWidth()) {
        months.forEachIndexed { index, mo ->
            val isCurrent = index == 0
            // 月份标题（含年份）
            Row(
                Modifier.fillMaxWidth().padding(vertical = Dimen.s4),
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
            }
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
