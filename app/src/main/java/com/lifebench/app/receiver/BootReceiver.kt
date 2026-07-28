package com.lifebench.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifebench.app.util.AlarmScheduler

/**
 * 开机/应用更新后恢复闹钟：设备重启或应用被覆盖安装后，
 * 系统会清掉所有 AlarmManager 定时任务，这里读取持久化的 alarms.json 重新调度未过期闹钟。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AlarmScheduler.rescheduleAll(context)
            }
        }
    }
}
