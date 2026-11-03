package com.example.data

data class VaultAccount(
    val vaultId: String,
    val userName: String,
    val passwordHash: String,
    val saltHex: String,
    val wrappedMasterKeyHex: String = "",
    val keystoreWrappedMasterKeyHex: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isBiometricEnabled: Boolean = true
)
