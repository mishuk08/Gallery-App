package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PhotoEntity
import com.example.data.PhotoRepository
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.DateUtils

@Composable
fun PhotoGrid(
    photos: List<PhotoEntity>,
    repository: PhotoRepository,
    isVaultUnlocked: Boolean,
    onPhotoClick: (PhotoEntity) -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    onFavoriteToggle: ((PhotoEntity) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
    emptyTitle: String = "No Photos Yet",
    emptySubtitle: String = "Photos you upload will be stored securely and viewable anywhere."
) {
    if (photos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = DarkSurfaceVariant,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = emptySubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onUploadClick,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.testTag("empty_upload_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Upload Photos",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
        return
    }

    // Group photos by date
    val groupedPhotos = remember(photos) {
        photos.groupBy { DateUtils.getDateGroupTitle(it.createdAt) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        groupedPhotos.forEach { (groupTitle, groupPhotos) ->
            item(key = "header_$groupTitle") {
                DateSectionHeader(
                    title = groupTitle,
                    photoCount = groupPhotos.size
                )
            }

            // Render rows of columns photos each for a clean balanced grid (supports 1 to 5 columns on pinch zoom)
            val colCount = columns.coerceIn(1, 5)
            val rows = groupPhotos.chunked(colCount)
            items(rows.size, key = { index -> "${groupTitle}_row_$index" }) { rowIndex ->
                val rowPhotos = rows[rowIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until colCount) {
                        if (i < rowPhotos.size) {
                            val photo = rowPhotos[i]
                            Box(modifier = Modifier.weight(1f)) {
                                PhotoThumbnail(
                                    photo = photo,
                                    repository = repository,
                                    isVaultUnlocked = isVaultUnlocked,
                                    onClick = { onPhotoClick(photo) },
                                    onFavoriteToggle = onFavoriteToggle?.let { { it(photo) } }
                                )
                            }
                        } else {
                            // Empty spacer to preserve column width
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "spacer_$groupTitle") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
