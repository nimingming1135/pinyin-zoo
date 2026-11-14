package com.alex.pinyinzoo.model

/**
 * 声调：1~4 为四声，5 为轻声。
 * 拼音文本用纯字母 + 数字声调表示（内部编码），显示时再转成带声调符号的形式。
 */
enum class Tone(val num: Int, val mark: String, val label: String) {
    T1(1, "ˉ", "第一声"),
    T2(2, "ˊ", "第二声"),
    T3(3, "ˇ", "第三声"),
    T4(4, "ˋ", "第四声"),
    T5(5, "˙", "轻声");

    companion object {
        fun of(num: Int): Tone = entries.firstOrNull { it.num == num } ?: T5
    }
}

/** 拼音知识域：对应地图上的一个区域，也是错题统计与加权出题的单位 */
enum class Domain(
    val key: String,
    val title: String,
    val subtitle: String,
    val weight: Int,
    val mascot: String,
) {
    SIBILANT("sibilant", "平翘舌擂台", "zh ch sh 和 z c s 别搞混", 18, "snake"),
    NASAL("nasal", "鼻韵母山谷", "an ang、en eng 要分清", 16, "elephant"),
    TONE("tone", "声调山", "一二三四声，还有轻声", 16, "frog"),
    UMLAUT("umlaut", "小鱼工厂", "ü 遇到 j q x y 要去点", 12, "fish"),
    FINAL("final", "韵母迷宫", "ui iu ie üe 长得很像", 12, "monkey"),
    INITIAL("initial", "形近声母森林", "b d p q 长得像双胞胎", 12, "bear"),
    WHOLE("whole", "整体认读城堡", "这些音节不用拆开拼", 8, "owl"),
    MIXED("mixed", "森林大考", "全部混在一起考", 6, "lion");

    companion object {
        fun of(key: String): Domain? = entries.firstOrNull { it.key == key }
        val playable: List<Domain> = entries.toList()
    }
}

/**
 * 一个拼音音节。
 *
 * @param base  不带调的字母形式，如 "zhang"、"nv"（ü 统一用 v 表示）
 * @param tone  声调
 * @param text  带声调符号的显示形式，如 "zhǎng"
 * @param char  用于驱动 TTS 的同音汉字（音频按音节生成，这里仅作展示与校验）
 */
data class Syllable(
    val base: String,
    val tone: Tone,
    val text: String,
    val char: String = "",
) {
    /** 音频资源键，如 syl/zhang3 */
    val audioKey: String get() = "syl/${base}${tone.num}"

    /** 不带调的显示形式（ü 显示为 ü） */
    val plainText: String get() = text.filter { it !in "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü" } + if (text.contains('ü')) "ü" else ""

    override fun toString(): String = text
}

/** 拼音知识标签：用于错题谱系统计，比 Domain 更细 */
enum class PinyinTag(val key: String, val label: String) {
    ZH_CH_SH_R("zh_ch_sh_r", "翘舌音"),
    Z_C_S("z_c_s", "平舌音"),
    AN_ANG("an_ang", "an / ang"),
    EN_ENG("en_eng", "en / eng"),
    IN_ING("in_ing", "in / ing"),
    UN_ON_ONG("un_ong", "un / ong"),
    TONE_2_3("tone_2_3", "二声三声"),
    TONE_1("tone_1", "第一声"),
    TONE_4("tone_4", "第四声"),
    TONE_NEUTRAL("tone_neutral", "轻声"),
    TONE_SANDHI("tone_sandhi", "变调"),
    TONE_PLACEMENT("tone_placement", "标调位置"),
    UMLAUT_DROP("umlaut_drop", "ü 去点"),
    UMLAUT_KEEP("umlaut_keep", "ü 保留"),
    FINAL_UI_IU("final_ui_iu", "ui / iu"),
    FINAL_IE_UE("final_ie_ue", "ie / üe"),
    FINAL_ABBREV("final_abbrev", "省写规则"),
    SHAPE_BD_PQ("shape_bd_pq", "b d p q"),
    F_H("f_h", "f / h"),
    N_L("n_l", "n / l"),
    ASPIRATE("aspirate", "送气音"),
    WHOLE_READ("whole_read", "整体认读");

    companion object {
        fun of(key: String): PinyinTag? = entries.firstOrNull { it.key == key }
    }
}

/** 题目类型 */
enum class QuestionKind {
    LISTEN_PICK,     // 听音选音节
    WORD_PICK,       // 看词选拼音
    TONE_PICK,       // 判声调
    BLEND,           // 拼合积木
    FILL_FINAL,      // 补全音节
    TONE_MARK,       // 标调位置
    MATCH,           // 配对连线
    READ_ALOUD,      // 跟读评分
}

/** 难度层：★ 认得 / ★★ 拼得 / ★★★ 用得上 */
enum class Tier(val star: Int, val label: String, val hint: String) {
    RECOGNIZE(1, "认得", "听一听、认一认"),
    BLEND(2, "拼得", "拼一拼、补一补"),
    APPLY(3, "用得上", "用到词语里");
}

/** 一个选项 */
data class Option(
    val id: String,
    /** 卡片上显示的主文本（拼音或汉字） */
    val text: String,
    /** 次级说明，可为空 */
    val hint: String = "",
    /** 该选项对应的读音 key，点击后播放 */
    val audioKey: String? = null,
    val isCorrect: Boolean = false,
)

/** 一道题 */
data class Question(
    val id: String,
    val kind: QuestionKind,
    val domain: Domain,
    val tier: Tier,
    val tags: List<PinyinTag>,
    /** 题干主文本（如"选出正确的读音"或词语汉字） */
    val prompt: String = "",
    /** 题干要播放的音频 key */
    val promptAudio: String? = null,
    /** 大号展示的拼音/汉字（可为空） */
    val display: String = "",
    /** 提示语 */
    val hint: String = "",
    val options: List<Option> = emptyList(),
    /** 正确答案的 option id；MATCH/READ_ALOUD 可空 */
    val answerId: String = "",
    /** 跟读题的目标音节 */
    val targetSyllable: Syllable? = null,
) {
    val correctOption: Option? get() = options.firstOrNull { it.id == answerId }
}

/** 会话结果 */
data class SessionResult(
    val domain: Domain,
    val tier: Tier,
    val total: Int,
    val correct: Int,
    val stars: Int,
    val wrongQuestionIds: List<String>,
) {
    val accuracy: Float get() = if (total == 0) 0f else correct.toFloat() / total
    val passed: Boolean get() = correct >= (total * 0.8f).toInt()
}
