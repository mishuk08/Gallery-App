package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassStyle(
    val id: String,
    val name: String,
    val accentColor: Color,
    val darkTint: Color,
    val lightTint: Color,
    val secondaryGlow: Color = Color(0xFF8B5CF6),
    val darkBorderAlpha: Float = 0.40f,
    val lightBorderAlpha: Float = 0.50f
)

val LocalGlassStyle = staticCompositionLocalOf {
    getGlassStyle("amber")
}

val LocalIsDarkMode = staticCompositionLocalOf { true }

fun getGlassStyle(preset: String): GlassStyle = when (preset.lowercase()) {
    "frost" -> GlassStyle(
        id = "frost",
        name = "OLED Frost",
        accentColor = Color(0xFF64D2FF),
        darkTint = Color(0xFF131926),
        lightTint = Color(0xFFF1F5F9),
        secondaryGlow = Color(0xFF38BDF8),
        darkBorderAlpha = 0.45f,
        lightBorderAlpha = 0.55f
    )
    "emerald" -> GlassStyle(
        id = "emerald",
        name = "Cyber Emerald",
        accentColor = Color(0xFF10B981),
        darkTint = Color(0xFF0E1E17),
        lightTint = Color(0xFFECFDF5),
        secondaryGlow = Color(0xFF059669),
        darkBorderAlpha = 0.45f,
        lightBorderAlpha = 0.50f
    )
    "violet" -> GlassStyle(
        id = "violet",
        name = "Amethyst Glass",
        accentColor = Color(0xFF8B5CF6),
        darkTint = Color(0xFF191328),
        lightTint = Color(0xFFF5F3FF),
        secondaryGlow = Color(0xFFC084FC),
        darkBorderAlpha = 0.45f,
        lightBorderAlpha = 0.50f
    )
    "sapphire" -> GlassStyle(
        id = "sapphire",
        name = "Deep Sapphire",
        accentColor = Color(0xFF38BDF8),
        darkTint = Color(0xFF0F1A2C),
        lightTint = Color(0xFFEFF6FF),
        secondaryGlow = Color(0xFF6366F1),
        darkBorderAlpha = 0.45f,
        lightBorderAlpha = 0.50f
    )
    else -> GlassStyle(
        id = "amber",
        name = "Amber Dunes",
        accentColor = Color(0xFFF59E0B),
        darkTint = Color(0xFF231912),
        lightTint = Color(0xFFFFFBEB),
        secondaryGlow = Color(0xFFEA580C),
        darkBorderAlpha = 0.45f,
        lightBorderAlpha = 0.50f
    )
}

/**
 * Ambient backdrop with glowing diffused light orbs designed specifically for Glassmorphism.
 * Glass cards sitting atop this canvas catch refractions, highlights, and subtle translucency.
 */
@Composable
fun GlassmorphismBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val style = LocalGlassStyle.current

    val primaryGlow = style.accentColor
    val secondaryGlow = style.secondaryGlow

    Box(modifier = modifier.fillMaxSize()) {
        // Dynamic Glowing Light Orbs Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Base gradient
            if (isDark) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF08090E),
                            Color(0xFF0C0E14),
                            Color(0xFF07080D)
                        )
                    )
                )
            } else {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF4F6FB),
                            Color(0xFFE9EEF6),
                            Color(0xFFF2F5FA)
                        )
                    )
                )
            }

            // Top-Right Ambient Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = if (isDark) 0.18f else 0.16f),
                        primaryGlow.copy(alpha = if (isDark) 0.07f else 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.15f),
                    radius = w * 0.65f
                ),
                center = Offset(w * 0.85f, h * 0.15f),
                radius = w * 0.65f
            )

            // Center-Left Secondary Glow Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryGlow.copy(alpha = if (isDark) 0.14f else 0.12f),
                        secondaryGlow.copy(alpha = if (isDark) 0.04f else 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.12f, h * 0.52f),
                    radius = w * 0.70f
                ),
                center = Offset(w * 0.12f, h * 0.52f),
                radius = w * 0.70f
            )

            // Bottom-Right Subtle Glow Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = if (isDark) 0.12f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.75f, h * 0.88f),
                    radius = w * 0.55f
                ),
                center = Offset(w * 0.75f, h * 0.88f),
                radius = w * 0.55f
            )
        }

        // Foreground content
        content()
    }
}

/**
 * Clean modern liquid glass surface modifier with glossy specular border effect.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = Color(0xFF1E222D),
    tintAlpha: Float = 0.82f,
    borderAlpha: Float = 0.40f,
    borderWidth: Dp = 1.2.dp,
    elevation: Dp = 8.dp
): Modifier {
    val glossyBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = (borderAlpha * 1.8f).coerceAtMost(0.92f)), // Top-left glossy specular shine
            Color.White.copy(alpha = (borderAlpha * 0.55f)),
            Color.White.copy(alpha = (borderAlpha * 0.12f)),
            Color.White.copy(alpha = (borderAlpha * 1.05f))  // Bottom-right rim reflection
        )
    )

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.40f),
            spotColor = Color.White.copy(alpha = 0.12f)
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    tint.copy(alpha = (tintAlpha * 1.05f).coerceAtMost(0.96f)),
                    tint.copy(alpha = tintAlpha),
                    tint.copy(alpha = (tintAlpha * 0.88f).coerceAtLeast(0f))
                )
            )
        )
        .border(
            width = borderWidth,
            brush = glossyBorderBrush,
            shape = shape
        )
}

/**
 * Clean theme-reactive liquid glass surface modifier with glossy specular border.
 */
@Composable
fun Modifier.liquidGlassThemed(
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 8.dp,
    borderWidth: Dp = 1.2.dp
): Modifier {
    val isDark = LocalIsDarkMode.current
    val style = LocalGlassStyle.current

    val bgColors = if (isDark) {
        listOf(
            Color(0xEE202534),
            Color(0xDD171A26),
            Color(0xCC10121B)
        )
    } else {
        listOf(
            Color(0xF5FFFFFF),
            Color(0xEEF8FAFD),
            Color(0xDEF0F4FA)
        )
    }

    val glossyBorderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.75f), // Crisp top-left specular highlight
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.48f)  // Glossy rim reflection
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f), // Crisp top specular shine
                Color(0x40000000),
                Color(0x10000000),
                Color.White.copy(alpha = 0.70f)
            )
        )
    }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.12f),
            spotColor = if (isDark) style.accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.50f)
        )
        .clip(shape)
        .background(Brush.verticalGradient(bgColors))
        .border(
            width = borderWidth,
            brush = glossyBorderBrush,
            shape = shape
        )
}

/**
 * Clean accent-tinted card surface modifier with glossy specular border.
 */
fun Modifier.liquidGlassGlow(
    accentColor: Color,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.2.dp
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.25f),
                accentColor.copy(alpha = 0.10f)
            )
        )
    )
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.88f),
                accentColor.copy(alpha = 0.70f),
                accentColor.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.50f)
            )
        ),
        shape = shape
    )

/**
 * Refined frosted glass pill modifier for chips, tags, and small badges.
 */
@Composable
fun Modifier.glassPill(
    selected: Boolean = false,
    activeColor: Color = LocalGlassStyle.current.accentColor,
    shape: Shape = RoundedCornerShape(22.dp)
): Modifier {
    val isDark = LocalIsDarkMode.current

    return this
        .clip(shape)
        .then(
            if (selected) {
                Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = if (isDark) 0.32f else 0.22f),
                                activeColor.copy(alpha = if (isDark) 0.16f else 0.10f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f),
                                activeColor.copy(alpha = 0.70f),
                                activeColor.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.40f)
                            )
                        ),
                        shape = shape
                    )
            } else {
                Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color.White.copy(alpha = 0.10f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.75f),
                                    Color.White.copy(alpha = 0.50f)
                                )
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.20f)
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.90f),
                                    Color.White.copy(alpha = 0.40f),
                                    Color(0x15000000),
                                    Color.White.copy(alpha = 0.60f)
                                )
                            }
                        ),
                        shape = shape
                    )
            }
        )
}
