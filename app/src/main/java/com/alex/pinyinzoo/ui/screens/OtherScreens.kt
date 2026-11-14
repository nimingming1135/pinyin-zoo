package com.alex.pinyinzoo.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.pinyinzoo.model.Domain
import com.alex.pinyinzoo.model.PinyinTag
import com.alex.pinyinzoo.model.Question
import com.alex.pinyinzoo.model.SessionResult
import com.alex.pinyinzoo.model.Tier
import com.alex.pinyinzoo.ui.components.Animal
import com.alex.pinyinzoo.ui.components.AnimalAvatar
import com.alex.pinyinzoo.ui.components.Confetti
import com.alex.pinyinzoo.ui.components.FloatingDots
import com.alex.pinyinzoo.ui.components.JellyButton
import com.alex.pinyinzoo.ui.components.Mood
import com.alex.pinyinzoo.ui.components.OutlinedTitle
import com.alex.pinyinzoo.ui.components.RoundIconButton
import com.alex.pinyinzoo.ui.components.StarIcon
import com.alex.pinyinzoo.ui.components.ZooCard
import com.alex.pinyinzoo.ui.components.zooHPad
import com.alex.pinyinzoo.ui.components.zooSafeInset
import com.alex.pinyinzoo.ui.theme.ZooColors

/**
 * 结算页：星级 + 错题清单 + 重练/继续。
 */
@Composable
fun ResultScreen(
    result: SessionResult,
    weakTags: List<PinyinTag>,
    wrongQuestions: List<Question>,
    nextTierAvailable: Boolean,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onBackToMap: () -> Unit,
    onOpenWrongBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = ZooColors.ofDomain(result.domain.key)
    var confetti by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        if (result.stars >= 2) confetti++
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ZooColors.Cream)
            .zooSafeInset()
    ) {
        // BoxWithConstraintsScope 会在 Column 的 content lambda 里被遮蔽，
        // 先取出宽度存成局部变量。
        val screenW = maxWidth
        val compact = maxHeight < 480.dp
        val pagePad = zooHPad(screenW.value)
        FloatingDots(Modifier.fillMaxSize(), color = color, count = 10)
        Confetti(Modifier.fillMaxSize(), trigger = confetti)

        // 布局重做（真机实测驱动）：
        // 旧版用一个 Spacer(weight=1f) 把剩余空间全推给中间，
        // 「答对 x/12」到按钮之间空了 130dp，按钮被顶到屏幕最底部。
        //
        // 新版按可用高度显式分区，保证视觉重心在屏幕中部略偏上：
        //   顶部留白 : 底部留白 = 0.8 : 1
        // 用固定比例而不是 weight 居中，是因为 Compose 的
        // StarIcon / AnimalAvatar 都是 Canvas 绘制，布局高度与可见图形不一致，
        // 纯 weight 居中的结果会系统性地偏下（实测偏 65dp）。
        val topGap = if (compact) 10.dp else 24.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pagePad, vertical = topGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compact) 2.dp else 10.dp))

            // ---------- 成绩区（星星 + 动物 + 文案）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
            ) {
                // 星级
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { i ->
                        val filled = i < result.stars
                        val scale by animateFloatAsState(
                            targetValue = if (filled) 1f else 0.78f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow),
                            label = "star$i"
                        )
                        Box(Modifier.padding(horizontal = 3.dp)) {
                            StarIcon(
                                filled = filled,
                                size = if (compact) 44.dp else 68.dp,
                                modifier = Modifier.size(if (compact) 44.dp else 68.dp),
                            )
                        }
                    }
                }

                AnimalAvatar(
                    animal = Animal.of(result.domain.mascot),
                    size = if (compact) 60.dp else 100.dp,
                    mood = when {
                        result.stars >= 3 -> Mood.EXCITED
                        result.stars >= 1 -> Mood.HAPPY
                        else -> Mood.SAD
                    },
                )

                OutlinedTitle(
                    text = when {
                        result.stars >= 3 -> "全部答对！"
                        result.stars == 2 -> "通过啦！"
                        result.stars == 1 -> "勉强过关"
                        else -> "还要加油"
                    },
                    color = ZooColors.White,
                    fontSize = if (compact) 24 else 34,
                )

                // 现在答错可以重试到对，所以「答对 x/12」容易误解成最终都对了。
                // 改成明确区分「一次答对」的概念，和评星口径一致。
                Text(
                    "一次答对 ${result.correct} / ${result.total} 题",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZooColors.Ink,
                )

                if (wrongQuestions.isNotEmpty()) {
                    Text(
                        "其中 ${wrongQuestions.size} 题做过错，已记入错题本",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZooColors.InkSoft,
                    )
                }

                if (weakTags.isNotEmpty() && !compact) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weakTags.take(3).forEach { tag ->
                            Text(
                                tag.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ZooColors.Ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ZooColors.Retry.copy(alpha = 0.35f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 16.dp else 28.dp))

            // ---------- 按钮区
            //
            // 之前每个按钮都用 weight 铺满整行，四个字摊在 300dp 宽的色块里，
            // 四周留白多得发空。现在统一成等宽按钮并整体居中：
            // 三个按钮宽度一致才显整齐，按文字自适应会导致
            // 「再练一次」比「看看错在哪（10 题）」窄一截，看着别扭。
            //
            // 宽度取文字最长那行的需要值，并随屏幕宽度封顶。
            val btnH = if (compact) 54.dp else 62.dp
            val btnW = (screenW * 0.30f).coerceIn(
                if (compact) 210.dp else 260.dp,
                if (compact) 300.dp else 380.dp
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    if (compact) 14.dp else 20.dp,
                    Alignment.CenterHorizontally
                )
            ) {
                JellyButton(
                    modifier = Modifier.width(btnW).height(btnH),
                    color = ZooColors.White,
                    shadowColor = ZooColors.Sky,
                    onClick = onRetry,
                ) {
                    Text("再练一次", color = ZooColors.Ink, style = MaterialTheme.typography.labelLarge)
                }
                if (nextTierAvailable && result.stars >= 1) {
                    JellyButton(
                        modifier = Modifier.width(btnW).height(btnH),
                        color = ZooColors.Mint,
                        shadowColor = ZooColors.MintDark,
                        onClick = onNext,
                    ) {
                        Text("下一关 →", color = ZooColors.White, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    JellyButton(
                        modifier = Modifier.width(btnW).height(btnH),
                        color = ZooColors.Coral,
                        shadowColor = ZooColors.CoralDark,
                        onClick = onBackToMap,
                    ) {
                        Text("回到地图", color = ZooColors.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (wrongQuestions.isNotEmpty()) {
                // 与主按钮行留出明显间隙，避免两排按钮糊在一起
                Spacer(Modifier.height(if (compact) 20.dp else 28.dp))
                JellyButton(
                    // 与主按钮同高同宽，视觉上是一组
                    modifier = Modifier.width(btnW).height(btnH),
                    color = ZooColors.Sun,
                    shadowColor = ZooColors.SunDark,
                    onClick = onOpenWrongBook,
                ) {
                    Text(
                        "看看错在哪（${wrongQuestions.size} 题）",
                        color = ZooColors.Ink,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }

            // 底部弹簧：把剩余空间全部吸收，内容自然贴近顶部
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 错题本：列出错题 + 正确读音回放。
 */
@Composable
fun WrongBookScreen(
    questions: List<Question>,
    onPlay: (Question) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ZooColors.Cream)
            .zooSafeInset()
    ) {
        val compact = maxHeight < 480.dp
        val pagePad = zooHPad(maxWidth.value)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pagePad, vertical = if (compact) 14.dp else 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(size = if (compact) 40.dp else 46.dp, onClick = onBack) {
                    Text("←", fontSize = if (compact) 18.sp else 22.sp, color = ZooColors.Ink)
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTitle(
                    "错题本",
                    color = ZooColors.White,
                    fontSize = if (compact) 22 else 28,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "共 ${questions.size} 题",
                    style = MaterialTheme.typography.titleSmall,
                    color = ZooColors.InkSoft,
                )
            }
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

            if (questions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimalAvatar(Animal.RABBIT, size = if (compact) 80.dp else 120.dp, mood = Mood.PROUD)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "还没有错题，真棒！",
                            style = MaterialTheme.typography.titleMedium,
                            color = ZooColors.InkSoft,
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                ) {
                    items(questions, key = { it.id }) { q ->
                        WrongCard(q, compact, onClick = { onPlay(q) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WrongCard(q: Question, compact: Boolean, onClick: () -> Unit) {
    ZooCard(
        modifier = Modifier.fillMaxWidth().height(if (compact) 68.dp else 92.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(if (compact) 40.dp else 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ZooColors.ofDomain(q.domain.key).copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (q.kind) {
                        com.alex.pinyinzoo.model.QuestionKind.LISTEN_PICK -> "🎧"
                        com.alex.pinyinzoo.model.QuestionKind.TONE_PICK -> "🎵"
                        com.alex.pinyinzoo.model.QuestionKind.BLEND -> "🧩"
                        com.alex.pinyinzoo.model.QuestionKind.FILL_FINAL -> "✏️"
                        com.alex.pinyinzoo.model.QuestionKind.TONE_MARK -> "✍️"
                        com.alex.pinyinzoo.model.QuestionKind.WORD_PICK -> "📖"
                        com.alex.pinyinzoo.model.QuestionKind.READ_ALOUD -> "🎙"
                        com.alex.pinyinzoo.model.QuestionKind.MATCH -> "🔗"
                    },
                    fontSize = if (compact) 18.sp else 24.sp,
                )
            }
            Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    q.display.ifEmpty { q.prompt },
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    color = ZooColors.Ink,
                    maxLines = 1,
                )
                Text(
                    q.correctOption?.text ?: q.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZooColors.InkSoft,
                    maxLines = 1,
                )
            }
            Text("🔊", fontSize = if (compact) 18.sp else 22.sp)
        }
    }
}

/**
 * 家长报告：知识点热力图 + 声调准确率。
 */
@Composable
fun ParentReportScreen(
    tagAccuracy: (PinyinTag) -> Float,
    tagCounts: (PinyinTag) -> Pair<Int, Int>,
    toneAverages: (Int) -> Float,
    totalStars: Int,
    wrongCount: Int,
    todayCount: Int,
    readCount: Int,
    onBack: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ZooColors.Cream)
            .zooSafeInset()
    ) {
        val compact = maxHeight < 480.dp
        val pagePad = zooHPad(maxWidth.value)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pagePad, vertical = if (compact) 14.dp else 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(size = if (compact) 40.dp else 46.dp, onClick = onBack) {
                    Text("←", fontSize = if (compact) 18.sp else 22.sp, color = ZooColors.Ink)
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTitle("学习报告", color = ZooColors.White, fontSize = if (compact) 22 else 28)
            }
            Spacer(Modifier.height(if (compact) 6.dp else 14.dp))

            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp)
            ) {
                // 左：概览 + 声调
                Column(
                    Modifier.weight(0.42f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                ) {
                    StatCard(
                        "总星数", "$totalStars / ${Domain.playable.size * 9}",
                        ZooColors.Sun, compact
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("今日答题", "$todayCount", ZooColors.Mint, compact, Modifier.weight(1f))
                        StatCard("错题", "$wrongCount", ZooColors.Coral, compact, Modifier.weight(1f))
                    }
                    ZooCard(Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.fillMaxSize().padding(if (compact) 8.dp else 14.dp)) {
                            Text(
                                "声调准确率",
                                style = MaterialTheme.typography.titleSmall,
                                color = ZooColors.Ink,
                            )
                            if (readCount == 0) {
                                Text(
                                    "还没有跟读记录\n玩玩「跟读」题就能看到啦",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZooColors.InkSoft,
                                )
                            } else {
                                Spacer(Modifier.height(6.dp))
                                listOf(1, 2, 3, 4).forEach { t ->
                                    val avg = toneAverages(t)
                                    ToneBar(t, avg, compact)
                                }
                            }
                        }
                    }
                }

                // 右：知识点热力图
                ZooCard(Modifier.weight(0.58f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(if (compact) 10.dp else 16.dp)) {
                        Text(
                            "知识点掌握情况",
                            style = MaterialTheme.typography.titleMedium,
                            color = ZooColors.Ink,
                        )
                        Text(
                            "橙色表示还要多练",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZooColors.InkSoft,
                        )
                        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

                        // 手机横屏只有 406dp 高，20 个标签必须可滚动，
                        // 否则会被压扁到不可见（每行高度接近 0）。
                        val tags = PinyinTag.entries
                        val rows = tags.chunked(2)
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp),
                        ) {
                            items(rows) { pair ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
                                ) {
                                    pair.forEach { tag ->
                                        TagChip(
                                            tag = tag,
                                            acc = tagAccuracy(tag),
                                            counts = tagCounts(tag),
                                            compact = compact,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(if (compact) 26.dp else 34.dp),
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            JellyButton(
                modifier = Modifier.height(if (compact) 36.dp else 44.dp).width(if (compact) 120.dp else 150.dp),
                color = ZooColors.InkSoft.copy(alpha = 0.25f),
                shadowColor = ZooColors.Ink.copy(alpha = 0.2f),
                onClick = onReset,
            ) {
                Text("清空全部进度", color = ZooColors.Ink, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    ZooCard(modifier = modifier.fillMaxWidth().height(if (compact) 52.dp else 70.dp), color = color.copy(alpha = 0.22f)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ZooColors.InkSoft)
            Text(
                value,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                color = ZooColors.Ink,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun ToneBar(tone: Int, avg: Float, compact: Boolean) {
    val has = avg >= 0
    val pct = if (has) avg / 100f else 0f
    val color = when {
        !has -> ZooColors.Ink.copy(alpha = 0.12f)
        pct >= 0.8f -> ZooColors.Good
        pct >= 0.6f -> ZooColors.Sun
        else -> ZooColors.Retry
    }
    Row(
        Modifier.fillMaxWidth().height(if (compact) 18.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${tone}声",
            style = MaterialTheme.typography.labelSmall,
            color = ZooColors.Ink,
            modifier = Modifier.width(if (compact) 26.dp else 34.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(if (compact) 10.dp else 14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ZooColors.Ink.copy(alpha = 0.08f))
        ) {
            if (has) {
                Box(
                    Modifier
                        .fillMaxWidth(pct.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(color)
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (has) "${avg.toInt()}" else "-",
            style = MaterialTheme.typography.labelSmall,
            color = ZooColors.InkSoft,
            modifier = Modifier.width(if (compact) 22.dp else 28.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TagChip(
    tag: PinyinTag,
    acc: Float,
    counts: Pair<Int, Int>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val has = acc >= 0
    val color = when {
        !has -> ZooColors.Ink.copy(alpha = 0.10f)
        acc >= 0.85f -> ZooColors.Good.copy(alpha = 0.35f)
        acc >= 0.65f -> ZooColors.Sun.copy(alpha = 0.42f)
        else -> ZooColors.Retry.copy(alpha = 0.42f)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .padding(horizontal = if (compact) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tag.label,
            style = MaterialTheme.typography.labelSmall,
            color = ZooColors.Ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (has) "${(acc * 100).toInt()}%" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = ZooColors.Ink,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 收集册：已解锁的动物。
 */
@Composable
fun CollectionScreen(
    unlocked: Set<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ZooColors.Cream)
            .zooSafeInset()
    ) {
        val compact = maxHeight < 480.dp
        val pagePad = zooHPad(maxWidth.value)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pagePad, vertical = if (compact) 14.dp else 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(size = if (compact) 40.dp else 46.dp, onClick = onBack) {
                    Text("←", fontSize = if (compact) 18.sp else 22.sp, color = ZooColors.Ink)
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTitle("动物收集册", color = ZooColors.White, fontSize = if (compact) 22 else 28)
                Spacer(Modifier.weight(1f))
                Text(
                    "${unlocked.size} / 8",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZooColors.Ink,
                )
            }
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

            val all = Domain.playable
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
            ) {
                all.chunked(4).forEach { row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
                    ) {
                        row.forEach { d ->
                            val has = d.key in unlocked
                            ZooCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                color = if (has) ZooColors.White else ZooColors.CreamDark,
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(if (compact) 6.dp else 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    AnimalAvatar(
                                        animal = Animal.of(d.mascot),
                                        size = if (compact) 54.dp else 88.dp,
                                        mood = if (has) Mood.PROUD else Mood.THINKING,
                                        animated = has,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (has) d.title else "???",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (has) ZooColors.Ink else ZooColors.InkSoft,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                    if (!has && !compact) {
                                        Text(
                                            "3 星解锁",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ZooColors.InkSoft,
                                        )
                                    }
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
