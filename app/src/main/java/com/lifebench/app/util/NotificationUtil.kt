package com.lifebench.app.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lifebench.app.receiver.AlarmReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 通知与精确闹钟调度。
 * - 通知渠道：专注 / 闹钟 / 待办纪念日 / 预算，满足 Android 8+ 必须建渠道的要求。
 * - 精确闹钟：用 AlarmManager.setExactAndAllowWhileIdle，保证待办/闹钟/纪念日准时（含 Doze）。
 * - 持久化：闹钟写入内部文件 alarms.json，开机后由 BootReceiver 重新调度，避免重启丢失。
 */
object NotificationUtil {
    const val CH_FOCUS = "ch_focus"
    const val CH_ALARM = "ch_alarm"
    const val CH_REMIND = "ch_remind"
    const val CH_BUDGET = "ch_budget"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(CH_FOCUS, "专注提醒", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_ALARM, "闹钟叫醒", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_REMIND, "待办与纪念日", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_BUDGET, "预算提醒", NotificationManager.IMPORTANCE_LOW),
            )
            channels.forEach { it.setBypassDnd(false) }
            mgr.createNotificationChannels(channels)
        }
    }

    fun notify(context: Context, channelId: String, id: Int, title: String, text: String, high: Boolean = false) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        mgr.notify(id, builder.build())
    }
}

/** 闹钟调度器：在 NotificationUtil 之外独立管理定时任务与其持久化。 */
object AlarmScheduler {
    private const val FILE = "alarms.json"

    data class Alarm(val id: Int, val triggerAt: Long, val title: String, val text: String)

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun readAll(context: Context): MutableList<Alarm> {
        val f = file(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val list = mutableListOf<Alarm>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Alarm(o.getInt("id"), o.getLong("triggerAt"), o.getString("title"), o.getString("text")))
            }
            list
        } catch (_: Exception) { mutableListOf() }
    }

    private fun writeAll(context: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("triggerAt", it.triggerAt)
                put("title", it.title); put("text", it.text)
            })
        }
        file(context).writeText(arr.toString())
    }

    /** 设定一个精确闹钟并持久化。 */
    fun schedule(context: Context, alarm: Alarm) {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", alarm.id); putExtra("title", alarm.title); putExtra("text", alarm.text)
        }
        val pi = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // API 31+ 需确认可精确调度；失败则退化为普通闹钟
        try {
            mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAt, pi)
        } catch (_: SecurityException) {
            mgr.set(AlarmManager.RTC_WAKEUP, alarm.triggerAt, pi)
        }
        val list = readAll(context).filter { it.id != alarm.id }.toMutableList()
        list.add(alarm); writeAll(context, list)
    }

    fun cancel(context: Context, id: Int) {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mgr.cancel(pi)
        writeAll(context, readAll(context).filter { it.id != id })
    }

    /** 开机后恢复所有未过期闹钟。 */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        readAll(context).filter { it.triggerAt > now }.forEach { schedule(context, it) }
    }
}
