package com.daozhang.yuyin.audio

import android.content.Context

/** 音色版：内置 Kokoro v1.1-zh 离线模型（100 个中文音色） */
object OfflineVoiceFactory {
    fun create(ctx: Context): OfflineVoiceEngine? = KokoroEngine(ctx)
}
