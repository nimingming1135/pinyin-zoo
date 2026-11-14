package com.alex.pinyinzoo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.pinyinzoo.data.ProgressStore
import com.alex.pinyinzoo.logic.AudioPlayer
import com.alex.pinyinzoo.logic.GameSession
import com.alex.pinyinzoo.logic.PronunciationScorer
import com.alex.pinyinzoo.logic.QuestionGenerator
import com.alex.pinyinzoo.logic.Recorder
import com.alex.pinyinzoo.model.Domain
import com.alex.pinyinzoo.model.PinyinTag
import com.alex.pinyinzoo.model.Question
import com.alex.pinyinzoo.model.SessionResult
import com.alex.pinyinzoo.model.Tier
import com.alex.pinyinzoo.model.Tone
import com.alex.pinyinzoo.ui.components.Animal
import com.alex.pinyinzoo.ui.components.AnimalAvatar
import com.alex.pinyinzoo.ui.components.Mood
import com.alex.pinyinzoo.ui.components.zooSafeInset
import com.alex.pinyinzoo.ui.screens.CollectionScreen
import com.alex.pinyinzoo.ui.screens.LevelSelectScreen
import com.alex.pinyinzoo.ui.screens.MapScreen
import com.alex.pinyinzoo.ui.screens.ParentReportScreen
import com.alex.pinyinzoo.ui.screens.PlayScreen
import com.alex.pinyinzoo.ui.screens.ResultScreen
import com.alex.pinyinzoo.ui.screens.WrongBookScreen
import com.alex.pinyinzoo.ui.theme.ZooColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导航目的地 */
sealed interface Screen {
    data object Map : Screen
    data class Levels(val domain: Domain) : Screen
    data class Play(val domain: Domain, val tier: Tier) : Screen
    data class Result(
        val domain: Domain,
        val tier: Tier,
        val stars: Int,
        val correct: Int,
        val total: Int,
        val weakTags: List<PinyinTag>,
        val wrongIds: List<String>,
    ) : Screen
    data object WrongBook : Screen
    data object Parent : Screen
    data object Collection : Screen
}

private const val SESSION_SIZE = 12

/**
 * 应用根节点：所有状态集中在这里管理。
 */
@Composable
fun AppRoot(
    onRequestMic: ((Boolean) -> Unit) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val progress = remember { ProgressStore(context) }
    val player = remember { AudioPlayer(context) }
    val recorder = remember { Recorder(context) }
    val generator = remember { QuestionGenerator() }

    var screen by remember { mutableStateOf<Screen>(Screen.Map) }
    var progressTick by remember { mutableIntStateOf(0) }

    // 设置已移除：声音固定开启，动画保持最佳状态。
    // 之前这两个开关写在 SharedPreferences 里，但 reduceMotion 实际上
    // 从未被任何动画读取，属于"假开关"，留着只会让人困惑。
    LaunchedEffect(Unit) {
        player.soundOn = true
        progress.soundOn = true
    }

    // ---------------- 答题状态
    var session by remember { mutableStateOf<GameSession?>(null) }
    /** 当前屏幕上显示的那道题（答错反馈期间仍显示原题，不提前跳到下一题） */
    var shownQuestion by remember { mutableStateOf<Question?>(null) }
    var feedback by remember { mutableStateOf<Boolean?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    // 跟读状态
    var readScore by remember { mutableStateOf<Int?>(null) }
    var readTip by remember { mutableStateOf<String?>(null) }
    var readProgress by remember { mutableStateOf(0f) }
    var isRecording by remember { mutableStateOf(false) }
    var micAvailable by remember { mutableStateOf(false) }

    val wrongPool = remember { mutableStateMapOf<String, Question>() }

    LaunchedEffect(Unit) {
        micAvailable = recorder.hasPermission()
    }

    // 每次题目切换时刷新麦克风权限状态（权限可能在运行期被授予或回收）
    LaunchedEffect(shownQuestion?.id) {
        if (shownQuestion?.kind == com.alex.pinyinzoo.model.QuestionKind.READ_ALOUD) {
            micAvailable = recorder.hasPermission()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.releaseAll()
            recorder.cancel()
        }
    }

    /** 声调出错时的语音提示键 */
    fun toneHintKey(tone: Tone): String = when (tone) {
        Tone.T1 -> "vo/tonestay"
        Tone.T2 -> "vo/tonerise"
        Tone.T3 -> "vo/tonedip"
        Tone.T4 -> "vo/tonehigh"
        else -> "vo/tryagain"
    }

    /** 结算 */
    fun doFinish() {
        val s = session ?: return
        val result = s.result()
        progress.setStars(s.domain, s.tier.star, result.stars)

        val wrongs = s.wrongQuestions()
        wrongs.forEach { q ->
            q.tags.forEach { tag -> progress.recordTag(tag.key, false) }
            progress.addWrong(q.id)
            wrongPool[q.id] = q
        }
        val wrongIds = wrongs.map { it.id }.toSet()
        s.questions.filter { it.id !in wrongIds }.forEach { q ->
            q.tags.forEach { tag -> progress.recordTag(tag.key, true) }
            progress.onCorrect(q.id)
            wrongPool.remove(q.id)
        }
        if (result.stars >= 3) progress.unlockAnimal(s.domain.key)
        progressTick++
        screen = Screen.Result(
            domain = s.domain,
            tier = s.tier,
            stars = result.stars,
            correct = result.correct,
            total = result.total,
            weakTags = s.weakTags(),
            wrongIds = result.wrongQuestionIds,
        )
    }

    /** 进入下一题（或结算） */
    fun advance() {
        val s = session ?: return
        feedback = null
        selectedId = null
        readScore = null
        readTip = null
        readProgress = 0f
        if (s.finished) {
            shownQuestion = null
            doFinish()
        } else {
            shownQuestion = s.current
            s.current?.promptAudio?.let { player.speak(it) }
        }
    }

    /** 停止录音并评分 */
    fun stopRecord() {
        if (!isRecording) return
        isRecording = false
        val pcm = recorder.stop()
        val s = session ?: return
        val q = shownQuestion ?: s.current ?: return
        val target = q.targetSyllable?.tone
        if (target == null || pcm.isEmpty()) {
            readTip = "没录到声音，再试一次"
            return
        }
        // 评分含自相关基频计算，放到后台线程避免卡顿
        scope.launch {
            val res = withContext(Dispatchers.Default) {
                PronunciationScorer.score(pcm, target)
            }
            readScore = res.total
            readTip = res.tip
            progress.addReadScore(
                q.targetSyllable.base + q.targetSyllable.tone.num,
                target.num,
                res.total
            )

            when {
                res.total >= 85 -> player.speak("vo/perfect")
                res.total >= 60 -> player.speak("vo/good")
                else -> player.speak(toneHintKey(target))
            }

            val ok = s.submitRead(res.total)
            player.sfx(if (ok) "correct" else "wrong")
            if (ok) {
                feedback = true
                delay(1100L)
                advance()
            } else {
                // 跟读未达标同样不跳题：显示这一次的分数和建议后，
                // 停留在本题让孩子再读一次（feedback 保持 null 才能重录）。
                feedback = null
                delay(2200L)
                readScore = null
                readTip = "再读一次试试，注意声调走向"
            }
        }
    }

    /**
     * 打开本应用的系统设置页，让用户手动开启麦克风。
     */
    fun openAppSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            readTip = "请到系统设置里开启麦克风权限"
        }
    }

    /** 开始录音 */
    fun startRecord() {
        readScore = null
        readTip = null
        if (!recorder.hasPermission()) {
            // 如果系统已不再弹窗（用户选过"不再询问"），直接引导去设置页，
            // 否则用户会卡在"点了没反应"的状态。
            if (progress.micPermanentlyDenied) {
                readTip = "请在系统设置里允许麦克风权限"
                openAppSettings()
                return
            }
            onRequestMic { granted ->
                micAvailable = granted
                if (!granted) progress.micPermanentlyDenied = true
                if (granted) {
                    val ok = recorder.start()
                    if (ok) {
                        isRecording = true
                        readProgress = 0f
                        scope.launch {
                            var t = 0
                            while (isRecording && t < 80) {
                                delay(100)
                                t++
                                readProgress = t / 80f
                            }
                            if (isRecording) stopRecord()
                        }
                    } else {
                        readTip = "麦克风启动失败"
                    }
                }
            }
            return
        }
        progress.micPermanentlyDenied = false
        micAvailable = true
        if (!recorder.start()) {
            readTip = "麦克风不可用"
            return
        }
        isRecording = true
        readProgress = 0f
        scope.launch {
            var t = 0
            // 最长 8 秒自动结束
            while (isRecording && t < 80) {
                delay(100)
                t++
                readProgress = t / 80f
            }
            if (isRecording) stopRecord()
        }
    }

    /** 提交选择 */
    fun submitOption(optionId: String) {
        val s = session ?: return
        if (feedback != null) return
        val answered = shownQuestion ?: s.current ?: return
        selectedId = optionId
        val ok = s.submit(optionId)
        player.sfx(if (ok) "correct" else "wrong")

        if (ok) {
            feedback = true
            player.speak(listOf("vo/perfect", "vo/great", "vo/correct", "vo/good").random())
            scope.launch {
                delay(900L)
                advance()
            }
        } else {
            // 答错：只把该选项标红，题目留在原地让孩子继续想。
            //
            // 之前答错会直接跳到下一题，孩子既不知道正确答案，
            // 也没有自己纠正的机会。现在必须答对才能前进。
            feedback = false
            player.speak("vo/thinkagain")
            scope.launch {
                // 1.4 秒后清掉红色标记、恢复可点击，但停留在同一题。
                // 比原来 2.8 秒短，因为不再播报正确答案，只是"再想想"。
                delay(1400L)
                feedback = null
                // 保留 selectedId 以外的清空，避免错误选项一直高亮
                selectedId = null
                // 重新播放题干音，方便孩子再听一遍
                answered.promptAudio?.let { player.speak(it) }
            }
        }
    }

    /** 开始一次会话 */
    fun startSession(domain: Domain, tier: Tier) {
        val wrongCodes = progress.pendingWrong().take(4).mapNotNull { id ->
            wrongPool[id]?.targetSyllable?.let { it.base + it.tone.num }
        }
        val questions = generator.generate(domain, tier, SESSION_SIZE, wrongCodes)
        session = GameSession(domain, tier, questions)
        shownQuestion = questions.firstOrNull()
        feedback = null
        selectedId = null
        readScore = null
        readTip = null
        readProgress = 0f
        screen = Screen.Play(domain, tier)
        questions.firstOrNull()?.promptAudio?.let { key -> player.speak(key) }
    }

    // ---------------- 渲染
    Box(Modifier.fillMaxSize().background(ZooColors.Cream)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState is Screen.Play ||
                    targetState is Screen.Levels ||
                    targetState is Screen.Result
                if (forward) {
                    (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300)))
                        .togetherWith(fadeOut(tween(180)))
                } else {
                    (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.94f))
                        .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.98f))
                }
            },
            label = "nav",
        ) { target ->
            when (target) {
                is Screen.Map -> {
                    // 依赖 progressTick 重新计算
                    val tick = remember(progressTick) { progressTick }
                    val starsMap = remember(tick) {
                        Domain.playable.associateWith { progress.domainStars(it) }
                    }
                    val totalStars = remember(tick) { progress.totalStars() }
                    val wrongCount = remember(tick) { progress.wrongCount() }
                    MapScreen(
                        starsOf = { d -> starsMap[d] ?: 0 },
                        isUnlocked = { d -> progress.isDomainUnlocked(d) },
                        totalStars = totalStars,
                        wrongCount = wrongCount,
                        onPickDomain = { d -> screen = Screen.Levels(d) },
                        onOpenCollection = { screen = Screen.Collection },
                        onOpenParent = { screen = Screen.Parent },
                    )
                }

                is Screen.Levels -> {
                    val tick = remember(progressTick) { progressTick }
                    val starsByTier = remember(tick, target.domain) {
                        (1..3).associateWith { progress.stars(target.domain, it) }
                    }
                    LevelSelectScreen(
                        domain = target.domain,
                        starsOf = { star -> starsByTier[star] ?: 0 },
                        isTierUnlocked = { star -> progress.isTierUnlocked(target.domain, star) },
                        onPickTier = { tier -> startSession(target.domain, tier) },
                        onBack = { screen = Screen.Map },
                    )
                }

                is Screen.Play -> PlayScreen(
                    domain = target.domain,
                    tier = target.tier,
                    question = shownQuestion,
                    index = session?.index ?: 0,
                    total = session?.total ?: 0,
                    correctCount = session?.correctCount ?: 0,
                    streak = session?.streak ?: 0,
                    feedback = feedback,
                    selectedOptionId = selectedId,
                    readScore = readScore,
                    readProgress = readProgress,
                    isRecording = isRecording,
                    micAvailable = micAvailable,
                    readTip = readTip,
                    onPickOption = { opt -> submitOption(opt.id) },
                    onPlayPrompt = { shownQuestion?.promptAudio?.let { player.speak(it) } },
                    onStartRecord = { startRecord() },
                    onStopRecord = { stopRecord() },
                    onSkipRead = { advance() },
                    onExit = {
                        recorder.cancel()
                        isRecording = false
                        player.stop()
                        session = null
                        shownQuestion = null
                        screen = Screen.Levels(target.domain)
                    },
                )

                is Screen.Result -> ResultScreen(
                    result = SessionResult(
                        domain = target.domain,
                        tier = target.tier,
                        total = target.total,
                        correct = target.correct,
                        stars = target.stars,
                        wrongQuestionIds = target.wrongIds,
                    ),
                    weakTags = target.weakTags,
                    wrongQuestions = target.wrongIds.mapNotNull { wrongPool[it] },
                    nextTierAvailable = target.tier.star < 3,
                    onRetry = { startSession(target.domain, target.tier) },
                    onNext = {
                        val next = Tier.entries.firstOrNull { it.star == target.tier.star + 1 }
                        if (next != null) startSession(target.domain, next) else screen = Screen.Map
                    },
                    onBackToMap = { screen = Screen.Map },
                    onOpenWrongBook = { screen = Screen.WrongBook },
                )

                is Screen.WrongBook -> WrongBookScreen(
                    questions = wrongPool.values.toList(),
                    onPlay = { q -> q.promptAudio?.let { player.speak(it) } },
                    onBack = { screen = Screen.Map },
                )

                is Screen.Parent -> ParentReportScreen(
                    tagAccuracy = { tag -> progress.tagAccuracy(tag.key) },
                    tagCounts = { tag -> progress.tagStats(tag.key) },
                    toneAverages = { tone -> progress.toneAverage(tone) },
                    totalStars = progress.totalStars(),
                    wrongCount = progress.wrongCount(),
                    todayCount = progress.todayCount,
                    readCount = progress.readHistory().size,
                    onBack = { screen = Screen.Map },
                    onReset = {
                        progress.resetAll()
                        progressTick++
                        screen = Screen.Map
                    },
                )

                is Screen.Collection -> {
                    val tick = remember(progressTick) { progressTick }
                    val animals = remember(tick) { progress.animals() }
                    CollectionScreen(
                        unlocked = animals,
                        onBack = { screen = Screen.Map },
                    )
                }
            }
        }

        // 录音提示条
        if (isRecording) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .zooSafeInset()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ZooColors.Coral)
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimalAvatar(Animal.BIRD, size = 26.dp, mood = Mood.EXCITED, animated = false)
                    Spacer(Modifier.width(6.dp))
                    Text("正在听你读…", color = ZooColors.White, fontSize = 14.sp)
                }
            }
        }
    }
}
