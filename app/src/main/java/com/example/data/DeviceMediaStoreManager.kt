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

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun fetchDevicePhotos(context: Context): List<LocalMediaPhoto> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<LocalMediaPhoto>()
        if (!hasStoragePermission(context)) {
            return@withContext photos
        }

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
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "IMG_$id"
                    val dateAdded = cursor.getLong(dateColumn) * 1000L // convert seconds to ms
                    val size = cursor.getLong(sizeColumn)
                    val bucket = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: "Camera" else "Camera"
                    val mime = cursor.getString(mimeColumn) ?: "image/jpeg"
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    photos.add(
                        LocalMediaPhoto(
                            id = id,
                            contentUri = contentUri,
                            displayName = name,
                            dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis(),
                            sizeBytes = size,
                            bucketName = bucket,
                            mimeType = mime
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        photos
    }
}
