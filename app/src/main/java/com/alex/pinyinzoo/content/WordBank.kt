package com.alex.pinyinzoo.content

/** 一个词语条目：用于「看词选拼音」「跟读」等题型的素材 */
data class WordEntry(
    /** 汉字，如 "苹果" */
    val hanzi: String,
    /** 带调拼音，空格分隔，如 "píng guǒ" */
    val pinyin: String,
    /** 音频键（不含前缀），如 "pingguo" */
    val audioKey: String,
    /** 该词主要考察的域 */
    val domainKey: String,
    /** 用来画图的图标 key */
    val iconKey: String,
) {
    val syllables: List<String> get() = pinyin.split(" ")
}

/**
 * 一年级常用词语库。
 * 拼音经过逐条核对，覆盖前后鼻音、平翘舌、ü、复韵母等易错点。
 */
object WordBank {

    val ALL: List<WordEntry> = listOf(
        // ---- 水果食物
        WordEntry("苹果", "píng guǒ", "pingguo", "nasal", "apple"),
        WordEntry("牛奶", "niú nǎi", "niunai", "initial", "milk"),
        WordEntry("梨子", "lí zi", "lizi", "sibilant", "pear"),
        WordEntry("葡萄", "pú tao", "putao", "initial", "grape"),
        WordEntry("西瓜", "xī guā", "xigua", "sibilant", "watermelon"),
        WordEntry("草莓", "cǎo méi", "caomei", "sibilant", "strawberry"),
        WordEntry("香蕉", "xiāng jiāo", "xiangjiao", "nasal", "banana"),
        WordEntry("橘子", "jú zi", "juzi", "umlaut", "orange"),
        WordEntry("桃子", "táo zi", "taozi", "final", "peach"),
        WordEntry("萝卜", "luó bo", "luobo", "final", "carrot"),
        WordEntry("菠菜", "bō cài", "bocai", "sibilant", "spinach"),
        WordEntry("面条", "miàn tiáo", "miantiao", "nasal", "noodle"),
        WordEntry("包子", "bāo zi", "baozi", "sibilant", "bun"),
        WordEntry("米饭", "mǐ fàn", "mifan", "nasal", "rice"),
        WordEntry("水果", "shuǐ guǒ", "shuiguo", "sibilant", "fruit"),

        // ---- 动物
        WordEntry("大象", "dà xiàng", "daxiang", "nasal", "elephant"),
        WordEntry("熊猫", "xióng māo", "xiongmao", "nasal", "panda"),
        WordEntry("小鸟", "xiǎo niǎo", "xiaoniao", "initial", "bird"),
        WordEntry("老虎", "lǎo hǔ", "laohu", "initial", "tiger"),
        WordEntry("兔子", "tù zi", "tuzi", "sibilant", "rabbit"),
        WordEntry("麻雀", "má què", "maque", "final", "sparrow"),
        WordEntry("蜜蜂", "mì fēng", "mifeng", "nasal", "bee"),
        WordEntry("蝴蝶", "hú dié", "hudie", "final", "butterfly"),
        WordEntry("青蛙", "qīng wā", "qingwa", "nasal", "frog"),
        WordEntry("袋鼠", "dài shǔ", "daishu", "sibilant", "kangaroo"),
        WordEntry("恐龙", "kǒng lóng", "konglong", "nasal", "dinosaur"),
        WordEntry("长颈鹿", "cháng jǐng lù", "changjinglu", "nasal", "giraffe"),
        WordEntry("斑马", "bān mǎ", "banma", "nasal", "zebra"),
        WordEntry("狮子", "shī zi", "shizi", "sibilant", "lion"),
        WordEntry("猴子", "hóu zi", "houzi", "sibilant", "monkey"),
        WordEntry("小猪", "xiǎo zhū", "xiaozhu", "sibilant", "pig"),
        WordEntry("小鸡", "xiǎo jī", "xiaoji", "sibilant", "chick"),

        // ---- 自然
        WordEntry("月亮", "yuè liang", "yueliang", "umlaut", "moon"),
        WordEntry("太阳", "tài yáng", "taiyang", "nasal", "sun"),
        WordEntry("星星", "xīng xing", "xingxing", "nasal", "star"),
        WordEntry("森林", "sēn lín", "senlin", "nasal", "forest"),
        WordEntry("池塘", "chí táng", "chitang", "sibilant", "pond"),
        WordEntry("羽毛", "yǔ máo", "yumao", "initial", "feather"),
        WordEntry("花园", "huā yuán", "huayuan", "umlaut", "garden"),
        WordEntry("小河", "xiǎo hé", "xiaohe", "initial", "river"),
        WordEntry("大山", "dà shān", "dashan", "sibilant", "mountain"),
        WordEntry("房子", "fáng zi", "fangzi", "nasal", "house"),

        // ---- 学习生活
        WordEntry("老师", "lǎo shī", "laoshi", "sibilant", "teacher"),
        WordEntry("书本", "shū běn", "shuben", "sibilant", "book"),
        WordEntry("铅笔", "qiān bǐ", "qianbi", "nasal", "pencil"),
        WordEntry("生日", "shēng rì", "shengri", "sibilant", "birthday"),
        WordEntry("朋友", "péng you", "pengyou", "nasal", "friend"),
        WordEntry("学校", "xué xiào", "xuexiao", "umlaut", "school"),
        WordEntry("家长", "jiā zhǎng", "jiazhang", "nasal", "parent"),
        WordEntry("桌子", "zhuō zi", "zhuozi", "sibilant", "table"),
        WordEntry("椅子", "yǐ zi", "yizi", "sibilant", "chair"),
        WordEntry("作业", "zuò yè", "zuoye", "sibilant", "homework"),
        WordEntry("数学", "shù xué", "shuxue", "umlaut", "math"),
        WordEntry("语文", "yǔ wén", "yuwen", "initial", "chinese"),
        WordEntry("音乐", "yīn yuè", "yinyue", "umlaut", "music"),
        WordEntry("美术", "měi shù", "meishu", "sibilant", "art"),
        WordEntry("体育", "tǐ yù", "tiyu", "umlaut", "pe"),
        WordEntry("上学", "shàng xué", "shangxue", "sibilant", "school"),
        WordEntry("放学", "fàng xué", "fangxue", "nasal", "afterschool"),

        // ---- 交通与运动
        WordEntry("自行车", "zì xíng chē", "zixingche", "sibilant", "bike"),
        WordEntry("火车", "huǒ chē", "huoche", "sibilant", "train"),
        WordEntry("飞机", "fēi jī", "feiji", "initial", "plane"),
        WordEntry("汽车", "qì chē", "qiche", "sibilant", "car"),
        WordEntry("跳绳", "tiào shéng", "tiaosheng", "nasal", "rope"),
        WordEntry("跑步", "pǎo bù", "paobu", "initial", "run"),
        WordEntry("游泳", "yóu yǒng", "youyong", "nasal", "swim"),
        WordEntry("足球", "zú qiú", "zuoqiu", "sibilant", "football"),
        WordEntry("篮球", "lán qiú", "lanqiu", "nasal", "basketball"),
        WordEntry("运动", "yùn dòng", "yundong", "nasal", "sport"),

        // ---- 家人身体
        WordEntry("妈妈", "mā ma", "mama", "tone", "mother"),
        WordEntry("爸爸", "bà ba", "baba", "tone", "father"),
        WordEntry("爷爷", "yé ye", "yeye", "tone", "grandpa"),
        WordEntry("奶奶", "nǎi nai", "nainai", "initial", "grandma"),
        WordEntry("哥哥", "gē ge", "gege", "tone", "brother"),
        WordEntry("姐姐", "jiě jie", "jiejie", "final", "sister"),
        WordEntry("弟弟", "dì di", "didi", "tone", "brother"),
        WordEntry("妹妹", "mèi mei", "meimei", "tone", "sister"),
        WordEntry("耳朵", "ěr duo", "erduo", "final", "ear"),
        WordEntry("眼睛", "yǎn jing", "yanjing", "nasal", "eye"),
        WordEntry("鼻子", "bí zi", "bizi", "sibilant", "nose"),
        WordEntry("嘴巴", "zuǐ ba", "zuiba", "sibilant", "mouth"),
    )

    /** 绿色（专用于 ü 对比） */
    val GREEN = WordEntry("绿色", "lǜ sè", "lvse", "umlaut", "green")
    /** 长城（专用于翘舌） */
    val WALL = WordEntry("长城", "cháng chéng", "changcheng", "sibilant", "wall")

    /** 按域筛选 */
    fun byDomain(domainKey: String): List<WordEntry> =
        if (domainKey == "mixed") ALL else ALL.filter { it.domainKey == domainKey }

    fun byKey(key: String): WordEntry? = ALL.firstOrNull { it.audioKey == key }
}
