package com.lifebench.app

import android.app.Application
import com.lifebench.app.data.Repo
import com.lifebench.app.util.NotificationUtil

/**
 * 应用级 Application：在进程启动时初始化全局仓库（Room + DataStore），
 * 并创建通知渠道（Android 8+ 必须显式建渠道才能发通知）。
 */
class LifeBenchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        NotificationUtil.createChannels(this)
    }
}
