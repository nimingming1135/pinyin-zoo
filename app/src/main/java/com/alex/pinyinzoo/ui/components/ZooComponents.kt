package com.alex.pinyinzoo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.pinyinzoo.ui.theme.ZooColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 扁平矢量风的基础组件。
 *
 * 设计语言：
 * - 粗描边（3dp 深藏青）
 * - 硬阴影（下移 4dp 的实色投影，不用模糊）
 * - 大圆角
 * - 按下时整体下移 + 缩放，模拟"按下去"的手感
 */

/** 硬阴影卡片：扁平风的标志性元素 */
@Composable
fun ZooCard(
    modifier: Modifier = Modifier,
    color: Color = ZooColors.White,
    corner: Dp = 24.dp,
    shadowOffset: Dp = 5.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        // 底部实色阴影层
        Box(
            Modifier
                .matchParentSize()
                .offset(y = shadowOffset)
                .clip(RoundedCornerShape(corner))
                .background(ZooColors.Ink.copy(alpha = 0.18f))
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(corner))
                .background(color)
                .border(3.dp, ZooColors.Ink, RoundedCornerShape(corner))
                .then(
                    if (onClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ) else Modifier
                )
        ) {
            content()
        }
    }
}

/** 可点击的"果冻"按钮：按下缩小回弹 */
@Composable
fun JellyButton(
    modifier: Modifier = Modifier,
    color: Color = ZooColors.Coral,
    shadowColor: Color = ZooColors.CoralDark,
    corner: Dp = 22.dp,
    enabled: Boolean = true,
    /** 禁用时是否仍使用传入的颜色。
     *  答题反馈需要保留绿/红底色，此时传 true；
     *  普通"按钮不可用"场景传 false，显示灰色。 */
    keepColorWhenDisabled: Boolean = false,
    /** 描边颜色，默认深藏青 */
    strokeColor: Color = ZooColors.Ink,
    /** 描边粗细，反馈态会加粗 */
    strokeWidth: Dp = 3.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.45f, stiffness = 900f
        ),
        label = "jellyScale"
    )

    // 关键：禁用时默认变灰，但反馈态需要保留颜色
    val dimmed = !enabled && !keepColorWhenDisabled
    val faceColor = if (dimmed) ZooColors.InkSoft.copy(alpha = 0.3f) else color
    val baseShadow = if (dimmed) ZooColors.InkSoft.copy(alpha = 0.3f) else shadowColor
    val strokeAlpha = if (enabled || keepColorWhenDisabled) 1f else 0.3f

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = if (pressed) 1.dp else 5.dp)
                .clip(RoundedCornerShape(corner))
                .background(baseShadow)
        )
        Box(
            Modifier
                .fillMaxSize()
                .offset(y = if (pressed) 4.dp else 0.dp)
                .clip(RoundedCornerShape(corner))
                .background(faceColor)
                .border(
                    strokeWidth,
                    strokeColor.copy(alpha = strokeAlpha),
                    RoundedCornerShape(corner)
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** 圆形图标按钮 */
@Composable
fun RoundIconButton(
    modifier: Modifier = Modifier,
    color: Color = ZooColors.White,
    size: Dp = 52.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.9f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 1000f),
        label = "roundScale"
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .shadow(if (pressed) 1.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, ZooColors.Ink, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 星星图标（自绘，扁平风） */
@Composable
fun StarIcon(
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    color: Color = ZooColors.Sun,
    size: Dp = 32.dp,
) {
    Canvas(modifier.size(size)) {
        val path = starPath(this.size.width, this.size.height)
        if (filled) {
            drawPath(path, color)
            drawPath(path, ZooColors.Ink, style = Stroke(width = this.size.width * 0.09f))
        } else {
            drawPath(path, ZooColors.Ink.copy(alpha = 0.15f))
            drawPath(path, ZooColors.Ink.copy(alpha = 0.4f), style = Stroke(width = this.size.width * 0.07f))
        }
    }
}

private fun starPath(w: Float, h: Float): Path {
    val cx = w / 2f
    val cy = h / 2f
    val outer = minOf(w, h) / 2f * 0.94f
    val inner = outer * 0.45f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** 四线三格：拼音教学专用背景 */
@Composable
fun FourLineGrid(
    modifier: Modifier = Modifier,
    lineColor: Color = ZooColors.Ink.copy(alpha = 0.22f),
    baselineColor: Color = ZooColors.Coral.copy(alpha = 0.45f),
) {
    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val gap = h / 3f
        // 四条横线
        for (i in 0..3) {
            val y = i * gap
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (i == 3) 3f else 2f
            )
        }
        // 基线加粗（第三线为书写基线）
        drawLine(
            color = baselineColor,
            start = Offset(0f, gap * 3),
            end = Offset(w, gap * 3),
            strokeWidth = 3.5f
        )
    }
}

/** 星星爆散粒子效果 */
@Composable
fun StarBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
    color: Color = ZooColors.Sun,
    count: Int = 14,
) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(720, easing = LinearEasing))
    }
    val seeds = remember(trigger) {
        Random(trigger).let { r ->
            List(count) {
                Triple(
                    r.nextFloat() * 360f,
                    0.6f + r.nextFloat() * 0.7f,
                    r.nextFloat() * 0.5f + 0.5f
                )
            }
        }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val maxR = minOf(size.width, size.height) * 0.46f
        seeds.forEach { (angleDeg, distScale, sizeScale) ->
            val angle = Math.toRadians(angleDeg.toDouble())
            val r = maxR * distScale * p
            val cx = size.width / 2f + (r * cos(angle)).toFloat()
            val cy = size.height / 2f + (r * sin(angle)).toFloat()
            val radius = 9f * sizeScale * (1f - p * 0.55f)
            val alpha = (1f - p).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = alpha), radius, Offset(cx, cy))
            drawCircle(ZooColors.Ink.copy(alpha = alpha * 0.8f), radius, Offset(cx, cy), style = Stroke(2f))
        }
    }
}

/** 彩带效果（通关时） */
@Composable
fun Confetti(modifier: Modifier = Modifier, trigger: Int) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1400, easing = LinearEasing))
    }
    val pieces = remember(trigger) {
        Random(trigger * 31).let { r ->
            List(26) {
                ConfettiPiece(
                    x = r.nextFloat(),
                    delay = r.nextFloat() * 0.4f,
                    speed = 0.7f + r.nextFloat() * 0.6f,
                    color = listOf(ZooColors.Coral, ZooColors.Mint, ZooColors.Sky, ZooColors.Sun, ZooColors.Lavender)
                        .random(r),
                    width = 6f + r.nextFloat() * 6f,
                    rot = r.nextFloat() * 360f,
                )
            }
        }
    }
    Canvas(modifier) {
        val p = progress.value
        pieces.forEach { piece ->
            val local = ((p - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20f + local * (size.height + 40f) * piece.speed
            val x = piece.x * size.width + sin(local * 6f) * 24f
            rotate(piece.rot + local * 540f, Offset(x, y)) {
                drawRoundRect(
                    color = piece.color.copy(alpha = (1f - local * 0.9f).coerceIn(0f, 1f)),
                    topLeft = Offset(x, y),
                    size = Size(piece.width, piece.width * 1.8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
            }
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val color: Color,
    val width: Float,
    val rot: Float,
)

/** 背景装饰：缓慢漂浮的圆点（可关闭以保证低端机流畅） */
@Composable
fun FloatingDots(
    modifier: Modifier = Modifier,
    color: Color = ZooColors.Sun,
    count: Int = 12,
    enabled: Boolean = true,
) {
    if (!enabled) return
    val transition = rememberInfiniteTransition(label = "dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotT"
    )
    val seeds = remember { Random(7).let { r -> List(count) { Triple(r.nextFloat(), r.nextFloat(), 3f + r.nextFloat() * 5f) } } }
    Canvas(modifier) {
        seeds.forEachIndexed { i, (sx, sy, rad) ->
            val phase = (t + i * 0.09f) % 1f
            val y = (sy * size.height - phase * 40f + size.height) % size.height
            val x = sx * size.width + sin(phase * 6.28f + i) * 12f
            drawCircle(
                color.copy(alpha = 0.20f + 0.10f * sin(phase * 6.28f)),
                rad,
                Offset(x, y)
            )
        }
    }
}

/** 进度条（关卡内） */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ZooColors.Mint,
    height: Dp = 16.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(ZooColors.Ink.copy(alpha = 0.12f))
            .border(2.dp, ZooColors.Ink.copy(alpha = 0.5f), RoundedCornerShape(height))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxSize()
                .clip(RoundedCornerShape(height))
                .background(color)
        )
    }
}

/** 拼音卡片（选项用）：大字 + 可选小喇叭 */
@Composable
fun PinyinCard(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ZooColors.White,
    selected: Boolean = false,
    correct: Boolean = false,
    wrong: Boolean = false,
    enabled: Boolean = true,
    fontSize: Int = 40,
    onClick: () -> Unit,
) {
    val borderColor = when {
        correct -> ZooColors.Good
        wrong -> ZooColors.Wrong
        selected -> ZooColors.Coral
        else -> ZooColors.Ink
    }
    // 反馈态用高饱和实色，让一年级孩子一眼能分清对错；
    // 之前用 alpha=0.18 的淡色，在奶油底上几乎看不出区别。
    val bg = when {
        correct -> ZooColors.Good.copy(alpha = 0.55f)
        wrong -> ZooColors.Wrong.copy(alpha = 0.55f)
        else -> color
    }
    val strokeWidth = if (correct || wrong) 5.dp else 3.dp
    val contentColor = when {
        correct -> ZooColors.Ink
        wrong -> ZooColors.Ink
        else -> ZooColors.Ink
    }

    JellyButton(
        modifier = modifier,
        color = bg,
        shadowColor = if (correct || wrong) borderColor else borderColor.copy(alpha = 0.5f),
        corner = 22.dp,
        enabled = enabled,
        // 反馈时虽然不能再点，但必须保留绿/红底色
        keepColorWhenDisabled = correct || wrong,
        onClick = onClick,
        strokeColor = if (correct || wrong) borderColor else ZooColors.Ink,
        strokeWidth = strokeWidth,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Black,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = (fontSize * 1.15f).sp,
            )
            // 右上角状态角标：✓ / ✕，颜色之外再加一层形状线索
            if (correct || wrong) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (correct) ZooColors.Good else ZooColors.Wrong),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (correct) "✓" else "✕",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = ZooColors.White,
                    )
                }
            }
        }
    }
}

/** 标题带描边效果 */
@Composable
fun OutlinedTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ZooColors.White,
    outline: Color = ZooColors.Ink,
    fontSize: Int = 32,
    strokeWidth: Float = 4f,
) {
    Box(modifier) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            ),
            color = outline,
        )
        Text(
            text = text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black,
            color = color,
        )
    }
}

/** 竖排提示气泡 */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ZooColors.White,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ZooColors.Ink.copy(alpha = 0.15f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(color)
                .border(3.dp, ZooColors.Ink, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = ZooColors.Ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}
