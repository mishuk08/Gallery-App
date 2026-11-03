package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import com.example.R
import com.example.crypto.CryptoException
import com.example.crypto.CryptoManager
import com.example.network.CloudUploadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PhotoRepository(
    private val context: Context,
    private val photoDao: PhotoDao,
    private val vaultManager: VaultManager
) {
    val allPhotos: Flow<List<PhotoEntity>> = photoDao.getAllPhotos()
    val publicPhotos: Flow<List<PhotoEntity>> = photoDao.getPublicPhotos()
    val vaultPhotos: Flow<List<PhotoEntity>> = photoDao.getVaultPhotos()
    val cloudPhotos: Flow<List<PhotoEntity>> = photoDao.getCloudPhotos()
    val favoritePhotos: Flow<List<PhotoEntity>> = photoDao.getFavoritePhotos()
    val pendingSyncPhotos: Flow<List<PhotoEntity>> = photoDao.getPendingSyncPhotos()
    val syncedPhotos: Flow<List<PhotoEntity>> = photoDao.getSyncedPhotos()

    val syncedCount: Flow<Int> = photoDao.getSyncedCount()
    val pendingSyncCount: Flow<Int> = photoDao.getPendingSyncCount()
    val localOnlyCount: Flow<Int> = photoDao.getLocalOnlyCount()

    fun searchPublicPhotos(query: String): Flow<List<PhotoEntity>> = photoDao.searchPublicPhotos(query)

    suspend fun getPhotoById(id: Long): PhotoEntity? = withContext(Dispatchers.IO) {
        photoDao.getPhotoById(id)
    }

    /**
     * Synchronizes device photos and videos into the Room database.
     * Prevents duplicates and maintains offline-first indexing.
     */
    suspend fun syncDevicePhotosToDatabase(mediaPhotos: List<LocalMediaPhoto>) = withContext(Dispatchers.IO) {
        val newEntities = mutableListOf<PhotoEntity>()
        for (media in mediaPhotos) {
            val existing = photoDao.getPhotoByDeviceMediaId(media.id) 
                ?: photoDao.getPhotoByUri(media.contentUri)
            if (existing == null) {
                newEntities.add(
                    PhotoEntity(
                        title = media.displayName,
                        description = if (media.isVideo) "Device Video" else "Device Image",
                        localUri = media.contentUri,
                        localFilePath = null,
                        cloudUrl = null,
                        uploadStatus = "LOCAL_ONLY",
                        syncStatus = PhotoEntity.SYNC_STATUS_LOCAL_ONLY,
                        isEncrypted = false,
                        fileSizeBytes = media.sizeBytes,
                        mimeType = media.mimeType,
                        createdAt = media.dateAdded,
                        album = media.bucketName,
                        isOfflineAvailable = true,
                        deviceMediaId = media.id,
                        isVideo = media.isVideo,
                        durationMs = media.durationMs
                    )
                )
            }
        }
        if (newEntities.isNotEmpty()) {
            photoDao.insertPhotos(newEntities)
        }
    }

    /**
     * Imports an image or video from a URI into Gallery or Encrypted Vault.
     */
    suspend fun importPhoto(
        uri: Uri,
        title: String,
        description: String = "",
        isEncrypted: Boolean = false,
        autoUploadToCloud: Boolean = false,
        pin: String? = null
    ): PhotoEntity = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not read media data from device.")

        val isVideo = mimeType.startsWith("video/")
        val durationMs = if (isVideo) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                durStr?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            }
        } else {
            0L
        }

        importRawBytes(
            rawBytes = rawBytes,
            uriString = uri.toString(),
            title = title,
            description = description,
            mimeType = mimeType,
            isEncrypted = isEncrypted,
            autoUploadToCloud = autoUploadToCloud,
            pin = pin,
            isVideo = isVideo,
            durationMs = durationMs
        )
    }

    /**
     * Imports raw bytes directly into Gallery or Encrypted Vault.
     * Enforces Master Vault Key encryption and verification before file persistence.
     */
    suspend fun importRawBytes(
        rawBytes: ByteArray,
        uriString: String = "",
        title: String = "",
        description: String = "",
        mimeType: String = "image/jpeg",
        isEncrypted: Boolean = false,
        autoUploadToCloud: Boolean = false,
        pin: String? = null,
        isVideo: Boolean = false,
        durationMs: Long = 0L
    ): PhotoEntity = withContext(Dispatchers.IO) {
        if (pin != null) {
            if (!vaultManager.hasAccount()) {
                vaultManager.createAccount("Vault Owner", pin)
            } else if (!vaultManager.isUnlocked.value) {
                vaultManager.verifyAndUnlock(pin)
            }
        }

        val masterKey: ByteArray? = if (isEncrypted) {
            vaultManager.getActiveMasterKey()
                ?: throw IllegalStateException("Vault is locked! Please authenticate.")
        } else {
            null
        }

        val subDir = if (isEncrypted) "vault" else "gallery"
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        val ext = when {
            isEncrypted -> "vlt"
            isVideo -> "mp4"
            mimeType.contains("png") -> "png"
            else -> "jpg"
        }
        val fileName = "media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
        val localFile = File(dir, fileName)

        val finalBytes = if (isEncrypted && masterKey != null) {
            val encrypted = CryptoManager.encryptWithMasterKey(rawBytes, masterKey)
            // Verify encryption header before committing
            if (!CryptoManager.isEncryptedPayload(encrypted)) {
                throw IllegalStateException("Encryption verification failed.")
            }
            encrypted
        } else {
            rawBytes
        }

        FileOutputStream(localFile).use { it.write(finalBytes) }

        val activeVaultId = vaultManager.account.value?.vaultId ?: "Vault"
        val initialSyncStatus = if (autoUploadToCloud) PhotoEntity.SYNC_STATUS_PENDING else PhotoEntity.SYNC_STATUS_LOCAL_ONLY

        val entity = PhotoEntity(
            title = title.ifBlank { if (isVideo) "Video ${System.currentTimeMillis() % 10000}" else "Photo ${System.currentTimeMillis() % 10000}" },
            description = description,
            localUri = uriString.ifBlank { localFile.toURI().toString() },
            localFilePath = localFile.absolutePath,
            cloudUrl = null,
            uploadStatus = if (autoUploadToCloud) "PENDING" else "LOCAL_ONLY",
            syncStatus = initialSyncStatus,
            isEncrypted = isEncrypted,
            fileSizeBytes = finalBytes.size.toLong(),
            mimeType = if (isEncrypted) "application/octet-stream" else mimeType,
            createdAt = System.currentTimeMillis(),
            album = if (isEncrypted) activeVaultId else if (isVideo) "Videos" else "All",
            isOfflineAvailable = true,
            isVideo = isVideo,
            durationMs = durationMs
        )

        val id = photoDao.insertPhoto(entity)
        val savedEntity = entity.copy(id = id)

        if (autoUploadToCloud) {
            uploadToCloud(savedEntity)
        }

        savedEntity
    }

    suspend fun importSamplePhoto(
        isEncrypted: Boolean = false,
        autoUploadToCloud: Boolean = false,
        pin: String? = null
    ): PhotoEntity = withContext(Dispatchers.IO) {
        val sampleResId = if (isEncrypted) R.drawable.ic_launcher_background else R.drawable.demo_photo
        val inputStream = context.resources.openRawResource(sampleResId)
        val bytes = inputStream.use { it.readBytes() }

        val sampleTitle = if (isEncrypted) "Confidential Document (Demo)" else "Demo Photo"
        importRawBytes(
            rawBytes = bytes,
            title = sampleTitle,
            description = if (isEncrypted) "Encrypted demo with AES-256-GCM" else "Gallery demo photo",
            mimeType = "image/jpeg",
            isEncrypted = isEncrypted,
            autoUploadToCloud = autoUploadToCloud,
            pin = pin,
            isVideo = false,
            durationMs = 0L
        )
    }

    /**
     * Decrypts and loads a vault photo or video thumbnail as a Bitmap.
     */
    suspend fun loadDecryptedBitmap(photo: PhotoEntity, pinOverride: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        if (!photo.isEncrypted) {
            return@withContext loadPlainBitmap(photo)
        }

        val masterKey = vaultManager.getActiveMasterKey()
        val fallbackPin = pinOverride ?: vaultManager.getActivePin()

        if (masterKey == null && fallbackPin == null) {
            return@withContext null
        }

        val encryptedBytes: ByteArray? = photo.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.readBytes() else null
        } ?: photo.cloudUrl?.let { url ->
            CloudUploadService.downloadBytes(url)
        }

        if (encryptedBytes == null) return@withContext null

        try {
            val effectiveMasterKey = masterKey ?: CryptoManager.generateMasterKey()
            val decryptedBytes = CryptoManager.decryptMedia(encryptedBytes, effectiveMasterKey, fallbackPin)

            if (photo.isVideo) {
                // Extract frame from decrypted video bytes via temporary cache file
                extractVideoThumbnail(decryptedBytes)
            } else {
                BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts encrypted media to a temporary file in cacheDir for video playback.
     * Automatically tagged to delete on JVM exit and safe for media player consumption.
     */
    suspend fun decryptToTempPlaybackFile(photo: PhotoEntity, pinOverride: String? = null): File? = withContext(Dispatchers.IO) {
        if (!photo.isEncrypted) {
            photo.localFilePath?.let { path ->
                val f = File(path)
                if (f.exists()) return@withContext f
            }
            return@withContext null
        }

        val masterKey = vaultManager.getActiveMasterKey()
        val fallbackPin = pinOverride ?: vaultManager.getActivePin()

        if (masterKey == null && fallbackPin == null) {
            return@withContext null
        }

        val encryptedBytes: ByteArray? = photo.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.readBytes() else null
        } ?: photo.cloudUrl?.let { url ->
            CloudUploadService.downloadBytes(url)
        }

        if (encryptedBytes == null) return@withContext null

        try {
            val effectiveMasterKey = masterKey ?: CryptoManager.generateMasterKey()
            val decryptedBytes = CryptoManager.decryptMedia(encryptedBytes, effectiveMasterKey, fallbackPin)
            val ext = if (photo.isVideo) ".mp4" else ".jpg"
            val tempFile = File.createTempFile("playback_${photo.id}_", ext, context.cacheDir)
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { it.write(decryptedBytes) }
            tempFile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadPlainBitmap(photo: PhotoEntity): Bitmap? = withContext(Dispatchers.IO) {
        if (photo.isVideo) {
            return@withContext loadVideoThumbnail(photo)
        }

        photo.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.let { return@withContext it }
            }
        }

        try {
            context.contentResolver.openInputStream(Uri.parse(photo.localUri))?.use {
                BitmapFactory.decodeStream(it)?.let { bmp -> return@withContext bmp }
            }
        } catch (_: Exception) {}

        photo.cloudUrl?.let { url ->
            val bytes = CloudUploadService.downloadBytes(url)
            if (bytes != null) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return@withContext it }
            }
        }

        null
    }

    private fun loadVideoThumbnail(photo: PhotoEntity): Bitmap? {
        return try {
            val uri = Uri.parse(photo.localUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val frame = retriever.frameAtTime
                retriever.release()
                frame
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVideoThumbnail(videoBytes: ByteArray): Bitmap? {
        val tempFile = File.createTempFile("v_thumb_", ".mp4", context.cacheDir)
        return try {
            FileOutputStream(tempFile).use { it.write(videoBytes) }
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempFile.absolutePath)
            val frame = retriever.frameAtTime
            retriever.release()
            frame
        } catch (_: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Toggles encryption between Vault and Public Gallery.
     */
    suspend fun toggleVaultProtection(photo: PhotoEntity, pinOverride: String? = null): Boolean = withContext(Dispatchers.IO) {
        val masterKey = vaultManager.getActiveMasterKey()
        val pin = pinOverride ?: vaultManager.getActivePin()

        if (masterKey == null && pin == null) return@withContext false

        if (photo.isEncrypted) {
            // Move from Vault -> Public Gallery (Decrypt)
            val encryptedBytes = photo.localFilePath?.let { File(it).readBytes() } ?: return@withContext false
            val decryptedBytes = try {
                val effectiveMasterKey = masterKey ?: CryptoManager.generateMasterKey()
                CryptoManager.decryptMedia(encryptedBytes, effectiveMasterKey, pin)
            } catch (_: Exception) {
                return@withContext false
            }

            val dir = File(context.filesDir, "gallery").apply { mkdirs() }
            val ext = if (photo.isVideo) "mp4" else "jpg"
            val newFile = File(dir, "photo_${System.currentTimeMillis()}.$ext")
            FileOutputStream(newFile).use { it.write(decryptedBytes) }

            // Delete old encrypted file
            photo.localFilePath?.let { File(it).delete() }

            val updated = photo.copy(
                isEncrypted = false,
                localUri = Uri.fromFile(newFile).toString(),
                localFilePath = newFile.absolutePath,
                fileSizeBytes = newFile.length(),
                mimeType = if (photo.isVideo) "video/mp4" else "image/jpeg",
                uploadStatus = "LOCAL_ONLY",
                syncStatus = PhotoEntity.SYNC_STATUS_LOCAL_ONLY,
                cloudUrl = null,
                album = if (photo.isVideo) "Videos" else "All"
            )
            photoDao.updatePhoto(updated)
            true
        } else {
            // Move from Public Gallery -> Vault (Encrypt)
            val rawBytes = photo.localFilePath?.let { File(it).readBytes() }
                ?: try {
                    context.contentResolver.openInputStream(Uri.parse(photo.localUri))?.use { it.readBytes() }
                } catch (_: Exception) { null }
                ?: return@withContext false

            val effectiveMasterKey = masterKey ?: return@withContext false
            val encryptedBytes = CryptoManager.encryptWithMasterKey(rawBytes, effectiveMasterKey)

            val dir = File(context.filesDir, "vault").apply { mkdirs() }
            val newFile = File(dir, "vault_${System.currentTimeMillis()}.vlt")
            FileOutputStream(newFile).use { it.write(encryptedBytes) }

            // Delete old plaintext file
            photo.localFilePath?.let { File(it).delete() }

            val activeVaultId = vaultManager.account.value?.vaultId ?: "Vault"
            val updated = photo.copy(
                isEncrypted = true,
                localUri = Uri.fromFile(newFile).toString(),
                localFilePath = newFile.absolutePath,
                fileSizeBytes = encryptedBytes.size.toLong(),
                mimeType = "application/octet-stream",
                uploadStatus = "LOCAL_ONLY",
                syncStatus = PhotoEntity.SYNC_STATUS_LOCAL_ONLY,
                cloudUrl = null,
                album = activeVaultId
            )
            photoDao.updatePhoto(updated)
            true
        }
    }

    suspend fun uploadToCloud(
        photo: PhotoEntity,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        photoDao.updateSyncDetails(
            id = photo.id,
            cloudUrl = photo.cloudUrl,
            status = "UPLOADING",
            syncStatus = PhotoEntity.SYNC_STATUS_PENDING,
            timestamp = photo.lastSyncTimestamp
        )

        val file = photo.localFilePath?.let { File(it) }
        val bytes = when {
            file != null && file.exists() -> file.readBytes()
            else -> {
                try {
                    context.contentResolver.openInputStream(Uri.parse(photo.localUri))?.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
            }
        }

        if (bytes == null || bytes.isEmpty()) {
            photoDao.updateSyncDetails(
                id = photo.id,
                cloudUrl = photo.cloudUrl,
                status = "FAILED",
                syncStatus = PhotoEntity.SYNC_STATUS_FAILED,
                timestamp = System.currentTimeMillis()
            )
            return@withContext false
        }

        val uploadFileName = if (photo.isEncrypted) {
            "vault_${photo.id}_${System.currentTimeMillis()}.vlt"
        } else if (photo.isVideo) {
            "vid_${photo.id}_${System.currentTimeMillis()}.mp4"
        } else {
            "img_${photo.id}_${System.currentTimeMillis()}.jpg"
        }

        val uploadMime = if (photo.isEncrypted) "application/octet-stream" else photo.mimeType

        when (val result = CloudUploadService.upload(uploadFileName, uploadMime, bytes, onProgress)) {
            is CloudUploadService.UploadResult.Success -> {
                photoDao.updateSyncDetails(
                    id = photo.id,
                    cloudUrl = result.url,
                    status = "UPLOADED",
                    syncStatus = PhotoEntity.SYNC_STATUS_SYNCED,
                    timestamp = System.currentTimeMillis()
                )
                true
            }
            is CloudUploadService.UploadResult.Error -> {
                photoDao.updateSyncDetails(
                    id = photo.id,
                    cloudUrl = photo.cloudUrl,
                    status = "FAILED",
                    syncStatus = PhotoEntity.SYNC_STATUS_FAILED,
                    timestamp = System.currentTimeMillis()
                )
                false
            }
        }
    }

    suspend fun toggleFavorite(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        photoDao.updateFavorite(photo.id, !photo.isFavorite)
    }

    suspend fun updatePhoto(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        photoDao.updatePhoto(photo)
    }

    suspend fun deletePhoto(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        photo.localFilePath?.let { File(it).delete() }
        photoDao.deletePhoto(photo)
    }
}
