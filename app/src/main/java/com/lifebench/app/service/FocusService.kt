package com.lifebench.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lifebench.app.MainActivity
import com.lifebench.app.R

/**
 * 番茄钟前台服务：专注进行时常驻一条通知，保证锁屏/后台时进程不被回收、计时持续。
 * 服务本身只负责"常驻通知"，计时逻辑仍在 FocusScreen 的协程中；退出或暂停时由页面停止服务。
 */
class FocusService : Service() {

    companion object {
        const val CHANNEL = "ch_focus_foreground"
        const val NOTIF_ID = 9001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL, "专注常驻", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("专注进行中")
            .setContentText("保持专注，锁屏也能继续计时")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }
}
