package com.example.data

data class VaultAccount(
    val vaultId: String,
    val userName: String,
    val passwordHash: String,
    val saltHex: String,
    val createdAt: Long,
    val isBiometricEnabled: Boolean = true
)
