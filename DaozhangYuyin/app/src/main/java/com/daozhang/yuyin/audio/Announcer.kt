package com.daozhang.yuyin.audio

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.daozhang.yuyin.data.Settings
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 播报管线：提示音 + 合成语音 → 增益 → AudioTrack。
 * 单线程串行队列，同一时刻只播一条，队列满（20 条）时丢弃最旧的。
 */
object Announcer {
    private const val TAG = "Announcer"
    private const val QUEUE_LIMIT = 20
    const val EXPORT_DIR = "到账语音工坊"

    private lateinit var app: Context
    private lateinit var settings: Settings
    private lateinit var synth: SpeechSynth
    private val main = Handler(Looper.getMainLooper())

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(QUEUE_LIMIT),
    ) { r, ex ->
        // 队列满：丢弃最旧的一条再放入新的，并修正计数
        if (!ex.isShutdown) {
            if (ex.queue.poll() != null) inFlight.decrementAndGet()
            ex.execute(r)
        }
    }
    private val inFlight = AtomicInteger(0)
    private val idleListeners = mutableListOf<() -> Unit>()

    /** 渲染缓存：同一话术+音色+语速+音调+提示音不重复合成 */
    private val cache = object : LinkedHashMap<String, PcmClip>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PcmClip>?) = size > 30
    }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        settings = Settings(app)
        synth = SpeechSynth(app)
    }

    val isIdle: Boolean get() = inFlight.get() == 0

    fun addIdleListener(l: () -> Unit) {
        synchronized(idleListeners) { idleListeners.add(l) }
    }

    fun removeIdleListener(l: () -> Unit) {
        synchronized(idleListeners) { idleListeners.remove(l) }
    }

    fun chineseVoices() = synth.chineseVoices()

    /**
     * 加入播报队列。
     * @param times 重复播报次数 1..3
     * @param voice 指定音色（试听用），null = 使用设置里的音色
     * @param onResult 主线程回调：null = 成功，否则为错误信息
     */
    fun enqueue(text: String, times: Int = 1, voice: String? = null, onResult: ((String?) -> Unit)? = null) {
        inFlight.incrementAndGet()
        executor.execute {
            var error: String? = null
            try {
                val clip = render(text, voice)
                repeat(times.coerceIn(1, 3)) { i ->
                    if (i > 0) Thread.sleep(600)
                    play(clip)
                }
            } catch (e: Exception) {
                Log.w(TAG, "播报失败", e)
                error = e.message ?: "播报失败"
            } finally {
                onResult?.let { cb -> main.post { cb(error) } }
                if (inFlight.decrementAndGet() == 0) notifyIdle()
            }
        }
    }

    private fun notifyIdle() {
        val ls = synchronized(idleListeners) { idleListeners.toList() }
        main.post { ls.forEach { it() } }
    }

    /** 生成完整播报音频（提示音 + 语音 + 增益）。阻塞，后台线程调用。 */
    fun render(text: String, voice: String? = null): PcmClip {
        val voiceName = voice ?: settings.voiceName
        val key = listOf(text, voiceName, settings.speechRate, settings.pitch, settings.chimeStyle).joinToString("|")
        val base = synchronized(cache) { cache[key] } ?: run {
            val speech = synthesizeSpeech(text, voiceName)
            val sr = speech.sampleRate
            val joined = Pcm.concat(
                Pcm.silence(sr, 60),
                Pcm.chime(settings.chimeStyle, sr),
                Pcm.silence(sr, 180),
                Pcm.fade(speech.samples, sr),
                Pcm.silence(sr, 120),
            )
            PcmClip(sr, joined).also { synchronized(cache) { cache[key] = it } }
        }
        return PcmClip(base.sampleRate, Pcm.applyGain(base.samples, settings.gainDb))
    }

    /** 内置离线音色（kokoro:编号）走 Kokoro；其余走系统 TTS。内置音色不可用时自动退回系统语音。 */
    private fun synthesizeSpeech(text: String, voiceName: String): PcmClip {
        val sid = OfflineVoice.sidOf(voiceName)
        val engine = if (sid != null) OfflineVoices.engine(app) else null
        if (sid != null && engine != null) {
            // Kokoro 没有音调参数，只用语速
            return engine.synthesize(sid, text, settings.speechRate)
        }
        val systemVoice = if (sid != null) "" else voiceName
        return synth.synthesize(text, systemVoice, settings.speechRate, settings.pitch)
    }

    private fun play(clip: PcmClip) {
        val attrs = AudioAttributes.Builder()
            .setUsage(if (settings.useAlarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        am.requestAudioFocus(focus)

        val minBuf = AudioTrack.getMinBufferSize(
            clip.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(clip.sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, 16 * 1024))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            track.write(clip.samples, 0, clip.samples.size)
            // write 返回时数据只是进了缓冲区，等播放头走完再停
            val deadline = System.currentTimeMillis() + clip.durationMs + 1500
            while (track.playbackHeadPosition < clip.samples.size && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            track.stop()
        } finally {
            track.release()
            am.abandonAudioFocusRequest(focus)
        }
    }

    /**
     * 导出为 WAV，保存到“音乐/到账语音工坊”。阻塞，后台线程调用。
     * 文件元数据（文件名与标题）标明为模拟播报。
     */
    fun exportWav(text: String, fileStem: String): Uri {
        val clip = render(text)
        val name = "模拟播报_${fileStem}_${System.currentTimeMillis()}.wav"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.TITLE, "模拟播报 · $text")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + EXPORT_DIR)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = app.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建文件")
            resolver.openOutputStream(uri)!!.use { Pcm.writeWav(clip, it) }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC), EXPORT_DIR).apply { mkdirs() }
            val f = File(dir, name)
            f.outputStream().use { Pcm.writeWav(clip, it) }
            Uri.fromFile(f)
        }
    }

    /** 在后台线程执行导出，结果回到主线程 */
    fun exportAsync(text: String, fileStem: String, cb: (Result<Uri>) -> Unit) {
        Thread {
            val r = runCatching { exportWav(text, fileStem) }
            main.post { cb(r) }
        }.start()
    }

    /** 设置变化（音色/语速/提示音）后清缓存 */
    fun clearCache() = synchronized(cache) { cache.clear() }
}
