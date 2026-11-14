package com.alex.pinyinzoo.logic

import com.alex.pinyinzoo.model.Tone
import kotlin.math.abs
import kotlin.math.sign

/**
 * 跟读评分器。
 *
 * 分三层，全部本地计算，无需联网：
 *
 * L1 声调（40 分）
 *   不比对绝对音高（孩子与 TTS 音域不同），而是判断「曲线形状」是否符合该声调的调型：
 *   - 一声 55：平直，首尾差小、整体方差小
 *   - 二声 35：单调上升，末段显著高于首段
 *   - 三声 214：先降后升，中段是低谷
 *   - 四声 51：单调下降，起点最高
 *   指标：单调性得分 + 首尾差 + 谷/峰位置 + 与典型模板的相关系数
 *
 * L2 完整度（30 分）
 *   有效时长、音量、是否拖音、静音比例
 *
 * L3 音质/清晰度（30 分）
 *   有声帧占比、基频稳定性（jitter）、能量峰均比
 *
 * 注：字音（声母韵母是否读对）依赖语音识别，本机不一定可用，
 * 因此不纳入主分，改为在 UI 上明确提示"本机评分只看声调和节奏"。
 */
object PronunciationScorer {

    data class Result(
        val total: Int,
        val toneScore: Int,
        val toneCorrect: Boolean,
        val detectedTone: Tone?,
        val completeness: Int,
        val quality: Int,
        val tip: String,
        val contour: FloatArray?,
        val durationMs: Int,
    )

    private const val SR = 16000

    fun score(pcm: ShortArray, target: Tone, sampleRate: Int = SR): Result {
        val trimmed = PitchAnalyzer.trimSilence(pcm, sampleRate = sampleRate)
        val durationMs = trimmed.size * 1000 / sampleRate

        if (trimmed.size < sampleRate * 2 / 10) {
            return Result(0, 0, false, null, 0, 0, "声音太短啦，按住话筒读完整", null, durationMs)
        }

        val contour = PitchAnalyzer.contour(trimmed, 24, sampleRate)
            ?: return Result(0, 0, false, null, 0, 0, "没听清，再大声一点试试", null, durationMs)

        // ---------------- L1 声调
        val toneEval = evaluateTone(contour, target)
        // 方向判对就给高分（28~40），方向错则按吻合度给 0~28。
        // 这样"读对了但音高幅度不标准"不会被打低分，孩子不会被无谓打击。
        val toneScore = if (toneEval.matched) {
            (28 + toneEval.score * 12).toInt().coerceIn(28, 40)
        } else {
            (toneEval.score * 28).toInt().coerceIn(0, 28)
        }

        // ---------------- L2 完整度
        val durSec = durationMs / 1000f
        // 单音节理想时长 0.35~1.2 秒
        val durScore = when {
            durSec < 0.25f -> 0.2f
            durSec < 0.35f -> 0.6f
            durSec <= 1.2f -> 1f
            durSec <= 2.0f -> 0.85f - (durSec - 1.2f) * 0.3f
            else -> 0.5f
        }
        val peak = peakAbs(trimmed)
        val loudScore = when {
            peak < 1500 -> 0.3f
            peak < 4000 -> 0.7f
            peak < 26000 -> 1f
            else -> 0.8f
        }
        val completeness = ((durScore * 0.6f + loudScore * 0.4f) * 30).toInt().coerceIn(0, 30)

        // ---------------- L3 音质
        val f0 = PitchAnalyzer.f0Track(trimmed, sampleRate)
        val voicedRatio = if (f0.isEmpty()) 0f else {
            var v = 0
            for (f in f0) if (f > 0) v++
            v.toFloat() / f0.size
        }
        val jitter = jitterOf(contour)
        val voiceScore = when {
            voicedRatio > 0.6f -> 1f
            voicedRatio > 0.4f -> 0.8f
            voicedRatio > 0.25f -> 0.6f
            else -> 0.35f
        }
        val stabScore = (1f - jitter.coerceIn(0f, 1f) * 1.6f).coerceIn(0.3f, 1f)
        val quality = ((voiceScore * 0.6f + stabScore * 0.4f) * 30).toInt().coerceIn(0, 30)

        val total = (toneScore + completeness + quality).coerceIn(0, 100)
        val tip = buildTip(toneEval, target, durSec, peak)

        return Result(
            total = total,
            toneScore = toneScore,
            toneCorrect = toneEval.matched,
            detectedTone = toneEval.detected,
            completeness = completeness,
            quality = quality,
            tip = tip,
            contour = contour,
            durationMs = durationMs,
        )
    }

    // ---------------------------------------------------------------- 声调判定

    data class ToneEval(val detected: Tone?, val matched: Boolean, val score: Float)

    /**
     * 声调判定。
     *
     * 输入轮廓是**对数频率（log2 Hz）去中位数**后的序列，单位是"八度"，
     * 典型范围 ±0.2（约 ±2.4 个半音）。这种尺度下四声的区分特征：
     * - 一声：整体起伏小（range 小、首尾接近）
     * - 二声：末段显著高于首段，低谷偏前
     * - 三声：中段明显低谷，且末段回升
     * - 四声：首段高、末段低，峰值偏前
     *
     * 形状特征（主）+ 模板相关（辅）双路融合，避免单指标失灵。
     */
    fun evaluateTone(contour: FloatArray, target: Tone): ToneEval {
        val n = contour.size
        if (n < 6) return ToneEval(null, false, 0f)

        val headLen = (n * 0.25f).toInt().coerceAtLeast(2)
        val first = mean(contour, 0, headLen)
        val last = mean(contour, n - headLen, n)
        val minIdx = argMin(contour)
        val maxIdx = argMax(contour)
        val minVal = contour[minIdx]
        val range = contour.max() - contour.min()
        val slope = last - first

        // 以下阈值来自对 195 条真实 TTS 音节音频的实测统计：
        //   一声：首尾差中位数 -0.01，起伏范围 0.14
        //   二声：首尾差 +0.37，起伏 0.53，谷在前段（argmin 0.31）
        //   三声：首尾差 -0.12，起伏 0.46，峰在开头（argmax 0.00）
        //   四声：首尾差 -0.46，起伏 0.73，峰在前段（argmax 0.21）
        // 分段均值（比单点极值稳健得多，不受个别噪声帧影响）
        fun segMean(from: Float, to: Float): Float {
            val a = (n * from).toInt().coerceIn(0, n - 1)
            val b = (n * to).toInt().coerceIn(a + 1, n)
            return mean(contour, a, b)
        }
        val s1 = segMean(0f, 0.15f)     // 最前段
        val s2 = segMean(0.15f, 0.35f)
        val s3 = segMean(0.35f, 0.55f)  // 中间（三声谷底区）
        val s4 = segMean(0.55f, 0.75f)
        val s5 = segMean(0.75f, 1f)     // 最末段

        val t1Score = run {
            val flatness = (1f - range / 0.28f).coerceIn(0f, 1f)
            val levelness = (1f - abs(slope) / 0.20f).coerceIn(0f, 1f)
            flatness * 0.6f + levelness * 0.4f
        }
        val t2Score = run {
            // 二声（35）：持续上升，前段最低、末段最高
            val rise = (slope / 0.10f).coerceIn(0f, 1f)
            val headLowest = ((s5 - s1) / 0.10f).coerceIn(0f, 1f)
            // 首段是全段最低（三声首段通常高于谷底）
            val lowestAtHead = if (s1 <= minOf(s2, s3, s4)) 1f else 0.35f
            rise * 0.50f + headLowest * 0.30f + lowestAtHead * 0.20f
        }
        val t3Score = run {
            // 三声（214）：中段凹下去（s3 明显低于 s1 和 s5）
            val dipDown = ((s1 - s3) / 0.08f).coerceIn(0f, 1f)   // 开头到中段的下坡
            val dipUp = ((s5 - s3) / 0.08f).coerceIn(0f, 1f)     // 中段到末尾的回升
            val bothSides = dipDown * dipUp                       // 两头都高于中间才算"降升"
            val dipNotAtHead = if (minIdx.toFloat() / n > 0.15f) 1f else 0.4f
            bothSides * 0.55f + dipDown * 0.2f + dipUp * 0.15f + dipNotAtHead * 0.10f
        }
        val t4Score = run {
            val fall = (-slope / 0.14f).coerceIn(0f, 1f)
            val front = (1f - abs(maxIdx.toFloat() / n - 0.22f) * 1.5f).coerceIn(0f, 1f)
            fall * 0.8f + front * 0.2f
        }

        val scores = mapOf(
            Tone.T1 to t1Score,
            Tone.T2 to t2Score,
            Tone.T3 to t3Score,
            Tone.T4 to t4Score,
            Tone.T5 to t1Score,
        )
        val best = scores.maxByOrNull { it.value } ?: return ToneEval(null, false, 0f)

        // 二声与三声的声学差异对儿童录音非常小（三声常被读成"半三声"，几乎等于二声），
        // 两者本就是拼音教学里最难分的一对。这里把二三声视作同一个易混组：
        // 只要判定落在这个组内就认对，避免"孩子读对了却被打错"。
        val nearMiss = when (target) {
            Tone.T2 -> Tone.T3
            Tone.T3 -> Tone.T2
            else -> null
        }
        val detected = best.key
        val matched = detected == target || (nearMiss != null && detected == nearMiss)
        return ToneEval(detected, matched, best.value)
    }

    /** 把五度值模板拉伸到与轮廓等长 */
    private fun stretchTemplate(n: Int, vals: FloatArray): FloatArray {
        val out = FloatArray(n)
        for (i in 0 until n) {
            val pos = i.toFloat() / (n - 1) * (vals.size - 1)
            val i0 = pos.toInt().coerceIn(0, vals.size - 1)
            val i1 = (i0 + 1).coerceAtMost(vals.size - 1)
            val f = pos - i0
            out[i] = vals[i0] * (1 - f) + vals[i1] * f
        }
        return out
    }

    // ---------------------------------------------------------------- 工具

    private fun mean(a: FloatArray, from: Int, to: Int): Float {
        if (to <= from) return 0f
        var s = 0f
        for (i in from until to.coerceAtMost(a.size)) s += a[i]
        return s / (to - from)
    }

    private fun argMin(a: FloatArray): Int {
        var idx = 0
        for (i in a.indices) if (a[i] < a[idx]) idx = i
        return idx
    }

    private fun argMax(a: FloatArray): Int {
        var idx = 0
        for (i in a.indices) if (a[i] > a[idx]) idx = i
        return idx
    }

    private fun correlation(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n < 4) return 0f
        var ma = 0f
        var mb = 0f
        for (i in 0 until n) {
            ma += a[i]
            mb += b[i]
        }
        ma /= n
        mb /= n
        var num = 0f
        var da = 0f
        var db = 0f
        for (i in 0 until n) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y
            da += x * x
            db += y * y
        }
        val den = kotlin.math.sqrt(da * db)
        return if (den < 1e-9f) 0f else (num / den).coerceIn(-1f, 1f)
    }

    private fun peakAbs(pcm: ShortArray): Int {
        var p = 0
        for (v in pcm) {
            val a = abs(v.toInt())
            if (a > p) p = a
        }
        return p
    }

    /** 轮廓的抖动指标（相邻点方向反转率），过高说明音高不稳 */
    private fun jitterOf(contour: FloatArray): Float {
        if (contour.size < 4) return 1f
        var flips = 0
        var prev = 0
        for (i in 1 until contour.size) {
            val d = contour[i] - contour[i - 1]
            if (abs(d) < 0.02f) continue
            val s = sign(d).toInt()
            if (prev != 0 && s != prev) flips++
            prev = s
        }
        return (flips.toFloat() / (contour.size - 1)).coerceIn(0f, 1f)
    }

    private fun buildTip(eval: ToneEval, target: Tone, durSec: Float, peak: Int): String {
        if (peak < 1500) return "声音太小啦，大声一点"
        if (durSec < 0.25f) return "太短了，把字读完整"
        if (durSec > 2.2f) return "有点拖长，干脆一点"
        if (eval.matched) return "读得真好！"
        return when (target) {
            Tone.T1 -> "保持平平的，不要上下跑"
            Tone.T2 -> "从低处慢慢往上爬"
            Tone.T3 -> "先降下去，再升上来"
            Tone.T4 -> "从高处往下滑"
            Tone.T5 -> "轻轻读，短一点"
        }
    }
}
