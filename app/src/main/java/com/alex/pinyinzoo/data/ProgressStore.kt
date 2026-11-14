package com.alex.pinyinzoo.data

import android.content.Context
import com.alex.pinyinzoo.model.Domain
import org.json.JSONArray
import org.json.JSONObject

/**
 * 学习进度持久化。
 *
 * 存什么：
 * - 每关星级（domain+tier -> 0~3）
 * - 知识标签正确率（tagKey -> 对/错次数）
 * - 错题盒（三盒调度：questionId -> box+下次复习题号）
 * - 跟读历史（最近 N 次的声调得分，用于家长曲线）
 * - 已解锁的动物卡
 *
 * 用 SharedPreferences + org.json，零第三方依赖。
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("pinyin_zoo_progress", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 星级

    /** 读取某关星级，未打过返回 0 */
    fun stars(domain: Domain, tierStar: Int): Int =
        prefs.getInt("stars_${domain.key}_$tierStar", 0)

    /** 记录一次成绩：只有更高才覆盖 */
    fun setStars(domain: Domain, tierStar: Int, stars: Int) {
        val old = stars(domain, tierStar)
        if (stars > old) prefs.edit().putInt("stars_${domain.key}_$tierStar", stars).apply()
    }

    /** 某域总星数 */
    fun domainStars(domain: Domain): Int = (1..3).sumOf { stars(domain, it) }

    /** 全部总星数 */
    fun totalStars(): Int = Domain.playable.sumOf { domainStars(it) }

    /**
     * 关卡是否解锁。
     *
     * 第一层永远开放；第二、三层需要前一层至少 1 星。
     * 这样孩子在同一个区域内仍有渐进感，但不会被卡死——
     * 任何一层拿到 1 星（12 题答对 8 题）就能继续往下。
     */
    fun isTierUnlocked(domain: Domain, tierStar: Int): Boolean =
        tierStar == 1 || stars(domain, tierStar - 1) >= 1

    /**
     * 域是否解锁。
     *
     * 设计取舍：**全部 8 个区域从一开始就解锁**。
     * 理由：家长明确要求"全方位考核，不限制某个方面"，
     * 如果强制按顺序解锁，孩子会被卡在第一关，无法测出真实短板。
     * 星级仍然记录，用于展示掌握度与解锁动物卡，但不作为准入门槛。
     */
    fun isDomainUnlocked(domain: Domain): Boolean = true

    // ---------------------------------------------------------------- 标签正确率

    fun recordTag(tagKey: String, correct: Boolean) {
        val okKey = "tag_${tagKey}_ok"
        val badKey = "tag_${tagKey}_bad"
        val e = prefs.edit()
        if (correct) e.putInt(okKey, prefs.getInt(okKey, 0) + 1)
        else e.putInt(badKey, prefs.getInt(badKey, 0) + 1)
        e.apply()
    }

    fun tagStats(tagKey: String): Pair<Int, Int> =
        prefs.getInt("tag_${tagKey}_ok", 0) to prefs.getInt("tag_${tagKey}_bad", 0)

    /** 返回某标签正确率，无数据返回 -1 */
    fun tagAccuracy(tagKey: String): Float {
        val (ok, bad) = tagStats(tagKey)
        val total = ok + bad
        return if (total == 0) -1f else ok.toFloat() / total
    }

    // ---------------------------------------------------------------- 错题盒（三盒）

    private fun boxes(): JSONObject {
        val raw = prefs.getString("boxes", null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun saveBoxes(obj: JSONObject) {
        prefs.edit().putString("boxes", obj.toString()).apply()
    }

    /** 错题入盒 1 */
    fun addWrong(questionId: String) {
        val obj = boxes()
        obj.put(questionId, JSONObject().put("box", 1).put("seen", 0))
        saveBoxes(obj)
    }

    /** 答对时提升盒子；盒 3 且答对 2 次则移出 */
    fun onCorrect(questionId: String) {
        val obj = boxes()
        if (!obj.has(questionId)) return
        val item = obj.getJSONObject(questionId)
        val box = item.optInt("box", 1)
        val seen = item.optInt("seen", 0)
        if (box >= 3 && seen >= 1) {
            obj.remove(questionId)
        } else if (seen >= 1) {
            item.put("box", (box + 1).coerceAtMost(3)).put("seen", 0)
        } else {
            item.put("seen", seen + 1)
        }
        saveBoxes(obj)
    }

    /** 待复习的题目 id 列表（按盒子升序） */
    fun pendingWrong(): List<String> {
        val obj = boxes()
        return obj.keys().asSequence()
            .sortedBy { obj.getJSONObject(it).optInt("box", 1) }
            .toList()
    }

    fun wrongCount(): Int = boxes().length()

    // ---------------------------------------------------------------- 跟读历史

    /** 记录一次跟读得分（0~100）+ 声调 */
    fun addReadScore(syllableCode: String, toneNum: Int, score: Int) {
        val arrRaw = prefs.getString("read_history", null)
        val arr = if (arrRaw != null) runCatching { JSONArray(arrRaw) }.getOrElse { JSONArray() } else JSONArray()
        arr.put(
            JSONObject()
                .put("s", syllableCode)
                .put("t", toneNum)
                .put("v", score)
                .put("ts", System.currentTimeMillis())
        )
        // 只保留最近 300 条
        val trimmed = JSONArray()
        val start = (arr.length() - 300).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString("read_history", trimmed.toString()).apply()
    }

    data class ReadRecord(val syllable: String, val tone: Int, val score: Int, val time: Long)

    fun readHistory(): List<ReadRecord> {
        val raw = prefs.getString("read_history", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ReadRecord(o.optString("s"), o.optInt("t"), o.optInt("v"), o.optLong("ts"))
        }
    }

    /** 某声调的平均分，无数据返回 -1 */
    fun toneAverage(tone: Int): Float {
        val list = readHistory().filter { it.tone == tone }
        return if (list.isEmpty()) -1f else list.sumOf { it.score }.toFloat() / list.size
    }

    // ---------------------------------------------------------------- 动物卡

    fun unlockAnimal(id: String) {
        val set = prefs.getStringSet("animals", emptySet()) ?: emptySet()
        if (id !in set) prefs.edit().putStringSet("animals", set + id).apply()
    }

    fun animals(): Set<String> = prefs.getStringSet("animals", emptySet()) ?: emptySet()

    // ---------------------------------------------------------------- 设置

    /**
     * 声音开关。
     *
     * 设置界面已移除，App 固定以"声音开启"启动；
     * 保留这个字段是为了兼容旧版本已经写过的偏好值，不做迁移。
     */
    var soundOn: Boolean
        get() = prefs.getBoolean("sound_on", true)
        set(v) = prefs.edit().putBoolean("sound_on", v).apply()

    /**
     * 麦克风是否已被永久拒绝。
     *
     * 用途：用户拒绝一次后，系统通常不再弹窗，此时必须引导到系统设置，
     * 否则"开启麦克风"按钮点了毫无反应，是无法接受的体验。
     */
    var micPermanentlyDenied: Boolean
        get() = prefs.getBoolean("mic_denied", false)
        set(v) = prefs.edit().putBoolean("mic_denied", v).apply()

    /** 每日答题计数（用于首页「今日复习」气泡） */
    var todayCount: Int
        get() = prefs.getInt("count_${todayKey()}", 0)
        set(v) = prefs.edit().putInt("count_${todayKey()}", v).apply()

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}${cal.get(java.util.Calendar.MONTH)}${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
