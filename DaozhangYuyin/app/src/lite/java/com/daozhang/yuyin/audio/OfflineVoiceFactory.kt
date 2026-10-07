package com.daozhang.yuyin.audio

import android.content.Context

/** 标准版：不内置离线音色，只用系统语音引擎 */
object OfflineVoiceFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(ctx: Context): OfflineVoiceEngine? = null
}
