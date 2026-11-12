package com.badens.guitartuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object TunerColors {
    val Background = Color(0xFF141516)
    val Grid = Color(0xFF1D1F21)
    val ButtonIdle = Color(0xFF2C2D2F)
    val ButtonActive = Color(0xFF46484B)
    val BubbleFill = Color(0xFF1E1F21)
    val Neutral = Color(0xFF5A5D60)
    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFFB4B6B8)
    val TextMuted = Color(0xFF6E7174)
    val InTune = Color(0xFF34C77B)
    val Close = Color(0xFFF5A524)
    val Off = Color(0xFFEF5350)
    val Lock = Color(0xFF5B9BFF)
}

private val colorScheme = darkColorScheme(
    primary = TunerColors.InTune,
    onPrimary = Color.Black,
    background = TunerColors.Background,
    onBackground = TunerColors.TextPrimary,
    surface = TunerColors.Background,
    onSurface = TunerColors.TextPrimary,
)

@Composable
fun GuitarTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
