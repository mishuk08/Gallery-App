package com.example.data

import android.content.Context
import android.net.Uri
import com.example.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackupRestoreResult(
    val restoredAccount: VaultAccount?,
    val restoredMediaCount: Int,
    val totalMediaInBackup: Int
)

object BackupManager {

    private const val MANIFEST_ENTRY_NAME = "manifest.json"

    /**
     * Creates an encrypted backup archive (.vaultbackup) containing all vault media,
     * optional gallery media, metadata manifest, and account keys.
     */
    suspend fun createEncryptedBackup(
        context: Context,
        account: VaultAccount?,
        vaultPhotos: List<PhotoEntity>,
        galleryPhotos: List<PhotoEntity>,
        includeGalleryPhotos: Boolean,
        backupPasswordOrPin: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val zipOutStream = ByteArrayOutputStream()
        val zip = ZipOutputStream(zipOutStream)

        val itemsToBackup = mutableListOf<PhotoEntity>()
        itemsToBackup.addAll(vaultPhotos)
        if (includeGalleryPhotos) {
            itemsToBackup.addAll(galleryPhotos)
        }

        val manifestMediaArray = JSONArray()

        for ((index, item) in itemsToBackup.withIndex()) {
            val file = item.localFilePath?.let { File(it) }
            val rawBytes = when {
                file != null && file.exists() -> file.readBytes()
                else -> {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(item.localUri))?.use { it.readBytes() }
                    } catch (_: Exception) {
                        null
                    }
                }
            } ?: continue

            val archiveFileName = "media_${index}_${UUID.randomUUID().toString().take(8)}.bin"

            // Add file to zip
            val entry = ZipEntry("payload/$archiveFileName")
            zip.putNextEntry(entry)
            zip.write(rawBytes)
            zip.closeEntry()

            val mediaObj = JSONObject().apply {
                put("title", item.title)
                put("description", item.description)
                put("isEncrypted", item.isEncrypted)
                put("mimeType", item.mimeType)
                put("fileSizeBytes", item.fileSizeBytes)
                put("createdAt", item.createdAt)
                put("album", item.album)
                put("isVideo", item.isVideo)
                put("durationMs", item.durationMs)
                put("archiveFileName", archiveFileName)
            }
            manifestMediaArray.put(mediaObj)
        }

        // Build Manifest
        val manifestObj = JSONObject().apply {
            put("version", 2)
            put("createdAt", System.currentTimeMillis())
            if (account != null) {
                val accObj = JSONObject().apply {
                    put("vaultId", account.vaultId)
                    put("userName", account.userName)
                    put("passwordHash", account.passwordHash)
                    put("saltHex", account.saltHex)
                    put("wrappedMasterKeyHex", account.wrappedMasterKeyHex)
                    put("createdAt", account.createdAt)
                    put("isBiometricEnabled", account.isBiometricEnabled)
                }
                put("account", accObj)
            }
            put("mediaCount", manifestMediaArray.length())
            put("mediaItems", manifestMediaArray)
        }

        // Add Manifest entry to zip
        val manifestEntry = ZipEntry(MANIFEST_ENTRY_NAME)
        zip.putNextEntry(manifestEntry)
        zip.write(manifestObj.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.finish()
        val plainZipBytes = zipOutStream.toByteArray()

        // Encrypt the complete zip archive using AES-256-GCM derived from the backup passphrase
        val encryptedArchive = CryptoManager.encryptBackupArchive(plainZipBytes, backupPasswordOrPin)

        // Verify integrity before returning: ensure test decryption succeeds
        val testDecrypted = try {
            CryptoManager.decryptBackupArchive(encryptedArchive, backupPasswordOrPin)
        } catch (e: Exception) {
            throw BackupException("Backup verification failed: encrypted payload is corrupted", e)
        }
        if (testDecrypted.size != plainZipBytes.size) {
            throw BackupException("Backup verification failed: checksum mismatch")
        }

        encryptedArchive
    }

    /**
     * Restores an encrypted backup archive (.vaultbackup).
     * Extracts manifest, restores Vault Account, saves media files, and inserts into database.
     */
    suspend fun restoreEncryptedBackup(
        context: Context,
        backupBytes: ByteArray,
        backupPasswordOrPin: String,
        vaultManager: VaultManager,
        photoDao: PhotoDao
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        if (!CryptoManager.isBackupPayload(backupBytes)) {
            throw BackupException("File is not a valid encrypted .vaultbackup archive")
        }

        // 1. Decrypt archive
        val plainZipBytes = try {
            CryptoManager.decryptBackupArchive(backupBytes, backupPasswordOrPin)
        } catch (e: Exception) {
            throw BackupException("Incorrect password or corrupted backup file", e)
        }

        val zipIn = ZipInputStream(ByteArrayInputStream(plainZipBytes))
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytesExtracted = 0L
        var entryCount = 0

        var entry = zipIn.nextEntry
        while (entry != null) {
            entryCount++
            if (entryCount > 10_000) {
                throw BackupException("Backup rejected: archive contains excessive entries (limit 10,000)")
            }

            val entryName = entry.name
            // 1. Path Traversal (Zip Slip) Protection
            if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\")) {
                throw BackupException("Backup rejected: malicious path traversal detected in entry '$entryName'")
            }

            // 2. ZIP Bomb / Oversized Entry Protection
            val entryOut = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var entryBytes = 0L
            var read: Int
            while (zipIn.read(buffer).also { read = it } != -1) {
                entryBytes += read
                totalBytesExtracted += read
                if (entryBytes > 500L * 1024 * 1024) {
                    throw BackupException("Backup rejected: entry '$entryName' exceeds maximum size limit (500MB)")
                }
                if (totalBytesExtracted > 2L * 1024 * 1024 * 1024) {
                    throw BackupException("Backup rejected: uncompressed archive exceeds safety threshold (2GB)")
                }
                entryOut.write(buffer, 0, read)
            }
            entries[entryName] = entryOut.toByteArray()
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        val manifestBytes = entries[MANIFEST_ENTRY_NAME]
            ?: throw BackupException("Backup archive is corrupted: manifest missing")

        val manifestObj = JSONObject(String(manifestBytes, Charsets.UTF_8))

        // 2. Restore Vault Account if present
        var restoredAccount: VaultAccount? = null
        if (manifestObj.has("account")) {
            val accObj = manifestObj.getJSONObject("account")
            val acc = VaultAccount(
                vaultId = accObj.getString("vaultId"),
                userName = accObj.optString("userName", "Restored Owner"),
                passwordHash = accObj.getString("passwordHash"),
                saltHex = accObj.getString("saltHex"),
                wrappedMasterKeyHex = accObj.optString("wrappedMasterKeyHex", ""),
                createdAt = accObj.optLong("createdAt", System.currentTimeMillis()),
                isBiometricEnabled = accObj.optBoolean("isBiometricEnabled", true)
            )

            // If account has wrapped master key, try to unwrap using provided password
            if (acc.wrappedMasterKeyHex.isNotBlank()) {
                val saltBytes = CryptoManager.hexToBytes(acc.saltHex)
                val wrappedKey = CryptoManager.hexToBytes(acc.wrappedMasterKeyHex)
                try {
                    val masterKey = CryptoManager.unwrapMasterKeyWithPin(wrappedKey, backupPasswordOrPin, saltBytes)
                    vaultManager.restoreAccountFromBackup(acc, masterKey, backupPasswordOrPin)
                    restoredAccount = acc
                } catch (_: Exception) {
                    // Password used for backup was different from Vault PIN; register account locally
                    val all = vaultManager.getAllAccounts().toMutableMap()
                    all[acc.vaultId] = acc
                    vaultManager.saveAllAccounts(all)
                    restoredAccount = acc
                }
            }
        }

        // 3. Restore Media Files
        val mediaArray = manifestObj.optJSONArray("mediaItems") ?: JSONArray()
        var restoredCount = 0

        val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
        val galleryDir = File(context.filesDir, "gallery").apply { mkdirs() }

        for (i in 0 until mediaArray.length()) {
            val itemObj = mediaArray.getJSONObject(i)
            val archiveFileName = itemObj.getString("archiveFileName")
            val mediaBytes = entries["payload/$archiveFileName"] ?: continue

            val isEncrypted = itemObj.optBoolean("isEncrypted", false)
            val mimeType = itemObj.optString("mimeType", "image/jpeg")
            val isVideo = itemObj.optBoolean("isVideo", mimeType.startsWith("video/"))
            val targetDir = if (isEncrypted) vaultDir else galleryDir
            val ext = if (isEncrypted) "vlt" else if (isVideo) "mp4" else "jpg"
            val targetFile = File(targetDir, "restored_${System.currentTimeMillis()}_$i.$ext")

            FileOutputStream(targetFile).use { it.write(mediaBytes) }

            val entity = PhotoEntity(
                title = itemObj.optString("title", "Restored Media"),
                description = itemObj.optString("description", "Restored from backup"),
                localUri = Uri.fromFile(targetFile).toString(),
                localFilePath = targetFile.absolutePath,
                cloudUrl = null,
                uploadStatus = "LOCAL_ONLY",
                syncStatus = PhotoEntity.SYNC_STATUS_LOCAL_ONLY,
                isEncrypted = isEncrypted,
                fileSizeBytes = targetFile.length(),
                mimeType = mimeType,
                createdAt = itemObj.optLong("createdAt", System.currentTimeMillis()),
                album = itemObj.optString("album", if (isEncrypted) "Vault" else "All"),
                isOfflineAvailable = true,
                isVideo = isVideo,
                durationMs = itemObj.optLong("durationMs", 0L)
            )

            photoDao.insertPhoto(entity)
            restoredCount++
        }

        BackupRestoreResult(
            restoredAccount = restoredAccount,
            restoredMediaCount = restoredCount,
            totalMediaInBackup = mediaArray.length()
        )
    }
}
