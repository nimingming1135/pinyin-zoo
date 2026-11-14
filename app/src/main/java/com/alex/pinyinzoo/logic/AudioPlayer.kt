package com.alex.pinyinzoo.logic

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 音频索引：assets/audio_index.json 的解析结果。
 *
 * 音频在构建期由 tools/build_audio.py 用 edge-tts 生成，运行期纯离线播放，
 * 不依赖设备上的任何 TTS 引擎（彻底规避 MIUI 系统的 TTS 问题）。
 */
object AudioIndex {

    private const val TAG = "AudioIndex"

    /** key -> 发音文本（汉字），仅用于调试显示 */
    private val texts = ConcurrentHashMap<String, String>()

    /** key -> 标准化音高曲线 */
    private val contours = ConcurrentHashMap<String, FloatArray>()

    @Volatile
    private var loaded = false

    val isLoaded: Boolean get() = loaded
    val size: Int get() = texts.size

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val t0 = System.currentTimeMillis()
            try {
                val raw = context.assets.open("audio_index.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(raw)
                val entries = root.optJSONObject("entries") ?: JSONObject()
                val keys = entries.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val e = entries.optJSONObject(key) ?: continue
                    texts[key] = e.optString("t", "")
                    val c = e.optJSONArray("c") ?: continue
                    val arr = FloatArray(c.length())
                    for (i in 0 until c.length()) arr[i] = c.optDouble(i, 0.0).toFloat()
                    if (arr.size >= 6) contours[key] = arr
                }
                loaded = true
                Log.d(TAG, "索引加载完成 ${texts.size} 条，${System.currentTimeMillis() - t0}ms")
            } catch (e: Exception) {
                Log.w(TAG, "音频索引加载失败，将退化为无音频模式", e)
                loaded = true
            }
        }
    }

    fun has(key: String?): Boolean = key != null && texts.containsKey(key)

    fun contour(key: String?): FloatArray? = if (key == null) null else contours[key]

    fun textOf(key: String?): String = if (key == null) "" else texts[key].orEmpty()
}

/**
 * 音频播放器。
 *
 * - 短音效走 SoundPool（低延迟）；
 * - 语音（音节/词语/反馈）走 MediaPlayer，assets 未压缩可直接 openFd；
 * - 支持串行播放队列（先播"太棒了"再播下一个音节）；
 * - 音效开关、播放回调、资源释放。
 */
class AudioPlayer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playerKey: String? = null
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sfxIds = HashMap<String, Int>()
    private var sfxLoaded = false

    /** 音效文件扩展名优先级 */
    private val SFX_EXTS = listOf("wav", "mp3")

    /** 静音开关 */
    var soundOn: Boolean = true

    /** 语音语速（1.0 为原速，已预生成较慢语速，一般保持 1.0） */
    var speechRate: Float = 1.0f

    /** 当前是否有语音在播放 */
    val isSpeaking: Boolean get() = player?.isPlaying == true

    init {
        AudioIndex.ensureLoaded(context)
        loadSfx()
    }

    private fun loadSfx() {
        // 音效位于 assets/sfx，优先 wav（tools/build_sfx.py 生成），兼容 mp3。
        // 历史坑：之前只找 "sfx/$name.mp3"，而该目录从未生成过，
        // 导致 sfxLoaded 恒为 false，答对/答错完全没有提示音。
        val names = listOf("tap", "correct", "wrong", "star", "unlock", "whoosh")
        var any = false
        names.forEach { name ->
            val path = SFX_EXTS.firstNotNullOfOrNull { ext ->
                "sfx/$name.$ext".takeIf { existsAsset(it) }
            }
            if (path != null) {
                try {
                    val afd = context.assets.openFd(path)
                    val id = pool.load(afd, 1)
                    sfxIds[name] = id
                    afd.close()
                    any = true
                } catch (e: Exception) {
                    Log.w(TAG, "音效加载失败: $path", e)
                }
            }
        }
        sfxLoaded = any
        if (!any) Log.w(TAG, "未找到任何音效文件，答题反馈将静音")
    }

    /** assets 里是否存在该文件（openFd 只支持未压缩条目，故用 open 探测） */
    private fun existsAsset(path: String): Boolean = try {
        context.assets.open(path).close()
        true
    } catch (_: Exception) {
        false
    }

    /** 播放短音效 */
    fun sfx(name: String) {
        if (!soundOn || !sfxLoaded) return
        val id = sfxIds[name] ?: return
        pool.play(id, 0.9f, 0.9f, 1, 0, 1.0f)
    }

    /**
     * 播放一条语音。
     * @param key 音频索引键，如 "syl/zhang3"、"vo/perfect"
     */
    fun speak(key: String?, onDone: (() -> Unit)? = null) {
        if (!soundOn || key == null) {
            onDone?.let { handler.postDelayed(it, 350L) }
            return
        }
        val path = "audio/$key.mp3"
        try {
            release()
            val afd: AssetFileDescriptor = context.assets.openFd(path)
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setOnCompletionListener {
                release()
                onDone?.let { cb -> handler.post(cb) }
            }
            mp.setOnErrorListener { _, _, _ ->
                release()
                onDone?.let { cb -> handler.post(cb) }
                true
            }
            mp.prepare()
            mp.setPlaybackParams(mp.playbackParams.setSpeed(speechRate.coerceIn(0.5f, 2.0f)))
            mp.start()
            player = mp
            playerKey = key
        } catch (e: Exception) {
            Log.w(TAG, "播放失败: $path", e)
            release()
            onDone?.let { handler.postDelayed(it, 300L) }
        }
    }

    /** 串行播放多条 */
    fun speakAll(keys: List<String>, onAllDone: (() -> Unit)? = null) {
        fun step(i: Int) {
            if (i >= keys.size) {
                onAllDone?.invoke()
                return
            }
            speak(keys[i]) { step(i + 1) }
        }
        step(0)
    }

    fun stop() {
        release()
    }

    private fun release() {
        try {
            player?.setOnCompletionListener(null)
            player?.setOnErrorListener(null)
            if (player?.isPlaying == true) player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playerKey = null
    }

    fun releaseAll() {
        release()
        try {
            pool.release()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "AudioPlayer"
    }
}
