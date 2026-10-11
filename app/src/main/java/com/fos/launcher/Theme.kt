package com.fos.launcher

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

fun accentOf(s: Settings): Color = Color.hsv(s.accentHue.coerceIn(0f, 360f), s.accentSat.coerceIn(0f, 1f), 0.95f)

@Composable
fun isDarkTheme(s: Settings): Boolean = when (s.themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

fun fontOf(i: Int): FontFamily = when (i) {
    1 -> FontFamily.SansSerif
    2 -> FontFamily.Serif
    3 -> FontFamily.Monospace
    4 -> FontFamily.Cursive
    else -> FontFamily.Default
}

private fun typo(ff: FontFamily): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = ff),
        displayMedium = b.displayMedium.copy(fontFamily = ff),
        displaySmall = b.displaySmall.copy(fontFamily = ff),
        headlineLarge = b.headlineLarge.copy(fontFamily = ff),
        headlineMedium = b.headlineMedium.copy(fontFamily = ff),
        headlineSmall = b.headlineSmall.copy(fontFamily = ff),
        titleLarge = b.titleLarge.copy(fontFamily = ff),
        titleMedium = b.titleMedium.copy(fontFamily = ff),
        titleSmall = b.titleSmall.copy(fontFamily = ff),
        bodyLarge = b.bodyLarge.copy(fontFamily = ff),
        bodyMedium = b.bodyMedium.copy(fontFamily = ff),
        bodySmall = b.bodySmall.copy(fontFamily = ff),
        labelLarge = b.labelLarge.copy(fontFamily = ff),
        labelMedium = b.labelMedium.copy(fontFamily = ff),
        labelSmall = b.labelSmall.copy(fontFamily = ff)
    )
}

@Composable
fun FosTheme(s: Settings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(s)
    val ctx = LocalContext.current
    val accent = accentOf(s)
    val onAccent = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color.Black else Color.White

    val scheme = if (s.useDynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(accent, Color.Black, 0.6f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            background = Color(0xFF101114),
            onBackground = Color(0xFFE8E9ED),
            surface = Color(0xFF17181C),
            onSurface = Color(0xFFE8E9ED),
            surfaceVariant = Color(0xFF24262C),
            onSurfaceVariant = Color(0xFFA9ACB6),
            outline = Color(0xFF4A4D57)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(accent, Color.White, 0.75f),
            onPrimaryContainer = Color(0xFF16171B),
            secondary = accent,
            background = Color(0xFFF6F6FA),
            onBackground = Color(0xFF16171B),
            surface = Color.White,
            onSurface = Color(0xFF16171B),
            surfaceVariant = Color(0xFFE9EAF0),
            onSurfaceVariant = Color(0xFF5A5D68),
            outline = Color(0xFFB9BCC6)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typo(fontOf(s.fontFamily)), content = content)
}
