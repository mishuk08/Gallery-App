package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ImageExportHelper
import com.example.data.LocalMediaPhoto
import com.example.data.PhotoEntity
import com.example.ui.GalleryFilter
import com.example.ui.GalleryTab
import com.example.ui.GalleryViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import com.example.ui.components.PhotoGrid
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassThemed
import com.example.ui.components.LocalIsDarkMode
import com.example.ui.components.LocalGlassStyle
import com.example.ui.components.glassPill
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.util.DateUtils

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onOpenUploadDialog: () -> Unit
) {
    val context = LocalContext.current
    val publicPhotos by viewModel.publicPhotos.collectAsStateWithLifecycle()
    val devicePhotos by viewModel.devicePhotos.collectAsStateWithLifecycle()
    val favoritePhotos by viewModel.favoritePhotos.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkMode.current

    val coroutineScope = rememberCoroutineScope()
    var isGestureTracking by remember { mutableStateOf(false) }
    var reachedUpperRight by remember { mutableStateOf(false) }
    var secretHoldProgress by remember { mutableFloatStateOf(0f) }
    var secretHoldSecondsLeft by remember { mutableIntStateOf(7) }
    var isSecretUnlocked by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var selectedDevicePhoto by remember { mutableStateOf<LocalMediaPhoto?>(null) }
    var accumulatedZoom by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var zoomNotice by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(gridColumns) {
        zoomNotice = "$gridColumns Columns Grid"
        kotlinx.coroutines.delay(1600)
        zoomNotice = null
    }

    // Permission launcher for Android 13+ and older
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onStoragePermissionGranted()
        }
    }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) OledBlack else Color(0xFFF3F4F6))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val downChange = downEvent.changes.firstOrNull { it.pressed } ?: continue
                        val startPos = downChange.position
                        val screenW = size.width.toFloat()
                        val screenH = size.height.toFloat()

                        // Check if touched down in bottom-left corner region (left 40%, bottom 40%)
                        val isLowerLeft = startPos.x <= screenW * 0.40f && startPos.y >= screenH * 0.60f
                        if (!isLowerLeft) continue

                        val activePointerId = downChange.id
                        var isHolding = false
                        isGestureTracking = true

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val currentChange = event.changes.firstOrNull { it.id == activePointerId }

                            if (currentChange == null || !currentChange.pressed) {
                                // Finger lifted or gesture canceled
                                holdJob?.cancel()
                                holdJob = null
                                isGestureTracking = false
                                reachedUpperRight = false
                                secretHoldProgress = 0f
                                secretHoldSecondsLeft = 7
                                isHolding = false
                                break
                            }

                            val currentPos = currentChange.position
                            // Target: right upper corner (right 40%, top 40%)
                            val isUpperRight = currentPos.x >= screenW * 0.58f && currentPos.y <= screenH * 0.42f

                            if (isUpperRight && !isHolding) {
                                isHolding = true
                                reachedUpperRight = true
                                currentChange.consume()

                                holdJob?.cancel()
                                holdJob = coroutineScope.launch {
                                    val durationMs = 7000L
                                    val startTime = System.currentTimeMillis()
                                    var lastSec = 8
                                    while (isActive) {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val prog = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                                        secretHoldProgress = prog
                                        val remainingSec = ceil((durationMs - elapsed) / 1000.0).toInt().coerceAtLeast(1)
                                        secretHoldSecondsLeft = remainingSec

                                        if (remainingSec < lastSec) {
                                            lastSec = remainingSec
                                            try {
                                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    vibrator?.vibrate(35)
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        if (elapsed >= durationMs) {
                                            secretHoldProgress = 1f
                                            secretHoldSecondsLeft = 0
                                            isSecretUnlocked = true

                                            try {
                                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    vibrator?.vibrate(200)
                                                }
                                            } catch (_: Exception) {}

                                            // Completely invisible - No message, toast or HUD
                                            viewModel.setTab(GalleryTab.VAULT)
                                            isGestureTracking = false
                                            reachedUpperRight = false
                                            isSecretUnlocked = false
                                            secretHoldProgress = 0f
                                            break
                                        }
                                        delay(30)
                                    }
                                }
                            } else if (isHolding) {
                                currentChange.consume()
                                // Tolerance radius while holding in upper-right corner
                                val isStillInTargetArea = currentPos.x >= screenW * 0.45f && currentPos.y <= screenH * 0.55f
                                if (!isStillInTargetArea) {
                                    holdJob?.cancel()
                                    holdJob = null
                                    isHolding = false
                                    reachedUpperRight = false
                                    secretHoldProgress = 0f
                                    secretHoldSecondsLeft = 7
                                }
                            }
                        }
                    }
                }
            }
            .testTag("gallery_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                if (isSearchExpanded) {
                    // Liquid glass search field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .liquidGlassThemed(shape = RoundedCornerShape(23.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(TextPrimary),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gallery_search_input"),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search photos...", color = TextTertiary, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                        IconButton(
                            onClick = {
                                viewModel.setSearchQuery("")
                                isSearchExpanded = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Close search",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Gallery",
                                color = TextPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val totalCount = when (activeFilter) {
                                GalleryFilter.ALL -> publicPhotos.size + devicePhotos.size
                                GalleryFilter.VIDEOS -> publicPhotos.count { it.isVideo } + devicePhotos.count { it.isVideo }
                                GalleryFilter.DEVICE_PHOTOS -> devicePhotos.size
                                GalleryFilter.FAVORITES -> favoritePhotos.size
                            }
                            Text(
                                text = "$totalCount Items • Mobile & Local",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Zoom Out Button (More columns / smaller thumbnails)
                            IconButton(
                                onClick = { viewModel.zoomGridOut() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .liquidGlassThemed(shape = CircleShape)
                                    .testTag("gallery_zoom_out_button"),
                                enabled = gridColumns < 5
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out (More columns)",
                                    tint = if (gridColumns < 5) TextPrimary else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Zoom In Button (Fewer columns / larger thumbnails)
                            IconButton(
                                onClick = { viewModel.zoomGridIn() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .liquidGlassThemed(shape = CircleShape)
                                    .testTag("gallery_zoom_in_button"),
                                enabled = gridColumns > 1
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In (Fewer columns)",
                                    tint = if (gridColumns > 1) TextPrimary else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Search Button
                            IconButton(
                                onClick = { isSearchExpanded = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .liquidGlassThemed(shape = CircleShape)
                                    .testTag("open_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Sort Button
                            IconButton(
                                onClick = { showSortDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .liquidGlassThemed(shape = CircleShape)
                                    .testTag("gallery_sort_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort media",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Liquid Glass Permission Banner (Requirement 3: show all phone pics)
            if (!hasStoragePermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            tint = Color(0xFF1B202D),
                            tintAlpha = 0.85f,
                            borderAlpha = 0.40f
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF64D2FF).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF64D2FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Mobile Device Photos",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Allow gallery access to display all device photos",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = { permissionLauncher.launch(permissionToRequest) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF64D2FF),
                                contentColor = OledBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("grant_storage_permission_button")
                        ) {
                            Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Filter Chips Bar (All, Videos, Device, Favorites)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    label = "All Photos",
                    selected = activeFilter == GalleryFilter.ALL,
                    onClick = { viewModel.setFilter(GalleryFilter.ALL) }
                )
                FilterChip(
                    label = "Videos",
                    selected = activeFilter == GalleryFilter.VIDEOS,
                    onClick = { viewModel.setFilter(GalleryFilter.VIDEOS) }
                )
                FilterChip(
                    label = "Mobile (${devicePhotos.size})",
                    selected = activeFilter == GalleryFilter.DEVICE_PHOTOS,
                    onClick = { viewModel.setFilter(GalleryFilter.DEVICE_PHOTOS) }
                )
                FilterChip(
                    label = "Favorites (${favoritePhotos.size})",
                    selected = activeFilter == GalleryFilter.FAVORITES,
                    onClick = { viewModel.setFilter(GalleryFilter.FAVORITES) }
                )
            }

            // Content Grid with Pinch-to-Zoom Gesture
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            accumulatedZoom *= zoom
                            if (accumulatedZoom > 1.28f) {
                                accumulatedZoom = 1f
                                viewModel.zoomGridIn()
                            } else if (accumulatedZoom < 0.78f) {
                                accumulatedZoom = 1f
                                viewModel.zoomGridOut()
                            }
                        }
                    }
            ) {
                when (activeFilter) {
                    GalleryFilter.VIDEOS -> {
                        val videoList = publicPhotos.filter { it.isVideo }
                        PhotoGrid(
                            photos = videoList,
                            repository = viewModel.repository,
                            isVaultUnlocked = isVaultUnlocked,
                            columns = gridColumns,
                            onPhotoClick = { viewModel.selectPhoto(it) },
                            onUploadClick = onOpenUploadDialog,
                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                            emptyTitle = "No Videos Found",
                            emptySubtitle = "Device videos and recorded clips will be grouped here."
                        )
                    }
                    GalleryFilter.DEVICE_PHOTOS -> {
                        DevicePhotosGrid(
                            photos = devicePhotos,
                            gridColumns = gridColumns,
                            onPhotoClick = { selectedDevicePhoto = it },
                            onGrantPermission = { permissionLauncher.launch(permissionToRequest) },
                            hasPermission = hasStoragePermission
                        )
                    }
                    GalleryFilter.FAVORITES -> {
                        PhotoGrid(
                            photos = favoritePhotos,
                            repository = viewModel.repository,
                            isVaultUnlocked = isVaultUnlocked,
                            columns = gridColumns,
                            onPhotoClick = { viewModel.selectPhoto(it) },
                            onUploadClick = onOpenUploadDialog,
                            emptyTitle = "No Favorites Yet",
                            emptySubtitle = "Tap the heart on any photo to add it to your favorites."
                        )
                    }
                    GalleryFilter.ALL -> {
                        if (devicePhotos.isNotEmpty() && publicPhotos.isEmpty()) {
                            DevicePhotosGrid(
                                photos = devicePhotos,
                                gridColumns = gridColumns,
                                onPhotoClick = { selectedDevicePhoto = it },
                                onGrantPermission = { permissionLauncher.launch(permissionToRequest) },
                                hasPermission = hasStoragePermission
                            )
                        } else {
                            PhotoGrid(
                                photos = publicPhotos,
                                repository = viewModel.repository,
                                isVaultUnlocked = isVaultUnlocked,
                                columns = gridColumns,
                                onPhotoClick = { viewModel.selectPhoto(it) },
                                onUploadClick = onOpenUploadDialog,
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                emptyTitle = "Your Gallery is Ready",
                                emptySubtitle = "All your phone photos and uploads will appear here."
                            )
                        }
                    }
                }

                // Floating Zoom Notification Pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = zoomNotice != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    zoomNotice?.let { msg ->
                        Box(
                            modifier = Modifier
                                .liquidGlassThemed(shape = RoundedCornerShape(20.dp), elevation = 10.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = msg,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button to Import / Add photo with Liquid Glass
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 86.dp)
                .size(56.dp)
                .liquidGlass(
                    shape = CircleShape,
                    tint = if (isDark) Color(0xFF222838) else Color.White,
                    tintAlpha = if (isDark) 0.85f else 0.95f,
                    borderAlpha = if (isDark) 0.55f else 0.40f,
                    elevation = 12.dp
                )
                .clickable(onClick = onOpenUploadDialog)
                .testTag("gallery_fab_upload"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Photo",
                tint = if (isDark) Color.White else Color(0xFF0F172A),
                modifier = Modifier.size(26.dp)
            )
        }

        // Selected Device Photo Dialog / Detail with "Move to Vault"
        if (selectedDevicePhoto != null) {
            val photo = selectedDevicePhoto!!
            DevicePhotoPreviewSheet(
                photo = photo,
                onDismiss = { selectedDevicePhoto = null },
                onMoveToVault = {
                    viewModel.moveDevicePhotoToVault(photo)
                    selectedDevicePhoto = null
                }
            )
        }

        // Sort Dialog
        if (showSortDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSortDialog = false },
                title = {
                    Text("Sort Media", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val sortOptions = listOf(
                            com.example.ui.MediaSortOrder.DATE_DESC to "Date: Newest First",
                            com.example.ui.MediaSortOrder.DATE_ASC to "Date: Oldest First",
                            com.example.ui.MediaSortOrder.NAME_ASC to "Name: A to Z",
                            com.example.ui.MediaSortOrder.NAME_DESC to "Name: Z to A",
                            com.example.ui.MediaSortOrder.SIZE_DESC to "Size: Largest First",
                            com.example.ui.MediaSortOrder.SIZE_ASC to "Size: Smallest First"
                        )
                        sortOptions.forEach { (order, label) ->
                            val isSelected = sortOrder == order
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) AccentGold.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        viewModel.setSortOrder(order)
                                        showSortDialog = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) AccentGold else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showSortDialog = false }) {
                        Text("Close", color = TextSecondary)
                    }
                },
                containerColor = if (isDark) Color(0xFF181B26) else Color.White
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val style = LocalGlassStyle.current
    Box(
        modifier = Modifier
            .glassPill(selected = selected, activeColor = style.accentColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Grid layout for device MediaStore photos
 */
@Composable
private fun DevicePhotosGrid(
    photos: List<LocalMediaPhoto>,
    gridColumns: Int,
    onPhotoClick: (LocalMediaPhoto) -> Unit,
    onGrantPermission: () -> Unit,
    hasPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cols = gridColumns.coerceIn(1, 5)

    if (photos.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (!hasPermission) "Permission Required" else "No Photos Found On Device",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!hasPermission)
                        "Grant storage access to let Cloud Gallery display all your phone's camera and saved pictures."
                    else
                        "Take a photo with your phone camera or save images to see them here.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                if (!hasPermission) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onGrantPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = OledBlack),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val rows = remember(photos, cols) { photos.chunked(cols) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 100.dp)
    ) {
        items(rows.size, key = { "dev_row_$it" }) { rowIndex ->
            val rowPhotos = rows[rowIndex]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0 until cols) {
                    if (i < rowPhotos.size) {
                        val photo = rowPhotos[i]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkSurfaceVariant)
                                .border(0.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                .clickable { onPhotoClick(photo) }
                                .testTag("device_photo_${photo.id}")
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(photo.contentUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = photo.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Preview sheet for device photo with "Move to Vault" action
 */
@Composable
private fun DevicePhotoPreviewSheet(
    photo: LocalMediaPhoto,
    onDismiss: () -> Unit,
    onMoveToVault: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .liquidGlass(shape = RoundedCornerShape(26.dp), tint = Color(0xFF1A1D27))
                .clickable(enabled = false) {}
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photo.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = photo.displayName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Text(
                text = "Device Folder: ${photo.bucketName} • ${(photo.sizeBytes / 1024)} KB",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action row: Edit, Download & Share (Requirement: photo full screen korle jeno niche edit download er option thake)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        ImageExportHelper.launchSystemEditor(context, Uri.parse(photo.contentUri), photo.mimeType)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val savedUri = ImageExportHelper.saveUriToGallery(context, Uri.parse(photo.contentUri), photo.displayName)
                        if (savedUri != null) {
                            Toast.makeText(context, "Saved to Pictures/VaultGallery!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not save photo", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(photo.contentUri))
                            type = photo.mimeType
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Photo"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onMoveToVault,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGold,
                        contentColor = OledBlack
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Move to Vault", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x25FFFFFF),
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close", fontSize = 13.sp)
                }
            }
        }
    }
}
