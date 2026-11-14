package com.alex.pinyinzoo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 配色：明亮糖果色 + 深藏青描边，扁平矢量风的标配。
 * 刻意不用红色表示错误，改用橙黄，避免挫败感。
 */
object ZooColors {
    val Cream = Color(0xFFFFF8E7)
    val CreamDark = Color(0xFFF6ECD6)
    val Ink = Color(0xFF2B3A55)
    val InkSoft = Color(0xFF5A6B88)

    val Coral = Color(0xFFFF8A65)
    val CoralDark = Color(0xFFE86E48)
    val Mint = Color(0xFF4DD0A7)
    val MintDark = Color(0xFF2FB488)
    val Sky = Color(0xFF5BB8F5)
    val SkyDark = Color(0xFF3E96D4)
    val Sun = Color(0xFFFFD54F)
    val SunDark = Color(0xFFE8B830)
    val Lavender = Color(0xFFB39DF5)
    val LavenderDark = Color(0xFF8F76DC)

    val Good = Color(0xFF66BB6A)
    val GoodDark = Color(0xFF43A047)
    val Retry = Color(0xFFFFB74D)

    /** 明确的错误红：答题反馈用。
     *  比纯红(0xFFFF0000)柔和一档，既一眼分辨清楚，又不至于太刺眼。 */
    val Wrong = Color(0xFFEF5350)
    val WrongDark = Color(0xFFC62828)

    val White = Color(0xFFFFFFFF)
    val Shadow = Color(0x1A2B3A55)

    /** 域主色 */
    fun ofDomain(key: String): Color = when (key) {
        "sibilant" -> Coral
        "nasal" -> Sky
        "tone" -> Mint
        "umlaut" -> Lavender
        "final" -> Sun
        "initial" -> Color(0xFFF49AC1)
        "whole" -> Color(0xFF7ECFC0)
        else -> Coral
    }

    fun ofDomainDark(key: String): Color = when (key) {
        "sibilant" -> CoralDark
        "nasal" -> SkyDark
        "tone" -> MintDark
        "umlaut" -> LavenderDark
        "final" -> SunDark
        "initial" -> Color(0xFFD8749F)
        "whole" -> Color(0xFF56AE9F)
        else -> CoralDark
    }
}

private val ZooColorScheme = lightColorScheme(
    primary = ZooColors.Coral,
    onPrimary = ZooColors.White,
    primaryContainer = ZooColors.CreamDark,
    onPrimaryContainer = ZooColors.Ink,
    secondary = ZooColors.Mint,
    onSecondary = ZooColors.White,
    tertiary = ZooColors.Sky,
    background = ZooColors.Cream,
    onBackground = ZooColors.Ink,
    surface = ZooColors.White,
    onSurface = ZooColors.Ink,
    surfaceVariant = ZooColors.CreamDark,
    onSurfaceVariant = ZooColors.InkSoft,
    error = ZooColors.Retry,
)

/** 圆润的形状规范：扁平矢量风的关键 */
private val ZooShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/**
 * 字号规范：不跟随系统字体缩放（fontScale 固定），
 * 防止 MIUI 大字体把横屏布局撑爆。
 */
private val ZooTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, lineHeight = 64.sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 52.sp),
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

@Composable
fun PinyinZooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZooColorScheme,
        shapes = ZooShapes,
        typography = ZooTypography,
        content = content,
    )
}
