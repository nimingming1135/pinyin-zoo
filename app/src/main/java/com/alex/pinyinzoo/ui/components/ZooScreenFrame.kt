package com.alex.pinyinzoo.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 统一的屏幕安全框与视觉边距。
 *
 * 解决的问题（真机实测 K50 Ultra 1220x2712 / density 480，横屏可用高度仅 406dp）：
 * 1. 应用使用 edge-to-edge + 隐藏系统栏，内容会铺到物理屏幕边缘。
 *    该机型**左上角有挖孔摄像头**（insets 117px = 39dp），原实现左边距只有 12dp，标题真的压在挖孔上；
 * 2. 各页面内边距写死 10~12dp，在 904dp 宽的横屏里比例失衡，显得贴边拥挤；
 * 3. 挖孔只在左侧，若直接把 safeDrawing 套上去，左侧会比右侧多出 39dp，
 *    肉眼一看就是"左留白大、右留白小"。所以这里把左右两侧**取同一值**。
 *
 * 解决方式：
 * - 硬约束（挖孔、屏幕圆角、系统栏）取左右两侧的最大值，左右对称应用；
 * - 视觉留白按屏幕宽度等比计算，手机约 16dp、平板封顶 24dp，两台设备观感一致。
 */

/**
 * 应用安全 inset：避开挖孔、屏幕圆角、系统栏。
 *
 * 关键点：safeDrawing 的左右值可能不相等（横屏时挖孔只在一边），
 * 若各用各的值，内容区居中但视觉上一边宽一边窄。
 * 这里统一取 `max(left, right)` 应用到两侧，保证内容区水平居中、左右留白严格相等。
 */
@Composable
fun Modifier.zooSafeInset(): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing

    val leftPx = insets.getLeft(density, layoutDirection)
    val rightPx = insets.getRight(density, layoutDirection)
    val topPx = insets.getTop(density)
    val bottomPx = insets.getBottom(density)

    // 左右取同一值 → 内容区水平居中，两侧留白绝对相等
    val horizontal = with(density) { maxOf(leftPx, rightPx).toDp() }
    val top = with(density) { topPx.toDp() }
    val bottom = with(density) { bottomPx.toDp() }

    return this.padding(start = horizontal, end = horizontal, top = top, bottom = bottom)
}

/**
 * 计算页面内边距。
 *
 * @param screenWidthDp 当前可用宽度（dp），取自 BoxWithConstraints 的 maxWidth
 * @param compact 是否紧凑高度（手机横屏 406dp 为 true，平板 900dp 为 false）
 */
@Composable
fun zooPadding(screenWidthDp: Float, compact: Boolean): PaddingValues {
    val h = (screenWidthDp * H_RATIO).dp.coerceIn(MIN_H_PAD.dp, MAX_H_PAD.dp)
    val v = if (compact) 14.dp else 24.dp
    return PaddingValues(horizontal = h, vertical = v)
}

/** 只取水平方向的视觉边距，供需要自定义垂直间距的页面使用 */
fun zooHPad(screenWidthDp: Float) =
    (screenWidthDp * H_RATIO).dp.coerceIn(MIN_H_PAD.dp, MAX_H_PAD.dp)

/** 水平内边距占屏宽的比例：904dp → 约 25dp */
private const val H_RATIO = 0.028f
private const val MIN_H_PAD = 20f
private const val MAX_H_PAD = 32f
