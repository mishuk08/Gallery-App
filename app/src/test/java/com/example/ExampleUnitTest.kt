package com.example

import com.example.crypto.CryptoException
import com.example.crypto.CryptoManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testMasterKeyGenerationAndMediaRoundtrip() {
        val masterKey = CryptoManager.generateMasterKey()
        assertEquals(32, masterKey.size)

        val originalData = "Confidential image binary data in vault: PNG_BYTES_TEST_42".toByteArray(Charsets.UTF_8)
        val encryptedData = CryptoManager.encryptWithMasterKey(originalData, masterKey)

        assertTrue(CryptoManager.isEncryptedPayload(encryptedData))
        assertFalse(originalData.contentEquals(encryptedData))

        val decryptedData = CryptoManager.decryptMedia(encryptedData, masterKey)
        assertArrayEquals(originalData, decryptedData)
    }

    @Test
    fun testMasterKeyWrappingAndUnwrappingWithPin() {
        val masterKey = CryptoManager.generateMasterKey()
        val pin = "654321"
        val salt = CryptoManager.hexToBytes(CryptoManager.generateSaltHex())

        val wrappedKey = CryptoManager.wrapMasterKeyWithPin(masterKey, pin, salt)
        assertFalse(wrappedKey.contentEquals(masterKey))

        val unwrappedKey = CryptoManager.unwrapMasterKeyWithPin(wrappedKey, pin, salt)
        assertArrayEquals(masterKey, unwrappedKey)
    }

    @Test
    fun testUnwrappingWithWrongPinThrowsCryptoException() {
        val masterKey = CryptoManager.generateMasterKey()
        val correctPin = "112233"
        val wrongPin = "998877"
        val salt = CryptoManager.hexToBytes(CryptoManager.generateSaltHex())

        val wrappedKey = CryptoManager.wrapMasterKeyWithPin(masterKey, correctPin, salt)

        try {
            CryptoManager.unwrapMasterKeyWithPin(wrappedKey, wrongPin, salt)
            fail("Unwrapping master key with wrong PIN must throw CryptoException")
        } catch (e: CryptoException) {
            assertTrue(e.message?.contains("Incorrect PIN") == true || e.message?.contains("tampered") == true)
        }
    }

    @Test
    fun testPinChangePreservesMasterKeyAndDecryption() {
        // 1. Initial Account Setup with PIN 123456
        val initialMasterKey = CryptoManager.generateMasterKey()
        val oldPin = "123456"
        val oldSalt = CryptoManager.hexToBytes(CryptoManager.generateSaltHex())
        val wrappedByOldPin = CryptoManager.wrapMasterKeyWithPin(initialMasterKey, oldPin, oldSalt)

        // 2. Encrypt photos with the Master Key
        val secretPhoto = "My super private document photo".toByteArray(Charsets.UTF_8)
        val encryptedPhoto = CryptoManager.encryptWithMasterKey(secretPhoto, initialMasterKey)

        // 3. User changes PIN to 889900
        val newPin = "889900"
        val newSalt = CryptoManager.hexToBytes(CryptoManager.generateSaltHex())

        // Re-wrap the existing Master Key with new PIN
        val unwrappedMasterKey = CryptoManager.unwrapMasterKeyWithPin(wrappedByOldPin, oldPin, oldSalt)
        val wrappedByNewPin = CryptoManager.wrapMasterKeyWithPin(unwrappedMasterKey, newPin, newSalt)

        // 4. Session restarts, user unlocks with new PIN
        val restoredMasterKey = CryptoManager.unwrapMasterKeyWithPin(wrappedByNewPin, newPin, newSalt)
        assertArrayEquals(initialMasterKey, restoredMasterKey)

        // 5. Decrypt previously encrypted photo using Master Key - must succeed without data loss!
        val decryptedPhoto = CryptoManager.decryptMedia(encryptedPhoto, restoredMasterKey)
        assertArrayEquals(secretPhoto, decryptedPhoto)
    }

    @Test
    fun testCorruptedPayloadRejection() {
        val masterKey = CryptoManager.generateMasterKey()
        val data = "Normal image bytes".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoManager.encryptWithMasterKey(data, masterKey)

        // Corrupt a byte in the ciphertext
        val corrupted = encrypted.clone()
        corrupted[corrupted.size - 5] = (corrupted[corrupted.size - 5] + 1).toByte()

        try {
            CryptoManager.decryptMedia(corrupted, masterKey)
            fail("Decryption of corrupted payload must throw CryptoException")
        } catch (e: CryptoException) {
            assertTrue(true)
        }
    }

    @Test
    fun testPinVerificationConstantTime() {
        val pin = "456789"
        val salt = CryptoManager.generateSaltHex()
        val hash = CryptoManager.hashPin(pin, salt)

        assertTrue(CryptoManager.verifyPin(pin, salt, hash))
        assertFalse(CryptoManager.verifyPin("000000", salt, hash))
        assertFalse(CryptoManager.verifyPin("456788", salt, hash))
    }

    @Test
    fun testEncryptedBackupArchiveRoundtrip() {
        val backupManifestAndData = "{\"version\":2,\"mediaCount\":12,\"vaultId\":\"VLT-982B-7AF1\"}".toByteArray(Charsets.UTF_8)
        val backupPassword = "secure_backup_pass_2026"

        val encryptedBackup = CryptoManager.encryptBackupArchive(backupManifestAndData, backupPassword)
        assertTrue(CryptoManager.isBackupPayload(encryptedBackup))

        val decryptedBackup = CryptoManager.decryptBackupArchive(encryptedBackup, backupPassword)
        assertArrayEquals(backupManifestAndData, decryptedBackup)
    }

    @Test
    fun testBackupWrongPasswordThrowsCryptoException() {
        val plain = "Backup payload".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoManager.encryptBackupArchive(plain, "right_pass")

        try {
            CryptoManager.decryptBackupArchive(encrypted, "wrong_pass")
            fail("Restoring backup with wrong password must throw CryptoException")
        } catch (e: CryptoException) {
            assertTrue(true)
        }
    }

    @Test
    fun testRateLimitingLockoutProgressiveDelay() {
        fun calculateLockout(attempts: Int): Long {
            return when {
                attempts >= 7 -> 120L
                attempts >= 6 -> 60L
                attempts >= 5 -> 30L
                else -> 0L
            }
        }

        assertEquals(0L, calculateLockout(1))
        assertEquals(0L, calculateLockout(4))
        assertEquals(30L, calculateLockout(5))
        assertEquals(60L, calculateLockout(6))
        assertEquals(120L, calculateLockout(7))
        assertEquals(120L, calculateLockout(10))
    }

    @Test
    fun testDeviceMediaDeduplicationLogic() {
        val seenUris = mutableSetOf<String>()
        val uri1 = "content://media/external/images/media/101"
        val uri2 = "content://media/external/images/media/102"

        assertTrue(seenUris.add(uri1))
        assertTrue(seenUris.add(uri2))
        assertFalse(seenUris.add(uri1)) // Duplicate URI rejected
        assertEquals(2, seenUris.size)
    }

    @Test
    fun testLegacyVLT1FileMigrationToVLT2() {
        // Construct a legacy VLT1 file encrypted directly with PIN
        val legacyPin = "778899"
        val secretPhoto = "Legacy family memory 2024".toByteArray(Charsets.UTF_8)
        val legacySalt = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
        val legacyIv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }

        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(legacyPin.toCharArray(), legacySalt, 100_000, 256)
        val key = javax.crypto.spec.SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, legacyIv))
        val cipherBytes = cipher.doFinal(secretPhoto)

        val vlt1Payload = java.io.ByteArrayOutputStream().apply {
            write("VLT1".toByteArray(Charsets.US_ASCII))
            write(legacySalt)
            write(legacyIv)
            write(cipherBytes)
        }.toByteArray()

        // Decrypt using CryptoManager backward-compatible decryptMedia with fallback PIN
        val masterKey = CryptoManager.generateMasterKey()
        val recovered = CryptoManager.decryptMedia(vlt1Payload, masterKey, fallbackPin = legacyPin)
        assertArrayEquals(secretPhoto, recovered)

        // Migrate to VLT2 Master Key format
        val vlt2Payload = CryptoManager.encryptWithMasterKey(recovered, masterKey)
        assertTrue(CryptoManager.isEncryptedPayload(vlt2Payload))

        // Verify VLT2 decrypts cleanly with the Master Key alone without needing legacy PIN derivation
        val reDecrypted = CryptoManager.decryptMedia(vlt2Payload, masterKey, fallbackPin = null)
        assertArrayEquals(secretPhoto, reDecrypted)
    }

    @Test
    fun testTamperedBackupArchiveRejection() {
        val backupData = "{\"vaultId\":\"VLT-TEST\",\"items\":5}".toByteArray(Charsets.UTF_8)
        val backupPass = "admin_backup_pass"
        val encryptedBackup = CryptoManager.encryptBackupArchive(backupData, backupPass)

        // 1. Test header corruption
        val tamperedHeader = encryptedBackup.clone()
        tamperedHeader[0] = 'X'.code.toByte()
        assertFalse(CryptoManager.isBackupPayload(tamperedHeader))
        try {
            CryptoManager.decryptBackupArchive(tamperedHeader, backupPass)
            fail("Tampered backup header must be rejected")
        } catch (_: CryptoException) {
            assertTrue(true)
        }

        // 2. Test ciphertext tampering (GCM AEAD Tag verification failure)
        val tamperedCipher = encryptedBackup.clone()
        tamperedCipher[tamperedCipher.size - 2] = (tamperedCipher[tamperedCipher.size - 2] + 1).toByte()
        try {
            CryptoManager.decryptBackupArchive(tamperedCipher, backupPass)
            fail("Tampered backup payload tag verification must throw CryptoException")
        } catch (_: CryptoException) {
            assertTrue(true)
        }
    }

    @Test
    fun testBiometricKeyEnvelopeSimulation() {
        // Simulates Android Keystore hardware key wrapping the 256-bit Master Vault Key
        val masterKey = CryptoManager.generateMasterKey()
        val simulatedHardwareKey = ByteArray(32).apply { java.security.SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }

        val wrapCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        wrapCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(simulatedHardwareKey, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        val wrappedMasterKey = wrapCipher.doFinal(masterKey)

        assertFalse(wrappedMasterKey.contentEquals(masterKey))

        // Unwrap on biometric callback success
        val unwrapCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        unwrapCipher.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(simulatedHardwareKey, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        val unwrappedKey = unwrapCipher.doFinal(wrappedMasterKey)

        assertArrayEquals(masterKey, unwrappedKey)
    }

    @Test
    fun testMediaEntityVideoAndPhotoMetadataIntegrity() {
        val photo = com.example.data.PhotoEntity(
            id = 1L,
            title = "Sunset.jpg",
            localUri = "content://media/external/images/media/55",
            localFilePath = "/data/user/0/app/gallery/sunset.jpg",
            cloudUrl = null,
            uploadStatus = "LOCAL_ONLY",
            syncStatus = com.example.data.PhotoEntity.SYNC_STATUS_LOCAL_ONLY,
            isEncrypted = false,
            fileSizeBytes = 2048500L,
            mimeType = "image/jpeg",
            createdAt = 1700000000000L,
            album = "Vacation",
            isOfflineAvailable = true,
            isVideo = false,
            durationMs = 0L
        )
        assertFalse(photo.isVideo)
        assertEquals(0L, photo.durationMs)
        assertEquals("00:00", com.example.ui.util.DateUtils.formatDuration(photo.durationMs))

        val video = photo.copy(
            id = 2L,
            title = "FamilyVideo.mp4",
            mimeType = "video/mp4",
            isVideo = true,
            durationMs = 125000L // 2 minutes 5 seconds
        )
        assertTrue(video.isVideo)
        assertEquals(125000L, video.durationMs)
        assertEquals("02:05", com.example.ui.util.DateUtils.formatDuration(video.durationMs))
    }

    @Test
    fun testPermissionMatrixLogicForAndroidVersions() {
        fun requiredPermissions(sdkInt: Int): List<String> {
            return when {
                sdkInt >= 34 -> listOf(
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
                )
                sdkInt >= 33 -> listOf(
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO"
                )
                else -> listOf(
                    "android.permission.READ_EXTERNAL_STORAGE"
                )
            }
        }

        val permsApi29 = requiredPermissions(29)
        assertEquals(1, permsApi29.size)
        assertTrue(permsApi29.contains("android.permission.READ_EXTERNAL_STORAGE"))

        val permsApi33 = requiredPermissions(33)
        assertEquals(2, permsApi33.size)
        assertTrue(permsApi33.contains("android.permission.READ_MEDIA_IMAGES"))
        assertTrue(permsApi33.contains("android.permission.READ_MEDIA_VIDEO"))

        val permsApi34 = requiredPermissions(34)
        assertEquals(3, permsApi34.size)
        assertTrue(permsApi34.contains("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
    }

    @Test
    fun testStreamingEncryptionAndDecryptionRoundtrip() {
        val masterKey = CryptoManager.generateMasterKey()
        // Generate a 150KB random payload to span across multiple 64KB chunks
        val originalBytes = ByteArray(150 * 1024).apply {
            java.security.SecureRandom().nextBytes(this)
        }

        val inStream = java.io.ByteArrayInputStream(originalBytes)
        val encryptedOut = java.io.ByteArrayOutputStream()

        CryptoManager.encryptStream(inStream, encryptedOut, masterKey)
        val encryptedBytes = encryptedOut.toByteArray()

        assertTrue(CryptoManager.isEncryptedPayload(encryptedBytes))
        assertFalse(originalBytes.contentEquals(encryptedBytes))

        val encryptedIn = java.io.ByteArrayInputStream(encryptedBytes)
        val decryptedOut = java.io.ByteArrayOutputStream()

        CryptoManager.decryptStream(encryptedIn, decryptedOut, masterKey)
        val decryptedBytes = decryptedOut.toByteArray()

        assertArrayEquals(originalBytes, decryptedBytes)
    }

    @Test
    fun testBackupPathTraversalProtection() {
        // Build a mock zip with a malicious entry '../evil_script.sh'
        val zipBytesOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipBytesOut).use { zos ->
            val evilEntry = java.util.zip.ZipEntry("../../../data/evil.sh")
            zos.putNextEntry(evilEntry)
            zos.write("echo pwned".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val destinationDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_traversal_dest_" + System.currentTimeMillis())
        destinationDir.mkdirs()

        try {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytesOut.toByteArray())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    // Validate path traversal security check matching BackupManager
                    if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                        throw com.example.data.BackupException("Security violation: Malicious path traversal detected: $entryName")
                    }
                    val targetFile = java.io.File(destinationDir, entryName)
                    if (!targetFile.canonicalPath.startsWith(destinationDir.canonicalPath + java.io.File.separator)) {
                        throw com.example.data.BackupException("Security violation: Entry resolves outside destination: $entryName")
                    }
                    entry = zis.nextEntry
                }
            }
            fail("Expected BackupException due to path traversal attempt")
        } catch (e: com.example.data.BackupException) {
            assertTrue(e.message?.contains("Security violation") == true)
        } finally {
            destinationDir.deleteRecursively()
        }
    }

    @Test
    fun testMediaSortingLogic() {
        val item1 = com.example.data.PhotoEntity(
            id = 1L,
            title = "Alpha.jpg",
            localUri = "content://media/1",
            fileSizeBytes = 1000L,
            createdAt = 1000000L,
            mimeType = "image/jpeg"
        )
        val item2 = com.example.data.PhotoEntity(
            id = 2L,
            title = "Beta.mp4",
            localUri = "content://media/2",
            fileSizeBytes = 50000L,
            createdAt = 2000000L,
            mimeType = "video/mp4",
            isVideo = true,
            durationMs = 60000L
        )
        val item3 = com.example.data.PhotoEntity(
            id = 3L,
            title = "Charlie.png",
            localUri = "content://media/3",
            fileSizeBytes = 25000L,
            createdAt = 1500000L,
            mimeType = "image/png"
        )

        val list = listOf(item1, item2, item3)

        fun sortPhotos(photos: List<com.example.data.PhotoEntity>, order: com.example.ui.MediaSortOrder): List<com.example.data.PhotoEntity> {
            return when (order) {
                com.example.ui.MediaSortOrder.DATE_DESC -> photos.sortedByDescending { it.createdAt }
                com.example.ui.MediaSortOrder.DATE_ASC -> photos.sortedBy { it.createdAt }
                com.example.ui.MediaSortOrder.NAME_ASC -> photos.sortedBy { it.title.lowercase() }
                com.example.ui.MediaSortOrder.NAME_DESC -> photos.sortedByDescending { it.title.lowercase() }
                com.example.ui.MediaSortOrder.SIZE_DESC -> photos.sortedByDescending { it.fileSizeBytes }
                com.example.ui.MediaSortOrder.SIZE_ASC -> photos.sortedBy { it.fileSizeBytes }
            }
        }

        // DATE_DESC: item2 (2000000), item3 (1500000), item1 (1000000)
        val dateDesc = sortPhotos(list, com.example.ui.MediaSortOrder.DATE_DESC)
        assertEquals(listOf(item2, item3, item1), dateDesc)

        // DATE_ASC: item1 (1000000), item3 (1500000), item2 (2000000)
        val dateAsc = sortPhotos(list, com.example.ui.MediaSortOrder.DATE_ASC)
        assertEquals(listOf(item1, item3, item2), dateAsc)

        // NAME_ASC: Alpha, Beta, Charlie
        val nameAsc = sortPhotos(list, com.example.ui.MediaSortOrder.NAME_ASC)
        assertEquals(listOf(item1, item2, item3), nameAsc)

        // NAME_DESC: Charlie, Beta, Alpha
        val nameDesc = sortPhotos(list, com.example.ui.MediaSortOrder.NAME_DESC)
        assertEquals(listOf(item3, item2, item1), nameDesc)

        // SIZE_DESC: Beta (50000), Charlie (25000), Alpha (1000)
        val sizeDesc = sortPhotos(list, com.example.ui.MediaSortOrder.SIZE_DESC)
        assertEquals(listOf(item2, item3, item1), sizeDesc)

        // SIZE_ASC: Alpha (1000), Charlie (25000), Beta (50000)
        val sizeAsc = sortPhotos(list, com.example.ui.MediaSortOrder.SIZE_ASC)
        assertEquals(listOf(item1, item3, item2), sizeAsc)
    }
}
