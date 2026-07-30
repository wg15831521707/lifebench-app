package com.lifebench.app.util

import android.content.Context
import android.media.MediaPlayer
import com.lifebench.app.R

/**
 * 番茄钟背景白噪音播放器。
 * 播放 res/raw 中的真实音频资源（本地文件，离线循环），不再实时合成 PCM。
 * 后续新增白噪音：把 mp3 放进 res/raw（小写 + 下划线命名），并在 PRESET_RES 里追加一条映射即可。
 */
object WhiteNoisePlayer {

    private var player: MediaPlayer? = null
    @Volatile private var current = "无"

    /** 预设名 -> res/raw 资源 id。新增白噪音只需在此追加一项。 */
    private val PRESET_RES = mapOf(
        "火炉白噪音" to R.raw.fireplace,
    )

    fun currentPreset(): String = current

    @Synchronized
    fun play(context: Context, preset: String) {
        if (preset == "无") { stop(); return }
        val resId = PRESET_RES[preset] ?: run { stop(); return }
        if (current == preset && player?.isPlaying == true) return
        stop()
        current = preset
        try {
            player = MediaPlayer.create(context.applicationContext, resId)?.apply {
                isLooping = true
                setVolume(0.6f, 0.6f)
                start()
            } ?: run { current = "无"; return }
        } catch (e: Exception) {
            current = "无"
        }
    }

    @Synchronized
    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        current = "无"
    }
}
