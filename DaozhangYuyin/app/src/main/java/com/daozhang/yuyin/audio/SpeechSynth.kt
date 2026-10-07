package com.daozhang.yuyin.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 系统 TTS 包装：把文字合成为 WAV 文件后再交给我们自己的播放管线，
 * 这样才能加专属提示音、做软件增益、导出音频，并保证各机型行为一致。
 *
 * 所有阻塞方法只能在后台线程调用。
 */
class SpeechSynth(context: Context) {
    private val app = context.applicationContext
    private val ready = CountDownLatch(1)
    @Volatile private var ok = false
    private val pending = HashMap<String, CountDownLatch>()
    private val failed = HashSet<String>()

    private val tts: TextToSpeech = TextToSpeech(app) { status ->
        ok = status == TextToSpeech.SUCCESS
        ready.countDown()
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) = finish(id, false)
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) = finish(id, true)
            override fun onError(id: String, errorCode: Int) = finish(id, true)
        })
    }

    private fun finish(id: String, error: Boolean) {
        synchronized(pending) {
            if (error) failed += id
            pending.remove(id)?.countDown()
        }
    }

    /** 等待引擎初始化，返回是否可用 */
    fun awaitReady(timeoutMs: Long = 5000): Boolean {
        ready.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (!ok) return false
        val r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** 可选的中文音色（离线优先） */
    fun chineseVoices(): List<Voice> {
        if (!awaitReady()) return emptyList()
        return runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == "zh" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
        }.getOrDefault(emptyList())
    }

    /** 合成为 PCM。失败抛异常。 */
    fun synthesize(text: String, voiceName: String, rate: Float, pitch: Float): PcmClip {
        check(awaitReady()) { "系统没有可用的中文语音引擎，请在系统设置中安装" }
        val out = File(app.cacheDir, "tts_${UUID.randomUUID()}.wav")
        val id = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        synchronized(pending) { pending[id] = latch }
        try {
            if (voiceName.isNotEmpty()) {
                tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.setVoice(it) }
            }
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            val r = tts.synthesizeToFile(text, null, out, id)
            check(r == TextToSpeech.SUCCESS) { "语音合成请求失败" }
            check(latch.await(15, TimeUnit.SECONDS)) { "语音合成超时" }
            synchronized(pending) { check(!failed.remove(id)) { "语音合成出错" } }
            return Pcm.readWav(out)
        } catch (e: Exception) {
            Log.w("SpeechSynth", "synthesize failed", e)
            throw e
        } finally {
            synchronized(pending) { pending.remove(id) }
            out.delete()
        }
    }

    fun shutdown() = tts.shutdown()
}
