package com.alex.pinyinzoo

import com.alex.pinyinzoo.logic.PitchAnalyzer
import com.alex.pinyinzoo.logic.PronunciationScorer
import com.alex.pinyinzoo.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * 评分器测试。
 *
 * 关键用例使用**真实 TTS 音频样本**（app/src/test/resources/samples 目录下的 wav，
 * 由真实 edge-tts 音节转成 16k PCM），而不是合成波形。
 * 原因：合成正弦波的音高起伏幅度与真实语音差 4 倍，用合成数据校准会把阈值调错。
 */
class PronunciationScorerTest {

    /**
     * 读取 16k 单声道 PCM16 WAV。
     *
     * 注意：ffmpeg 输出的 wav 头不一定正好 44 字节（含 LIST 元数据块时更长），
     * 必须解析 RIFF 分块找到 data，否则读取错位会得到完全错误的波形。
     */
    private fun loadWav(name: String): ShortArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("samples/$name")
            ?: error("找不到测试样本 samples/$name")
        val bytes = stream.readBytes()
        stream.close()

        var pos = 12 // 跳过 "RIFF" + size + "WAVE"
        var dataOffset = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = (bytes[pos + 4].toInt() and 0xFF) or
                ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 7].toInt() and 0xFF) shl 24)
            if (id == "data") {
                dataOffset = pos + 8
                dataLen = size
                break
            }
            pos += 8 + size + (size and 1)
        }
        require(dataOffset > 0) { "WAV 中找不到 data 块: $name" }

        val n = (dataLen / 2).coerceAtMost((bytes.size - dataOffset) / 2)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val lo = bytes[dataOffset + i * 2].toInt() and 0xFF
            val hi = bytes[dataOffset + i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }

    private fun samplesOf(tone: Int): List<ShortArray> =
        (0..2).map { loadWav("tone${tone}_$it.wav") }

    @Test
    fun `真实一声音频判为一声`() {
        samplesOf(1).forEachIndexed { i, pcm ->
            val r = PronunciationScorer.score(pcm, Tone.T1)
            assertTrue(
                "一声样本 $i 应判对，detected=${r.detectedTone} toneScore=${r.toneScore} tip=${r.tip}",
                r.toneCorrect
            )
        }
    }

    @Test
    fun `真实二声音频判为二声`() {
        samplesOf(2).forEachIndexed { i, pcm ->
            val r = PronunciationScorer.score(pcm, Tone.T2)
            assertTrue(
                "二声样本 $i 应判对，detected=${r.detectedTone} toneScore=${r.toneScore}",
                r.toneCorrect
            )
        }
    }

    @Test
    fun `真实三声音频判为三声`() {
        samplesOf(3).forEachIndexed { i, pcm ->
            val r = PronunciationScorer.score(pcm, Tone.T3)
            assertTrue(
                "三声样本 $i 应判对，detected=${r.detectedTone} toneScore=${r.toneScore}",
                r.toneCorrect
            )
        }
    }

    @Test
    fun `真实四声音频判为四声`() {
        samplesOf(4).forEachIndexed { i, pcm ->
            val r = PronunciationScorer.score(pcm, Tone.T4)
            assertTrue(
                "四声样本 $i 应判对，detected=${r.detectedTone} toneScore=${r.toneScore}",
                r.toneCorrect
            )
        }
    }

    @Test
    fun `真实音频总分都应在 60 以上`() {
        for (t in 1..4) {
            samplesOf(t).forEachIndexed { i, pcm ->
                val r = PronunciationScorer.score(pcm, Tone.of(t))
                assertTrue(
                    "$t 声样本 $i 总分应 >= 60，实际 ${r.total}（声调 ${r.toneScore}/完整 ${r.completeness}/音质 ${r.quality}）",
                    r.total >= 60
                )
            }
        }
    }

    @Test
    fun `调型错配要判错`() {
        // 用真实四声（明显下降）去考二声（应该上升）
        val falling = loadWav("tone4_0.wav")
        val r = PronunciationScorer.score(falling, Tone.T2)
        assertTrue("下降曲线不该被判为上升的二声", !r.toneCorrect)

        // 用真实一声（平直）去考四声
        val flat = loadWav("tone1_0.wav")
        val r2 = PronunciationScorer.score(flat, Tone.T4)
        assertTrue("平直曲线不该被判为四声", !r2.toneCorrect)

        // 用真实二声（上升）去考四声
        val rising = loadWav("tone2_0.wav")
        val r3 = PronunciationScorer.score(rising, Tone.T4)
        assertTrue("上升曲线不该被判为四声", !r3.toneCorrect)
    }

    @Test
    fun `静音与过短要被拒绝`() {
        val silence = ShortArray(8000)
        val r = PronunciationScorer.score(silence, Tone.T1)
        assertEquals("静音应为 0 分", 0, r.total)

        val tiny = ShortArray(200) { 1000 }
        val r2 = PronunciationScorer.score(tiny, Tone.T1)
        assertEquals(0, r2.total)
    }

    @Test
    fun `音量太小要低分`() {
        val quiet = loadWav("tone1_0.wav").map { (it * 0.04).toInt().toShort() }.toShortArray()
        val r = PronunciationScorer.score(quiet, Tone.T1)
        assertTrue("音量极小应低分，实际 ${r.total}", r.total < 75)
    }

    @Test
    fun `轮廓能反映声调方向`() {
        val c2 = PitchAnalyzer.contour(loadWav("tone2_0.wav"))
        assertNotNull(c2)
        assertEquals(24, c2!!.size)
        val head2 = c2.take(6).average()
        val tail2 = c2.takeLast(6).average()
        assertTrue("二声首段应低于末段，head=$head2 tail=$tail2", tail2 > head2)

        val c4 = PitchAnalyzer.contour(loadWav("tone4_0.wav"))
        assertNotNull(c4)
        val head4 = c4!!.take(6).average()
        val tail4 = c4.takeLast(6).average()
        assertTrue("四声首段应高于末段，head=$head4 tail=$tail4", head4 > tail4)
    }

    @Test
    fun `合成正弦波也能给出合理结果`() {
        // 平直 300Hz 正弦波，考一声应判对
        val sr = 16000
        val n = (sr * 0.8).toInt()
        var phase = 0.0
        val pcm = ShortArray(n) { i ->
            phase += 2 * PI * 300.0 / sr
            val t = i.toDouble() / n
            val env = if (t < 0.05) t / 0.05 else if (t > 0.9) (1 - t) / 0.1 else 1.0
            (sin(phase) * env * 12000).toInt().toShort()
        }
        val r = PronunciationScorer.score(pcm, Tone.T1)
        assertTrue("平直正弦波应判为一声，detected=${r.detectedTone}", r.toneCorrect)
    }
}
