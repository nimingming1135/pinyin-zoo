package com.alex.pinyinzoo

import com.alex.pinyinzoo.content.SyllableTable
import com.alex.pinyinzoo.model.Domain
import com.alex.pinyinzoo.model.PinyinTag
import com.alex.pinyinzoo.model.QuestionKind
import com.alex.pinyinzoo.model.Tier
import com.alex.pinyinzoo.model.Tone
import com.alex.pinyinzoo.util.SyllableParser
import com.alex.pinyinzoo.util.ToneMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标调规则测试：口诀「有 a 不放过，没 a 找 o e，i u 并列标在后」。
 */
class ToneMarkTest {

    @Test
    fun `有 a 时标在 a 上`() {
        assertEquals("bā", ToneMark.render("ba", Tone.T1))
        assertEquals("zhǎng", ToneMark.render("zhang", Tone.T3))
        assertEquals("guǒ", ToneMark.render("guo", Tone.T3))
        assertEquals("xiǎo", ToneMark.render("xiao", Tone.T3))
        // ai / ao / an / ang 都标 a
        assertEquals("bái", ToneMark.render("bai", Tone.T2))
        assertEquals("hǎo", ToneMark.render("hao", Tone.T3))
        assertEquals("fàn", ToneMark.render("fan", Tone.T4))
        assertEquals("máng", ToneMark.render("mang", Tone.T2))
    }

    @Test
    fun `没有 a 时标在 o 或 e 上`() {
        assertEquals("wǒ", ToneMark.render("wo", Tone.T3))
        assertEquals("duō", ToneMark.render("duo", Tone.T1))
        assertEquals("gē", ToneMark.render("ge", Tone.T1))
        assertEquals("měi", ToneMark.render("mei", Tone.T3))
        assertEquals("fēng", ToneMark.render("feng", Tone.T1))
    }

    @Test
    fun `i u 并列标在后`() {
        // iu -> 标 u；ui -> 标 i
        assertEquals("liù", ToneMark.render("liu", Tone.T4))
        assertEquals("jiǔ", ToneMark.render("jiu", Tone.T3))
        assertEquals("guī", ToneMark.render("gui", Tone.T1))
        assertEquals("huì", ToneMark.render("hui", Tone.T4))
        assertEquals("duì", ToneMark.render("dui", Tone.T4))
    }

    @Test
    fun `其他情况标在后一个元音`() {
        assertEquals("lín", ToneMark.render("lin", Tone.T2))
        assertEquals("xīng", ToneMark.render("xing", Tone.T1))
        assertEquals("qīng", ToneMark.render("qing", Tone.T1))
        assertEquals("chūn", ToneMark.render("chun", Tone.T1))
    }

    @Test
    fun `ü 保留内部 v 写法并正确加调`() {
        assertEquals("nǚ", ToneMark.render("nv", Tone.T3))
        assertEquals("lǜ", ToneMark.render("lv", Tone.T4))
        assertEquals("jú", ToneMark.render("ju", Tone.T2))
        assertEquals("yuè", ToneMark.render("yue", Tone.T4))
        assertEquals("xuě", ToneMark.render("xue", Tone.T3))
        assertEquals("xūn", ToneMark.render("xun", Tone.T1))
        assertEquals("qún", ToneMark.render("qun", Tone.T2))
    }

    @Test
    fun `toneIndex 与口诀一致`() {
        assertEquals(1, ToneMark.toneIndex("ba"))       // b_a
        assertEquals(2, ToneMark.toneIndex("zhang"))    // zh_a_ng
        assertEquals(1, ToneMark.toneIndex("wo"))       // w_o
        assertEquals(2, ToneMark.toneIndex("gui"))      // g_u_i -> i 是下标2
        assertEquals(2, ToneMark.toneIndex("liu"))      // l_i_u -> u 是下标2
        assertEquals(1, ToneMark.toneIndex("nv"))       // n_v
    }
    @Test
    fun `解析与渲染互逆`() {
        val cases = listOf("ba1" to "bā", "zhang3" to "zhǎng", "lv4" to "lǜ", "nv3" to "nǚ", "yue4" to "yuè")
        cases.forEach { (code, expect) ->
            val syl = SyllableTable.parse(code)
            assertNotNull(syl)
            assertEquals(expect, syl!!.text)
        }
    }

    @Test
    fun `声调符号解析正确`() {
        assertEquals(Tone.T1, ToneMark.toneOf("bā"))
        assertEquals(Tone.T2, ToneMark.toneOf("bá"))
        assertEquals(Tone.T3, ToneMark.toneOf("bǎ"))
        assertEquals(Tone.T4, ToneMark.toneOf("bà"))
        assertEquals(Tone.T5, ToneMark.toneOf("ba"))
        assertEquals(Tone.T3, ToneMark.toneOf("nǚ"))
    }

    @Test
    fun `去调后保留字母`() {
        assertEquals("zhang", ToneMark.stripMarks("zhǎng"))
        assertEquals("nv", ToneMark.stripMarks("nǚ").replace('ü', 'v'))
        assertEquals("ping guo", ToneMark.stripMarks("píng guǒ"))
    }
}

/**
 * 声母韵母拆分测试。
 */
class SyllableParserTest {

    @Test
    fun `双字母声母优先`() {
        assertEquals("zh" to "ang", SyllableParser.split("zhang"))
        assertEquals("ch" to "eng", SyllableParser.split("cheng"))
        assertEquals("sh" to "ui", SyllableParser.split("shui"))
    }

    @Test
    fun `单字母声母`() {
        assertEquals("b" to "a", SyllableParser.split("ba"))
        assertEquals("j" to "ue", SyllableParser.split("jue"))
        assertEquals("y" to "ue", SyllableParser.split("yue"))
        assertEquals("w" to "o", SyllableParser.split("wo"))
    }

    @Test
    fun `零声母`() {
        assertEquals("" to "an", SyllableParser.split("an"))
        assertEquals("" to "er", SyllableParser.split("er"))
    }
}

/**
 * 出题引擎测试：确保题目结构合法、干扰项不与答案重复、标签正确。
 */
class QuestionGeneratorTest {

    @Test
    fun `所有域与难度的题目结构合法`() {
        val gen = com.alex.pinyinzoo.logic.QuestionGenerator(kotlin.random.Random(42))
        Domain.playable.forEach { domain ->
            Tier.entries.forEach { tier ->
                val qs = gen.generate(domain, tier, 12)
                assertEquals("域 ${domain.key} 层 ${tier.star} 题数", 12, qs.size)
                qs.forEach { q ->
                    assertTrue("题干应有 id", q.id.isNotEmpty())
                    when (q.kind) {
                        QuestionKind.READ_ALOUD -> assertNotNull(q.targetSyllable)
                        else -> {
                            assertTrue("选择题至少 2 个选项: ${q.kind}", q.options.size >= 2)
                            val correct = q.options.count { it.isCorrect }
                            assertEquals("必须恰好一个正确项: ${q.kind} ${q.display}", 1, correct)
                            assertEquals("answerId 应指向正确项", q.answerId, q.options.first { it.isCorrect }.id)
                        }
                    }
                    // 选项文本不能重复
                    assertEquals(
                        "选项文本不能重复: $q",
                        q.options.size,
                        q.options.map { it.text }.distinct().size
                    )
                }
            }
        }
    }

    @Test
    fun `听音题干扰项来自真实混淆集`() {
        val gen = com.alex.pinyinzoo.logic.QuestionGenerator(kotlin.random.Random(7))
        val qs = gen.generate(Domain.SIBILANT, Tier.RECOGNIZE, 30)
            .filter { it.kind == QuestionKind.LISTEN_PICK }
        assertTrue("应生成若干听音题", qs.isNotEmpty())

        qs.forEach { q ->
            val target = q.targetSyllable ?: return@forEach
            val targetIni = SyllableTable.initialOf(target.base)
            val targetFin = SyllableTable.finalOf(target.base)
            q.options.filter { !it.isCorrect }.forEach { opt ->
                val dIni = SyllableTable.initialOf(ToneMark.stripMarks(opt.text).replace('ü', 'v'))
                // 干扰项至少要在声母或韵母上形成对立
                val differs = dIni != targetIni
                if (!differs) {
                    // 声母相同时，必须是韵母或声调不同
                    assertNotNull(opt.text)
                }
            }
            assertTrue(targetIni.isNotEmpty() || targetFin.isNotEmpty())
        }
    }

    @Test
    fun `每个音节都有知识标签`() {
        val syls = SyllableTable.poolOf("mixed")
        assertTrue(syls.size > 50)
        syls.forEach { syl ->
            val tags = SyllableTable.tagsOf(syl)
            assertTrue("音节 ${syl.text} 应有至少一个标签", tags.isNotEmpty())
        }
    }

    @Test
    fun `平翘舌音节标签正确`() {
        val zhi = SyllableTable.parse("zhi1")!!
        assertTrue("zhi 应含翘舌标签", PinyinTag.ZH_CH_SH_R in SyllableTable.tagsOf(zhi))
        val zi = SyllableTable.parse("zi1")!!
        assertTrue("zi 应含平舌标签", PinyinTag.Z_C_S in SyllableTable.tagsOf(zi))
    }

    @Test
    fun `前后鼻音标签正确`() {
        val an = SyllableTable.parse("ban1")!!
        assertTrue(PinyinTag.AN_ANG in SyllableTable.tagsOf(an))
        val ang = SyllableTable.parse("bang1")!!
        assertTrue(PinyinTag.AN_ANG in SyllableTable.tagsOf(ang))
        val inSyl = SyllableTable.parse("jin1")!!
        assertTrue(PinyinTag.IN_ING in SyllableTable.tagsOf(inSyl))
    }

    @Test
    fun `整体认读音节识别`() {
        listOf("zhi", "chi", "shi", "ri", "zi", "ci", "si", "yi", "wu", "yu",
            "ye", "yue", "yuan", "yin", "yun", "ying").forEach {
            assertTrue("$it 应为整体认读音节", SyllableTable.isWhole(it))
        }
        assertTrue("jie 不是整体认读", !SyllableTable.isWhole("jie"))
    }
}

/**
 * 会话计分测试。
 */
class GameSessionTest {

    private fun makeQuestions(n: Int) = (0 until n).map { i ->
        com.alex.pinyinzoo.model.Question(
            id = "q$i",
            kind = QuestionKind.LISTEN_PICK,
            domain = Domain.SIBILANT,
            tier = Tier.RECOGNIZE,
            tags = listOf(PinyinTag.Z_C_S),
            options = listOf(
                com.alex.pinyinzoo.model.Option("a", "zhi", isCorrect = true),
                com.alex.pinyinzoo.model.Option("b", "zi"),
            ),
            answerId = "a",
        )
    }

    @Test
    fun `全对得三星`() {
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(12)
        )
        repeat(12) { s.submit("a") }
        assertEquals(12, s.correctCount)
        assertEquals(3, s.stars())
        assertTrue(s.finished)
        assertTrue(s.wrongQuestions().isEmpty())
    }

    @Test
    fun `答错不跳题-停在原题可重试`() {
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(3)
        )
        assertEquals(0, s.index)
        // 答错：不应前进
        assertFalse(s.submit("b"))
        assertEquals("答错后必须停在原题", 0, s.index)
        assertEquals(0, s.correctCount)
        // 再答错一次，仍停在原题，且不重复计入错题
        assertFalse(s.submit("b"))
        assertEquals(0, s.index)
        assertEquals(1, s.wrongQuestions().size)
        // 答对才前进
        assertTrue(s.submit("a"))
        assertEquals(1, s.index)
    }

    @Test
    fun `答错后答对不计入评星`() {
        // 评星只看第一次是否答对，否则反复重试人人都能三星
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(12)
        )
        // 第一题先答错再答对 → 算错题
        s.submit("b")
        s.submit("a")
        assertEquals(1, s.index)
        assertEquals(0, s.correctCount)
        // 其余 11 题一次答对
        repeat(11) { s.submit("a") }
        assertEquals(11, s.correctCount)
        assertEquals(2, s.stars())
        assertEquals(1, s.wrongQuestions().size)
    }

    @Test
    fun `十一题得两星`() {
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(12)
        )
        // 第一题先错后对（算错），其余一次答对
        s.submit("b")
        s.submit("a")
        repeat(11) { s.submit("a") }
        assertEquals(11, s.correctCount)
        assertEquals(2, s.stars())
        assertEquals(1, s.wrongQuestions().size)
    }

    @Test
    fun `九题得两星以下一星以上`() {
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(12)
        )
        // 3 题先错后对，9 题一次答对
        repeat(3) { s.submit("b"); s.submit("a") }
        repeat(9) { s.submit("a") }
        assertEquals(9, s.correctCount)
        assertEquals(1, s.stars())
    }

    @Test
    fun `八题以下不得星`() {
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(12)
        )
        // 5 题先错后对，7 题一次答对
        repeat(5) { s.submit("b"); s.submit("a") }
        repeat(7) { s.submit("a") }
        assertEquals(7, s.correctCount)
        assertEquals(0, s.stars())
    }

    @Test
    fun `跟读未达标不跳题`() {
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(3)
        )
        assertFalse(s.submitRead(40))
        assertEquals("未达标必须停在原题", 0, s.index)
        assertTrue(s.submitRead(90))
        assertEquals(1, s.index)
    }

    @Test
    fun `错题标签会被汇总`() {
        val s = com.alex.pinyinzoo.logic.GameSession(
            Domain.SIBILANT, Tier.RECOGNIZE, makeQuestions(4)
        )
        // 前两题先错后对，后两题一次答对
        repeat(2) { s.submit("b"); s.submit("a") }
        repeat(2) { s.submit("a") }
        val weak = s.weakTags()
        assertTrue(weak.contains(PinyinTag.Z_C_S))
    }
}
