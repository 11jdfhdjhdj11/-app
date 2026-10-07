package com.daozhang.yuyin.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kokoro v1.1-zh 离线合成（sherpa-onnx）。
 * 模型、音色、词典从 APK 资源直接读取；espeak-ng-data 必须是真实目录，首次使用时复制到应用私有目录。
 */
class KokoroEngine(ctx: Context) : OfflineVoiceEngine {
    private val app = ctx.applicationContext
    private var tts: OfflineTts? = null

    override val voices: List<OfflineVoice> = KokoroCatalog.voices

    @Synchronized
    private fun load(): OfflineTts {
        tts?.let { return it }
        val t0 = System.currentTimeMillis()
        val dataDir = copyAssetDirOnce("$ASSET_DIR/espeak-ng-data")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "$ASSET_DIR/model.int8.onnx",
                    voices = "$ASSET_DIR/voices.bin",
                    tokens = "$ASSET_DIR/tokens.txt",
                    dataDir = dataDir,
                    lexicon = "$ASSET_DIR/lexicon-us-en.txt,$ASSET_DIR/lexicon-zh.txt",
                ),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                debug = false,
                provider = "cpu",
            ),
            ruleFsts = "$ASSET_DIR/phone-zh.fst,$ASSET_DIR/date-zh.fst,$ASSET_DIR/number-zh.fst",
        )
        return OfflineTts(assetManager = app.assets, config = config).also {
            tts = it
            Log.i(TAG, "Kokoro 加载完成：${System.currentTimeMillis() - t0} ms，${it.numSpeakers()} 个说话人")
        }
    }

    @Synchronized
    override fun synthesize(sid: Int, text: String, speed: Float): PcmClip {
        val audio = load().generate(text = text, sid = sid, speed = speed.coerceIn(0.5f, 2.0f))
        return PcmClip(audio.sampleRate, toPcm16(audio.samples))
    }

    /** float → 16-bit，并把峰值归一到约 -1 dBFS，各音色响度更一致 */
    private fun toPcm16(samples: FloatArray): ShortArray {
        var peak = 0f
        for (s in samples) peak = maxOf(peak, abs(s))
        val g = if (peak > 1e-4f) 0.89f / peak else 1f
        return ShortArray(samples.size) { i ->
            (samples[i] * g * Short.MAX_VALUE).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** 把资源目录复制到应用私有目录（只复制一次，用标记文件判断），返回绝对路径 */
    private fun copyAssetDirOnce(assetPath: String): String {
        val root = File(app.filesDir, "kokoro")
        val target = File(root, assetPath)
        val marker = File(root, ".copied_$MODEL_VERSION")
        if (!marker.exists()) {
            root.deleteRecursively()
            copyAsset(assetPath, root)
            marker.createNewFile()
        }
        return target.absolutePath
    }

    private fun copyAsset(path: String, root: File) {
        val children = app.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            val out = File(root, path)
            out.parentFile?.mkdirs()
            app.assets.open(path).use { input -> out.outputStream().use { input.copyTo(it) } }
        } else {
            File(root, path).mkdirs()
            for (c in children) copyAsset("$path/$c", root)
        }
    }

    companion object {
        private const val TAG = "KokoroEngine"
        private const val ASSET_DIR = "kokoro"
        private const val MODEL_VERSION = "v1_1_int8"
    }
}
