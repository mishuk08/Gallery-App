# Changelog

All notable changes to the Gallery & Secure Vault Application are documented in this file.

## [2.1.0] - 2026-11-04

### Critical Security Fixes
- **Atomic Vault Migration (`moveDevicePhotoToVault`)**:
  - Implemented streaming authenticated AES-256-GCM encryption with 64KB buffers.
  - Implemented mandatory pre-deletion verification: media is decrypted into a temporary buffer and validated against original byte contents before the unencrypted original is removed from MediaStore.
  - Implemented staging files (`.tmp`) that are atomically renamed only upon verification success, preventing orphaned partial files or unencrypted remnants upon unexpected termination.
  - Handled Android 10+ scoped storage restrictions (`RecoverableSecurityException` and delete failures) gracefully by preserving encrypted vault items while logging original deletion status.
- **Cryptography & Key Wrapping**:
  - Eliminated PIN-as-direct-key vulnerability. Every vault is governed by a 256-bit cryptographically secure random Master Key (`generateMasterKey()`).
  - Implemented key wrapping with PBKDF2-HMAC-SHA256 (100,000 iterations) and 128-bit cryptographically secure random salt, followed by AES-256-GCM authenticated envelope wrapping.
  - Zeroized key arrays from memory using `CryptoManager.zeroize(ByteArray)` after cryptographic operations.
  - Integrated Android Keystore hardware-backed biometric key wrapper (`AndroidKeyStore`) with secure PIN fallback.
  - Supported lossless PIN rotation: changing a 6-digit PIN unwraps the Master Key with the current PIN, derives a fresh salt and PBKDF2 key, and re-wraps the Master Key without re-encrypting underlying media files.
- **Backup & Restore Hardening**:
  - Configured `data_extraction_rules.xml` and `backup_rules.xml` to strictly exclude `vault/`, `backups/`, `gallery_database*`, and preferences (`vault_prefs.xml`, `app_settings_prefs.xml`) from Android Auto Backup and ADB transfer.
  - Mitigated Zip Slip path traversal vulnerabilities: archive entries containing `..`, absolute paths, or escaping destination roots are rejected with `BackupException`.
  - Implemented Zip bomb safeguards (limits on total uncompressed bytes, per-entry sizes, and entry counts).
- **Zero-Plaintext Cloud Architecture**:
  - Completely redesigned `CloudUploadService.kt`: removed all insecure third-party public hosting dependencies (freeimage.host, litterbox.catbox.moe) and purged hardcoded API keys.
  - Enforced local-first private storage mode: unencrypted media is never transmitted across the network.

### Performance & Architecture Upgrades
- **Streaming Encryption & Decryption**:
  - Replaced all whole-buffer in-memory allocations (`readBytes()`) in `CryptoManager` with streaming `CipherInputStream` and `CipherOutputStream` operations using 64KB buffers to prevent `OutOfMemoryError` on high-resolution photos and 4K videos.
- **Media Sorting & Video Filtering**:
  - Added `MediaSortOrder` enum supporting `DATE_NEWEST`, `DATE_OLDEST`, `NAME_AZ`, `NAME_ZA`, `SIZE_LARGEST`, and `SIZE_SMALLEST`.
  - Added "Videos" filter chip in `GalleryFilter` for instant video-only library views.
  - Integrated in-app video playback with scrubber, play/pause controls, duration formatting, and on-the-fly decryption in `PhotoDetailDialog`.
- **Album Management**:
  - Added Room DAO support for album renaming and deletion with persistent database transaction updates.

### UI & UX Polish
- Preserved signature Liquid Glassmorphic styling with glossy translucent borders and backdrop blurs.
- Maintained secret invisible gesture entry for the Vault (hold bottom-left to top-right for 7 seconds) with no external hints.
- Maintained 3-way Vault UI tabs: Create Vault, Log In, and Restore Vault (.vaultbackup archive import).
