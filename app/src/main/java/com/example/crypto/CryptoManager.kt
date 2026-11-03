package com.example.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    // 4-byte magic header "VLT1" for Vault File Format v1
    private val MAGIC_HEADER = byteArrayOf(0x56, 0x4C, 0x54, 0x31)

    private val secureRandom = SecureRandom()

    /**
     * Encrypt raw image bytes using AES-256-GCM derived from the user PIN/passphrase.
     * Output format: [4 bytes MAGIC_HEADER][16 bytes SALT][12 bytes IV][CIPHERTEXT + TAG]
     */
    fun encryptBytes(plainBytes: ByteArray, pin: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        val keySpec = deriveKey(pin, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val cipherBytes = cipher.doFinal(plainBytes)

        val out = ByteArrayOutputStream(MAGIC_HEADER.size + salt.size + iv.size + cipherBytes.size)
        out.write(MAGIC_HEADER)
        out.write(salt)
        out.write(iv)
        out.write(cipherBytes)
        return out.toByteArray()
    }

    /**
     * Decrypt encrypted payload using AES-256-GCM with the user PIN/passphrase.
     */
    fun decryptBytes(encryptedBytes: ByteArray, pin: String): ByteArray {
        require(isEncryptedPayload(encryptedBytes)) { "Invalid encrypted payload or missing header" }

        val inStream = ByteArrayInputStream(encryptedBytes)
        val magic = ByteArray(MAGIC_HEADER.size)
        inStream.read(magic)

        val salt = ByteArray(SALT_LENGTH_BYTES)
        inStream.read(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        inStream.read(iv)

        val cipherBytes = inStream.readBytes()

        val keySpec = deriveKey(pin, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(cipherBytes)
    }

    /**
     * Checks if byte array begins with the VLT1 magic header.
     */
    fun isEncryptedPayload(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_HEADER.size + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 1) return false
        for (i in MAGIC_HEADER.indices) {
            if (bytes[i] != MAGIC_HEADER[i]) return false
        }
        return true
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    /**
     * Compute a salted hash of the user PIN for verification.
     */
    fun hashPin(pin: String, saltHex: String): String {
        val salt = hexToBytes(saltHex)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, 256)
        val hash = factory.generateSecret(spec).encoded
        return bytesToHex(hash)
    }

    fun generateSaltHex(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        return bytesToHex(salt)
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
