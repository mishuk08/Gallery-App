package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1C1C1E),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1C1C1E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = AccentEmerald,
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF080808),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0x30FFFFFF),
    outlineVariant = Color(0x45FFFFFF),
    error = AccentRose,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF111827),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3F4F6),
    onPrimaryContainer = Color(0xFF111827),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E7EB),
    onSecondaryContainer = Color(0xFF111827),
    tertiary = AccentEmerald,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = AccentRose,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

