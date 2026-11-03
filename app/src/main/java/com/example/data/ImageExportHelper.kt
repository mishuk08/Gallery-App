package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ImageExportHelper {

    /**
     * Downloads and saves a bitmap directly into the device's public Pictures/Gallery folder.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val resolver = context.contentResolver
        val cleanTitle = title.ifBlank { "Photo" }.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val fileName = "Gallery_${cleanTitle}_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Gallery")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies an existing local image URI directly into the device's public Pictures/Gallery folder.
     */
    fun saveUriToGallery(context: Context, sourceUri: Uri, title: String): Uri? {
        val resolver = context.contentResolver
        val cleanTitle = title.ifBlank { "Photo" }.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val fileName = "Gallery_${cleanTitle}_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Gallery")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        return try {
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Rotates bitmap by given degrees (e.g. 90, 180, 270).
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Applies preset photo filters:
     * - "original": returns copy
     * - "bw": Black & White monochrome
     * - "vintage": Warm vintage golden tone
     * - "cyber": Vibrant contrast cool tone
     * - "cool": Soft blue chilling tone
     */
    fun applyFilter(source: Bitmap, filterName: String): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        when (filterName.lowercase()) {
            "bw", "monochrome" -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }
            "vintage", "warm" -> {
                val matrix = floatArrayOf(
                    1.25f, 0f, 0f, 0f, 25f,
                    0f, 1.10f, 0f, 0f, 15f,
                    0f, 0f, 0.85f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                )
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
            }
            "cyber", "vibrant" -> {
                val matrix = floatArrayOf(
                    1.15f, 0f, 0f, 0f, 10f,
                    0f, 1.25f, 0f, 0f, 15f,
                    0f, 0f, 1.35f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                )
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
            }
            "cool" -> {
                val matrix = floatArrayOf(
                    0.90f, 0f, 0f, 0f, 0f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 1.30f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                )
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
            }
            else -> {
                // Return untouched clone
                return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
            }
        }

        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /**
     * Launches Android system image editor if available.
     */
    fun launchSystemEditor(context: Context, photoUri: Uri, mimeType: String = "image/*") {
        try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(photoUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Edit Photo With"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
