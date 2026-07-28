package com.lifebench.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebench.app.ui.theme.Dimen
import com.lifebench.app.util.TimeUtil
import java.util.Calendar

/**
 * GitHub 风格年度热力图：53 周 × 7 天，颜色越深代表当日打卡越多。
 * data: dayKey(当天 0 点 ms) -> 当日打卡总次数；组件自动取最近 53 周渲染，未来日期置灰。
 * 颜色基于品牌主色透明度分级，浅/深主题均清晰；点击单元格提示「日期 + 次数」。
 * 扩展交互：① 今天单元格高亮边框；② 点击左侧星期标签可筛选（强调）该星期全年打卡。
 */
@Composable
fun HabitHeatmap(
    data: Map<Long, Int>,
    modifier: Modifier = Modifier,
    onWeekdayClick: ((Int) -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val context = LocalContext.current
    val cell = 13.dp
    val gap = 3.dp
    val radius = 3.dp
    val monthRowH = 14.dp          // 月份标签行高，用于左侧星期标签竖向对齐
    val weekdayW = 18.dp           // 左侧星期标签列宽

    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val endSunday = cal.timeInMillis
    cal.add(Calendar.WEEK_OF_YEAR, -52)
    val startSunday = cal.timeInMillis
    val now = System.currentTimeMillis()
    val todayKey = TimeUtil.dayKey(now)

    val monthLabels = remember(startSunday) {
        val arr = arrayOfNulls<String>(53)
        val c = Calendar.getInstance()
        var prev = -1
        repeat(53) { w ->
            c.timeInMillis = startSunday + w * 7L * 86_400_000L
            val m = c.get(Calendar.MONTH)
            if (m != prev) { arr[w] = "${m + 1}月"; prev = m }
        }
        arr
    }

    // 星期筛选状态：null = 不过滤；0..6 = 周日..周六（与网格 d 对齐）
    var selectedWeekday by remember { mutableStateOf<Int?>(null) }
    val weekLabels = listOf("日", "一", "二", "三", "四", "五", "六")
    // 每个星期几在全年有打卡的天数（用于筛选汇总文案）
    val weekdayChecked = remember(data, startSunday) {
        val arr = IntArray(7)
        data.forEach { (dayKey, cnt) ->
            if (cnt > 0) {
                val d = ((dayKey - startSunday) / 86_400_000L % 7).toInt()
                if (d in 0..6) arr[d]++
            }
        }
        arr
    }

    Row(Modifier.then(modifier).fillMaxWidth()) {
        // 左侧星期标签列（固定，不随网格横向滚动）：点击可筛选该星期打卡
        Column(Modifier.padding(top = monthRowH + Dimen.s4)) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(7) { d ->
                    val sel = selectedWeekday == d
                    Box(
                        Modifier.height(cell).width(weekdayW)
                            .clickable { selectedWeekday = if (sel) null else d; onWeekdayClick?.invoke(d) },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            weekLabels[d], style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(Dimen.s4))
        Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            // 月份标签：仅在该周为某月首周时显示月份缩写，与下方列对齐
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(53) { w ->
                    Box(Modifier.width(cell).height(monthRowH), contentAlignment = Alignment.CenterStart) {
                        val ml = monthLabels[w]
                        if (ml != null) {
                            Text(ml, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s4))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(53) { w ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) { d ->
                            val dateMs = startSunday + (w * 7L + d) * 86_400_000L
                            val dayKey = TimeUtil.dayKey(dateMs)
                            val count = data[dayKey] ?: 0
                            val future = dateMs > now
                            val isToday = dayKey == todayKey
                            val level = when {
                                future -> -1
                                count <= 0 -> 0
                                count == 1 -> 1
                                count == 2 -> 2
                                count == 3 -> 3
                                else -> 4
                            }
                            val dimmed = selectedWeekday != null && d != selectedWeekday
                            Box(
                                Modifier.size(cell)
                                    .clip(RoundedCornerShape(radius))
                                    .background(heatColor(level, primary, surfaceVariant))
                                    .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(radius)) else Modifier)
                                    .then(if (dimmed) Modifier.alpha(0.22f) else Modifier)
                                    .clickable {
                                        val tip = if (future) "未来日期" else "${TimeUtil.formatDate(dateMs)} · 打卡 $count 次"
                                        Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimen.s8))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(5) { i -> Box(Modifier.size(cell).clip(RoundedCornerShape(radius)).background(heatColor(i - 1, primary, surfaceVariant))) }
                Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 星期筛选汇总：强调该星期全年打卡天数
            val wd = selectedWeekday
            if (wd != null) {
                Spacer(Modifier.height(Dimen.s4))
                Text(
                    "每周${weekLabels[wd]}：全年 ${weekdayChecked[wd]} 天打卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 热力图分级配色：基于品牌主色透明度，未来日期最浅置灰。 */
private fun heatColor(level: Int, primary: Color, surfaceVariant: Color): Color = when (level) {
    -1 -> surfaceVariant.copy(alpha = 0.12f)   // 未来（不可打卡）
    0 -> surfaceVariant.copy(alpha = 0.28f)    // 未打卡
    1 -> primary.copy(alpha = 0.30f)
    2 -> primary.copy(alpha = 0.55f)
    3 -> primary.copy(alpha = 0.80f)
    else -> primary
}
