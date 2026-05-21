package com.example.sleepguardian

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SleepBg = Color(0xFF0C0714)
private val SleepSurface = Color(0xFF171022)
private val SleepSurface2 = Color(0xFF221530)
private val SleepPrimary = Color(0xFFB388FF)
private val SleepPrimary2 = Color(0xFF7C4DFF)
private val SleepAccent = Color(0xFFE1BEE7)
private val SleepText = Color(0xFFF5F0FF)
private val SleepTextMuted = Color(0xFFB9AFCC)
private val SleepSuccess = Color(0xFF53D18B)
private val SleepWarning = Color(0xFFFFC857)
private val SleepError = Color(0xFFFF6B8A)

val SleepGuardianColorScheme: ColorScheme = darkColorScheme(
    primary = SleepPrimary,
    onPrimary = Color(0xFF1A1027),
    primaryContainer = Color(0xFF2D1A44),
    onPrimaryContainer = SleepText,
    secondary = SleepAccent,
    onSecondary = Color(0xFF1A1027),
    secondaryContainer = Color(0xFF2D1A44),
    onSecondaryContainer = SleepText,
    tertiary = SleepPrimary2,
    background = SleepBg,
    onBackground = SleepText,
    surface = SleepSurface,
    onSurface = SleepText,
    surfaceVariant = SleepSurface2,
    onSurfaceVariant = SleepTextMuted,
    error = SleepError,
    onError = Color.White,
    outline = Color(0xFF5C5070),
    outlineVariant = Color(0xFF3B304B)
)

@Composable
fun SleepGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SleepGuardianColorScheme,
        typography = Typography(),
        content = content
    )
}

val SleepThemeBackground = SleepBg
val SleepThemeSurface = SleepSurface
val SleepThemeSurfaceAlt = SleepSurface2
val SleepThemePrimary = SleepPrimary
val SleepThemePrimaryAlt = SleepPrimary2
val SleepThemeAccent = SleepAccent
val SleepThemeText = SleepText
val SleepThemeTextMuted = SleepTextMuted
val SleepThemeSuccess = SleepSuccess
val SleepThemeWarning = SleepWarning
val SleepThemeError = SleepError
