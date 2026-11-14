package com.alex.pinyinzoo.content

import com.alex.pinyinzoo.model.PinyinTag
import com.alex.pinyinzoo.model.Syllable
import com.alex.pinyinzoo.model.Tone
import com.alex.pinyinzoo.util.SyllableParser
import com.alex.pinyinzoo.util.ToneMark

/**
 * 小学拼音音节表。
 *
 * 只收录一年级声韵拼合表内、以及日常高频的音节，保证：
 * - 每个音节都有对应音频（音频由 tools/build_audio.py 按 base+tone 生成）；
 * - 干扰项能从「真实混淆集」里取，而不是随机噪音。
 */
object SyllableTable {

    /** 声母表（含教学呼读音） */
    val INITIALS: Map<String, String> = linkedMapOf(
        "b" to "玻", "p" to "坡", "m" to "摸", "f" to "佛",
        "d" to "得", "t" to "特", "n" to "讷", "l" to "勒",
        "g" to "哥", "k" to "科", "h" to "喝",
        "j" to "基", "q" to "欺", "x" to "希",
        "zh" to "知", "ch" to "吃", "sh" to "诗", "r" to "日",
        "z" to "资", "c" to "次", "s" to "思",
        "y" to "衣", "w" to "乌",
    )

    /** 韵母表 */
    val FINALS: Map<String, String> = linkedMapOf(
        "a" to "啊", "o" to "哦", "e" to "鹅", "i" to "衣", "u" to "乌", "v" to "迂",
        "ai" to "哀", "ei" to "诶", "ui" to "威", "ao" to "熬", "ou" to "欧", "iu" to "优",
        "ie" to "耶", "ve" to "约", "er" to "儿",
        "an" to "安", "en" to "恩", "in" to "因", "un" to "温", "vn" to "晕",
        "ang" to "昂", "eng" to "鞥", "ing" to "英", "ong" to "翁",
    )

    /** 16 个整体认读音节 */
    val WHOLE: List<String> = listOf(
        "zhi", "chi", "shi", "ri", "zi", "ci", "si",
        "yi", "wu", "yu", "ye", "yue", "yuan", "yin", "yun", "ying",
    )

    /** 平舌音 */
    val FLAT = setOf("z", "c", "s")

    /** 翘舌音 */
    val CURL = setOf("zh", "ch", "sh", "r")

    /** 易混对：真实混淆集，用于生成干扰项 */
    val CONFUSABLES: Map<String, List<String>> = mapOf(
        // 平翘舌
        "zh" to listOf("z"), "z" to listOf("zh"),
        "ch" to listOf("c"), "c" to listOf("ch"),
        "sh" to listOf("s"), "s" to listOf("sh"),
        // 形近 / 方言
        "b" to listOf("p", "d"), "p" to listOf("b", "q"),
        "d" to listOf("t", "b"), "t" to listOf("d", "p"),
        "q" to listOf("p", "j"), "j" to listOf("q", "x"),
        "x" to listOf("j", "s"), "f" to listOf("h"), "h" to listOf("f"),
        "n" to listOf("l"), "l" to listOf("n"),
        "g" to listOf("k"), "k" to listOf("g"),
    )

    /** 韵母易混对 */
    val FINAL_CONFUSABLES: Map<String, List<String>> = mapOf(
        "an" to listOf("ang"), "ang" to listOf("an"),
        "en" to listOf("eng"), "eng" to listOf("en"),
        "in" to listOf("ing"), "ing" to listOf("in"),
        "un" to listOf("ong"), "ong" to listOf("un"),
        "ui" to listOf("iu"), "iu" to listOf("ui"),
        "ie" to listOf("ve"), "ve" to listOf("ie"),
        "ai" to listOf("ei"), "ei" to listOf("ai"),
        "ao" to listOf("ou"), "ou" to listOf("ao"),
        "u" to listOf("v"), "v" to listOf("u"),
    )

    /** 常用带调音节池（按域分组，出题时从这里取） */
    private val POOL: Map<String, List<String>> = mapOf(
        // 平翘舌对比（成对的最小对立对）
        "sibilant" to listOf(
            "zhi1", "zi1", "zha1", "za1", "zhe2", "ze2", "zhi4", "zi4",
            "chi1", "ci1", "cha2", "ca2", "che1", "ce1", "chi4", "ci4",
            "shi1", "si1", "sha1", "sa1", "she1", "se1", "shi4", "si4",
            "zhu1", "zu1", "chu1", "cu1", "shu1", "su1", "ri4",
            "zhang3", "zang4", "chang2", "cang2", "shang4", "sang1",
            "zhao3", "zao3", "chao2", "cao2", "shao3", "sao3",
            "zhong1", "zong1", "chong2", "cong2", "sheng1", "seng1",
        ),
        "nasal" to listOf(
            "ban1", "bang1", "pan2", "pang2", "man3", "mang2",
            "dan4", "dang4", "tan2", "tang2", "nan2", "nang2",
            "lan2", "lang2", "gan1", "gang1", "kan4", "kang4",
            "han2", "hang2", "zhan3", "zhang3", "chan2", "chang2",
            "shan1", "shang1", "ren2", "reng2", "ben1", "beng1",
            "pen2", "peng2", "men2", "meng2", "fen1", "feng1",
            "gen1", "geng1", "ken3", "keng1", "hen3", "heng2",
            "jin1", "jing1", "qin1", "qing1", "xin1", "xing1",
            "lin2", "ling2", "min2", "ming2", "nin2", "ning2",
        ),
        "tone" to listOf(
            "ma1", "ma2", "ma3", "ma4", "ba1", "ba2", "ba3", "ba4",
            "yi1", "yi2", "yi3", "yi4", "wu1", "wu2", "wu3", "wu4",
            "yu1", "yu2", "yu3", "yu4", "shi1", "shi2", "shi3", "shi4",
            "hao3", "hao4", "ni3", "ni2", "wo3", "ta1", "men5", "de5",
        ),
        "umlaut" to listOf(
            "nv3", "nv4", "lv4", "lv3", "ju1", "ju2", "ju3", "ju4",
            "qu1", "qu2", "qu3", "qu4", "xu1", "xu2", "xu3", "xu4",
            "yu2", "yu3", "yue4", "yuan2", "jun1", "qun2", "xun2",
            "nve4", "lve4", "jue2", "que4", "xue2",
        ),
        "final" to listOf(
            "liu2", "jiu3", "jiu4", "gui1", "hui2", "gui4",
            "dui4", "diu4", "tui1", "shui3", "chui1", "zhui1",
            "jie2", "jue2", "qie4", "que4", "xie1", "xue1",
            "lie4", "lve4", "nie4", "nve4", "die2", "tie3",
            "lun2", "chun1", "shun4", "zhun3", "qun2", "xun2",
        ),
        "initial" to listOf(
            "ba1", "da1", "pa1", "bo1", "bi3", "di3", "pi3", "qi3",
            "bang4", "dang4", "pang4", "bi4", "di4", "pi4", "qi4",
            "fa1", "ha1", "fang1", "hang1", "fei1", "hei1",
            "na2", "la2", "ni3", "li3", "nan2", "lan2", "niu2", "liu2",
            "ge1", "ke1", "gao1", "kao1", "gu3", "ku3",
        ),
    )

    /** 按域取音节池；mixed 取全部 */
    fun poolOf(domainKey: String): List<Syllable> {
        val keys = if (domainKey == "mixed") POOL.keys.toList() else listOf(domainKey)
        return keys.flatMap { POOL[it].orEmpty() }.distinct().mapNotNull { parse(it) }
    }

    /** 解析 "zhang3" -> Syllable */
    fun parse(code: String): Syllable? {
        if (code.isEmpty()) return null
        val last = code.last()
        if (!last.isDigit()) {
            return Syllable(code, Tone.T5, ToneMark.display(code))
        }
        val base = code.dropLast(1)
        val tone = Tone.of(last - '0')
        return Syllable(base, tone, ToneMark.render(base, tone))
    }

    /** 生成带调音节的编码形式，如 ("zhang", 3) -> "zhang3" */
    fun code(base: String, tone: Tone): String = "$base${tone.num}"

    /** 取该音节的声母 */
    fun initialOf(base: String): String = SyllableParser.initial(base)

    /** 取该音节的韵母 */
    fun finalOf(base: String): String = SyllableParser.final(base)

    /** 是否整体认读音节（不含声调） */
    fun isWhole(base: String): Boolean = base in WHOLE

    /** 该音节对应的知识标签，用于错题统计 */
    fun tagsOf(syl: Syllable): List<PinyinTag> {
        val out = mutableListOf<PinyinTag>()
        val ini = initialOf(syl.base)
        val fin = finalOf(syl.base)

        when {
            ini in CURL -> out += PinyinTag.ZH_CH_SH_R
            ini in FLAT -> out += PinyinTag.Z_C_S
        }
        when (fin) {
            "an", "ang" -> out += PinyinTag.AN_ANG
            "en", "eng" -> out += PinyinTag.EN_ENG
            "in", "ing" -> out += PinyinTag.IN_ING
            "un", "ong" -> out += PinyinTag.UN_ON_ONG
            "ui", "iu" -> out += PinyinTag.FINAL_UI_IU
            "ie", "ve" -> out += PinyinTag.FINAL_IE_UE
        }
        if (syl.base.contains('v')) out += PinyinTag.UMLAUT_KEEP
        when (syl.tone) {
            Tone.T1 -> out += PinyinTag.TONE_1
            Tone.T2, Tone.T3 -> out += PinyinTag.TONE_2_3
            Tone.T4 -> out += PinyinTag.TONE_4
            Tone.T5 -> out += PinyinTag.TONE_NEUTRAL
        }
        when (ini) {
            "b" -> out += PinyinTag.SHAPE_BD_PQ
            "d" -> out += PinyinTag.SHAPE_BD_PQ
            "p" -> out += PinyinTag.SHAPE_BD_PQ
            "q" -> out += PinyinTag.SHAPE_BD_PQ
            "f", "h" -> out += PinyinTag.F_H
            "n", "l" -> out += PinyinTag.N_L
        }
        if (isWhole(syl.base)) out += PinyinTag.WHOLE_READ
        return out.distinct()
    }

    /** 取某域下指定难度可用的音节池 */
    fun poolFor(domainKey: String, tierStars: Int): List<Syllable> {
        val all = poolOf(domainKey)
        // 高难度层优先出现拗口/多字母音节
        return when (tierStars) {
            1 -> all.filter { it.base.length <= 4 }.ifEmpty { all }
            2 -> all
            else -> all.sortedByDescending { it.base.length }
        }
    }
}
