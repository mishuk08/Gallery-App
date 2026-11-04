package com.example.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100000
    private const val MASTER_KEY_SIZE_BYTES = 32 // 256 bits
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    // 4-byte magic headers
    private val HEADER_VLT1 = byteArrayOf(0x56, 0x4C, 0x54, 0x31) // Legacy "VLT1"
    private val HEADER_VLT2 = byteArrayOf(0x56, 0x4C, 0x54, 0x32) // Master Key "VLT2"
    private val HEADER_VBK1 = byteArrayOf(0x56, 0x42, 0x4B, 0x31) // Backup "VBK1"

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure 256-bit Master Vault Key.
     */
    fun generateMasterKey(): ByteArray {
        val key = ByteArray(MASTER_KEY_SIZE_BYTES)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Derives an AES-256 SecretKeySpec from a PIN and salt using PBKDF2-HMAC-SHA256.
     */
    fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    /**
     * Wraps (encrypts) the Master Vault Key using a PIN-derived Key-Encryption-Key (KEK).
     * Output format: [12 bytes IV][Ciphertext + 16 bytes Tag]
     */
    fun wrapMasterKeyWithPin(masterKey: ByteArray, pin: String, salt: ByteArray): ByteArray {
        require(masterKey.size == MASTER_KEY_SIZE_BYTES) { "Invalid master key size" }
        val kek = deriveKey(pin, salt)
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(masterKey)

        val out = ByteArrayOutputStream(iv.size + cipherBytes.size)
        out.write(iv)
        out.write(cipherBytes)
        return out.toByteArray()
    }

    /**
     * Unwraps (decrypts) the Master Vault Key using the PIN-derived KEK.
     */
    fun unwrapMasterKeyWithPin(wrappedKey: ByteArray, pin: String, salt: ByteArray): ByteArray {
        if (wrappedKey.size < IV_LENGTH_BYTES + 16) {
            throw CryptoException("Corrupted or invalid wrapped key format")
        }

        try {
            val inStream = ByteArrayInputStream(wrappedKey)
            val iv = ByteArray(IV_LENGTH_BYTES)
            inStream.read(iv)
            val cipherBytes = inStream.readBytes()

            val kek = deriveKey(pin, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            return cipher.doFinal(cipherBytes)
        } catch (e: AEADBadTagException) {
            throw CryptoException("Incorrect PIN or tampered key", e)
        } catch (e: Exception) {
            throw CryptoException("Failed to unwrap master key: ${e.localizedMessage}", e)
        }
    }

    /**
     * Encrypts plain media bytes using the 256-bit Master Vault Key.
     * Output format (VLT2): [4 bytes VLT2][12 bytes IV][CIPHERTEXT + TAG]
     */
    fun encryptWithMasterKey(plainBytes: ByteArray, masterKey: ByteArray): ByteArray {
        require(masterKey.size == MASTER_KEY_SIZE_BYTES) { "Invalid master key size" }
        val keySpec = SecretKeySpec(masterKey, "AES")
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(plainBytes)

        val out = ByteArrayOutputStream(HEADER_VLT2.size + iv.size + cipherBytes.size)
        out.write(HEADER_VLT2)
        out.write(iv)
        out.write(cipherBytes)
        return out.toByteArray()
    }

    /**
     * Encrypts an InputStream directly to an OutputStream in streaming chunks.
     * Prevents OOM by avoiding loading entire video/photo files into memory.
     * Format: [4 bytes "VLT2"][12 bytes IV][CIPHERTEXT + 16 bytes GCM TAG]
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        masterKey: ByteArray
    ) {
        require(masterKey.size == MASTER_KEY_SIZE_BYTES) { "Invalid master key size" }
        val keySpec = SecretKeySpec(masterKey, "AES")
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        outputStream.write(HEADER_VLT2)
        outputStream.write(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val cipherOut = CipherOutputStream(outputStream, cipher)
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            cipherOut.write(buffer, 0, bytesRead)
        }
        cipherOut.flush()
        cipherOut.close()
    }

    /**
     * Decrypts an encrypted media stream directly to an OutputStream in streaming chunks.
     */
    fun decryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        masterKey: ByteArray,
        fallbackPin: String? = null
    ) {
        val header = ByteArray(4)
        var totalHeader = 0
        while (totalHeader < 4) {
            val r = inputStream.read(header, totalHeader, 4 - totalHeader)
            if (r == -1) break
            totalHeader += r
        }
        if (totalHeader < 4) throw CryptoException("Encrypted stream is too short")

        if (header.contentEquals(HEADER_VLT2)) {
            val iv = ByteArray(IV_LENGTH_BYTES)
            var totalIv = 0
            while (totalIv < IV_LENGTH_BYTES) {
                val r = inputStream.read(iv, totalIv, IV_LENGTH_BYTES - totalIv)
                if (r == -1) break
                totalIv += r
            }
            if (totalIv < IV_LENGTH_BYTES) throw CryptoException("Corrupted VLT2 stream: missing IV")

            val keySpec = SecretKeySpec(masterKey, "AES")
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))

            val cipherIn = CipherInputStream(inputStream, cipher)
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            try {
                while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            } catch (e: Exception) {
                throw CryptoException("Decryption authentication failed or stream corrupted", e)
            }
        } else if (header.contentEquals(HEADER_VLT1)) {
            if (fallbackPin == null) throw CryptoException("Legacy VLT1 stream requires PIN to decrypt")
            val salt = ByteArray(SALT_LENGTH_BYTES)
            var totalSalt = 0
            while (totalSalt < SALT_LENGTH_BYTES) {
                val r = inputStream.read(salt, totalSalt, SALT_LENGTH_BYTES - totalSalt)
                if (r == -1) break
                totalSalt += r
            }
            if (totalSalt < SALT_LENGTH_BYTES) throw CryptoException("Corrupted VLT1: missing salt")

            val iv = ByteArray(IV_LENGTH_BYTES)
            var totalIv = 0
            while (totalIv < IV_LENGTH_BYTES) {
                val r = inputStream.read(iv, totalIv, IV_LENGTH_BYTES - totalIv)
                if (r == -1) break
                totalIv += r
            }
            if (totalIv < IV_LENGTH_BYTES) throw CryptoException("Corrupted VLT1: missing IV")

            val keySpec = deriveKey(fallbackPin, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))

            val cipherIn = CipherInputStream(inputStream, cipher)
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            try {
                while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
            } catch (e: Exception) {
                throw CryptoException("Legacy decryption failed or corrupted stream", e)
            }
        } else {
            throw CryptoException("Unrecognized or unencrypted stream header")
        }
    }

    /**
     * Decrypts media bytes with the Master Vault Key (VLT2) or legacy PIN (VLT1).
     */
    fun decryptMedia(
        encryptedBytes: ByteArray,
        masterKey: ByteArray,
        fallbackPin: String? = null
    ): ByteArray {
        if (encryptedBytes.size < 4) {
            throw CryptoException("Encrypted payload too short")
        }

        // Check for VLT2 (Master Key Envelope Encryption)
        if (matchesHeader(encryptedBytes, HEADER_VLT2)) {
            if (encryptedBytes.size < HEADER_VLT2.size + IV_LENGTH_BYTES + 16) {
                throw CryptoException("Corrupted VLT2 payload")
            }
            try {
                val inStream = ByteArrayInputStream(encryptedBytes)
                inStream.skip(HEADER_VLT2.size.toLong())

                val iv = ByteArray(IV_LENGTH_BYTES)
                inStream.read(iv)
                val cipherBytes = inStream.readBytes()

                val keySpec = SecretKeySpec(masterKey, "AES")
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
                return cipher.doFinal(cipherBytes)
            } catch (e: AEADBadTagException) {
                throw CryptoException("Decryption authentication failed: corrupted or wrong master key", e)
            } catch (e: Exception) {
                throw CryptoException("Failed to decrypt VLT2 media: ${e.localizedMessage}", e)
            }
        }

        // Check for VLT1 (Legacy per-file PIN encryption)
        if (matchesHeader(encryptedBytes, HEADER_VLT1)) {
            if (fallbackPin == null) {
                throw CryptoException("Legacy VLT1 file requires PIN to decrypt")
            }
            return decryptLegacyVlt1(encryptedBytes, fallbackPin)
        }

        throw CryptoException("Unknown or unencrypted file format")
    }

    /**
     * Legacy VLT1 decryption for backward compatibility.
     * Format: [4 bytes VLT1][16 bytes SALT][12 bytes IV][CIPHERTEXT + TAG]
     */
    fun decryptLegacyVlt1(encryptedBytes: ByteArray, pin: String): ByteArray {
        val minSize = HEADER_VLT1.size + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16
        if (encryptedBytes.size < minSize) {
            throw CryptoException("Corrupted legacy VLT1 payload")
        }

        try {
            val inStream = ByteArrayInputStream(encryptedBytes)
            inStream.skip(HEADER_VLT1.size.toLong())

            val salt = ByteArray(SALT_LENGTH_BYTES)
            inStream.read(salt)

            val iv = ByteArray(IV_LENGTH_BYTES)
            inStream.read(iv)

            val cipherBytes = inStream.readBytes()

            val keySpec = deriveKey(pin, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            return cipher.doFinal(cipherBytes)
        } catch (e: AEADBadTagException) {
            throw CryptoException("Incorrect PIN for legacy VLT1 file", e)
        } catch (e: Exception) {
            throw CryptoException("Legacy decryption failed: ${e.localizedMessage}", e)
        }
    }

    /**
     * Encrypts an entire backup archive (JSON/binary) using AES-256-GCM.
     * Format (VBK1): [4 bytes VBK1][16 bytes SALT][12 bytes IV][CIPHERTEXT + TAG]
     */
    fun encryptBackupArchive(plainData: ByteArray, backupPasswordOrPin: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        val keySpec = deriveKey(backupPasswordOrPin, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(plainData)

        val out = ByteArrayOutputStream(HEADER_VBK1.size + salt.size + iv.size + cipherBytes.size)
        out.write(HEADER_VBK1)
        out.write(salt)
        out.write(iv)
        out.write(cipherBytes)
        return out.toByteArray()
    }

    /**
     * Decrypts an encrypted backup archive (.vaultbackup).
     */
    fun decryptBackupArchive(encryptedData: ByteArray, backupPasswordOrPin: String): ByteArray {
        val minSize = HEADER_VBK1.size + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16
        if (encryptedData.size < minSize || !matchesHeader(encryptedData, HEADER_VBK1)) {
            throw CryptoException("Invalid or unsupported backup file format")
        }

        try {
            val inStream = ByteArrayInputStream(encryptedData)
            inStream.skip(HEADER_VBK1.size.toLong())

            val salt = ByteArray(SALT_LENGTH_BYTES)
            inStream.read(salt)

            val iv = ByteArray(IV_LENGTH_BYTES)
            inStream.read(iv)

            val cipherBytes = inStream.readBytes()

            val keySpec = deriveKey(backupPasswordOrPin, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            return cipher.doFinal(cipherBytes)
        } catch (e: AEADBadTagException) {
            throw CryptoException("Incorrect backup password or corrupted backup file", e)
        } catch (e: Exception) {
            throw CryptoException("Failed to restore backup: ${e.localizedMessage}", e)
        }
    }

    /**
     * Checks if byte array begins with VLT1 or VLT2 header.
     */
    fun isEncryptedPayload(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return matchesHeader(bytes, HEADER_VLT2) || matchesHeader(bytes, HEADER_VLT1)
    }

    /**
     * Checks if byte array begins with VBK1 backup header.
     */
    fun isBackupPayload(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return matchesHeader(bytes, HEADER_VBK1)
    }

    private fun matchesHeader(bytes: ByteArray, header: ByteArray): Boolean {
        if (bytes.size < header.size) return false
        for (i in header.indices) {
            if (bytes[i] != header[i]) return false
        }
        return true
    }

    /**
     * Derives a salted hash for verification using PBKDF2-HMAC-SHA256 with 100,000 iterations.
     */
    fun hashPin(pin: String, saltHex: String): String {
        val salt = hexToBytes(saltHex)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val hash = factory.generateSecret(spec).encoded
        return bytesToHex(hash)
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    fun verifyPin(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        return try {
            val computedHash = hashPin(pin, saltHex)
            MessageDigest.isEqual(
                computedHash.toByteArray(Charsets.UTF_8),
                expectedHashHex.toByteArray(Charsets.UTF_8)
            )
        } catch (_: Exception) {
            false
        }
    }

    fun generateSaltHex(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        return bytesToHex(salt)
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
