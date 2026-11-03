package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceMediaStoreManager {

    /**
     * Checks whether the app has permission to read images/videos.
     * Supports Android 14+ selected photo access (READ_MEDIA_VISUAL_USER_SELECTED),
     * Android 13 (READ_MEDIA_IMAGES / READ_MEDIA_VIDEO), and legacy storage permissions.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val hasImages = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED

                val hasSelected = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED

                val hasVideos = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED

                hasImages || hasSelected || hasVideos
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasImages = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED

                val hasVideos = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED

                hasImages || hasVideos
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Returns true if user has granted limited/partial photo access on Android 14+.
     */
    fun isPartialAccessOnly(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val hasFull = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val hasPartial = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        return hasPartial && !hasFull
    }

    /**
     * Scans device images AND videos from MediaStore, preventing duplicates and
     * sorting newest first.
     */
    suspend fun fetchDevicePhotos(context: Context): List<LocalMediaPhoto> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<LocalMediaPhoto>()
        if (!hasStoragePermission(context)) {
            return@withContext mediaList
        }

        val seenUris = mutableSetOf<String>()

        // 1. Scan Images
        queryImages(context, mediaList, seenUris)

        // 2. Scan Videos
        queryVideos(context, mediaList, seenUris)

        // Sort descending by dateAdded
        mediaList.sortByDescending { it.dateAdded }
        mediaList
    }

    private fun queryImages(
        context: Context,
        outList: MutableList<LocalMediaPhoto>,
        seenUris: MutableSet<String>
    ) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            } else {
                MediaStore.Images.Media._ID
            },
            MediaStore.Images.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val bucketColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                } else -1
                val mimeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "IMG_$id.jpg"
                    val dateAdded = cursor.getLong(dateColumn) * 1000L
                    val size = cursor.getLong(sizeColumn)
                    val bucket = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: "Camera" else "Camera"
                    val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn) ?: "image/jpeg" else "image/jpeg"
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    if (seenUris.add(contentUri)) {
                        outList.add(
                            LocalMediaPhoto(
                                id = id,
                                contentUri = contentUri,
                                displayName = name,
                                dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis(),
                                sizeBytes = size,
                                bucketName = bucket,
                                mimeType = mime,
                                isVideo = false,
                                durationMs = 0L
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun queryVideos(
        context: Context,
        outList: MutableList<LocalMediaPhoto>,
        seenUris: MutableSet<String>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME
            } else {
                MediaStore.Video.Media._ID
            },
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val bucketColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                } else -1
                val mimeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "VID_$id.mp4"
                    val dateAdded = cursor.getLong(dateColumn) * 1000L
                    val size = cursor.getLong(sizeColumn)
                    val bucket = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: "Videos" else "Videos"
                    val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn) ?: "video/mp4" else "video/mp4"
                    val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    if (seenUris.add(contentUri)) {
                        outList.add(
                            LocalMediaPhoto(
                                id = id,
                                contentUri = contentUri,
                                displayName = name,
                                dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis(),
                                sizeBytes = size,
                                bucketName = bucket,
                                mimeType = mime,
                                isVideo = true,
                                durationMs = duration
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
