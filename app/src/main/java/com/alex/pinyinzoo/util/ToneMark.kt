package com.alex.pinyinzoo.util

import com.alex.pinyinzoo.model.Tone

/**
 * 拼音声调符号渲染与「标调位置」规则。
 *
 * 标调口诀：有 a 不放过，没 a 找 o e，i u 并列标在后。
 * 这里把口诀实现成可测试的纯函数，既用于渲染，也用于「标调位置」题型判分。
 */
object ToneMark {

    private val MARKS = mapOf(
        'a' to "āáǎà",
        'e' to "ēéěè",
        'i' to "īíǐì",
        'o' to "ōóǒò",
        'u' to "ūúǔù",
        'v' to "ǖǘǚǜ",
    )

    /** 判断字符是否为可带调的元音（含 ü） */
    fun isVowel(c: Char): Boolean = c in "aeiouv"

    /**
     * 计算声调应标在哪个下标上（base 中不含声调符号，ü 用 v 表示）。
     * 返回 -1 表示没有元音（如 "hm"）。
     *
     * 口诀落地：
     * - 有 a → 标 a
     * - 没 a → 标 o 或 e
     * - iu / ui → 标在后面那个（iu 标 u，ui 标 i），两者都在末尾
     * - 其余 → 标在最后一个元音上（i、u、v、in、ing、un 等）
     */
    fun toneIndex(base: String): Int {
        if (base.isEmpty()) return -1
        base.indexOf('a').let { if (it >= 0) return it }
        base.indexOf('o').let { if (it >= 0) return it }
        base.indexOf('e').let { if (it >= 0) return it }
        // iu 标 u、ui 标 i，都是该韵母的最后一个字母，与"标在最后元音"一致
        for (i in base.length - 1 downTo 0) {
            if (isVowel(base[i])) return i
        }
        return -1
    }

    /** 把 "nv" + 3 渲染成 "nǚ" */
    fun render(base: String, tone: Tone): String {
        if (tone == Tone.T5) return display(base)
        val idx = toneIndex(base)
        val plain = display(base)
        if (idx < 0) return plain
        // base 与 display 长度一致（v->ü 是单字符替换），可直接用下标
        val vowels = MARKS[base[idx]] ?: return plain
        val marked = vowels[tone.num - 1]
        return plain.substring(0, idx) + marked + plain.substring(idx + 1)
    }

    /** 把内部 v 写法转成显示用 ü */
    fun display(base: String): String = base.replace('v', 'ü')

    /** 去掉声调符号，得到纯字母（ü 保留） */
    fun stripMarks(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            var replaced = false
            for ((vowel, marks) in MARKS) {
                val i = marks.indexOf(c)
                if (i >= 0) {
                    sb.append(if (vowel == 'v') 'ü' else vowel)
                    replaced = true
                    break
                }
            }
            if (!replaced) sb.append(c)
        }
        return sb.toString()
    }

    /** 从带调文本解析出声调号；无符号返回 5（轻声） */
    fun toneOf(text: String): Tone {
        for ((_, marks) in MARKS) {
            for ((i, c) in marks.withIndex()) {
                if (text.contains(c)) return Tone.of(i + 1)
            }
        }
        return Tone.T5
    }

    /** 显示用的声调符号（用于「判声调」题型的按钮） */
    fun markSymbol(tone: Tone): String = when (tone) {
        Tone.T1 -> "ˉ"
        Tone.T2 -> "ˊ"
        Tone.T3 -> "ˇ"
        Tone.T4 -> "ˋ"
        Tone.T5 -> "˙"
    }
}

/** 声母 / 韵母 拆分 */
object SyllableParser {

    private val DOUBLE = listOf("zh", "ch", "sh")
    private val SINGLE = listOf(
        "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
        "j", "q", "x", "r", "z", "c", "s", "y", "w",
    )

    /** 返回 声母（可能为空）+ 韵母 */
    fun split(base: String): Pair<String, String> {
        for (d in DOUBLE) {
            if (base.startsWith(d)) return d to base.substring(d.length)
        }
        for (s in SINGLE) {
            if (base.startsWith(s)) return s to base.substring(s.length)
        }
        return "" to base
    }

    fun initial(base: String): String = split(base).first

    fun final(base: String): String = split(base).second
}
