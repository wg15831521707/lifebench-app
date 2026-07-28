package com.lifebench.app.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * 时间与日期工具。所有"天"聚合统一用 dayKey（当天 0 点时间戳），避免时区与格式混乱。
 */
object TimeUtil {
    /** 取某时间戳所在天的 0 点（dayKey）。 */
    fun dayKey(ts: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 某天所在月份的第一天 dayKey / 最后一天 dayKey（含）。 */
    fun monthRange(anyTs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val c = Calendar.getInstance().apply { timeInMillis = anyTs }
        c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1); c.add(Calendar.DAY_OF_MONTH, -1)
        val end = c.timeInMillis
        return start to end
    }

    /** 睡眠时长（分钟），正确处理跨午夜：起床 < 入睡 视为跨天。 */
    fun sleepDurationMin(sleepTs: Long, wakeTs: Long): Int {
        val diff = if (wakeTs >= sleepTs) wakeTs - sleepTs else (wakeTs + 24L * 3600_000) - sleepTs
        return (diff / 60_000).toInt().coerceAtLeast(0)
    }

    fun formatDuration(min: Int): String {
        val h = min / 60; val m = min % 60
        return if (h > 0) "${h}小时${if (m > 0) m.toString() + "分" else ""}" else "${m}分"
    }

    fun formatClock(ts: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))
    fun formatDate(ts: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ts))
    fun formatMonthDay(ts: Long): String = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ts))
    fun formatHM(ts: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))

    /** 距离某日期(ms)还有多少天（含今天为 0）。 */
    fun daysUntil(targetTs: Long, now: Long = System.currentTimeMillis()): Int {
        val a = dayKey(targetTs); val b = dayKey(now)
        return ((a - b) / 86_400_000).toInt()
    }
}
