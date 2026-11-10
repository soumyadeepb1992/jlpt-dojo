package com.jlptdojo.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Mincho-style serif for kanji, falls back to the phone's Noto CJK fonts. */
val KanjiFont: FontFamily = FontFamily.Serif

object Dojo {
    val Correct = Color(0xFF2E9E5B)
    val Wrong = Color(0xFFD64545)
    val Gold = Color(0xFFE8A93B)
    val Flame = Color(0xFFF26B3A)
    val Heart = Color(0xFFE5484D)

    fun levelColor(level: Int): Color = when (level) {
        5 -> Color(0xFF3E9E62)
        4 -> Color(0xFF2A8C8C)
        3 -> Color(0xFF3A6FC4)
        2 -> Color(0xFF7C4DBA)
        else -> Color(0xFFC0392B)
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B3A67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3F6),
    onPrimaryContainer = Color(0xFF14204A),
    secondary = Color(0xFFC8442A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBDCD3),
    onSecondaryContainer = Color(0xFF4A120A),
    tertiary = Color(0xFF5E7F45),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEBCF),
    onTertiaryContainer = Color(0xFF1B2E0E),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1D1B18),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1D1B18),
    surfaceVariant = Color(0xFFECE5D8),
    onSurfaceVariant = Color(0xFF5A544B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7EF),
    surfaceContainer = Color(0xFFF2ECE0),
    surfaceContainerHigh = Color(0xFFECE5D8),
    surfaceContainerHighest = Color(0xFFE6DECF),
    outline = Color(0xFFB9B0A2),
    outlineVariant = Color(0xFFDDD5C7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9B9F2),
    onPrimary = Color(0xFF14204A),
    primaryContainer = Color(0xFF2E3B66),
    onPrimaryContainer = Color(0xFFDDE3F6),
    secondary = Color(0xFFFF8F70),
    onSecondary = Color(0xFF4A120A),
    secondaryContainer = Color(0xFF6B2416),
    onSecondaryContainer = Color(0xFFFBDCD3),
    tertiary = Color(0xFFAACB8E),
    onTertiary = Color(0xFF1B2E0E),
    tertiaryContainer = Color(0xFF34502A),
    onTertiaryContainer = Color(0xFFDDEBCF),
    background = Color(0xFF13151D),
    onBackground = Color(0xFFE8E4DC),
    surface = Color(0xFF1B1E28),
    onSurface = Color(0xFFE8E4DC),
    surfaceVariant = Color(0xFF2A2E3A),
    onSurfaceVariant = Color(0xFFB8B3A8),
    surfaceContainerLowest = Color(0xFF0F1118),
    surfaceContainerLow = Color(0xFF181B24),
    surfaceContainer = Color(0xFF1F2330),
    surfaceContainerHigh = Color(0xFF262A37),
    surfaceContainerHighest = Color(0xFF2E3240),
    outline = Color(0xFF6E6A62),
    outlineVariant = Color(0xFF3A3E4A),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun DojoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
