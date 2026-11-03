package com.example.ui.theme
 
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.example.ui.components.LocalIsDarkMode

// OLED Black & Near Black Foundations (Theme-Aware)
val OledBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFF000000) else Color(0xFFF8FAFC)

val NearBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFF080808) else Color(0xFFFFFFFF)

val DarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFF121212) else Color(0xFFFFFFFF)

val DarkSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFF1C1C1E) else Color(0xFFF1F5F9)

val DarkSurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFF242426) else Color(0xFFE2E8F0)

val BorderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0x30FFFFFF) else Color(0x25000000)

val BorderLight: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0x45FFFFFF) else Color(0x40000000)

// Glassmorphic translucent tokens (Theme-Aware)
val GlassPillBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xDD121212) else Color(0xF0FFFFFF)

val GlassCardBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0x9918181A) else Color(0xE8F8FAFC)

val GlassBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0x26FFFFFF) else Color(0x26000000)

// High-Contrast Dynamic Typography - Crisp & Visible in Every Mode!
val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFFFFFFFF) else Color(0xFF0F172A)

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFF94A3B8) else Color(0xFF475569)

val TextTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkMode.current) Color(0xFF64748B) else Color(0xFF64748B)

// Subtle Accents
val AccentGold = Color(0xFFF59E0B)
val AccentEmerald = Color(0xFF10B981)
val AccentRose = Color(0xFFFF3B30)
val AccentBlue = Color(0xFF0A84FF)

// Backward Compatibility Aliases
val Slate950: Color @Composable @ReadOnlyComposable get() = OledBlack
val Slate900: Color @Composable @ReadOnlyComposable get() = DarkSurface
val Slate800: Color @Composable @ReadOnlyComposable get() = DarkSurfaceVariant
val Slate700: Color @Composable @ReadOnlyComposable get() = BorderSubtle
val Slate600: Color @Composable @ReadOnlyComposable get() = TextTertiary
val Slate400: Color @Composable @ReadOnlyComposable get() = TextSecondary
val Slate200 = Color(0xFFE5E5EA)
val Slate100 = Color(0xFFF2F2F7)
val Slate50: Color @Composable @ReadOnlyComposable get() = TextPrimary

val CyanPrimary = AccentBlue
val CyanPrimaryLight = Color(0xFF64D2FF)
val IndigoAccent = Color(0xFF5E5CE6)
val IndigoLight = Color(0xFF7D7AFF)
val EmeraldSecure = AccentEmerald
val GoldVault = AccentGold
val RoseDestructive = AccentRose


