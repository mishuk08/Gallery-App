package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GalleryTab
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FloatingNavigation(
    currentTab: GalleryTab,
    isVaultUnlocked: Boolean,
    onTabSelected: (GalleryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkMode.current
    val style = LocalGlassStyle.current

    val containerBgColors = if (isDark) {
        listOf(
            Color(0xEE1E2330),
            Color(0xDD141722),
            Color(0xCC0E1018)
        )
    } else {
        listOf(
            Color(0xF2FFFFFF),
            Color(0xEEF8FAFD),
            Color(0xE0EFF3F9)
        )
    }

    val glossyBorderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f), // Crisp top-left specular highlight
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.50f)  // Bottom-right rim reflection
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                Color(0x35000000),
                Color(0x0C000000),
                Color.White.copy(alpha = 0.75f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 14.dp)
            .testTag("main_navigation_bar"),
        contentAlignment = Alignment.Center
    ) {
        // Outer Translucent Liquid Glass Container
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.15f),
                    spotColor = if (isDark) style.accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.60f)
                )
                .clip(RoundedCornerShape(36.dp))
                .background(Brush.verticalGradient(containerBgColors))
                .border(
                    width = 1.3.dp,
                    brush = glossyBorderBrush,
                    shape = RoundedCornerShape(36.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassNavItem(
                    label = "Gallery",
                    icon = Icons.Default.PhotoLibrary,
                    selected = currentTab == GalleryTab.GALLERY,
                    activeColor = if (isDark) Color.White else Color(0xFF0F172A),
                    testTag = "nav_gallery_tab",
                    onClick = { onTabSelected(GalleryTab.GALLERY) }
                )

                LiquidGlassNavItem(
                    label = "Settings",
                    icon = Icons.Default.Settings,
                    selected = currentTab == GalleryTab.SETTINGS,
                    activeColor = style.accentColor,
                    testTag = "nav_settings_tab",
                    onClick = { onTabSelected(GalleryTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val animatedColor by animateColorAsState(
        targetValue = if (selected) activeColor else TextSecondary,
        animationSpec = spring(),
        label = "navColor"
    )

    val itemShape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(itemShape)
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    activeColor.copy(alpha = if (isDark) 0.28f else 0.18f),
                                    activeColor.copy(alpha = if (isDark) 0.12f else 0.08f),
                                    Color.White.copy(alpha = if (isDark) 0.15f else 0.35f)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.85f),
                                    activeColor.copy(alpha = 0.70f),
                                    activeColor.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.40f)
                                )
                            ),
                            shape = itemShape
                        )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animatedColor,
                modifier = Modifier.size(20.dp)
            )

            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = animatedColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
