package com.daozhang.yuyin.data

import android.content.Context
import android.content.SharedPreferences
import com.daozhang.yuyin.core.AmountSpeller

/** 全局设置。量小、读写简单，用 SharedPreferences 即可。 */
class Settings(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 全局总开关：关闭时所有定时/循环任务到点直接跳过 */
    var globalEnabled: Boolean
        get() = sp.getBoolean("global_enabled", true)
        set(v) = sp.edit().putBoolean("global_enabled", v).apply()

    /** true = 闹钟音频流（静音模式也会响），false = 媒体音频流 */
    var useAlarmStream: Boolean
        get() = sp.getBoolean("alarm_stream", false)
        set(v) = sp.edit().putBoolean("alarm_stream", v).apply()

    /** 软件增益 0..6 dB */
    var gainDb: Float
        get() = sp.getFloat("gain_db", 0f)
        set(v) = sp.edit().putFloat("gain_db", v.coerceIn(0f, 6f)).apply()

    /** 语速 0.8..1.2 */
    var speechRate: Float
        get() = sp.getFloat("speech_rate", 1.0f)
        set(v) = sp.edit().putFloat("speech_rate", v.coerceIn(0.8f, 1.2f)).apply()

    /** 音调 0.8..1.2 */
    var pitch: Float
        get() = sp.getFloat("pitch", 1.0f)
        set(v) = sp.edit().putFloat("pitch", v.coerceIn(0.8f, 1.2f)).apply()

    /** 系统 TTS 中的中文音色名，空 = 引擎默认 */
    var voiceName: String
        get() = sp.getString("voice_name", "") ?: ""
        set(v) = sp.edit().putString("voice_name", v).apply()

    /** 专属提示音样式 0..2（可切换，不可关闭） */
    var chimeStyle: Int
        get() = sp.getInt("chime_style", 0)
        set(v) = sp.edit().putInt("chime_style", v.coerceIn(0, Chimes.COUNT - 1)).apply()

    var colloquial: Boolean
        get() = sp.getBoolean("colloquial", true)
        set(v) = sp.edit().putBoolean("colloquial", v).apply()

    var useLiang: Boolean
        get() = sp.getBoolean("use_liang", true)
        set(v) = sp.edit().putBoolean("use_liang", v).apply()

    var appendZheng: Boolean
        get() = sp.getBoolean("append_zheng", false)
        set(v) = sp.edit().putBoolean("append_zheng", v).apply()

    var lastPrefix: String
        get() = sp.getString("last_prefix", "收款") ?: "收款"
        set(v) = sp.edit().putString("last_prefix", v).apply()

    var lastSuffix: String
        get() = sp.getString("last_suffix", "") ?: ""
        set(v) = sp.edit().putString("last_suffix", v).apply()

    /** 是否已做过默认音色初始化（音色版默认用推荐的内置音色） */
    var voiceInitialized: Boolean
        get() = sp.getBoolean("voice_init", false)
        set(v) = sp.edit().putBoolean("voice_init", v).apply()

    var disclaimerAccepted: Boolean
        get() = sp.getBoolean("disclaimer_ok", false)
        set(v) = sp.edit().putBoolean("disclaimer_ok", v).apply()

    fun spellOptions() = AmountSpeller.Options(
        if (colloquial) AmountSpeller.Style.COLLOQUIAL else AmountSpeller.Style.STANDARD,
        useLiang,
        appendZheng,
    )

    object Chimes {
        const val COUNT = 3
        val NAMES = listOf("清脆双音", "柔和三音", "短促单音")
    }
}
