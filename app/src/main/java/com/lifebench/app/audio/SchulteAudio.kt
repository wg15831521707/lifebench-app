package com.lifebench.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.lifebench.app.R

/**
 * 舒尔特方格 SFX 播放器（SoundPool，低延迟，适合短促 SFX）
 *
 * 5 个音轨（res/raw/）：
 *  - schulte_tick     倒计时（3,2,1）
 *  - schulte_go       倒计时归零后"开始"
 *  - schulte_correct  点中下一个数字
 *  - schulte_wrong    点错（无论是否中断）
 *  - schulte_complete 自然完成 1..n²
 *
 * 用法：在 Composable 内 `remember { SchulteAudio(ctx) }`，
 * 在 `DisposableEffect(Unit) { onDispose { it.release() } }` 中释放。
 */
class SchulteAudio(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4) // 允许倒计时尾音与正确音效短暂叠播
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME) // 走游戏媒体流
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val tickId = pool.load(context, R.raw.schulte_tick, 1)
    private val goId = pool.load(context, R.raw.schulte_go, 1)
    private val correctId = pool.load(context, R.raw.schulte_correct, 1)
    private val wrongId = pool.load(context, R.raw.schulte_wrong, 1)
    private val completeId = pool.load(context, R.raw.schulte_complete, 1)

    /** SoundPool.load 是异步的，未加载完成时 play 会被忽略；这里跟踪已加载 sampleId。 */
    private val loaded = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, _ -> loaded.add(sampleId) }
    }

    fun tick() = play(tickId, 0.7f)
    fun go() = play(goId, 0.85f)
    fun correct() = play(correctId, 0.85f)
    fun wrong() = play(wrongId, 0.9f)
    fun complete() = play(completeId, 0.95f)

    private fun play(id: Int, vol: Float) {
        if (id !in loaded) return // 资源未就绪：静默忽略（避免首点没声音）
        pool.play(id, vol, vol, 1, 0, 1f)
    }

    fun release() = pool.release()
}
