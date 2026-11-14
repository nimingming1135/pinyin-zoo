package com.alex.pinyinzoo.logic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * 录音器：16kHz 单声道 PCM16。
 *
 * 只负责采集与端点检测（VAD），不做评分。
 * 录音结果用于 [PitchAnalyzer] 提取基频曲线，以及 [PronunciationScorer] 综合打分。
 */
class Recorder(private val context: Context) {

    /** 采样率：16k 足够分析 75~500Hz 的基频，且数据量小 */
    val sampleRate = 16000

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var recording = false

    @Volatile
    private var cancelled = false

    /** 录音缓冲区，最长 4 秒 */
    private val buffer = ArrayList<Short>(sampleRate * 4)
    private val lock = Any()

    /** 实时音量回调（0..1），用于声波动画 */
    @Volatile
    var onLevel: ((Float) -> Unit)? = null

    /** 实时频谱（简化：返回 8 个频带的能量），用于波形可视化 */
    @Volatile
    var onSpectrum: ((FloatArray) -> Unit)? = null

    val isRecording: Boolean get() = recording

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!hasPermission()) return false
        if (recording) return false

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord 创建失败", e)
            return false
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord 未初始化")
            rec.release()
            return false
        }

        synchronized(lock) { buffer.clear() }
        cancelled = false
        record = rec
        recording = true

        thread = Thread {
            val chunk = ShortArray(1024)
            try {
                rec.startRecording()
                loop@ while (recording) {
                    val n = rec.read(chunk, 0, chunk.size)
                    if (n <= 0) continue@loop
                    synchronized(lock) {
                        if (buffer.size < sampleRate * 4) {
                            for (i in 0 until n) buffer.add(chunk[i])
                        }
                    }
                    emitLevel(chunk, n)
                    emitSpectrum(chunk, n)
                }
            } catch (e: Exception) {
                Log.w(TAG, "录音循环异常", e)
            } finally {
                try {
                    if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
                } catch (_: Exception) {
                }
                rec.release()
            }
        }.also { it.priority = Thread.MAX_PRIORITY }
        thread?.start()
        return true
    }

    /** 停止录音并返回 PCM 数据（16bit 单声道）。取消返回空 */
    fun stop(): ShortArray {
        if (!recording) return ShortArray(0)
        recording = false
        // 录音线程会在缓冲刷新后自行退出；最长等 400ms，避免主线程卡顿
        try {
            thread?.join(400)
        } catch (_: Exception) {
        }
        thread = null
        val out = synchronized(lock) { buffer.toShortArray() }
        synchronized(lock) { buffer.clear() }
        return if (cancelled) ShortArray(0) else out
    }

    fun cancel() {
        cancelled = true
        stop()
    }

    private fun emitLevel(chunk: ShortArray, n: Int) {
        val cb = onLevel ?: return
        var sum = 0.0
        for (i in 0 until n) {
            val v = chunk[i] / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / n)
        // 映射到 0..1，做对数压缩更符合听感
        val level = if (rms < 1e-4) 0f else ((20 * log10(rms) + 60) / 60).toFloat().coerceIn(0f, 1f)
        cb(level)
    }

    private fun emitSpectrum(chunk: ShortArray, n: Int) {
        val cb = onSpectrum ?: return
        // 简化能量分组（8 段），只用于视觉，不做真实 FFT
        val bands = FloatArray(8)
        val per = (n / 8).coerceAtLeast(1)
        for (b in 0 until 8) {
            var s = 0.0
            val from = b * per
            val to = ((b + 1) * per).coerceAtMost(n)
            for (i in from until to) {
                val v = chunk[i] / 32768.0
                s += v * v
            }
            val cnt = (to - from).coerceAtLeast(1)
            bands[b] = (sqrt(s / cnt) * 8).toFloat().coerceIn(0f, 1f)
        }
        cb(bands)
    }

    private fun log10(x: Double): Double = kotlin.math.log10(x)

    companion object {
        private const val TAG = "Recorder"
    }
}

/**
 * 基频分析器：自相关法（与构建期 Python 提取算法一致，保证曲线可比）。
 */
object PitchAnalyzer {

    /**
     * 提取基频轨迹。
     *
     * 性能与精度权衡：先用 2 倍抽取（16k → 8k），既保留 75~500Hz 的基频信息
     * （8k 采样率下的奈奎斯特频率为 4000Hz，远高于 500Hz），
     * 又把自相关计算量降到 1/2，因此可以用更细的 hop（80 = 10ms）拿到足够帧数。
     *
     * 帧数对声调判定很关键：帧太少会丢掉二声的上升趋势。
     *
     * @return 每帧频率（Hz），0 表示清音/静音
     */
    fun f0Track(
        pcm: ShortArray,
        sampleRate: Int = 16000,
        frame: Int = 1024,
        hop: Int = 80,
        fmin: Int = 75,
        fmax: Int = 500,
    ): FloatArray {
        if (pcm.size < frame) return FloatArray(0)
        val n = 1 + (pcm.size - frame) / hop
        val out = FloatArray(n)
        val tauMin = sampleRate / fmax
        val tauMax = (sampleRate / fmin).coerceAtMost(frame - 2)

        val seg = DoubleArray(frame)

        // 预计算 tau 粗搜序列
        val coarseTaus = ArrayList<Int>(((tauMax - tauMin) / 2) + 2)
        var t = tauMin
        while (t < tauMax) {
            coarseTaus.add(t)
            t += 2
        }
        if (coarseTaus.isEmpty()) return FloatArray(0)

        fun ac(tau: Int, r0: Double): Double {
            if (tau < 1 || tau >= frame) return 0.0
            var s = 0.0
            val limit = frame - tau
            var j = 0
            while (j < limit) {
                s += seg[j] * seg[j + tau]
                j++
            }
            return s / r0
        }

        for (i in 0 until n) {
            val start = i * hop
            var energy = 0.0
            for (j in 0 until frame) {
                val v = pcm[start + j] / 32768.0
                seg[j] = v
                energy += v * v
            }
            val rms = sqrt(energy / frame)
            if (rms < 0.012) continue

            var mean = 0.0
            for (j in 0 until frame) mean += seg[j]
            mean /= frame
            var r0 = 0.0
            for (j in 0 until frame) {
                seg[j] -= mean
                r0 += seg[j] * seg[j]
            }
            if (r0 <= 1e-12) continue

            var bestTau = -1
            var bestVal = 0.0
            for (tau in coarseTaus) {
                val v = ac(tau, r0)
                if (v > bestVal) {
                    bestVal = v
                    bestTau = tau
                }
            }
            if (bestTau < 0 || bestVal < 0.32) continue

            var refined = bestTau
            var refinedVal = bestVal
            for (d in intArrayOf(-1, 1)) {
                val tau = bestTau + d
                if (tau < tauMin || tau >= tauMax) continue
                val v = ac(tau, r0)
                if (v > refinedVal) {
                    refinedVal = v
                    refined = tau
                }
            }

            for (div in intArrayOf(2, 3)) {
                val tt = refined / div
                if (tt < tauMin) continue
                if (ac(tt, r0) > 0.85 * refinedVal) {
                    refined = tt
                    break
                }
            }

            val a = ac(refined - 1, r0)
            val b = ac(refined, r0)
            val c = ac(refined + 1, r0)
            val denom = 2 * (2 * b - a - c)
            val shift = if (abs(denom) > 1e-9) ((c - a) / denom).coerceIn(-1.0, 1.0) else 0.0
            out[i] = (sampleRate / (refined + shift)).toFloat()
        }
        return out
    }

    /** 裁剪首尾静音 */
    fun trimSilence(pcm: ShortArray, thr: Int = 650, padMs: Int = 30, sampleRate: Int = 16000): ShortArray {
        var start = -1
        var end = -1
        for (i in pcm.indices) {
            if (abs(pcm[i].toInt()) > thr) {
                if (start < 0) start = i
                end = i
            }
        }
        if (start < 0) return pcm
        val pad = sampleRate * padMs / 1000
        return pcm.copyOfRange((start - pad).coerceAtLeast(0), (end + pad).coerceAtMost(pcm.size))
    }

    /**
     * 归一化音高轮廓（对数域，去中位数，缩放至 [-1,1]）。
     *
     * 注意：一声是"平直"曲线，方差天然很小，不能因为方差小就判定失败——
     * 否则一声永远拿不到分。这里只在完全没有有声帧时才返回 null，
     * 平直与否交给声调判定逻辑（用 slope/range 判断）。
     */
    fun contour(pcm: ShortArray, points: Int = 24, sampleRate: Int = 16000): FloatArray? {
        val trimmed = trimSilence(pcm, sampleRate = sampleRate)
        val f0 = f0Track(trimmed, sampleRate)
        if (f0.isEmpty()) return null

        // 取最长连续有声段
        var bestStart = -1
        var bestLen = 0
        var curStart = -1
        var curLen = 0
        for (i in f0.indices) {
            if (f0[i] > 0) {
                if (curStart < 0) {
                    curStart = i
                    curLen = 0
                }
                curLen++
                if (curLen > bestLen) {
                    bestLen = curLen
                    bestStart = curStart
                }
            } else {
                curStart = -1
                curLen = 0
            }
        }
        if (bestStart < 0 || bestLen < 5) return null

        // 中值滤波
        val raw = FloatArray(bestLen)
        for (i in 0 until bestLen) raw[i] = f0[bestStart + i]
        val sm = FloatArray(bestLen) { i ->
            val lo = (i - 2).coerceAtLeast(0)
            val hi = (i + 3).coerceAtMost(bestLen)
            val seg = (lo until hi).map { raw[it] }.filter { it > 0 }.sorted()
            if (seg.isEmpty()) 0f else seg[seg.size / 2]
        }

        val voiced = sm.filter { it > 0 }
        if (voiced.size < 5) return null

        // 对数 + 重采样
        val logf = voiced.map { log2(it.toDouble()) }

        val res = FloatArray(points) { i ->
            val pos = i.toDouble() / (points - 1) * (logf.size - 1)
            val i0 = pos.toInt().coerceIn(0, logf.size - 1)
            val i1 = (i0 + 1).coerceAtMost(logf.size - 1)
            val frac = pos - i0
            (logf[i0] * (1 - frac) + logf[i1] * frac).toFloat()
        }
        val median = res.sorted()[res.size / 2]
        for (i in res.indices) res[i] -= median
        // 不做幅度归一化：保留对数频率（log2 Hz）的真实尺度。
        // 这样"平直"（一声）与"起伏"（其他声调）才能区分开。
        // 典型值域约 ±0.25（约 ±4 个半音）。
        return res
    }
}
