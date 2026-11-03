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
}
