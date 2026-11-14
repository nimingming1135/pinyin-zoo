package com.alex.pinyinzoo.logic

import com.alex.pinyinzoo.content.SyllableTable
import com.alex.pinyinzoo.content.WordBank
import com.alex.pinyinzoo.content.WordEntry
import com.alex.pinyinzoo.model.Domain
import com.alex.pinyinzoo.model.Option
import com.alex.pinyinzoo.model.PinyinTag
import com.alex.pinyinzoo.model.Question
import com.alex.pinyinzoo.model.QuestionKind
import com.alex.pinyinzoo.model.Syllable
import com.alex.pinyinzoo.model.Tier
import com.alex.pinyinzoo.model.Tone
import com.alex.pinyinzoo.util.ToneMark
import kotlin.random.Random

/**
 * 出题引擎。
 *
 * 设计原则：
 * 1. 干扰项只能来自「真实混淆集」（Confusables），不用随机噪音——否则测不出问题；
 * 2. 每道题必须携带 PinyinTag，用于错题谱系统计与个性化出题；
 * 3. 同一层内不重复考同一个知识点，保证覆盖面；
 * 4. 纯函数式、无 Android 依赖，可被 JUnit 直接测试。
 */
class QuestionGenerator(private val rng: Random = Random.Default) {

    /** 生成一整套题 */
    fun generate(
        domain: Domain,
        tier: Tier,
        count: Int,
        favoriteWrongCodes: List<String> = emptyList(),
    ): List<Question> {
        val out = mutableListOf<Question>()
        val usedSyllables = mutableSetOf<String>()
        val pool = buildPool(domain, tier, favoriteWrongCodes)

        // 按题型轮转，保证题型多样
        val kinds = kindsFor(domain, tier)
        var kindIdx = 0
        var poolIdx = 0

        while (out.size < count) {
            val kind = kinds[kindIdx % kinds.size]
            kindIdx++

            var syl: Syllable? = null
            var guard = 0
            while (guard < pool.size && syl == null) {
                val c = pool[poolIdx % pool.size]
                poolIdx++
                guard++
                if (c.base + c.tone.num !in usedSyllables || guard > pool.size) {
                    syl = c
                }
            }
            syl = syl ?: pool.random(rng)
            usedSyllables += syl.base + syl.tone.num

            val q = when (kind) {
                QuestionKind.LISTEN_PICK -> listenPick(syl, domain, tier, out.size)
                QuestionKind.TONE_PICK -> tonePick(syl, domain, tier, out.size)
                QuestionKind.BLEND -> blend(syl, domain, tier, out.size)
                QuestionKind.FILL_FINAL -> fillFinal(syl, domain, tier, out.size)
                QuestionKind.TONE_MARK -> toneMark(syl, domain, tier, out.size)
                QuestionKind.WORD_PICK -> wordPick(domain, tier, out.size)
                QuestionKind.READ_ALOUD -> readAloud(syl, domain, tier, out.size)
                QuestionKind.MATCH -> wordPick(domain, tier, out.size)
            }
            if (q != null) out += q
        }
        return out
    }

    // ---------------------------------------------------------------- 题池

    private fun buildPool(domain: Domain, tier: Tier, extra: List<String>): List<Syllable> {
        val base = SyllableTable.poolFor(domain.key, tier.star).toMutableList()
        val extraSyls = extra.mapNotNull { SyllableTable.parse(it) }
        return (base + extraSyls).distinctBy { it.base + it.tone.num }
            .ifEmpty { SyllableTable.poolOf("mixed") }
    }

    /** 每个域在不同难度下用哪些题型 */
    private fun kindsFor(domain: Domain, tier: Tier): List<QuestionKind> {
        val common = listOf(QuestionKind.LISTEN_PICK, QuestionKind.TONE_PICK)
        return when (domain) {
            Domain.SIBILANT -> when (tier) {
                Tier.RECOGNIZE -> common + QuestionKind.FILL_FINAL
                Tier.BLEND -> common + QuestionKind.FILL_FINAL + QuestionKind.BLEND
                Tier.APPLY -> common + QuestionKind.WORD_PICK + QuestionKind.READ_ALOUD
            }
            Domain.NASAL -> when (tier) {
                Tier.RECOGNIZE -> common + QuestionKind.FILL_FINAL
                Tier.BLEND -> common + QuestionKind.FILL_FINAL + QuestionKind.BLEND
                Tier.APPLY -> common + QuestionKind.WORD_PICK + QuestionKind.READ_ALOUD
            }
            Domain.TONE -> when (tier) {
                Tier.RECOGNIZE -> listOf(QuestionKind.TONE_PICK, QuestionKind.LISTEN_PICK)
                Tier.BLEND -> listOf(QuestionKind.TONE_PICK, QuestionKind.TONE_MARK)
                Tier.APPLY -> listOf(QuestionKind.TONE_PICK, QuestionKind.TONE_MARK, QuestionKind.READ_ALOUD)
            }
            Domain.UMLAUT -> when (tier) {
                Tier.RECOGNIZE -> common + QuestionKind.FILL_FINAL
                Tier.BLEND -> common + QuestionKind.BLEND + QuestionKind.TONE_MARK
                Tier.APPLY -> common + QuestionKind.WORD_PICK + QuestionKind.READ_ALOUD
            }
            Domain.FINAL -> when (tier) {
                Tier.RECOGNIZE -> common + QuestionKind.FILL_FINAL
                Tier.BLEND -> common + QuestionKind.BLEND + QuestionKind.FILL_FINAL
                Tier.APPLY -> common + QuestionKind.WORD_PICK
            }
            Domain.INITIAL -> when (tier) {
                Tier.RECOGNIZE -> common + QuestionKind.BLEND
                Tier.BLEND -> common + QuestionKind.BLEND + QuestionKind.FILL_FINAL
                Tier.APPLY -> common + QuestionKind.WORD_PICK
            }
            Domain.WHOLE -> when (tier) {
                Tier.RECOGNIZE -> common + QuestionKind.FILL_FINAL
                Tier.BLEND -> listOf(QuestionKind.LISTEN_PICK, QuestionKind.MATCH)
                Tier.APPLY -> listOf(QuestionKind.WORD_PICK, QuestionKind.READ_ALOUD)
            }
            Domain.MIXED -> listOf(
                QuestionKind.LISTEN_PICK, QuestionKind.WORD_PICK,
                QuestionKind.TONE_PICK, QuestionKind.FILL_FINAL,
                QuestionKind.BLEND, QuestionKind.TONE_MARK,
                QuestionKind.READ_ALOUD,
            )
        }
    }

    // ---------------------------------------------------------------- 题型实现

    /** 听音选音节 */
    private fun listenPick(syl: Syllable, domain: Domain, tier: Tier, idx: Int): Question {
        val correct = Option(
            id = "o0",
            text = syl.text,
            audioKey = syl.audioKey,
            isCorrect = true,
        )
        val distractors = distractorsFor(syl, 3)
        val options = (listOf(correct) + distractors.mapIndexed { i, d ->
            Option("o${i + 1}", d.text, audioKey = d.audioKey)
        }).shuffled(rng)

        return Question(
            id = "q_${domain.key}_${tier.star}_$idx",
            kind = QuestionKind.LISTEN_PICK,
            domain = domain,
            tier = tier,
            tags = SyllableTable.tagsOf(syl),
            prompt = "听一听，选出正确的读音",
            promptAudio = syl.audioKey,
            hint = "点小喇叭可以再听一次",
            options = options,
            answerId = options.first { it.isCorrect }.id,
            targetSyllable = syl,
        )
    }

    /** 判声调 */
    private fun tonePick(syl: Syllable, domain: Domain, tier: Tier, idx: Int): Question {
        // 只考 1~4 声（轻声单独成题，避免干扰项混淆）
        val baseTone = if (syl.tone == Tone.T5) Tone.of(rng.nextInt(1, 4)) else syl.tone
        val tones = listOf(Tone.T1, Tone.T2, Tone.T3, Tone.T4)
        val options = tones.map { t ->
            val s = Syllable(syl.base, t, ToneMark.render(syl.base, t))
            Option(
                id = "o${t.num}",
                text = t.mark,
                hint = t.label,
                audioKey = s.audioKey,
                isCorrect = t == baseTone,
            )
        }
        val shown = Syllable(syl.base, baseTone, ToneMark.render(syl.base, baseTone))
        return Question(
            id = "q_${domain.key}_${tier.star}_$idx",
            kind = QuestionKind.TONE_PICK,
            domain = domain,
            tier = tier,
            tags = SyllableTable.tagsOf(shown),
            prompt = "听一听，这是第几声？",
            promptAudio = shown.audioKey,
            display = ToneMark.display(shown.base),
            hint = "先听声音的高低变化，再选",
            options = options,
            answerId = "o${baseTone.num}",
            targetSyllable = shown,
        )
    }

    /** 拼合积木：声母 + 韵母 */
    private fun blend(syl: Syllable, domain: Domain, tier: Tier, idx: Int): Question {
        val ini = SyllableTable.initialOf(syl.base)
        val fin = SyllableTable.finalOf(syl.base)
        if (ini.isEmpty() || fin.isEmpty()) {
            return listenPick(syl, domain, tier, idx)
        }
        // 正确选项 + 用易混对做干扰
        val rightFin = fin
        val confFin = SyllableTable.FINAL_CONFUSABLES[rightFin].orEmpty()
            .filter { it != rightFin }
            .take(2)
        val confIni = SyllableTable.CONFUSABLES[ini].orEmpty().filter { it != ini }.take(1)

        val finCandidates = (listOf(rightFin) + confFin).distinct()
        val extraFin = listOf("a", "e", "i", "u", "ai", "ao", "an", "ang", "en", "eng", "in", "ing", "ui", "iu", "ie", "ve", "ong", "un")
            .filter { it !in finCandidates }
        val allFins = (finCandidates + extraFin.shuffled(rng)).take(4)

        val options = mutableListOf<Option>()
        allFins.forEachIndexed { i, f ->
            val s = Syllable(ini + f, syl.tone, ToneMark.render(ini + f, syl.tone))
            options += Option(
                id = "o$i",
                text = ToneMark.display(f),
                audioKey = s.audioKey,
                isCorrect = f == rightFin,
                hint = if (confIni.isNotEmpty() && f == rightFin) "" else "",
            )
        }
        val shuffled = options.shuffled(rng)
        return Question(
            id = "q_${domain.key}_${tier.star}_$idx",
            kind = QuestionKind.BLEND,
            domain = domain,
            tier = tier,
            tags = SyllableTable.tagsOf(syl),
            prompt = "拼一拼：${ToneMark.display(ini)} ＋ ？ ＝ ${syl.text}",
            promptAudio = null,
            display = "${ToneMark.display(ini)}  ＋  ?  ＝  ${syl.text}",
            hint = "想想哪个韵母能拼成这个音",
            options = shuffled,
            answerId = shuffled.first { it.isCorrect }.id,
            targetSyllable = syl,
        )
    }

    /**
     * 补全音节：把整个韵母挖空，从易混韵母里挑。
     * 这是「前后鼻音 / 复韵母」最直接的考法。
     */
    private fun fillFinal(syl: Syllable, domain: Domain, tier: Tier, idx: Int): Question {
        val ini = SyllableTable.initialOf(syl.base)
        val fin = SyllableTable.finalOf(syl.base)
        if (ini.isEmpty() || fin.isEmpty()) {
            return listenPick(syl, domain, tier, idx)
        }

        // 干扰项优先取易混韵母，其次同韵尾的相近韵母
        val picked = linkedSetOf(fin)
        SyllableTable.FINAL_CONFUSABLES[fin].orEmpty().forEach { c -> if (picked.size < 4) picked += c }
        if (picked.size < 4) {
            val sameShape = SyllableTable.FINALS.keys.filter { f ->
                f != fin && (
                    // 前后鼻音 / 复韵母结构相近
                    (f.endsWith("ng") && fin.endsWith("ng")) ||
                        (f.endsWith("n") && fin.endsWith("n") && !f.endsWith("ng")) ||
                        (f.length == fin.length)
                    )
            }
            sameShape.shuffled(rng).forEach { f -> if (picked.size < 4) picked += f }
        }
        val choices = picked.take(4).shuffled(rng)

        val options = choices.mapIndexed { i, f ->
            val fullBase = ini + f
            val s = Syllable(fullBase, syl.tone, ToneMark.render(fullBase, syl.tone))
            Option(
                id = "o$i",
                text = ToneMark.display(f),
                audioKey = s.audioKey,
                isCorrect = f == fin,
            )
        }
        return Question(
            id = "q_${domain.key}_${tier.star}_$idx",
            kind = QuestionKind.FILL_FINAL,
            domain = domain,
            tier = tier,
            tags = SyllableTable.tagsOf(syl),
            prompt = "缺了韵母，补上它",
            promptAudio = syl.audioKey,
            display = "${ToneMark.display(ini)} ＿  ·  ${syl.text}",
            hint = "听一遍，注意鼻音和韵母",
            options = options,
            answerId = options.first { it.isCorrect }.id,
            targetSyllable = syl,
        )
    }

    /** 标调位置：声调标在哪个字母上 */
    private fun toneMark(syl: Syllable, domain: Domain, tier: Tier, idx: Int): Question {
        val base = syl.base
        val plain = ToneMark.display(base)
        val correctIdx = ToneMark.toneIndex(base)
        if (correctIdx < 0) return listenPick(syl, domain, tier, idx)

        // 每个元音位置生成一个"把调标在这里"的候选写法
        val vowelIdx = base.indices.filter { base[it] in "aeiouv" }
        if (vowelIdx.size < 2) return tonePick(syl, domain, tier, idx)

        val options = vowelIdx.mapIndexed { i, vi ->
            val single = base[vi].toString()
            val markedChar = ToneMark.render(single, syl.tone)
            val shown = plain.substring(0, vi) + markedChar + plain.substring(vi + 1)
            Option(
                id = "o$i",
                text = shown,
                isCorrect = vi == correctIdx,
            )
        }

        return Question(
            id = "q_${domain.key}_${tier.star}_$idx",
            kind = QuestionKind.TONE_MARK,
            domain = domain,
            tier = tier,
            tags = SyllableTable.tagsOf(syl) + PinyinTag.TONE_PLACEMENT,
            prompt = "声调应该标在哪个字母上？",
            promptAudio = syl.audioKey,
            display = plain + "  ·  " + syl.tone.label,
            hint = "有 a 不放过，没 a 找 o e，i u 并列标在后",
            options = options,
            answerId = options.first { it.isCorrect }.id,
            targetSyllable = syl,
        )
    }

    /** 看词选拼音 */
    private fun wordPick(domain: Domain, tier: Tier, idx: Int): Question? {
        val words = WordBank.byDomain(domain.key).ifEmpty { WordBank.ALL }
        val word = words.random(rng)
        val correctPinyin = word.pinyin

        // 干扰项：改动某一个音节的易混点
        val distractors = mutableListOf<String>()
        val parts = word.syllables.toMutableList()
        var guard = 0
        while (distractors.size < 3 && guard < 30) {
            guard++
            val pos = rng.nextInt(parts.size)
            val changed = mutateSyllable(parts[pos]) ?: continue
            val candidate = parts.toMutableList().also { it[pos] = changed }.joinToString(" ")
            if (candidate != correctPinyin && candidate !in distractors) distractors += candidate
        }
        if (distractors.size < 3) {
            // 兜底：只改第一个音节的声调
            repeat(3) {
                val t = Tone.of(rng.nextInt(1, 5))
                val stripped = ToneMark.stripMarks(parts[0]).replace('ü', 'v')
                if (ToneMark.toneOf(parts[0]) == t) return@repeat
                val candidate = parts.toMutableList().also { it[0] = ToneMark.render(stripped, t) }
                    .joinToString(" ")
                if (candidate != correctPinyin && candidate !in distractors) distractors += candidate
            }
        }

        val all = (listOf(correctPinyin) + distractors).distinct().shuffled(rng)
        val options = all.mapIndexed { i, p ->
            Option(id = "o$i", text = p, isCorrect = p == correctPinyin)
        }
        val answer = options.firstOrNull { it.isCorrect } ?: return null

        val tags = mutableListOf<PinyinTag>()
        SyllableTable.parse(ToneMark.stripMarks(word.syllables[0]).replace('ü', 'v') + ToneMark.toneOf(word.syllables[0]).num)
            ?.let { tags += SyllableTable.tagsOf(it) }

        return Question(
            id = "q_${domain.key}_${tier.star}_$idx",
            kind = QuestionKind.WORD_PICK,
            domain = domain,
            tier = tier,
            tags = tags.distinct(),
            prompt = "这个词怎么拼？",
            promptAudio = "word/${word.audioKey}",
            display = word.hanzi,
            hint = "一个字一个字慢慢拼",
            options = options,
            answerId = answer.id,
        )
    }

    /** 跟读题 */
    private fun readAloud(syl: Syllable, domain: Domain, tier: Tier, idx: Int): Question {
        return Question(
            id = "q_${domain.key}_${tier.star}_$idx",
            kind = QuestionKind.READ_ALOUD,
            domain = domain,
            tier = tier,
            tags = SyllableTable.tagsOf(syl),
            prompt = "跟着读一遍",
            promptAudio = syl.audioKey,
            display = syl.text,
            hint = "先听标准音，再按住话筒读出来",
            targetSyllable = syl,
        )
    }

    // ---------------------------------------------------------------- 辅助

    /** 从真实混淆集取 3 个干扰音节 */
    private fun distractorsFor(syl: Syllable, n: Int): List<Syllable> {
        val ini = SyllableTable.initialOf(syl.base)
        val fin = SyllableTable.finalOf(syl.base)
        val out = mutableListOf<Syllable>()

        fun tryAdd(base: String) {
            if (out.size >= n) return
            if (base == syl.base) return
            if (base.isEmpty()) return
            val s = Syllable(base, syl.tone, ToneMark.render(base, syl.tone))
            if (out.none { it.base == s.base && it.tone == s.tone }) out += s
        }

        // 策略 1：换声母（平翘舌、n/l、f/h 等）
        SyllableTable.CONFUSABLES[ini].orEmpty().forEach { ci ->
            tryAdd(ci + fin)
        }
        // 策略 2：换韵母（前后鼻音等）
        SyllableTable.FINAL_CONFUSABLES[fin].orEmpty().forEach { cf ->
            tryAdd(ini + cf)
        }
        // 策略 3：换声调
        if (out.size < n) {
            listOf(Tone.T1, Tone.T2, Tone.T3, Tone.T4)
                .filter { it != syl.tone }
                .shuffled(rng)
                .forEach { t ->
                    if (out.size < n) {
                        val s = Syllable(syl.base, t, ToneMark.render(syl.base, t))
                        if (out.none { it.base == s.base && it.tone == s.tone }) out += s
                    }
                }
        }
        // 策略 4：同域其他音节兜底
        if (out.size < n) {
            SyllableTable.poolOf("mixed")
                .filter { it.base != syl.base && it.base + it.tone.num != syl.base + syl.tone.num }
                .shuffled(rng)
                .forEach { if (out.size < n) out += it }
        }
        return out.distinctBy { it.base + it.tone.num }.take(n)
    }

    /** 改坏一个音节（用于生成错误拼音） */
    private fun mutateSyllable(syl: String): String? {
        val stripped = ToneMark.stripMarks(syl).replace('ü', 'v')
        val tone = ToneMark.toneOf(syl)
        val ini = SyllableTable.initialOf(stripped)
        val fin = SyllableTable.finalOf(stripped)

        // 40% 换声母，40% 换韵母，20% 换声调
        val r = rng.nextFloat()
        return when {
            r < 0.4f -> {
                val c = SyllableTable.CONFUSABLES[ini]?.randomOrNull(rng) ?: return null
                ToneMark.render(c + fin, tone)
            }
            r < 0.8f -> {
                val c = SyllableTable.FINAL_CONFUSABLES[fin]?.randomOrNull(rng) ?: return null
                ToneMark.render(ini + c, tone)
            }
            else -> {
                val nt = Tone.of(if (tone.num >= 4) 1 else tone.num + 1)
                ToneMark.render(stripped, nt)
            }
        }
    }
}
