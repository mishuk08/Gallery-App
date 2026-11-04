# Security Policy & Cryptographic Architecture

## Overview
This document outlines the security architecture, threat model, cryptographic primitives, and implementation standards for the Gallery & Secure Vault Application.

---

## 1. Threat Model & Security Objectives

| Threat | Mitigation Mechanism |
|---|---|
| **Unauthorized Vault Inspection** | Files are encrypted at rest using AES-256-GCM authenticated encryption. Raw files in app storage cannot be read without unsealing the Master Key. |
| **PIN Guessing / Brute-Force** | Progressive exponential rate-limiting delays after repeated incorrect attempts (30s at 5 attempts, 60s at 6, 120s at 7+). Constant-time verification prevents timing attacks. |
| **Direct PIN-as-Key Compromise** | The PIN is never used as an encryption key. It only derives a key-wrapping key via PBKDF2-HMAC-SHA256 (100,000 iterations + 128-bit secure random salt) to unwrap a random 256-bit Master Key. |
| **Lossless PIN Rotation** | Changing the PIN re-wraps the Master Key with the newly derived key without touching or corrupting underlying media files. |
| **Memory Dump / Cold Boot** | Sensitive key material in byte arrays is proactively zeroized (`CryptoManager.zeroize()`) when out of scope. |
| **Cloud Plaintext Leakage** | All unauthenticated public image hosts and third-party upload services have been permanently removed. Media remains strictly local-first and encrypted. |
| **Malicious Backup Restoration (Zip Slip)** | Archive entries are validated against canonical directory boundaries and forbidden substrings (`..`, leading `/`, `\`) to prevent arbitrary file overwrites. |
| **Android Auto Backup / ADB Extraction** | `data_extraction_rules.xml` and `backup_rules.xml` exclude `vault/`, `backups/`, databases, and sensitive shared preferences from cloud backups and device extraction. |

---

## 2. Cryptographic Specifications

### File Format: VLT2 (Current Standard)
All encrypted media files in `/vault` conform to the binary specification:
```
+---------------+----------------+----------------+-------------------------------+
| Magic Header  | Salt (PBKDF2)  | IV / Nonce     | Ciphertext + GCM Tag (16B)   |
| 4 Bytes: VLT2 | 16 Bytes       | 12 Bytes       | N Bytes                       |
+---------------+----------------+----------------+-------------------------------+
```

- **Algorithm**: `AES/GCM/NoPadding`
- **Key Length**: 256 bits (32 bytes)
- **Tag Length**: 128 bits (16 bytes authenticated tag)
- **IV / Nonce**: 96 bits (12 bytes) generated via `java.security.SecureRandom` per encryption operation. Nonces are never reused.
- **Key Derivation**: `PBKDF2WithHmacSHA256` with 100,000 iterations and 16 bytes of cryptographically random salt.

### Master Key Envelope & Keystore Binding
1. **Master Key**: Generated using `SecureRandom.nextBytes(32)`.
2. **Biometric Envelope**: Wrapped using Android Keystore `AES/GCM/NoPadding` hardware-backed key (`AndroidKeyStore`), unlocked via `BiometricPrompt` with `CryptoObject`.
3. **PIN Envelope**: Wrapped using PBKDF2-HMAC-SHA256 derived key + AES-256-GCM envelope stored in encrypted SharedPreferences.
4. **Zeroization**: Arrays containing key bytes are cleared with zeroes immediately after wrap/unwrap operations.

---

## 3. Atomic Vault Migration Protocol (`moveDevicePhotoToVault`)

To guarantee data safety and prevent loss during crashes or permission denials, migrations execute in 5 atomic phases:

1. **Stage to Temporary File**:
   Content is read from Android `MediaStore` via `ContentResolver` and streamed directly into `vault/<uuid>.tmp` using `CryptoManager.encryptStream()`.
2. **Pre-Deletion Integrity Verification**:
   The newly written encrypted `.tmp` file is test-decrypted through `CryptoManager.decryptStream()` to verify AEAD authentication tags and payload integrity.
3. **Atomic Commit**:
   Upon verification success, `<uuid>.tmp` is atomically renamed to `<uuid>.enc`.
4. **Database Record Persistence**:
   A corresponding `PhotoEntity` is saved to Room DB (`isEncrypted = true`).
5. **Safe MediaStore Removal**:
   The unencrypted original is removed via `ContentResolver.delete()`. If the user is on Android 10+ and scoped storage permissions prevent silent deletion, the operation succeeds gracefully with the vault record intact and an informational notification.

---

## 4. Backup & Archive Security

Exported `.vaultbackup` archives conform to the `BAK1` format:
- Encrypted with AES-256-GCM using a user-specified backup passphrase and 100,000-iteration PBKDF2 salt.
- Zip Slip and Zip Bomb protections:
  - Canonical path resolution must remain within the extraction directory.
  - Total uncompressed size capped at 2 GB.
  - Per-entry uncompressed size capped at 500 MB.
  - Total entry count capped at 10,000 files.
