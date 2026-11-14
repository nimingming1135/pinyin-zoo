package com.alex.pinyinzoo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.pinyinzoo.model.Domain
import com.alex.pinyinzoo.model.Option
import com.alex.pinyinzoo.model.Question
import com.alex.pinyinzoo.model.QuestionKind
import com.alex.pinyinzoo.model.Tier
import com.alex.pinyinzoo.ui.components.Animal
import com.alex.pinyinzoo.ui.components.AnimalAvatar
import com.alex.pinyinzoo.ui.components.FloatingDots
import com.alex.pinyinzoo.ui.components.JellyButton
import com.alex.pinyinzoo.ui.components.Mood
import com.alex.pinyinzoo.ui.components.OutlinedTitle
import com.alex.pinyinzoo.ui.components.PinyinCard
import com.alex.pinyinzoo.ui.components.ProgressBar
import com.alex.pinyinzoo.ui.components.RoundIconButton
import com.alex.pinyinzoo.ui.components.StarBurst
import com.alex.pinyinzoo.ui.components.ZooCard
import com.alex.pinyinzoo.ui.components.zooHPad
import com.alex.pinyinzoo.ui.components.zooSafeInset
import com.alex.pinyinzoo.ui.theme.ZooColors
import kotlin.math.sin

/**
 * 答题页。
 *
 * 布局自适应：
 * - 紧凑（手机横屏，高 < 480dp）：题目区在上，选项横排在下
 * - 宽松（平板）：左侧题目 + 右侧动物，选项横排
 */
@Composable
fun PlayScreen(
    domain: Domain,
    tier: Tier,
    question: Question?,
    index: Int,
    total: Int,
    correctCount: Int,
    streak: Int,
    /** 反馈状态：null 未作答，true 正确，false 错误 */
    feedback: Boolean?,
    selectedOptionId: String?,
    readScore: Int?,
    readProgress: Float,
    isRecording: Boolean,
    micAvailable: Boolean,
    readTip: String?,
    onPickOption: (Option) -> Unit,
    onPlayPrompt: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onSkipRead: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = ZooColors.ofDomain(domain.key)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ZooColors.Cream)
            .zooSafeInset()
    ) {
        val compact = maxHeight < 480.dp
        val pagePad = zooHPad(maxWidth.value)
        FloatingDots(Modifier.fillMaxSize(), color = color, count = if (compact) 6 else 12)

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pagePad, vertical = if (compact) 12.dp else 20.dp)
        ) {
            // ---------------- 顶栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(
                    size = if (compact) 36.dp else 44.dp,
                    onClick = onExit
                ) { Text("←", fontSize = if (compact) 16.sp else 20.sp, color = ZooColors.Ink) }
                Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1} / $total",
                            style = MaterialTheme.typography.titleMedium,
                            color = ZooColors.Ink
                        )
                        Spacer(Modifier.width(10.dp))
                        if (streak >= 2) {
                            Text(
                                "连对 $streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = ZooColors.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ZooColors.Coral)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(
                        progress = if (total == 0) 0f else index.toFloat() / total,
                        color = color,
                        height = if (compact) 10.dp else 14.dp,
                    )
                }
                Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                // 反馈提示：答对/答错都要有明确文字，不只靠颜色
                if (feedback != null) {
                    val ok = feedback == true
                    Text(
                        if (ok) "✓ 答对啦" else "再试一次",
                        fontSize = if (compact) 14.sp else 17.sp,
                        fontWeight = FontWeight.Black,
                        color = ZooColors.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (ok) ZooColors.Good else ZooColors.Wrong)
                            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                }
                // 答对数
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", fontSize = if (compact) 16.sp else 20.sp, color = ZooColors.Good, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "$correctCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = ZooColors.Ink
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

            if (question == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = color)
                }
                return@Column
            }

            // ---------------- 主体
            if (compact) {
                // 手机横屏：左右分栏（题干左，选项右）
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)
                ) {
                    QuestionPanel(
                        question = question,
                        domain = domain,
                        color = color,
                        compact = true,
                        isRecording = isRecording,
                        readTip = readTip,
                        readProgress = readProgress,
                        modifier = Modifier.weight(0.42f).fillMaxHeight(),
                        onPlayPrompt = onPlayPrompt,
                    )
                    OptionsPanel(
                        question = question,
                        feedback = feedback,
                        selectedOptionId = selectedOptionId,
                        readScore = readScore,
                        isRecording = isRecording,
                        micAvailable = micAvailable,
                        compact = true,
                        color = color,
                        readTip = readTip,
                        readProgress = readProgress,
                        modifier = Modifier.weight(0.58f).fillMaxHeight(),
                        onPickOption = onPickOption,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                        onPlayPrompt = onPlayPrompt,
                        onSkipRead = onSkipRead,
                    )
                }
            } else {
                // 平板：上方题干，下方选项
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    QuestionPanel(
                        question = question,
                        domain = domain,
                        color = color,
                        compact = false,
                        isRecording = isRecording,
                        readTip = readTip,
                        readProgress = readProgress,
                        modifier = Modifier.weight(0.38f).fillMaxHeight(),
                        onPlayPrompt = onPlayPrompt,
                    )
                    OptionsPanel(
                        question = question,
                        feedback = feedback,
                        selectedOptionId = selectedOptionId,
                        readScore = readScore,
                        isRecording = isRecording,
                        micAvailable = micAvailable,
                        compact = false,
                        color = color,
                        readTip = readTip,
                        readProgress = readProgress,
                        modifier = Modifier.weight(0.62f).fillMaxHeight(),
                        onPickOption = onPickOption,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                        onPlayPrompt = onPlayPrompt,
                        onSkipRead = onSkipRead,
                    )
                }
            }
        }
    }
}

/** 左侧（上侧）题干区 */
@Composable
private fun QuestionPanel(
    question: Question,
    domain: Domain,
    color: Color,
    compact: Boolean,
    isRecording: Boolean,
    readTip: String?,
    readProgress: Float,
    modifier: Modifier = Modifier,
    onPlayPrompt: () -> Unit,
) {
    ZooCard(
        modifier = modifier,
        color = ZooColors.White,
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 10.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 动物
            Box {
                AnimalAvatar(
                    animal = Animal.of(domain.mascot),
                    size = if (compact) 54.dp else 96.dp,
                    mood = if (isRecording) Mood.EXCITED else Mood.HAPPY,
                    animated = !isRecording,
                )
            }
            Spacer(Modifier.height(if (compact) 4.dp else 10.dp))

            Text(
                question.prompt,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                color = ZooColors.InkSoft,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )

            // 大号展示内容
            if (question.display.isNotEmpty()) {
                Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
                Text(
                    text = question.display,
                    fontSize = if (compact) 30.sp else 56.sp,
                    fontWeight = FontWeight.Black,
                    color = ZooColors.Ink,
                    textAlign = TextAlign.Center,
                    lineHeight = if (compact) 34.sp else 64.sp,
                )
            }

            // 播放按钮
            if (question.promptAudio != null) {
                Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
                JellyButton(
                    modifier = Modifier
                        .height(if (compact) 44.dp else 56.dp)
                        .width(if (compact) 108.dp else 148.dp),
                    color = color,
                    shadowColor = ZooColors.ofDomainDark(domain.key),
                    onClick = onPlayPrompt,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔊", fontSize = if (compact) 16.sp else 20.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "听一听",
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            color = ZooColors.White,
                        )
                    }
                }
            }

            if (!compact && question.hint.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    question.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZooColors.InkSoft,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** 右侧（下侧）选项区 */
@Composable
private fun OptionsPanel(
    question: Question,
    feedback: Boolean?,
    selectedOptionId: String?,
    readScore: Int?,
    isRecording: Boolean,
    micAvailable: Boolean,
    compact: Boolean,
    color: Color,
    readTip: String?,
    readProgress: Float,
    modifier: Modifier = Modifier,
    onPickOption: (Option) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onPlayPrompt: () -> Unit,
    onSkipRead: () -> Unit,
) {
    // 星星爆散触发
    var burst by remember(question.id) { mutableIntStateOf(0) }
    LaunchedEffect(feedback) {
        if (feedback == true) burst++
    }

    Box(modifier) {
        when (question.kind) {
            QuestionKind.READ_ALOUD -> ReadAloudPanel(
                question = question,
                readScore = readScore,
                isRecording = isRecording,
                micAvailable = micAvailable,
                readTip = readTip,
                readProgress = readProgress,
                compact = compact,
                color = color,
                onStartRecord = onStartRecord,
                onStopRecord = onStopRecord,
                onPlayPrompt = onPlayPrompt,
                onSkipRead = onSkipRead,
            )

            else -> OptionsGrid(
                question = question,
                feedback = feedback,
                selectedOptionId = selectedOptionId,
                compact = compact,
                color = color,
                onPickOption = onPickOption,
            )
        }

        // 答对粒子
        StarBurst(
            trigger = burst,
            modifier = Modifier.fillMaxSize(),
            color = if (feedback == true) ZooColors.Sun else ZooColors.Wrong,
        )
    }
}

/** 普通选项网格：2x2（紧凑时 2x2 也会很高，用 1 列自适应） */
@Composable
private fun OptionsGrid(
    question: Question,
    feedback: Boolean?,
    selectedOptionId: String?,
    compact: Boolean,
    color: Color,
    onPickOption: (Option) -> Unit,
) {
    val opts = question.options
    val cols = if (opts.size <= 2) opts.size.coerceAtLeast(1) else 2
    val rows = (opts.size + cols - 1) / cols
    val fontSize = when {
        compact && opts.any { it.text.length > 8 } -> 20
        compact -> 26
        opts.any { it.text.length > 8 } -> 30
        else -> 42
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
    ) {
        for (r in 0 until rows) {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
            ) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    if (i < opts.size) {
                        val opt = opts[i]
                        val isSelected = selectedOptionId == opt.id
                        // 答对：所选卡片变绿。
                        // 答错：只把所选卡片标红，**不显示正确答案**，
                        //      孩子要自己再想一遍直到答对（题目不会跳过）。
                        val showCorrect = feedback == true && opt.isCorrect
                        val showWrong = feedback == false && isSelected && !opt.isCorrect
                        PinyinCard(
                            text = opt.text,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            correct = showCorrect,
                            wrong = showWrong,
                            // 答错后立刻恢复可点击（feedback 会被上层重置，
                            // 这里额外用 wrong 态放开是为了避免那 1.4 秒锁死手感）
                            enabled = true,
                            fontSize = fontSize,
                            onClick = { if (feedback == null) onPickOption(opt) },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 跟读面板 */
@Composable
private fun ReadAloudPanel(
    question: Question,
    readScore: Int?,
    isRecording: Boolean,
    micAvailable: Boolean,
    readTip: String?,
    readProgress: Float,
    compact: Boolean,
    color: Color,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onPlayPrompt: () -> Unit,
    onSkipRead: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
    ) {
        if (!micAvailable) {
            // 无麦克风权限：降级为听读模式，但提供"开启麦克风"入口。
            // 关键：不能只显示"没有权限"就完事——用户可能在设置里开启了权限，
            // 或者在首次弹窗中误点了拒绝，必须有重新申请的路径。
            ZooCard(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🎧", fontSize = if (compact) 32.sp else 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这一题是跟读练习\n需要麦克风才能给你打分",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZooColors.InkSoft,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        JellyButton(
                            modifier = Modifier.height(44.dp).width(if (compact) 120.dp else 150.dp),
                            color = color,
                            onClick = onStartRecord,
                        ) {
                            Text("开启麦克风", color = ZooColors.White, style = MaterialTheme.typography.labelLarge)
                        }
                        JellyButton(
                            modifier = Modifier.height(44.dp).width(if (compact) 110.dp else 130.dp),
                            color = ZooColors.White,
                            shadowColor = ZooColors.InkSoft,
                            onClick = onSkipRead,
                        ) {
                            Text("先跳过", color = ZooColors.Ink, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else {
            // 波形显示
            ZooCard(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxSize().padding(if (compact) 8.dp else 16.dp), contentAlignment = Alignment.Center) {
                    WaveformView(
                        active = isRecording,
                        progress = readProgress,
                        color = color,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!isRecording && readScore == null) {
                        Text(
                            "先听一遍，再按住话筒读",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZooColors.InkSoft,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (readScore != null) {
                        ScoreBadge(readScore, compact)
                    }
                }
            }

            if (readTip != null) {
                Text(
                    readTip,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                    color = ZooColors.Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
            ) {
                JellyButton(
                    modifier = Modifier.weight(0.4f).height(if (compact) 52.dp else 64.dp),
                    color = ZooColors.Sky,
                    shadowColor = ZooColors.SkyDark,
                    onClick = onPlayPrompt,
                ) {
                    Text("🔊 再听", color = ZooColors.White, style = MaterialTheme.typography.labelLarge)
                }
                // 按住说话
                Box(
                    Modifier
                        .weight(0.6f)
                        .height(if (compact) 52.dp else 64.dp),
                ) {
                    val holdModifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isRecording) ZooColors.Coral else ZooColors.Mint)
                        .border(3.dp, ZooColors.Ink, RoundedCornerShape(22.dp))
                        .clickable(enabled = readScore == null) {
                            if (isRecording) onStopRecord() else onStartRecord()
                        }
                    Box(holdModifier, contentAlignment = Alignment.Center) {
                        Text(
                            if (isRecording) "🎙 录音中… 点一下结束" else "🎙 点一下开始读",
                            color = ZooColors.White,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 声波可视化 */
@Composable
private fun WaveformView(
    active: Boolean,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val bars = 32
        val gap = w / bars
        val amp = if (active) (0.25f + progress * 0.75f) else 0.12f
        for (i in 0 until bars) {
            val phase = i / bars.toFloat()
            val s = sin(phase * 12.4f + progress * 18f) * 0.5f + 0.5f
            val bh = h * amp * (0.25f + s * 0.75f)
            val x = i * gap + gap / 2f
            drawLine(
                color = if (active) color else color.copy(alpha = 0.35f),
                start = Offset(x, mid - bh / 2f),
                end = Offset(x, mid + bh / 2f),
                strokeWidth = gap * 0.42f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 分数徽章 */
@Composable
private fun ScoreBadge(score: Int, compact: Boolean) {
    val c = when {
        score >= 85 -> ZooColors.Good
        score >= 60 -> ZooColors.Sun
        else -> ZooColors.Retry
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$score",
            fontSize = if (compact) 40.sp else 60.sp,
            fontWeight = FontWeight.Black,
            color = c,
        )
        Text(
            if (score >= 85) "太棒了" else if (score >= 60) "不错" else "再试试",
            style = MaterialTheme.typography.labelMedium,
            color = ZooColors.Ink,
        )
    }
}
