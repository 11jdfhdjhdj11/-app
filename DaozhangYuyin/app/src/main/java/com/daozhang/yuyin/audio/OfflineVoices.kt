package com.daozhang.yuyin.audio

import android.content.Context

/** 内置离线音色（音色版 = Kokoro；标准版没有） */
data class OfflineVoice(
    /** 模型里的说话人编号 */
    val sid: Int,
    /** 原始编号，如 zf_001 */
    val code: String,
    val female: Boolean,
    /** 显示名，如“女声 01” */
    val label: String,
    /** 推荐：金额读法自动校验通过且音量、语速适中 */
    val recommended: Boolean,
    /** 金额读法自动校验是否通过 */
    val verified: Boolean,
) {
    val key: String get() = KEY_PREFIX + sid

    companion object {
        const val KEY_PREFIX = "kokoro:"
        fun sidOf(key: String): Int? =
            if (key.startsWith(KEY_PREFIX)) key.removePrefix(KEY_PREFIX).toIntOrNull() else null
    }
}

interface OfflineVoiceEngine {
    val voices: List<OfflineVoice>

    /** 合成为 PCM。首次调用会加载模型（数秒）。阻塞，后台线程调用。 */
    fun synthesize(sid: Int, text: String, speed: Float): PcmClip
}

object OfflineVoices {
    @Volatile private var engine: OfflineVoiceEngine? = null
    @Volatile private var resolved = false

    /** 标准版返回 null */
    fun engine(ctx: Context): OfflineVoiceEngine? {
        if (!resolved) synchronized(this) {
            if (!resolved) {
                engine = OfflineVoiceFactory.create(ctx.applicationContext)
                resolved = true
            }
        }
        return engine
    }

    fun available(ctx: Context) = engine(ctx) != null
}
