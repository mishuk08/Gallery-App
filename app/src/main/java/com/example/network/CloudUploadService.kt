package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudUploadService {
    // Primary permanent image hosting with Cloudflare CDN
    private const val FREEIMAGE_API = "https://freeimage.host/api/1/upload"
    private const val FREEIMAGE_KEY = "6d207e02198a847aa98d0a2a901485a5"

    // Primary binary / vault file hosting service
    private const val LITTERBOX_API = "https://litterbox.catbox.moe/resources/internals/api.php"

    // Fallback file hosting
    private const val TMPFILES_API = "https://tmpfiles.org/api/v1/upload"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    sealed class UploadResult {
        data class Success(val url: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }

    /**
     * Uploads bytes to the cloud storage service.
     * Uses Freeimage.host for permanent image hosting and Litterbox/Tmpfiles for encrypted binaries.
     */
    suspend fun upload(
        fileName: String,
        mimeType: String,
        data: ByteArray,
        onProgress: (Int) -> Unit = {}
    ): UploadResult = withContext(Dispatchers.IO) {
        val isImage = mimeType.startsWith("image/") && !fileName.endsWith(".vlt") && !fileName.endsWith(".bin")

        // 1. Try Freeimage.host for standard images (Permanent Cloudflare CDN)
        if (isImage) {
            val freeimageResult = uploadToFreeimage(fileName, mimeType, data, onProgress)
            if (freeimageResult is UploadResult.Success) {
                return@withContext freeimageResult
            }
        }

        // 2. Try Litterbox (Works for both images and encrypted binary files)
        val litterboxResult = uploadToLitterbox(fileName, mimeType, data, onProgress)
        if (litterboxResult is UploadResult.Success) {
            return@withContext litterboxResult
        }

        // 3. Try Tmpfiles as fallback
        val tmpfilesResult = uploadToTmpfiles(fileName, mimeType, data, onProgress)
        if (tmpfilesResult is UploadResult.Success) {
            return@withContext tmpfilesResult
        }

        // Return error details if all providers failed
        val errorMsg = when {
            litterboxResult is UploadResult.Error -> litterboxResult.message
            tmpfilesResult is UploadResult.Error -> tmpfilesResult.message
            else -> "Upload failed across cloud providers. Check internet connection."
        }
        UploadResult.Error(errorMsg)
    }

    private fun createProgressRequestBody(
        mimeType: String,
        data: ByteArray,
        onProgress: (Int) -> Unit
    ): RequestBody {
        val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        return object : RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength() = data.size.toLong()
            override fun writeTo(sink: BufferedSink) {
                val bufferSize = 8192
                var uploaded = 0L
                val total = data.size.toLong()
                var offset = 0

                while (offset < data.size) {
                    val chunkSize = minOf(bufferSize, data.size - offset)
                    sink.write(data, offset, chunkSize)
                    offset += chunkSize
                    uploaded += chunkSize
                    val percent = if (total > 0) ((uploaded * 100) / total).toInt().coerceIn(0, 100) else 100
                    onProgress(percent)
                }
            }
        }
    }

    private fun uploadToFreeimage(
        fileName: String,
        mimeType: String,
        data: ByteArray,
        onProgress: (Int) -> Unit
    ): UploadResult {
        return try {
            val requestBody = createProgressRequestBody(mimeType, data, onProgress)
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", FREEIMAGE_KEY)
                .addFormDataPart("action", "upload")
                .addFormDataPart("source", fileName, requestBody)
                .build()

            val request = Request.Builder()
                .url(FREEIMAGE_API)
                .header("User-Agent", "SecureCloudGallery/1.0")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val status = json.optInt("status_code", 0)
                if (status == 200) {
                    val imageObj = json.optJSONObject("image")
                    val directUrl = imageObj?.optString("url") ?: ""
                    if (directUrl.startsWith("http")) {
                        return UploadResult.Success(directUrl)
                    }
                }
            }
            UploadResult.Error("Freeimage returned invalid response: $body")
        } catch (e: Exception) {
            UploadResult.Error("Freeimage upload failed: ${e.localizedMessage}")
        }
    }

    private fun uploadToLitterbox(
        fileName: String,
        mimeType: String,
        data: ByteArray,
        onProgress: (Int) -> Unit
    ): UploadResult {
        return try {
            val requestBody = createProgressRequestBody(mimeType, data, onProgress)
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("time", "72h")
                .addFormDataPart("fileToUpload", fileName, requestBody)
                .build()

            val request = Request.Builder()
                .url(LITTERBOX_API)
                .header("User-Agent", "SecureCloudGallery/1.0")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()?.trim() ?: ""
            if (response.isSuccessful && body.startsWith("http")) {
                UploadResult.Success(body)
            } else {
                UploadResult.Error("Litterbox error: $body")
            }
        } catch (e: Exception) {
            UploadResult.Error("Litterbox upload failed: ${e.localizedMessage}")
        }
    }

    private fun uploadToTmpfiles(
        fileName: String,
        mimeType: String,
        data: ByteArray,
        onProgress: (Int) -> Unit
    ): UploadResult {
        return try {
            val requestBody = createProgressRequestBody(mimeType, data, onProgress)
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, requestBody)
                .build()

            val request = Request.Builder()
                .url(TMPFILES_API)
                .header("User-Agent", "SecureCloudGallery/1.0")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val status = json.optString("status")
                if (status == "success") {
                    val dataObj = json.optJSONObject("data")
                    val rawUrl = dataObj?.optString("url") ?: ""
                    // Convert https://tmpfiles.org/XXXX/file to direct download https://tmpfiles.org/dl/XXXX/file
                    val directUrl = if (rawUrl.contains("tmpfiles.org/")) {
                        rawUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                    } else rawUrl

                    if (directUrl.startsWith("http")) {
                        return UploadResult.Success(directUrl)
                    }
                }
            }
            UploadResult.Error("Tmpfiles error: $body")
        } catch (e: Exception) {
            UploadResult.Error("Tmpfiles upload failed: ${e.localizedMessage}")
        }
    }

    /**
     * Downloads raw data from any internet URL.
     */
    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SecureCloudGallery/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
