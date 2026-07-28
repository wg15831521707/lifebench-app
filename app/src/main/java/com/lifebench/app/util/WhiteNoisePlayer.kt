package com.lifebench.app.util

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 离线白噪音播放器（番茄钟背景音）。
 * 使用 AudioTrack 在子线程实时合成 PCM，无需任何 res/raw 音频资源文件。
 * 预设：雨声 / 森林 / 海浪 / 咖啡馆 / 无（停止）。
 */
object WhiteNoisePlayer {

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var playing = false
    @Volatile private var current = "无"

    fun currentPreset(): String = current

    @Synchronized
    fun play(preset: String) {
        if (preset == "无") { stop(); return }
        if (current == preset && playing) return
        stop()
        current = preset
        playing = true

        val sr = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, sr * 2) // 至少约 2 秒缓冲

        track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_MUSIC, sr,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufSize, AudioTrack.MODE_STREAM
            )
        }
        track?.play()
        thread = Thread({ generate(track!!, sr, preset) }, "white-noise").also { it.start() }
    }

    @Synchronized
    fun stop() {
        playing = false
        thread?.let { t ->
            try { t.join(1000) } catch (_: InterruptedException) {}
        }
        thread = null
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        current = "无"
    }

    private fun generate(track: AudioTrack, sr: Int, preset: String) {
        val buf = ShortArray(sr / 10) // 100ms 分块
        var brown = 0.0
        var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
        var t = 0.0 // 时间累加，用于 LFO / 鸟鸣
        var chirpLen = 0; var chirpMax = 1; var chirpFreq = 0.0
        var clinkLen = 0; var clinkMax = 1; var clinkFreq = 0.0
        val rnd = Random.Default

        while (playing) {
            for (i in buf.indices) {
                val white = rnd.nextDouble() * 2 - 1
                // pink noise (Paul Kellet 近似)
                b1 = 0.99886 * b1 + white * 0.0555179
                b2 = 0.99332 * b2 + white * 0.0750759
                b3 = 0.96900 * b3 + white * 0.1538520
                b4 = 0.86650 * b4 + white * 0.3104856
                b5 = 0.55000 * b5 + white * 0.5329522
                b6 = -0.7616 * b6 - white * 0.0168980
                val pink = (b1 + b2 + b3 + b4 + b5 + b6 + b6 + white * 0.5362) * 0.11
                // brown noise
                brown = (brown + 0.02 * white) / 1.02
                val brownN = brown * 3.5

                var sample = when (preset) {
                    "雨声" -> brownN * 0.6 + pink * 0.5 + white * 0.05
                    "森林" -> {
                        val lfo = 0.85 + 0.15 * sin(2 * PI * t * 0.3)
                        (brownN * 0.4 + pink * 0.6) * lfo
                    }
                    "海浪" -> {
                        val wave = 0.5 + 0.5 * sin(2 * PI * t * 0.08)
                        val swell = wave * wave
                        (brownN * 0.5 + pink * 0.5) * (0.25 + 0.75 * swell)
                    }
                    "咖啡馆" -> {
                        val lfo = 0.9 + 0.1 * sin(2 * PI * t * 0.5)
                        (brownN * 0.5 + pink * 0.4) * lfo
                    }
                    else -> 0.0
                }

                // 森林：偶发鸟鸣
                if (preset == "森林") {
                    if (chirpLen > 0) {
                        val env = sin(PI * (1 - chirpLen.toDouble() / chirpMax))
                        sample += 0.4 * env * sin(2 * PI * chirpFreq * t)
                        chirpLen--
                    } else if (rnd.nextDouble() < 0.0008) {
                        chirpFreq = 1500 + rnd.nextDouble() * 2500
                        chirpLen = (0.05 + rnd.nextDouble() * 0.12) * sr.toInt()
                        chirpMax = chirpLen
                    }
                }

                // 咖啡馆：偶发杯碟碰撞
                if (preset == "咖啡馆") {
                    if (clinkLen > 0) {
                        val env = kotlin.math.exp(-(clinkMax - clinkLen).toDouble() / (clinkMax * 0.5))
                        sample += 0.3 * env * sin(2 * PI * clinkFreq * t)
                        clinkLen--
                    } else if (rnd.nextDouble() < 0.0004) {
                        clinkFreq = 2000 + rnd.nextDouble() * 3000
                        clinkLen = (0.03 * sr).toInt()
                        clinkMax = clinkLen
                    }
                }

                sample = sample.coerceIn(-1.0, 1.0) * 0.6
                buf[i] = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
                t += 1.0 / sr
            }
            track.write(buf, 0, buf.size)
        }
    }
}
