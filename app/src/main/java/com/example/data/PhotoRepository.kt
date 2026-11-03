package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.R
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
     * Synchronizes device photos into the Room database.
     * Indexes each device photo's metadata and sets syncStatus = "LOCAL_ONLY"
     * so that the gallery can operate completely offline-first without re-querying MediaStore.
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
                        description = "Device MediaStore Image",
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
                        deviceMediaId = media.id
                    )
                )
            }
        }
        if (newEntities.isNotEmpty()) {
            photoDao.insertPhotos(newEntities)
        }
    }

    /**
     * Imports an image from a URI (from device photo picker or documents).
     */
    suspend fun importPhoto(
        uri: Uri,
        title: String,
        description: String = "",
        isEncrypted: Boolean = false,
        autoUploadToCloud: Boolean = true,
        pin: String? = null
    ): PhotoEntity = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not read image data from device. Please try another photo.")

        importRawBytes(
            rawBytes = rawBytes,
            uriString = uri.toString(),
            title = title,
            description = description,
            mimeType = mimeType,
            isEncrypted = isEncrypted,
            autoUploadToCloud = autoUploadToCloud,
            pin = pin
        )
    }

    /**
     * Imports raw image bytes directly (supports sample photos, URLs, and file picker).
     */
    suspend fun importRawBytes(
        rawBytes: ByteArray,
        uriString: String = "",
        title: String = "",
        description: String = "",
        mimeType: String = "image/jpeg",
        isEncrypted: Boolean = false,
        autoUploadToCloud: Boolean = true,
        pin: String? = null
    ): PhotoEntity = withContext(Dispatchers.IO) {
        if (pin != null) {
            if (!vaultManager.hasAccount()) {
                vaultManager.createAccount("Vault Owner", pin)
            } else if (!vaultManager.isUnlocked.value) {
                vaultManager.verifyAndUnlock(pin)
            }
        }

        val subDir = if (isEncrypted) "vault" else "gallery"
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        val ext = if (isEncrypted) "vlt" else if (mimeType.contains("png")) "png" else "jpg"
        val fileName = "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
        val localFile = File(dir, fileName)

        val finalBytes = if (isEncrypted) {
            val activePin = pin ?: vaultManager.getActivePin()
                ?: throw IllegalStateException("Vault is locked! Please enter your master PIN.")
            CryptoManager.encryptBytes(rawBytes, activePin)
        } else {
            rawBytes
        }

        FileOutputStream(localFile).use { it.write(finalBytes) }

        val activeVaultId = vaultManager.account.value?.vaultId ?: "Vault"
        val initialSyncStatus = if (autoUploadToCloud) PhotoEntity.SYNC_STATUS_PENDING else PhotoEntity.SYNC_STATUS_LOCAL_ONLY

        val entity = PhotoEntity(
            title = title.ifBlank { "Photo ${System.currentTimeMillis() % 10000}" },
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
            album = if (isEncrypted) activeVaultId else "All",
            isOfflineAvailable = true
        )

        val id = photoDao.insertPhoto(entity)
        val savedEntity = entity.copy(id = id)

        if (autoUploadToCloud) {
            uploadToCloud(savedEntity)
        }

        savedEntity
    }

    /**
     * Creates and imports a sample demo photo (useful for testing on fresh emulators).
     */
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
            description = if (isEncrypted) "Encrypted demo with AES-256" else "Gallery demo photo",
            mimeType = "image/jpeg",
            isEncrypted = isEncrypted,
            autoUploadToCloud = autoUploadToCloud,
            pin = pin
        )
    }

    /**
     * Uploads the photo (standard or encrypted) to cloud storage and updates Room sync status.
     */
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
                } catch (e: Exception) {
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
            "vault_${photo.id}_${System.currentTimeMillis()}.bin"
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

    /**
     * Decrypt and load a vault photo as a Bitmap.
     */
    suspend fun loadDecryptedBitmap(photo: PhotoEntity, pinOverride: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        if (!photo.isEncrypted) {
            return@withContext loadPlainBitmap(photo)
        }

        val pin = pinOverride ?: vaultManager.getActivePin() ?: return@withContext null

        val encryptedBytes: ByteArray? = photo.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.readBytes() else null
        } ?: photo.cloudUrl?.let { url ->
            CloudUploadService.downloadBytes(url)
        }

        if (encryptedBytes == null) return@withContext null

        try {
            val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes, pin)
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadPlainBitmap(photo: PhotoEntity): Bitmap? = withContext(Dispatchers.IO) {
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

    /**
     * Move photo to Vault (encrypt) or out of Vault (decrypt).
     */
    suspend fun toggleVaultProtection(photo: PhotoEntity, pinOverride: String? = null): Boolean = withContext(Dispatchers.IO) {
        val pin = pinOverride ?: vaultManager.getActivePin() ?: return@withContext false

        if (photo.isEncrypted) {
            // Move from Vault -> Public Gallery (Decrypt)
            val decryptedBmp = loadDecryptedBitmap(photo, pin) ?: return@withContext false
            val dir = File(context.filesDir, "gallery").apply { mkdirs() }
            val newFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(newFile).use { out ->
                decryptedBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            // Delete old encrypted file
            photo.localFilePath?.let { File(it).delete() }

            val updated = photo.copy(
                isEncrypted = false,
                localFilePath = newFile.absolutePath,
                fileSizeBytes = newFile.length(),
                mimeType = "image/jpeg",
                uploadStatus = "PENDING",
                syncStatus = PhotoEntity.SYNC_STATUS_PENDING,
                cloudUrl = null,
                album = "All"
            )
            photoDao.updatePhoto(updated)
            uploadToCloud(updated)
            true
        } else {
            // Move from Public Gallery -> Vault (Encrypt)
            val rawBytes = photo.localFilePath?.let { File(it).readBytes() }
                ?: try {
                    context.contentResolver.openInputStream(Uri.parse(photo.localUri))?.use { it.readBytes() }
                } catch (e: Exception) { null }
                ?: return@withContext false

            val encryptedBytes = CryptoManager.encryptBytes(rawBytes, pin)
            val dir = File(context.filesDir, "vault").apply { mkdirs() }
            val newFile = File(dir, "vault_${System.currentTimeMillis()}.vlt")
            FileOutputStream(newFile).use { it.write(encryptedBytes) }

            // Delete old plaintext file
            photo.localFilePath?.let { File(it).delete() }

            val activeVaultId = vaultManager.account.value?.vaultId ?: "Vault"
            val updated = photo.copy(
                isEncrypted = true,
                localFilePath = newFile.absolutePath,
                fileSizeBytes = encryptedBytes.size.toLong(),
                mimeType = "application/octet-stream",
                uploadStatus = "PENDING",
                syncStatus = PhotoEntity.SYNC_STATUS_PENDING,
                cloudUrl = null,
                album = activeVaultId
            )
            photoDao.updatePhoto(updated)
            uploadToCloud(updated)
            true
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
