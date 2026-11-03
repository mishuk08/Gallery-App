package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PhotoEntity
import com.example.data.PhotoRepository
import androidx.compose.ui.graphics.Brush
import com.example.ui.components.liquidGlass
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoThumbnail(
    photo: PhotoEntity,
    repository: PhotoRepository,
    isVaultUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    aspectRatio: Float = 1f,
    onLongClick: () -> Unit = {},
    onFavoriteToggle: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var decryptedBitmap by remember(photo.id, isVaultUnlocked) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photo.id, isVaultUnlocked) {
        if (photo.isEncrypted && isVaultUnlocked) {
            decryptedBitmap = repository.loadDecryptedBitmap(photo)
        }
    }

    val shape = RoundedCornerShape(cornerRadius)

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.08f),
            Color.Transparent,
            Color.White.copy(alpha = 0.16f)
        )
    )

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(DarkSurfaceVariant)
            .border(0.8.dp, borderBrush, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("photo_card_${photo.id}")
    ) {
        when {
            photo.isEncrypted && !isVaultUnlocked -> {
                // Locked Vault State - sleek minimal dark lock
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted Vault Photo",
                        tint = AccentGold.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            photo.isEncrypted && isVaultUnlocked -> {
                if (decryptedBitmap != null) {
                    Image(
                        bitmap = decryptedBitmap!!.asImageBitmap(),
                        contentDescription = photo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Decrypting",
                            tint = AccentGold.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            else -> {
                // Standard plain photo
                val imageModel = photo.localFilePath?.let { File(it) } ?: photo.cloudUrl ?: photo.localUri
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Minimal Overlays (Top Badges) with Frosted Liquid Glass
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favorite Heart
            if (photo.isFavorite) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .liquidGlass(
                            shape = CircleShape,
                            tint = Color(0xFF181014),
                            tintAlpha = 0.70f,
                            borderAlpha = 0.50f,
                            elevation = 4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = AccentRose,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cloud synced indicator
            if (!photo.cloudUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .liquidGlass(
                            shape = CircleShape,
                            tint = Color(0xFF0F1A15),
                            tintAlpha = 0.70f,
                            borderAlpha = 0.50f,
                            elevation = 4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced to Cloud",
                        tint = AccentEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (photo.isEncrypted) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .liquidGlass(
                            shape = CircleShape,
                            tint = Color(0xFF1E180E),
                            tintAlpha = 0.70f,
                            borderAlpha = 0.50f,
                            elevation = 4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "AES-256 Encrypted",
                        tint = AccentGold,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Bottom Video Duration Badge
        if (photo.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(8.dp),
                        tint = Color.Black,
                        tintAlpha = 0.65f,
                        borderAlpha = 0.4f
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    if (photo.durationMs > 0) {
                        val totalSecs = photo.durationMs / 1000
                        val mins = totalSecs / 60
                        val secs = totalSecs % 60
                        androidx.compose.material3.Text(
                            text = "%d:%02d".format(mins, secs),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
