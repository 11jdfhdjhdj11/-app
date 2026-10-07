package com.daozhang.yuyin.audio

import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** 单声道 16-bit PCM 片段 */
class PcmClip(val sampleRate: Int, val samples: ShortArray) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate
}

object Pcm {

    /** 读取 TTS 输出的 WAV（16-bit PCM，单/双声道），统一转为单声道 */
    fun readWav(file: File): PcmClip {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size > 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "不是 WAV 文件" }
        var pos = 12
        var sampleRate = 22050
        var channels = 1
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    dataStart = body
                    // 部分引擎写入的长度不准，以文件实际长度为上限
                    dataLen = if (len <= 0 || body + len > bytes.size) bytes.size - body else len
                }
            }
            if (dataStart >= 0) break
            pos = body + len + (len and 1)
        }
        require(dataStart >= 0 && bits == 16) { "不支持的 WAV 格式" }
        val frames = dataLen / (2 * channels)
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            var acc = 0
            for (c in 0 until channels) acc += buf.getShort(dataStart + (i * channels + c) * 2)
            out[i] = (acc / channels).toShort()
        }
        return PcmClip(sampleRate, out)
    }

    fun writeWav(clip: PcmClip, out: OutputStream) {
        val dataLen = clip.samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(clip.sampleRate); putInt(clip.sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataLen)
        }
        out.write(header.array())
        out.write(toBytes(clip.samples))
    }

    fun toBytes(samples: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.asShortBuffer().put(samples)
        return bb.array()
    }

    fun silence(sampleRate: Int, ms: Int) = ShortArray(sampleRate * ms / 1000)

    fun concat(vararg parts: ShortArray): ShortArray {
        val result = ShortArray(parts.sumOf { it.size })
        var p = 0
        for (part in parts) { part.copyInto(result, p); p += part.size }
        return result
    }

    /** 软件增益（dB），带硬削波保护 */
    fun applyGain(samples: ShortArray, gainDb: Float): ShortArray {
        if (gainDb <= 0.01f) return samples
        val g = 10.0.pow(gainDb / 20.0)
        return ShortArray(samples.size) { i ->
            (samples[i] * g).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** 首尾 15ms 淡入淡出，避免拼接处爆音 */
    fun fade(samples: ShortArray, sampleRate: Int, ms: Int = 15): ShortArray {
        val n = (sampleRate * ms / 1000).coerceAtMost(samples.size / 2)
        val out = samples.copyOf()
        for (i in 0 until n) {
            val k = i.toFloat() / n
            out[i] = (out[i] * k).toInt().toShort()
            val j = out.size - 1 - i
            out[j] = (out[j] * k).toInt().toShort()
        }
        return out
    }

    /**
     * 本应用专属提示音，按采样率实时合成（不使用任何外部音频）。
     * 刻意与任何支付平台的提示音不同：用的是上行琶音 + 钢片琴式衰减。
     */
    fun chime(style: Int, sampleRate: Int): ShortArray {
        // 每个音：频率 Hz、时长 ms
        val notes: List<Pair<Double, Int>> = when (style) {
            1 -> listOf(784.0 to 130, 988.0 to 130, 1319.0 to 320)   // G5 B5 E6
            2 -> listOf(1568.0 to 180)                               // G6
            else -> listOf(1047.0 to 120, 1568.0 to 300)            // C6 G6
        }
        val parts = notes.map { (freq, ms) -> tone(freq, ms, sampleRate) }
        return concat(*parts.toTypedArray())
    }

    private fun tone(freq: Double, ms: Int, sampleRate: Int): ShortArray {
        val n = sampleRate * ms / 1000
        val attack = sampleRate * 5 / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val env = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-t * 9.0)
            val v = sin(2 * PI * freq * t) * 0.75 + sin(2 * PI * freq * 2 * t) * 0.18 + sin(2 * PI * freq * 3 * t) * 0.07
            (v * env * 0.55 * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
