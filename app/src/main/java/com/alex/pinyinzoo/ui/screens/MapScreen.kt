package com.alex.pinyinzoo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.pinyinzoo.model.Domain
import com.alex.pinyinzoo.model.Tier
import com.alex.pinyinzoo.ui.components.Animal
import com.alex.pinyinzoo.ui.components.AnimalAvatar
import com.alex.pinyinzoo.ui.components.FloatingDots
import com.alex.pinyinzoo.ui.components.Mood
import com.alex.pinyinzoo.ui.components.OutlinedTitle
import com.alex.pinyinzoo.ui.components.RoundIconButton
import com.alex.pinyinzoo.ui.components.StarIcon
import com.alex.pinyinzoo.ui.components.ZooCard
import com.alex.pinyinzoo.ui.components.zooHPad
import com.alex.pinyinzoo.ui.components.zooSafeInset
import com.alex.pinyinzoo.ui.theme.ZooColors

/**
 * 首页：森林地图。
 * 8 个区域排成 4 列 × 2 行的自适应网格，一屏全部可见。
 */
@Composable
fun MapScreen(
    starsOf: (Domain) -> Int,
    isUnlocked: (Domain) -> Boolean,
    totalStars: Int,
    wrongCount: Int,
    onPickDomain: (Domain) -> Unit,
    onOpenCollection: () -> Unit,
    onOpenParent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ZooColors.Cream)
            .zooSafeInset()
    ) {
        // 注意：一旦进入 Column 的 content lambda，ColumnScope 会遮蔽
        // BoxWithConstraintsScope，导致 maxWidth/maxHeight 无法隐式解析。
        // 因此先在这里取值存成局部变量。
        val screenW = maxWidth
        val screenH = maxHeight
        val compact = screenH < 480.dp
        val pagePad = zooHPad(screenW.value)
        // 背景装饰
        FloatingDots(
            Modifier.fillMaxSize(),
            color = ZooColors.Mint,
            count = if (compact) 8 else 14
        )

        Column(Modifier.fillMaxSize()) {
            // ---------- 顶部栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pagePad, vertical = if (compact) 10.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    OutlinedTitle(
                        text = "拼音动物园",
                        color = ZooColors.White,
                        outline = ZooColors.Ink,
                        fontSize = if (compact) 24 else 30
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarIcon(size = if (compact) 16.dp else 20.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$totalStars",
                            style = MaterialTheme.typography.titleMedium,
                            color = ZooColors.Ink
                        )
                        Spacer(Modifier.width(12.dp))
                        if (wrongCount > 0) {
                            Text(
                                "错题 $wrongCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = ZooColors.CoralDark,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ZooColors.Coral.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIconButton(
                        size = if (compact) 42.dp else 48.dp,
                        color = ZooColors.Sky,
                        onClick = onOpenCollection
                    ) { Text("📖", fontSize = if (compact) 18.sp else 22.sp) }
                    RoundIconButton(
                        size = if (compact) 42.dp else 48.dp,
                        color = ZooColors.Mint,
                        onClick = onOpenParent
                    ) { Text("👨‍👩‍👧", fontSize = if (compact) 16.sp else 20.sp) }
                    // 设置入口已隐藏：声音默认开启、动画保持最佳状态，
                    // 两个开关对使用者没有实际意义，去掉避免分散注意力。
                }
            }

            // ---------- 地图：自适应网格（一屏放下全部区域，左右留白严格对称）
            //
            // 之前用横向滚动列表，内容超出屏幕后卡片被硬切在右边缘，
            // 视觉上"右侧没有留白"。改为按屏宽等分的网格后：
            // 左留白 = 右留白 = pagePad，且 8 个区域一屏可见，不用滑动。
            val columns = 4
            val rows = (Domain.playable.size + columns - 1) / columns
            val gap = if (compact) 12.dp else 20.dp
            val availableW = screenW - pagePad * 2 - gap * (columns - 1)
            val cardW = availableW / columns
            val availableH = screenH - if (compact) 96.dp else 150.dp
            val cardH = (availableH - gap * (rows - 1)) / rows

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = pagePad),
                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)
            ) {
                Domain.playable.chunked(columns).forEachIndexed { rowIdx, rowDomains ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rowDomains.forEachIndexed { colIdx, domain ->
                            DomainCard(
                                domain = domain,
                                stars = starsOf(domain),
                                unlocked = isUnlocked(domain),
                                index = rowIdx * columns + colIdx,
                                compact = compact,
                                cardW = cardW,
                                cardH = cardH,
                                onClick = { if (isUnlocked(domain)) onPickDomain(domain) },
                            )
                        }
                        // 末行不满时补空位，保证不拉伸
                        repeat(columns - rowDomains.size) {
                            Spacer(Modifier.width(cardW))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainCard(
    domain: Domain,
    stars: Int,
    unlocked: Boolean,
    index: Int,
    compact: Boolean,
    cardW: Dp,
    cardH: Dp,
    onClick: () -> Unit,
) {
    val color = if (unlocked) ZooColors.ofDomain(domain.key) else ZooColors.InkSoft.copy(alpha = 0.35f)

    ZooCard(
        modifier = Modifier
            .width(cardW)
            .height(cardH),
        color = if (unlocked) ZooColors.White else ZooColors.CreamDark,
        corner = if (compact) 22.dp else 28.dp,
        onClick = if (unlocked) onClick else null,
    ) {
            Box(Modifier.fillMaxSize()) {
                // 顶部彩色条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (compact) 42.dp else 54.dp)
                        .clip(RoundedCornerShape(topStart = if (compact) 20.dp else 26.dp, topEnd = if (compact) 20.dp else 26.dp))
                        .background(color)
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(if (compact) 8.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimalAvatar(
                        animal = Animal.of(domain.mascot),
                        size = if (compact) 62.dp else 84.dp,
                        mood = if (unlocked) Mood.HAPPY else Mood.THINKING,
                        animated = unlocked,
                    )
                    Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
                    Text(
                        domain.title,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        color = if (unlocked) ZooColors.Ink else ZooColors.InkSoft,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = if (compact) 18.sp else 22.sp,
                    )
                    if (!compact) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            domain.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = ZooColors.InkSoft,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 15.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (unlocked) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            repeat(3) { i ->
                                StarIcon(
                                    filled = i < stars.coerceAtMost(3),
                                    size = if (compact) 20.dp else 26.dp,
                                )
                                if (i < 2) Spacer(Modifier.width(2.dp))
                            }
                        }
                    } else {
                        Text(
                            "暂未开放",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZooColors.InkSoft
                        )
                    }
                }
            }
        }
}

/**
 * 关卡选择：某域的 3 个难度层。
 */
@Composable
fun LevelSelectScreen(
    domain: Domain,
    starsOf: (Int) -> Int,
    isTierUnlocked: (Int) -> Boolean,
    onPickTier: (Tier) -> Unit,
    onBack: () -> Unit,
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
        FloatingDots(Modifier.fillMaxSize(), color = color, count = 10)

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pagePad, vertical = if (compact) 14.dp else 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(
                    size = if (compact) 40.dp else 46.dp,
                    onClick = onBack
                ) { Text("←", fontSize = if (compact) 18.sp else 22.sp, color = ZooColors.Ink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    OutlinedTitle(
                        domain.title,
                        color = ZooColors.White,
                        fontSize = if (compact) 22 else 28
                    )
                    Text(
                        domain.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = ZooColors.InkSoft
                    )
                }
                AnimalAvatar(
                    Animal.of(domain.mascot),
                    size = if (compact) 56.dp else 76.dp
                )
            }

            Spacer(Modifier.height(if (compact) 8.dp else 18.dp))

            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tier.entries.forEach { tier ->
                    TierCard(
                        tier = tier,
                        stars = starsOf(tier.star),
                        unlocked = isTierUnlocked(tier.star),
                        color = color,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { onPickTier(tier) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TierCard(
    tier: Tier,
    stars: Int,
    unlocked: Boolean,
    color: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ZooCard(
        modifier = modifier.fillMaxHeight(if (compact) 1f else 0.92f),
        color = if (unlocked) ZooColors.White else ZooColors.CreamDark,
        onClick = if (unlocked) onClick else null,
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 星数徽章
            Box(
                Modifier
                    .size(if (compact) 52.dp else 68.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) color else ZooColors.InkSoft.copy(alpha = 0.3f))
                    .border(3.dp, ZooColors.Ink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "★".repeat(tier.star),
                    fontSize = if (compact) 13.sp else 17.sp,
                    color = ZooColors.White,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            Text(
                tier.label,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
                color = if (unlocked) ZooColors.Ink else ZooColors.InkSoft,
            )
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tier.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZooColors.InkSoft,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
            Row {
                repeat(3) { i ->
                    StarIcon(filled = i < stars, size = if (compact) 20.dp else 28.dp)
                    if (i < 2) Spacer(Modifier.width(3.dp))
                }
            }
            if (!unlocked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "上一关通过后解锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZooColors.InkSoft
                )
            }
        }
    }
}
