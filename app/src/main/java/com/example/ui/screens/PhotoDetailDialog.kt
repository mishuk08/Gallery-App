package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ImageExportHelper
import com.example.data.PhotoEntity
import com.example.ui.GalleryViewModel
import com.example.ui.components.LocalIsDarkMode
import com.example.ui.components.liquidGlass
import com.example.ui.util.BiometricHelper
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassPillBackground
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.util.DateUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailDialog(
    photo: PhotoEntity,
    decryptedBitmap: Bitmap?,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Photo Editing State
    var editTitle by remember { mutableStateOf(photo.title) }
    var editRotation by remember { mutableFloatStateOf(0f) }
    var editFilter by remember { mutableStateOf("original") }

    fun loadSourceBitmap(): Bitmap? {
        return if (photo.isEncrypted) {
            decryptedBitmap
        } else {
            try {
                val path = photo.localFilePath
                if (path != null && java.io.File(path).exists()) {
                    BitmapFactory.decodeFile(path)
                } else {
                    val uri = Uri.parse(photo.localUri)
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getProcessedBitmap(): Bitmap? {
        val base = loadSourceBitmap() ?: return null
        var result = base
        if (editRotation != 0f) {
            result = ImageExportHelper.rotateBitmap(result, editRotation)
        }
        if (editFilter != "original") {
            result = ImageExportHelper.applyFilter(result, editFilter)
        }
        return result
    }

    // Zoom & pan gesture state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    val isDark = LocalIsDarkMode.current
    val activity = context as? FragmentActivity
    val isBiometricReady = remember(context) { BiometricHelper.isBiometricAvailable(context) }
    val vaultAccount by viewModel.vaultAccount.collectAsStateWithLifecycle()
    val canUseBiometric = isBiometricReady && (vaultAccount?.isBiometricEnabled != false)

    // PIN Confirmation Dialog for Vault protection
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                pinError = null
            },
            title = {
                Text("Enter Master PIN", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Please authenticate with your 6-digit PIN to manage Vault protection for this photo.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it, color = AccentRose) } },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (canUseBiometric && activity != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                BiometricHelper.promptBiometric(
                                    activity = activity,
                                    title = "Confirm Photo Protection",
                                    subtitle = "Verify fingerprint or face to proceed",
                                    onSuccess = {
                                        val unlocked = viewModel.unlockVaultWithBiometrics()
                                        if (unlocked) {
                                            showPinDialog = false
                                            viewModel.toggleVaultProtection(photo)
                                            onDismiss()
                                        }
                                    },
                                    onError = { err -> pinError = err }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF64D2FF).copy(alpha = 0.2f),
                                contentColor = Color(0xFF64D2FF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify with Biometrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 6) {
                            pinError = "PIN must be exactly 6 digits"
                            return@Button
                        }
                        showPinDialog = false
                        viewModel.toggleVaultProtection(photo, pinInput)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OledBlack)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = if (isDark) Color(0xFF181B26) else Color.White
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Photo?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text("This action cannot be undone. Are you sure you want to delete this photo?", color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deletePhoto(photo)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = if (isDark) Color(0xFF181B26) else Color.White
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OledBlack)
                .testTag("photo_detail_dialog")
        ) {
            // Central Edge-to-Edge Image Viewport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                if (photo.isVideo) {
                    VideoPlaybackView(
                        photo = photo,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (photo.isEncrypted) {
                    if (decryptedBitmap != null) {
                        Image(
                            bitmap = decryptedBitmap.asImageBitmap(),
                            contentDescription = photo.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Vault Encrypted",
                                tint = AccentGold,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "AES-256 Encrypted in Vault",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    val imageModel = photo.localFilePath?.let { File(it) } ?: photo.cloudUrl ?: photo.localUri
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Top Action Controls (Animated Fade)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .liquidGlass(
                            shape = RoundedCornerShape(26.dp),
                            tint = Color(0xFF141724),
                            tintAlpha = 0.82f,
                            borderAlpha = 0.45f,
                            elevation = 10.dp
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = photo.title.ifBlank { "Photo" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = DateUtils.formatFullDate(photo.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!photo.isEncrypted) {
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(photo) },
                                    modifier = Modifier.testTag("detail_favorite_button")
                                ) {
                                    Icon(
                                        imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (photo.isFavorite) AccentRose else TextPrimary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.testTag("detail_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = AccentRose
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Floating Action Controls (Animated Fade)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .liquidGlass(
                            shape = RoundedCornerShape(32.dp),
                            tint = Color(0xFF141724),
                            tintAlpha = 0.85f,
                            borderAlpha = 0.45f,
                            elevation = 12.dp
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Edit Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    editTitle = photo.title
                                    editRotation = 0f
                                    editFilter = "original"
                                    showEditSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("detail_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color(0xFF64D2FF),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Edit",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 2. Download Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val sourceBitmap = loadSourceBitmap()
                                    val savedUri = if (sourceBitmap != null) {
                                        ImageExportHelper.saveBitmapToGallery(context, sourceBitmap, photo.title)
                                    } else {
                                        ImageExportHelper.saveUriToGallery(context, Uri.parse(photo.localUri), photo.title)
                                    }
                                    if (savedUri != null) {
                                        Toast.makeText(context, "Saved to Pictures/Gallery!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Could not save photo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("detail_download_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = AccentEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Download",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 3. Share Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        if (!photo.cloudUrl.isNullOrBlank()) {
                                            putExtra(Intent.EXTRA_TEXT, "View photo: ${photo.cloudUrl}")
                                            type = "text/plain"
                                        } else {
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(photo.localUri))
                                            type = photo.mimeType
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Photo"))
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("detail_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Share",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 4. Details / Info Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showInfoSheet = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("detail_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Info",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 5. Vault Protection Toggle
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isVaultUnlocked) {
                                        viewModel.toggleVaultProtection(photo)
                                        onDismiss()
                                    } else {
                                        showPinDialog = true
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("toggle_vault_button")
                        ) {
                            Icon(
                                imageVector = if (photo.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Vault",
                                tint = if (photo.isEncrypted) AccentGold else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (photo.isEncrypted) "To Gallery" else "To Vault",
                                fontSize = 11.sp,
                                color = if (photo.isEncrypted) AccentGold else TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Photo Editor Bottom Sheet
            if (showEditSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showEditSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = DarkSurfaceVariant,
                    contentColor = TextPrimary
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 14.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Edit Photo",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            // Rotate 90 deg button
                            FilledTonalButton(
                                onClick = {
                                    editRotation = (editRotation + 90f) % 360f
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rotate (${editRotation.toInt()}°)")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Preview thumbnail if available
                        val previewBitmap = getProcessedBitmap()
                        if (previewBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Edited Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Filter Chips
                        Text(
                            text = "Color Filters",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filters = listOf(
                                "original" to "Normal",
                                "bw" to "B&W",
                                "vintage" to "Vintage",
                                "cyber" to "Vibrant",
                                "cool" to "Cool"
                            )
                            for ((id, label) in filters) {
                                val isSelected = editFilter == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) Color(0xFF64D2FF).copy(alpha = 0.25f) else Color(0x18FFFFFF))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF64D2FF) else Color(0x22FFFFFF),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { editFilter = id }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF64D2FF) else TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Rename title
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Photo Title") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Color(0xFF64D2FF),
                                unfocusedBorderColor = Color(0x30FFFFFF)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // External Editor launcher
                        OutlinedButton(
                            onClick = {
                                ImageExportHelper.launchSystemEditor(context, Uri.parse(photo.localUri), photo.mimeType)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in System Editor", color = TextSecondary, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Save Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showEditSheet = false }) {
                                Text("Cancel", color = TextSecondary)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = {
                                    val finalBitmap = getProcessedBitmap()
                                    if (finalBitmap != null) {
                                        viewModel.saveEditedPhoto(photo, finalBitmap, editTitle)
                                        Toast.makeText(context, "Photo edited & saved!", Toast.LENGTH_SHORT).show()
                                    }
                                    showEditSheet = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF64D2FF),
                                    contentColor = OledBlack
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Changes", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Info Modal Bottom Sheet
            if (showInfoSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showInfoSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = DarkSurfaceVariant,
                    contentColor = TextPrimary
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Photo Information",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metadata Grid
                        val sizeKb = photo.fileSizeBytes / 1024
                        Text("Filename: ${photo.title}", color = TextSecondary, fontSize = 13.sp)
                        Text("Date: ${DateUtils.formatFullDate(photo.createdAt)}", color = TextSecondary, fontSize = 13.sp)
                        Text("File Size: ${sizeKb} KB", color = TextSecondary, fontSize = 13.sp)
                        Text("Format: ${photo.mimeType}", color = TextSecondary, fontSize = 13.sp)
                        Text("Album: ${photo.album}", color = TextSecondary, fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Offline Room Database & Sync Metadata
                        Text("Sync & Storage Status", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        val syncLabel = when (photo.syncStatus) {
                            PhotoEntity.SYNC_STATUS_SYNCED -> "Synced to Cloud"
                            PhotoEntity.SYNC_STATUS_PENDING -> "Sync Pending (Queued for Cloud)"
                            PhotoEntity.SYNC_STATUS_FAILED -> "Sync Failed (Offline Only)"
                            else -> "Stored Locally on Device (Offline-First)"
                        }
                        Text("Sync Status: $syncLabel", color = if (photo.syncStatus == PhotoEntity.SYNC_STATUS_SYNCED) AccentEmerald else TextSecondary, fontSize = 13.sp)
                        Text("Offline Database: Cached in Room SQLite", color = AccentEmerald, fontSize = 13.sp)
                        if (photo.lastSyncTimestamp > 0L) {
                            Text("Last Synced: ${DateUtils.formatFullDate(photo.lastSyncTimestamp)}", color = TextSecondary, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Cloud Link details
                        if (!photo.cloudUrl.isNullOrBlank()) {
                            Text("Cloud Storage URL", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = photo.cloudUrl,
                                color = AccentEmerald,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(photo.cloudUrl))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.testTag("open_browser_button")
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Browser")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlaybackView(
    photo: PhotoEntity,
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(photo.durationMs.toInt()) }
    var tempVideoFile by remember { mutableStateOf<File?>(null) }
    var isDecrypting by remember { mutableStateOf(photo.isEncrypted) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var decryptionFailed by remember { mutableStateOf(false) }

    LaunchedEffect(photo.id) {
        if (photo.isEncrypted) {
            isDecrypting = true
            val file = viewModel.decryptToTempPlaybackFile(photo)
            if (file != null && file.exists()) {
                tempVideoFile = file
                isDecrypting = false
            } else {
                isDecrypting = false
                decryptionFailed = true
            }
        }
    }

    DisposableEffect(photo.id) {
        onDispose {
            videoViewRef?.stopPlayback()
            tempVideoFile?.delete()
        }
    }

    // Video playback progress polling loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition
                    if (durationMs <= 0 && vv.duration > 0) {
                        durationMs = vv.duration
                    }
                }
            }
            delay(250)
        }
    }

    val videoUri = remember(tempVideoFile, photo) {
        when {
            photo.isEncrypted -> tempVideoFile?.let { Uri.fromFile(it) }
            photo.localFilePath != null -> Uri.fromFile(File(photo.localFilePath))
            else -> try { Uri.parse(photo.localUri) } catch (_: Exception) { null }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isDecrypting -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentGold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Decrypting secure video...", color = TextPrimary, fontSize = 13.sp)
                }
            }
            decryptionFailed -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AccentRose,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Vault locked. Authenticate to play video.", color = TextPrimary, fontSize = 14.sp)
                }
            }
            videoUri != null -> {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(videoUri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                if (durationMs <= 0) durationMs = mp.duration
                            }
                            setOnCompletionListener {
                                isPlaying = false
                            }
                            videoViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Play / Pause central button overlay when paused
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable {
                                videoViewRef?.start()
                                isPlaying = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                videoViewRef?.pause()
                                isPlaying = false
                            }
                    )
                }

                // In-player video progress and scrub bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                        .liquidGlass(
                            shape = RoundedCornerShape(18.dp),
                            tint = Color(0xFF141724),
                            tintAlpha = 0.88f,
                            borderAlpha = 0.4f
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    videoViewRef?.pause()
                                    isPlaying = false
                                } else {
                                    videoViewRef?.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = AccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = DateUtils.formatDuration(currentPositionMs.toLong()),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        val safeDuration = if (durationMs > 0) durationMs else 1
                        Slider(
                            value = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val target = (fraction * safeDuration).toInt()
                                currentPositionMs = target
                                videoViewRef?.seekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = AccentGold,
                                activeTrackColor = AccentGold,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = DateUtils.formatDuration(durationMs.toLong()),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            else -> {
                Text("Unable to open video", color = AccentRose)
            }
        }
    }
}
