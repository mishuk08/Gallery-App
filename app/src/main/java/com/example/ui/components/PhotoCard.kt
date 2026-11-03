package com.example.ui.components

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PhotoEntity
import com.example.data.PhotoRepository
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCard(
    photo: PhotoEntity,
    repository: PhotoRepository,
    isVaultUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    var decryptedBitmap by remember(photo.id, isVaultUnlocked) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photo.id, isVaultUnlocked) {
        if (photo.isEncrypted && isVaultUnlocked) {
            decryptedBitmap = repository.loadDecryptedBitmap(photo)
        }
    }

    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(DarkSurfaceVariant)
            .border(0.5.dp, BorderSubtle, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("photo_card_${photo.id}")
    ) {
        when {
            photo.isEncrypted && !isVaultUnlocked -> {
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

        // Minimal Top Indicators
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (photo.isFavorite) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = AccentRose,
                            modifier = Modifier.size(13.dp)
                        )
                    }
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
            if (photo.syncStatus == PhotoEntity.SYNC_STATUS_SYNCED || !photo.cloudUrl.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Synced to Cloud",
                            tint = AccentEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else if (photo.syncStatus == PhotoEntity.SYNC_STATUS_PENDING) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Sync Pending",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Local Offline",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            if (photo.isEncrypted) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "AES-256 Encrypted",
                            tint = AccentGold,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }

        // Bottom Video Indicator Badge
        if (photo.isVideo) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
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
