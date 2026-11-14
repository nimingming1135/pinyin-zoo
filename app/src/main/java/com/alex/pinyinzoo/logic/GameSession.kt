package com.alex.pinyinzoo.logic

import com.alex.pinyinzoo.model.Domain
import com.alex.pinyinzoo.model.PinyinTag
import com.alex.pinyinzoo.model.Question
import com.alex.pinyinzoo.model.SessionResult
import com.alex.pinyinzoo.model.Tier

/**
 * 一次答题会话的状态机。
 *
 * 规则（按使用者反馈改过）：
 * - 每层 12 题；
 * - **答错不跳题**：只把该选项标红，题目留在原地让用户继续想，
 *   直到答对才前进 —— 一年级孩子被直接跳过会不知道正确答案是什么；
 * - 答错过的题记入错题盒，训练结束后可在错题本回看；
 * - 评星按**第一次就答对**的题数计算，否则人人都能重试到全对，星星失去意义：
 *   3 星 = 12 题全部一次答对；2 星 ≥ 10；1 星 ≥ 8；否则 0 星；
 * - 连续首次答对 3 题触发一次「连对鼓励」。
 */
class GameSession(
    val domain: Domain,
    val tier: Tier,
    val questions: List<Question>,
) {
    var index: Int = 0
        private set

    /** 第一次就答对的题数：只用于评星，不受重试影响 */
    var correctCount: Int = 0
        private set

    var streak: Int = 0
        private set

    /** 每道题的首次结果：true 一次答对 / false 第一次答错（用于错题盒） */
    private val records = mutableListOf<Pair<Question, Boolean>>()

    /** 曾经答错过的题 id，用于错题盒去重 */
    private val wrongIds = mutableSetOf<String>()

    val total: Int get() = questions.size
    val current: Question? get() = questions.getOrNull(index)
    val progress: Float get() = if (total == 0) 0f else index.toFloat() / total
    val finished: Boolean get() = index >= questions.size

    /**
     * 提交答案。
     *
     * @return true 表示答对并已前进到下一题；
     *         false 表示答错，**停在原题**，调用方应标红并允许重试。
     */
    fun submit(optionId: String): Boolean {
        val q = current ?: return false
        val ok = q.answerId.isNotEmpty() && optionId == q.answerId
        if (ok) {
            val firstTry = q.id !in wrongIds
            if (firstTry) {
                // 第一次就答对：计入评星
                correctCount++
                streak++
                records += q to true
            } else {
                // 之前答错过：已经有一条 false 记录，不再重复添加
                streak = 0
            }
            index++
        } else {
            streak = 0
            wrongIds += q.id
            // 只记录第一次结果，重试多次不重复计入
            if (records.none { it.first.id == q.id }) records += q to false
        }
        return ok
    }

    /**
     * 跟读题提交。
     *
     * @return true 通过并前进；false 未达标，停在原题重录。
     */
    fun submitRead(score: Int): Boolean {
        val q = current ?: return false
        val ok = score >= 60
        if (ok) {
            val firstTry = q.id !in wrongIds
            if (firstTry) {
                correctCount++
                streak++
                records += q to true
            } else {
                streak = 0
            }
            index++
        } else {
            streak = 0
            wrongIds += q.id
            if (records.none { it.first.id == q.id }) records += q to false
        }
        return ok
    }

    /** 跳过（用于录音权限被拒的降级：不计分也不停止） */
    fun skip() {
        val q = current ?: return
        records += q to true   // 降级视为通过，但不算进 correctCount
        index++
    }

    fun stars(): Int {
        if (total == 0) return 0
        val acc = correctCount.toFloat() / total
        return when {
            acc >= 0.999f -> 3
            correctCount >= (total * 10 / 12f).toInt() -> 2
            correctCount >= (total * 8 / 12f).toInt() -> 1
            else -> 0
        }
    }

    fun wrongQuestions(): List<Question> = records.filter { !it.second }.map { it.first }

    /** 本次会话暴露的薄弱标签 */
    fun weakTags(): List<PinyinTag> {
        val map = HashMap<PinyinTag, Int>()
        records.filter { !it.second }.forEach { (q, _) ->
            q.tags.forEach { map[it] = (map[it] ?: 0) + 1 }
        }
        return map.entries.sortedByDescending { it.value }.map { it.key }.take(3)
    }

    fun result(): SessionResult = SessionResult(
        domain = domain,
        tier = tier,
        total = total,
        correct = correctCount,
        stars = stars(),
        wrongQuestionIds = wrongQuestions().map { it.id },
    )
}
