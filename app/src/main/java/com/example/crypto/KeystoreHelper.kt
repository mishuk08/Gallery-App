package com.example.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages hardware-backed encryption keys in Android Keystore for securing
 * the Master Vault Key and enabling biometric unlock.
 */
object KeystoreHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH_BYTES = 12

    private fun isAndroidKeyStoreAvailable(): Boolean {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Gets or creates an AES-256 key inside Android Keystore with the given alias.
     */
    private fun getOrCreateSecretKey(alias: String): SecretKey? {
        if (!isAndroidKeyStoreAvailable()) return null

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                return entry?.secretKey
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Wraps (encrypts) the Master Vault Key using the Keystore key.
     * Output format: [12 bytes IV][Ciphertext + GCM Tag]
     */
    fun wrapMasterKey(alias: String, masterKey: ByteArray): ByteArray? {
        val secretKey = getOrCreateSecretKey(alias) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(masterKey)

            val out = ByteArrayOutputStream(iv.size + encrypted.size)
            out.write(iv)
            out.write(encrypted)
            out.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Unwraps (decrypts) the Master Vault Key using the Keystore key.
     */
    fun unwrapMasterKey(alias: String, wrappedBytes: ByteArray): ByteArray? {
        if (wrappedBytes.size < IV_LENGTH_BYTES + 16) return null
        val secretKey = getOrCreateSecretKey(alias) ?: return null

        return try {
            val inStream = ByteArrayInputStream(wrappedBytes)
            val iv = ByteArray(IV_LENGTH_BYTES)
            inStream.read(iv)
            val cipherBytes = inStream.readBytes()

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes the key from Android Keystore.
     */
    fun deleteKey(alias: String) {
        if (!isAndroidKeyStoreAvailable()) return
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
