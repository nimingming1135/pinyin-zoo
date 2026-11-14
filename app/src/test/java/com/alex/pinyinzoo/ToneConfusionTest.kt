package com.alex.pinyinzoo

import com.alex.pinyinzoo.logic.PronunciationScorer
import com.alex.pinyinzoo.model.Tone
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声调判定的混淆矩阵测试。
 *
 * 用 12 条真实 TTS 音频（每声 3 条）做 4x4 交叉判定，
 * 要求对角线上（真实声调 vs 同一目标）全部判对，非对角线尽量判错。
 * 这是评分器最关键的正确性保障。
 */
class ToneConfusionTest {

    private fun loadWav(name: String): ShortArray {
        val s = javaClass.classLoader!!.getResourceAsStream("samples/$name")!!
        val bytes = s.readBytes()
        s.close()
        var pos = 12
        var off = -1
        var len = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = (bytes[pos + 4].toInt() and 0xFF) or
                ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 7].toInt() and 0xFF) shl 24)
            if (id == "data") {
                off = pos + 8; len = size; break
            }
            pos += 8 + size + (size and 1)
        }
        val n = (len / 2).coerceAtMost((bytes.size - off) / 2)
        return ShortArray(n) { i ->
            val lo = bytes[off + i * 2].toInt() and 0xFF
            val hi = bytes[off + i * 2 + 1].toInt()
            ((hi shl 8) or lo).toShort()
        }
    }

    @Test
    fun `真实音频的声调判定准确率`() {
        var diag = 0
        var total = 0
        val report = StringBuilder()
        report.appendLine("真实声调\\判定目标    1声  2声  3声  4声")
        for (real in 1..4) {
            val row = StringBuilder("      ${real}声:        ")
            for (target in 1..4) {
                var hit = 0
                var cnt = 0
                for (i in 0..2) {
                    val pcm = loadWav("tone${real}_$i.wav")
                    val r = PronunciationScorer.score(pcm, Tone.of(target))
                    cnt++
                    if (r.toneCorrect) hit++
                }
                if (real == target) {
                    diag += hit
                    total += cnt
                }
                row.append(" $hit/$cnt ")
            }
            report.appendLine(row.toString())
        }
        println(report.toString())
        assertTrue(
            "对角线（正确判定）应全部命中，实际 $diag/$total\n$report",
            diag == total
        )
    }
}
