package com.alex.pinyinzoo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alex.pinyinzoo.ui.theme.ZooColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 程序化扁平矢量动物。
 *
 * 为什么自绘：
 * - 生图后端不稳定时仍能保证 UI 完整可玩；
 * - 体积为 0（纯 Canvas），任何分辨率都清晰；
 * - 风格与描边语言 100% 统一。
 *
 * 每个动物都在 100x100 的虚拟坐标里绘制，自动按尺寸缩放。
 * 全部遵循：粗描边 + 纯色块 + 硬阴影 + 圆润轮廓。
 */
enum class Animal(val key: String) {
    SNAKE("snake"), LION("lion"), FROG("frog"), ELEPHANT("elephant"),
    FISH("fish"), MONKEY("monkey"), BEAR("bear"), RABBIT("rabbit"),
    OWL("owl"), CAT("cat"), PIG("pig"), PANDA("panda"),
    TIGER("tiger"), BIRD("bird"), DOG("dog"), CHICK("chick");

    companion object {
        fun of(key: String): Animal = entries.firstOrNull { it.key == key } ?: BEAR
    }
}

/** 表情 */
enum class Mood { HAPPY, EXCITED, THINKING, SAD, SURPRISED, PROUD }

@Composable
fun AnimalAvatar(
    animal: Animal,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    mood: Mood = Mood.HAPPY,
    /** 呼吸动画：让画面活起来 */
    animated: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheT"
    )
    val breath = if (animated) t else 0.5f

    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension / 100f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            translate(cx - 50f * s, cy - 50f * s) {
                scaleBy(s) {
                    drawGroundShadow(breath)
                    when (animal) {
                        Animal.SNAKE -> drawSnake(breath, mood)
                        Animal.LION -> drawLion(breath, mood)
                        Animal.FROG -> drawFrog(breath, mood)
                        Animal.ELEPHANT -> drawElephant(breath, mood)
                        Animal.FISH -> drawFish(breath, mood)
                        Animal.MONKEY -> drawMonkey(breath, mood)
                        Animal.BEAR -> drawBear(breath, mood)
                        Animal.RABBIT -> drawRabbit(breath, mood)
                        Animal.OWL -> drawOwl(breath, mood)
                        Animal.CAT -> drawCat(breath, mood)
                        Animal.PIG -> drawPig(breath, mood)
                        Animal.PANDA -> drawPanda(breath, mood)
                        Animal.TIGER -> drawTiger(breath, mood)
                        Animal.BIRD -> drawBird(breath, mood)
                        Animal.DOG -> drawDog(breath, mood)
                        Animal.CHICK -> drawChick(breath, mood)
                    }
                }
            }
        }
    }
}

// 缩放包装：把 100x100 虚拟坐标映射到实际尺寸
private fun DrawScope.scaleBy(s: Float, block: DrawScope.() -> Unit) {
    scale(scaleX = s, scaleY = s, pivot = Offset(0f, 0f)) { block() }
}

// ---------------------------------------------------------------- 通用绘制工具

private const val OUTLINE = 3.4f

private fun DrawScope.ink(color: Color = ZooColors.Ink, width: Float = OUTLINE) =
    Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)

private fun DrawScope.ellipse(
    color: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    outlined: Boolean = true,
) {
    val w = right - left
    val h = bottom - top
    drawOval(color, Offset(left, top), Size(w, h))
    if (outlined) drawOval(ZooColors.Ink, Offset(left, top), Size(w, h), style = ink())
}

private fun DrawScope.circle(
    color: Color,
    cx: Float,
    cy: Float,
    r: Float,
    outlined: Boolean = true,
) {
    drawCircle(color, r, Offset(cx, cy))
    if (outlined) drawCircle(ZooColors.Ink, r, Offset(cx, cy), style = ink())
}

private fun DrawScope.rrect(
    color: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    outlined: Boolean = true,
) {
    drawRoundRect(
        color, Offset(left, top), Size(right - left, bottom - top),
        CornerRadius(radius, radius)
    )
    if (outlined) drawRoundRect(
        ZooColors.Ink, Offset(left, top), Size(right - left, bottom - top),
        CornerRadius(radius, radius), style = ink()
    )
}

/** 眼睛：带高光，表情由 mood 控制 */
private fun DrawScope.eye(cx: Float, cy: Float, r: Float, mood: Mood, blink: Float = 0f) {
    when (mood) {
        Mood.HAPPY, Mood.PROUD -> {
            // 弯弯的笑眼
            val path = Path().apply {
                moveTo(cx - r, cy + r * 0.3f)
                quadraticBezierTo(cx, cy - r * 1.1f, cx + r, cy + r * 0.3f)
            }
            drawPath(path, ZooColors.Ink, style = Stroke(width = r * 0.55f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Mood.THINKING -> {
            // 眯眼斜视
            drawLine(
                ZooColors.Ink, Offset(cx - r, cy + r * 0.2f),
                Offset(cx + r * 0.7f, cy - r * 0.3f),
                strokeWidth = r * 0.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        Mood.SAD -> {
            val path = Path().apply {
                moveTo(cx - r, cy - r * 0.4f)
                quadraticBezierTo(cx, cy + r * 0.8f, cx + r, cy - r * 0.4f)
            }
            drawPath(path, ZooColors.Ink, style = Stroke(width = r * 0.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Mood.EXCITED, Mood.SURPRISED -> {
            // 大眼 + 大高光
            drawCircle(ZooColors.White, r * 1.25f, Offset(cx, cy))
            drawCircle(ZooColors.Ink, r * 0.9f, Offset(cx, cy))
            drawCircle(ZooColors.White, r * 0.42f, Offset(cx - r * 0.3f, cy - r * 0.35f))
        }
    }
    if (mood == Mood.HAPPY) {
        // 普通圆眼
        drawCircle(ZooColors.White, r * 1.15f, Offset(cx, cy))
        drawCircle(ZooColors.Ink, r * 0.82f, Offset(cx, cy))
        drawCircle(ZooColors.White, r * 0.36f, Offset(cx - r * 0.28f, cy - r * 0.3f))
    }
}

/** 腮红 */
private fun DrawScope.cheek(cx: Float, cy: Float, r: Float) {
    drawCircle(ZooColors.Coral.copy(alpha = 0.35f), r, Offset(cx, cy))
}

/** 嘴巴 */
private fun DrawScope.mouth(cx: Float, cy: Float, w: Float, mood: Mood) {
    when (mood) {
        Mood.SAD -> {
            val path = Path().apply {
                moveTo(cx - w, cy + w * 0.3f)
                quadraticBezierTo(cx, cy - w * 0.5f, cx + w, cy + w * 0.3f)
            }
            drawPath(path, ZooColors.Ink, style = Stroke(width = 2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Mood.THINKING -> {
            drawLine(
                ZooColors.Ink, Offset(cx - w * 0.5f, cy), Offset(cx + w * 0.5f, cy),
                strokeWidth = 2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        Mood.EXCITED, Mood.SURPRISED -> {
            // 张开的嘴
            drawOval(
                ZooColors.Coral.copy(alpha = 0.85f),
                Offset(cx - w * 0.5f, cy - w * 0.3f),
                Size(w, w * 0.9f)
            )
            drawOval(
                ZooColors.Ink, Offset(cx - w * 0.5f, cy - w * 0.3f),
                Size(w, w * 0.9f), style = Stroke(2.2f)
            )
        }
        else -> {
            val path = Path().apply {
                moveTo(cx - w, cy)
                quadraticBezierTo(cx, cy + w * 1.0f, cx + w, cy)
            }
            drawPath(path, ZooColors.Ink, style = Stroke(width = 2.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawGroundShadow(breath: Float) {
    val w = 34f + breath * 2f
    drawOval(
        ZooColors.Ink.copy(alpha = 0.10f),
        Offset(50f - w / 2, 87f),
        Size(w, 7f)
    )
}

// ---------------------------------------------------------------- 各动物

private fun DrawScope.drawSnake(breath: Float, mood: Mood) {
    val bob = breath * 1.2f
    // 身体曲线
    val body = Path().apply {
        moveTo(28f, 84f)
        cubicTo(18f, 74f, 30f, 62f, 44f, 60f + bob)
        cubicTo(58f, 58f, 66f, 50f, 62f, 40f + bob)
    }
    drawPath(body, ZooColors.Mint, style = Stroke(width = 21f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    drawPath(body, ZooColors.Ink, style = Stroke(width = 25.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    drawPath(body, ZooColors.Mint, style = Stroke(width = 21f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    // 花纹
    for (i in 0..3) {
        val t = i / 4f
        val x = 30f + t * 30f
        val y = 80f - t * 36f
        drawCircle(ZooColors.MintDark, 3.2f, Offset(x, y))
    }
    // 头
    circle(ZooColors.Mint, 62f, 36f + bob, 17f)
    eye(56f, 32f + bob, 4.2f, mood)
    eye(68f, 32f + bob, 4.2f, mood)
    cheek(52f, 41f + bob, 3.4f)
    cheek(72f, 41f + bob, 3.4f)
    // 舌头
    val tongue = Path().apply {
        moveTo(62f, 47f + bob)
        lineTo(62f, 55f + bob)
        moveTo(62f, 55f + bob)
        lineTo(58f, 51f + bob)
        moveTo(62f, 55f + bob)
        lineTo(66f, 51f + bob)
    }
    drawPath(tongue, ZooColors.Coral, style = Stroke(2.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    mouth(62f, 44f + bob, 5f, mood)
}

private fun DrawScope.drawLion(breath: Float, mood: Mood) {
    val bob = breath * 1f
    // 鬃毛
    val mane = Path().apply {
        val cx = 50f
        val cy = 44f + bob
        for (i in 0 until 14) {
            val a = (i / 14f) * 2 * PI
            val r1 = 25f
            val r2 = 33f
            val x1 = cx + (r1 * cos(a)).toFloat()
            val y1 = cy + (r1 * sin(a)).toFloat()
            val x2 = cx + (r2 * cos(a + 0.1)).toFloat()
            val y2 = cy + (r2 * sin(a + 0.1)).toFloat()
            if (i == 0) moveTo(x1, y1) else lineTo(x1, y1)
            lineTo(x2, y2)
        }
        close()
    }
    drawPath(mane, ZooColors.Sun)
    drawPath(mane, ZooColors.Ink, style = ink())
    // 脸
    circle(ZooColors.SunDark.copy(alpha = 0.0f), 50f, 44f + bob, 20f, outlined = false)
    circle(ZooColors.Sun, 50f, 44f + bob, 20f)
    // 耳朵
    circle(ZooColors.Sun, 34f, 28f + bob, 6.4f)
    circle(ZooColors.Sun, 66f, 28f + bob, 6.4f)
    eye(43f, 41f + bob, 4.6f, mood)
    eye(57f, 41f + bob, 4.6f, mood)
    cheek(36f, 50f + bob, 4f)
    cheek(64f, 50f + bob, 4f)
    // 鼻子
    val nose = Path().apply {
        moveTo(46f, 49f + bob)
        lineTo(54f, 49f + bob)
        lineTo(50f, 54f + bob)
        close()
    }
    drawPath(nose, ZooColors.CoralDark)
    drawPath(nose, ZooColors.Ink, style = Stroke(2f))
    mouth(50f, 57f + bob, 5f, mood)
}

private fun DrawScope.drawFrog(breath: Float, mood: Mood) {
    val bob = breath * 1.4f
    // 后腿
    ellipse(ZooColors.Mint, 22f, 62f, 40f, 84f)
    ellipse(ZooColors.Mint, 60f, 62f, 78f, 84f)
    // 身体
    ellipse(ZooColors.Mint, 27f, 44f + bob, 73f, 86f)
    // 肚子
    ellipse(ZooColors.White.copy(alpha = 0.55f), 37f, 62f + bob, 63f, 84f, outlined = false)
    // 眼睛（凸起）
    circle(ZooColors.Mint, 38f, 34f + bob, 11f)
    circle(ZooColors.Mint, 62f, 34f + bob, 11f)
    drawCircle(ZooColors.White, 7f, Offset(38f, 34f + bob))
    drawCircle(ZooColors.White, 7f, Offset(62f, 34f + bob))
    drawCircle(ZooColors.Ink, 4.4f, Offset(38f + 1f, 34f + bob))
    drawCircle(ZooColors.Ink, 4.4f, Offset(62f + 1f, 34f + bob))
    drawCircle(ZooColors.White, 1.8f, Offset(36.4f, 32.4f + bob))
    drawCircle(ZooColors.White, 1.8f, Offset(60.4f, 32.4f + bob))
    cheek(28f, 52f + bob, 4.4f)
    cheek(72f, 52f + bob, 4.4f)
    mouth(50f, 54f + bob, 8f, mood)
}

private fun DrawScope.drawElephant(breath: Float, mood: Mood) {
    val bob = breath * 1f
    // 耳朵
    ellipse(ZooColors.Sky.copy(alpha = 0.9f), 10f, 30f + bob, 42f, 68f + bob)
    ellipse(ZooColors.Sky.copy(alpha = 0.9f), 58f, 30f + bob, 90f, 68f + bob)
    // 头
    ellipse(ZooColors.Sky, 28f, 26f + bob, 72f, 72f + bob)
    // 鼻子
    val trunk = Path().apply {
        moveTo(50f, 62f + bob)
        cubicTo(52f, 72f, 44f, 80f, 50f, 88f)
    }
    drawPath(trunk, ZooColors.Ink, style = Stroke(13f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    drawPath(trunk, ZooColors.Sky, style = Stroke(9f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    eye(41f, 44f + bob, 4.4f, mood)
    eye(59f, 44f + bob, 4.4f, mood)
    cheek(32f, 52f + bob, 4f)
    cheek(68f, 52f + bob, 4f)
    mouth(50f, 56f + bob, 6f, mood)
}

private fun DrawScope.drawFish(breath: Float, mood: Mood) {
    val bob = breath * 1.6f
    // 尾巴
    val tail = Path().apply {
        moveTo(28f, 50f + bob)
        lineTo(12f, 38f + bob)
        lineTo(12f, 62f + bob)
        close()
    }
    drawPath(tail, ZooColors.Lavender)
    drawPath(tail, ZooColors.Ink, style = ink())
    // 身体
    ellipse(ZooColors.Lavender, 26f, 34f + bob, 80f, 68f + bob)
    // 背鳍
    val fin = Path().apply {
        moveTo(42f, 36f + bob)
        quadraticBezierTo(56f, 18f + bob, 64f, 37f + bob)
    }
    drawPath(fin, ZooColors.LavenderDark)
    drawPath(fin, ZooColors.Ink, style = ink())
    eye(66f, 48f + bob, 4.6f, mood)
    cheek(58f, 56f + bob, 4f)
    mouth(74f, 56f + bob, 4f, mood)
    // 泡泡
    drawCircle(ZooColors.Sky.copy(alpha = 0.5f), 3f, Offset(84f, 34f + bob))
    drawCircle(ZooColors.Sky.copy(alpha = 0.35f), 2f, Offset(88f, 24f + bob))
}

private fun DrawScope.drawMonkey(breath: Float, mood: Mood) {
    val bob = breath * 1.2f
    // 耳朵
    circle(ZooColors.Coral, 22f, 46f + bob, 9f)
    circle(ZooColors.Coral, 78f, 46f + bob, 9f)
    // 头
    circle(ZooColors.CoralDark, 50f, 44f + bob, 24f)
    // 脸盘
    ellipse(ZooColors.Cream, 33f, 38f + bob, 67f, 70f + bob)
    eye(43f, 46f + bob, 4.4f, mood)
    eye(57f, 46f + bob, 4.4f, mood)
    cheek(35f, 55f + bob, 3.6f)
    cheek(65f, 55f + bob, 3.6f)
    // 鼻子
    drawCircle(ZooColors.Coral, 2.2f, Offset(47f, 54f + bob))
    drawCircle(ZooColors.Coral, 2.2f, Offset(53f, 54f + bob))
    mouth(50f, 60f + bob, 5f, mood)
}

private fun DrawScope.drawBear(breath: Float, mood: Mood) {
    val bob = breath * 1.1f
    // 耳朵
    circle(ZooColors.CoralDark, 30f, 28f + bob, 9f)
    circle(ZooColors.CoralDark, 70f, 28f + bob, 9f)
    drawCircle(ZooColors.Coral, 4.4f, Offset(30f, 28f + bob))
    drawCircle(ZooColors.Coral, 4.4f, Offset(70f, 28f + bob))
    // 头
    circle(ZooColors.CoralDark, 50f, 46f + bob, 24f)
    // 口鼻
    ellipse(ZooColors.Cream, 38f, 50f + bob, 62f, 68f + bob)
    eye(42f, 42f + bob, 4.4f, mood)
    eye(58f, 42f + bob, 4.4f, mood)
    // 鼻子
    ellipse(ZooColors.Ink, 46f, 52f + bob, 54f, 59f + bob, outlined = false)
    mouth(50f, 60f + bob, 5f, mood)
    cheek(34f, 55f + bob, 3.8f)
    cheek(66f, 55f + bob, 3.8f)
}

private fun DrawScope.drawRabbit(breath: Float, mood: Mood) {
    val bob = breath * 1.5f
    // 长耳朵
    rrect(ZooColors.White, 34f, 6f + bob, 45f, 42f + bob, 6f)
    rrect(ZooColors.White, 55f, 6f + bob, 66f, 42f + bob, 6f)
    rrect(ZooColors.Coral.copy(alpha = 0.55f), 38f, 12f + bob, 41f, 36f + bob, 2f, outlined = false)
    rrect(ZooColors.Coral.copy(alpha = 0.55f), 59f, 12f + bob, 62f, 36f + bob, 2f, outlined = false)
    // 头
    circle(ZooColors.White, 50f, 54f + bob, 21f)
    eye(43f, 50f + bob, 4.2f, mood)
    eye(57f, 50f + bob, 4.2f, mood)
    cheek(36f, 60f + bob, 3.6f)
    cheek(64f, 60f + bob, 3.6f)
    // 鼻子
    val nose = Path().apply {
        moveTo(47f, 58f + bob)
        lineTo(53f, 58f + bob)
        lineTo(50f, 62f + bob)
        close()
    }
    drawPath(nose, ZooColors.Coral)
    drawPath(nose, ZooColors.Ink, style = Stroke(1.8f))
    mouth(50f, 66f + bob, 4f, mood)
}

private fun DrawScope.drawOwl(breath: Float, mood: Mood) {
    val bob = breath * 1f
    // 身体
    ellipse(ZooColors.Lavender, 28f, 34f + bob, 72f, 88f + bob)
    // 翅膀
    ellipse(ZooColors.LavenderDark, 20f, 50f + bob, 34f, 78f + bob)
    ellipse(ZooColors.LavenderDark, 66f, 50f + bob, 80f, 78f + bob)
    // 眼睛盘
    circle(ZooColors.Cream, 41f, 48f + bob, 12f)
    circle(ZooColors.Cream, 59f, 48f + bob, 12f)
    drawCircle(ZooColors.Ink, 6.4f, Offset(41f, 48f + bob))
    drawCircle(ZooColors.Ink, 6.4f, Offset(59f, 48f + bob))
    drawCircle(ZooColors.White, 2.6f, Offset(39f, 45.6f + bob))
    drawCircle(ZooColors.White, 2.6f, Offset(57f, 45.6f + bob))
    // 喙
    val beak = Path().apply {
        moveTo(46f, 56f + bob)
        lineTo(54f, 56f + bob)
        lineTo(50f, 63f + bob)
        close()
    }
    drawPath(beak, ZooColors.Sun)
    drawPath(beak, ZooColors.Ink, style = Stroke(2f))
    // 冠羽
    drawLine(ZooColors.Ink, Offset(38f, 32f + bob), Offset(34f, 26f + bob), 2.6f)
    drawLine(ZooColors.Ink, Offset(62f, 32f + bob), Offset(66f, 26f + bob), 2.6f)
}

private fun DrawScope.drawCat(breath: Float, mood: Mood) {
    val bob = breath * 1.1f
    // 耳朵
    val earL = Path().apply {
        moveTo(30f, 34f + bob); lineTo(34f, 18f + bob); lineTo(46f, 28f + bob); close()
    }
    val earR = Path().apply {
        moveTo(70f, 34f + bob); lineTo(66f, 18f + bob); lineTo(54f, 28f + bob); close()
    }
    listOf(earL, earR).forEach {
        drawPath(it, ZooColors.Sun)
        drawPath(it, ZooColors.Ink, style = ink())
    }
    circle(ZooColors.Sun, 50f, 50f + bob, 22f)
    eye(42f, 48f + bob, 4.4f, mood)
    eye(58f, 48f + bob, 4.4f, mood)
    // 胡须
    listOf(-1f, 1f).forEach { dir ->
        drawLine(ZooColors.Ink, Offset(50f + dir * 8f, 58f + bob), Offset(50f + dir * 22f, 56f + bob), 1.8f)
        drawLine(ZooColors.Ink, Offset(50f + dir * 8f, 61f + bob), Offset(50f + dir * 21f, 63f + bob), 1.8f)
    }
    val nose = Path().apply {
        moveTo(47f, 56f + bob); lineTo(53f, 56f + bob); lineTo(50f, 60f + bob); close()
    }
    drawPath(nose, ZooColors.Coral)
    mouth(50f, 63f + bob, 4f, mood)
}

private fun DrawScope.drawPig(breath: Float, mood: Mood) {
    val bob = breath * 1.2f
    // 耳朵
    val earL = Path().apply {
        moveTo(30f, 32f + bob); lineTo(38f, 20f + bob); lineTo(46f, 30f + bob); close()
    }
    val earR = Path().apply {
        moveTo(70f, 32f + bob); lineTo(62f, 20f + bob); lineTo(54f, 30f + bob); close()
    }
    listOf(earL, earR).forEach {
        drawPath(it, ZooColors.Coral)
        drawPath(it, ZooColors.Ink, style = ink())
    }
    circle(ZooColors.Coral, 50f, 50f + bob, 23f)
    eye(41f, 44f + bob, 4f, mood)
    eye(59f, 44f + bob, 4f, mood)
    // 猪鼻
    ellipse(ZooColors.CoralDark, 42f, 52f + bob, 58f, 64f + bob)
    drawCircle(ZooColors.Ink, 2f, Offset(46.5f, 58f + bob))
    drawCircle(ZooColors.Ink, 2f, Offset(53.5f, 58f + bob))
    cheek(30f, 54f + bob, 4f)
    cheek(70f, 54f + bob, 4f)
    mouth(50f, 68f + bob, 4f, mood)
}

private fun DrawScope.drawPanda(breath: Float, mood: Mood) {
    val bob = breath * 1.1f
    circle(ZooColors.Ink, 32f, 28f + bob, 9f)
    circle(ZooColors.Ink, 68f, 28f + bob, 9f)
    circle(ZooColors.White, 50f, 50f + bob, 23f)
    // 黑眼圈
    ellipse(ZooColors.Ink, 32f, 40f + bob, 48f, 58f + bob, outlined = false)
    ellipse(ZooColors.Ink, 52f, 40f + bob, 68f, 58f + bob, outlined = false)
    drawCircle(ZooColors.White, 4.2f, Offset(40f, 48f + bob))
    drawCircle(ZooColors.White, 4.2f, Offset(60f, 48f + bob))
    drawCircle(ZooColors.Ink, 2.6f, Offset(40f, 48f + bob))
    drawCircle(ZooColors.Ink, 2.6f, Offset(60f, 48f + bob))
    ellipse(ZooColors.Ink, 46f, 56f + bob, 54f, 61f + bob, outlined = false)
    mouth(50f, 64f + bob, 4f, mood)
}

private fun DrawScope.drawTiger(breath: Float, mood: Mood) {
    val bob = breath * 1.1f
    // 耳朵
    circle(ZooColors.SunDark, 28f, 30f + bob, 9f)
    circle(ZooColors.SunDark, 72f, 30f + bob, 9f)
    circle(ZooColors.Sun, 50f, 48f + bob, 24f)
    // 条纹
    listOf(-1f, 1f).forEach { dir ->
        drawLine(ZooColors.Ink, Offset(50f + dir * 12f, 28f + bob), Offset(50f + dir * 16f, 38f + bob), 3f)
        drawLine(ZooColors.Ink, Offset(50f + dir * 20f, 40f + bob), Offset(50f + dir * 25f, 48f + bob), 3f)
    }
    // 白口鼻
    ellipse(ZooColors.Cream, 36f, 52f + bob, 64f, 72f + bob)
    eye(41f, 44f + bob, 4.6f, mood)
    eye(59f, 44f + bob, 4.6f, mood)
    val nose = Path().apply {
        moveTo(46f, 55f + bob); lineTo(54f, 55f + bob); lineTo(50f, 60f + bob); close()
    }
    drawPath(nose, ZooColors.CoralDark)
    mouth(50f, 63f + bob, 5f, mood)
}

private fun DrawScope.drawBird(breath: Float, mood: Mood) {
    val bob = breath * 1.6f
    // 尾巴
    val tail = Path().apply {
        moveTo(28f, 58f + bob); lineTo(12f, 50f + bob); lineTo(16f, 66f + bob); close()
    }
    drawPath(tail, ZooColors.SkyDark)
    drawPath(tail, ZooColors.Ink, style = ink())
    // 身体
    ellipse(ZooColors.Sky, 26f, 40f + bob, 68f, 82f + bob)
    // 翅膀
    ellipse(ZooColors.SkyDark, 38f, 54f + bob, 58f, 72f + bob)
    // 头
    circle(ZooColors.Sky, 62f, 36f + bob, 16f)
    eye(66f, 34f + bob, 4f, mood)
    // 喙
    val beak = Path().apply {
        moveTo(76f, 36f + bob); lineTo(88f, 40f + bob); lineTo(76f, 44f + bob); close()
    }
    drawPath(beak, ZooColors.Sun)
    drawPath(beak, ZooColors.Ink, style = Stroke(2.2f))
    cheek(56f, 44f + bob, 3.4f)
}

private fun DrawScope.drawDog(breath: Float, mood: Mood) {
    val bob = breath * 1.4f
    // 垂耳
    ellipse(ZooColors.CoralDark, 20f, 34f + bob, 36f, 62f + bob)
    ellipse(ZooColors.CoralDark, 64f, 34f + bob, 80f, 62f + bob)
    circle(ZooColors.Sun, 50f, 46f + bob, 22f)
    // 口鼻
    ellipse(ZooColors.Cream, 37f, 52f + bob, 63f, 70f + bob)
    eye(42f, 42f + bob, 4.4f, mood)
    eye(58f, 42f + bob, 4.4f, mood)
    ellipse(ZooColors.Ink, 45f, 54f + bob, 55f, 60f + bob, outlined = false)
    mouth(50f, 62f + bob, 5f, mood)
    // 舌头
    rrect(ZooColors.Coral, 47f, 64f + bob, 53f, 72f + bob, 2.5f)
}

private fun DrawScope.drawChick(breath: Float, mood: Mood) {
    val bob = breath * 1.8f
    // 绒毛
    circle(ZooColors.Sun, 50f, 54f + bob, 22f)
    // 冠
    drawLine(ZooColors.Ink, Offset(46f, 32f + bob), Offset(43f, 24f + bob), 2.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(ZooColors.Ink, Offset(52f, 31f + bob), Offset(53f, 23f + bob), 2.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    eye(43f, 50f + bob, 4f, mood)
    eye(57f, 50f + bob, 4f, mood)
    // 喙
    val beak = Path().apply {
        moveTo(46f, 58f + bob); lineTo(54f, 58f + bob); lineTo(50f, 64f + bob); close()
    }
    drawPath(beak, ZooColors.Coral)
    drawPath(beak, ZooColors.Ink, style = Stroke(2f))
    cheek(34f, 58f + bob, 3.6f)
    cheek(66f, 58f + bob, 3.6f)
    // 小翅膀
    ellipse(ZooColors.SunDark, 26f, 60f + bob, 38f, 72f + bob)
    ellipse(ZooColors.SunDark, 62f, 60f + bob, 74f, 72f + bob)
}
