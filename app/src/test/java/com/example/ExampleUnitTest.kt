package com.example

import com.example.crypto.CryptoManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCryptoManager_encryptAndDecryptRoundtrip() {
    val originalText = "Super secret personal photo binary data payload"
    val originalBytes = originalText.toByteArray(Charsets.UTF_8)
    val pin = "1234"

    val encryptedBytes = CryptoManager.encryptBytes(originalBytes, pin)

    // Verify it is encrypted and has the header
    assertTrue(CryptoManager.isEncryptedPayload(encryptedBytes))
    assertFalse(encryptedBytes.contentEquals(originalBytes))

    // Decrypt with same PIN
    val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes, pin)
    val decryptedText = String(decryptedBytes, Charsets.UTF_8)

    assertEquals(originalText, decryptedText)
  }

  @Test
  fun testCryptoManager_wrongPinFails() {
    val originalBytes = "Secret image content".toByteArray(Charsets.UTF_8)
    val pin = "5678"
    val wrongPin = "0000"

    val encryptedBytes = CryptoManager.encryptBytes(originalBytes, pin)

    try {
      CryptoManager.decryptBytes(encryptedBytes, wrongPin)
      fail("Decryption with wrong PIN should throw exception")
    } catch (e: Exception) {
      // Expected (AEADBadTagException or GeneralSecurityException)
      assertTrue(true)
    }
  }

  @Test
  fun testCryptoManager_pinHashing() {
    val pin = "9999"
    val salt = CryptoManager.generateSaltHex()
    val hash1 = CryptoManager.hashPin(pin, salt)
    val hash2 = CryptoManager.hashPin(pin, salt)

    assertEquals(hash1, hash2)
    assertNotEquals(pin, hash1)
  }
}
