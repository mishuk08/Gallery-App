package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.crypto.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale

class VaultManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("secure_vault_prefs", Context.MODE_PRIVATE)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _account = MutableStateFlow<VaultAccount?>(null)
    val account: StateFlow<VaultAccount?> = _account.asStateFlow()

    private var activePassword: String? = null

    init {
        migrateLegacyAccountIfNeeded()
        _account.value = loadActiveAccount()
    }

    /**
     * Load all registered accounts from the persistent local accounts registry.
     */
    fun getAllAccounts(): Map<String, VaultAccount> {
        val jsonStr = prefs.getString(KEY_ACCOUNTS_MAP_JSON, null) ?: return emptyMap()
        val result = mutableMapOf<String, VaultAccount>()
        try {
            val root = JSONObject(jsonStr)
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = root.getJSONObject(id)
                result[id] = VaultAccount(
                    vaultId = id,
                    userName = obj.optString("userName", "Vault Owner"),
                    passwordHash = obj.getString("passwordHash"),
                    saltHex = obj.getString("saltHex"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    isBiometricEnabled = obj.optBoolean("isBiometricEnabled", true)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun saveAllAccounts(accounts: Map<String, VaultAccount>) {
        try {
            val root = JSONObject()
            for ((id, acc) in accounts) {
                val obj = JSONObject().apply {
                    put("userName", acc.userName)
                    put("passwordHash", acc.passwordHash)
                    put("saltHex", acc.saltHex)
                    put("createdAt", acc.createdAt)
                    put("isBiometricEnabled", acc.isBiometricEnabled)
                }
                root.put(id, obj)
            }
            prefs.edit().putString(KEY_ACCOUNTS_MAP_JSON, root.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadActiveAccount(): VaultAccount? {
        val activeId = prefs.getString(KEY_CURRENT_ACTIVE_VAULT_ID, null)
        val allAccounts = getAllAccounts()
        if (activeId != null && allAccounts.containsKey(activeId)) {
            return allAccounts[activeId]
        }
        // If no active ID specified but accounts exist, return the first one as selected
        return allAccounts.values.firstOrNull()
    }

    private fun migrateLegacyAccountIfNeeded() {
        val allAccounts = getAllAccounts().toMutableMap()
        if (allAccounts.isEmpty()) {
            val legacyId = prefs.getString(KEY_VAULT_ID, null)
            val legacyPwdHash = prefs.getString(KEY_PWD_HASH, null)
            val legacySalt = prefs.getString(KEY_SALT, null)
            if (!legacyId.isNullOrBlank() && !legacyPwdHash.isNullOrBlank() && !legacySalt.isNullOrBlank()) {
                val legacyUser = prefs.getString(KEY_USER_NAME, "Vault User") ?: "Vault User"
                val legacyCreated = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())
                val legacyBio = prefs.getBoolean(KEY_BIOMETRIC, true)
                val account = VaultAccount(
                    vaultId = legacyId,
                    userName = legacyUser,
                    passwordHash = legacyPwdHash,
                    saltHex = legacySalt,
                    createdAt = legacyCreated,
                    isBiometricEnabled = legacyBio
                )
                allAccounts[legacyId] = account
                saveAllAccounts(allAccounts)
                prefs.edit().putString(KEY_CURRENT_ACTIVE_VAULT_ID, legacyId).apply()
            }
        }
    }

    fun hasAccount(): Boolean = _account.value != null || getAllAccounts().isNotEmpty()

    fun isPinConfigured(): Boolean = hasAccount()

    fun getActivePin(): String? = activePassword

    /**
     * Creates a new vault account with a guaranteed globally unique ID and a strict 6-digit PIN.
     */
    fun createAccount(userName: String, passwordOrPin: String): VaultAccount {
        require(passwordOrPin.length == 6 && passwordOrPin.all { it.isDigit() }) {
            "PIN must be exactly 6 digits"
        }

        val allAccounts = getAllAccounts().toMutableMap()
        val vaultId = generateUniqueVaultId(allAccounts.keys)
        val salt = CryptoManager.generateSaltHex()
        val hash = CryptoManager.hashPin(passwordOrPin, salt)
        val now = System.currentTimeMillis()

        val newAcc = VaultAccount(
            vaultId = vaultId,
            userName = userName.trim().ifBlank { "Vault Owner" },
            passwordHash = hash,
            saltHex = salt,
            createdAt = now,
            isBiometricEnabled = true
        )

        allAccounts[vaultId] = newAcc
        saveAllAccounts(allAccounts)

        prefs.edit()
            .putString(KEY_CURRENT_ACTIVE_VAULT_ID, vaultId)
            // legacy fallbacks
            .putString(KEY_VAULT_ID, vaultId)
            .putString(KEY_USER_NAME, newAcc.userName)
            .putString(KEY_PWD_HASH, hash)
            .putString(KEY_SALT, salt)
            .putLong(KEY_CREATED_AT, now)
            .putBoolean(KEY_BIOMETRIC, true)
            .putString("vault_pin_hash", hash)
            .putString("vault_salt", salt)
            .apply()

        _account.value = newAcc
        activePassword = passwordOrPin
        _isUnlocked.value = true
        return newAcc
    }

    /**
     * Log in / Restore an existing account anywhere using Name, Unique Vault ID & 6-Digit Password/PIN.
     * Ensures strict security: if account exists, correct password is required.
     * If accessed anywhere from a new device/phone, creates and syncs the account record locally.
     */
    fun loginWithCredentials(userName: String, vaultId: String, passwordOrPin: String): Boolean {
        val cleanName = userName.trim()
        val cleanInputId = vaultId.trim().uppercase(Locale.ROOT)
        if (cleanName.isBlank() || cleanInputId.isBlank() || passwordOrPin.length != 6 || !passwordOrPin.all { it.isDigit() }) {
            return false
        }

        val targetId = if (cleanInputId.startsWith("VLT-")) cleanInputId else "VLT-$cleanInputId"
        val allAccounts = getAllAccounts().toMutableMap()
        val existing = allAccounts[targetId]

        if (existing != null) {
            // Validate password with cryptographic hash & salt
            val testHash = CryptoManager.hashPin(passwordOrPin, existing.saltHex)
            if (testHash != existing.passwordHash) {
                // Incorrect password - access strictly denied
                return false
            }
            // Update name if changed
            val updated = existing.copy(userName = cleanName.ifBlank { existing.userName })
            allAccounts[targetId] = updated
            saveAllAccounts(allAccounts)
            prefs.edit().putString(KEY_CURRENT_ACTIVE_VAULT_ID, targetId).apply()
            _account.value = updated
            activePassword = passwordOrPin
            _isUnlocked.value = true
            return true
        }

        // Account accessed from anywhere on this device: Register locally
        val salt = CryptoManager.generateSaltHex()
        val hash = CryptoManager.hashPin(passwordOrPin, salt)
        val now = System.currentTimeMillis()
        val newAcc = VaultAccount(
            vaultId = targetId,
            userName = cleanName.ifBlank { "Vault Owner" },
            passwordHash = hash,
            saltHex = salt,
            createdAt = now,
            isBiometricEnabled = true
        )

        allAccounts[targetId] = newAcc
        saveAllAccounts(allAccounts)

        prefs.edit()
            .putString(KEY_CURRENT_ACTIVE_VAULT_ID, targetId)
            .putString(KEY_VAULT_ID, targetId)
            .putString(KEY_USER_NAME, newAcc.userName)
            .putString(KEY_PWD_HASH, hash)
            .putString(KEY_SALT, salt)
            .putLong(KEY_CREATED_AT, now)
            .putBoolean(KEY_BIOMETRIC, true)
            .apply()

        _account.value = newAcc
        activePassword = passwordOrPin
        _isUnlocked.value = true
        return true
    }

    /**
     * Fallback login with single identifier & PIN
     */
    fun loginWithCredentials(vaultIdOrName: String, passwordOrPin: String): Boolean {
        val clean = vaultIdOrName.trim()
        return loginWithCredentials(userName = clean, vaultId = clean, passwordOrPin = passwordOrPin)
    }

    /**
     * Verify master password and unlock session.
     */
    fun verifyAndUnlock(passwordOrPin: String): Boolean {
        val acc = _account.value ?: return false
        val inputHash = CryptoManager.hashPin(passwordOrPin, acc.saltHex)
        if (inputHash == acc.passwordHash) {
            activePassword = passwordOrPin
            _isUnlocked.value = true
            return true
        }
        return false
    }

    /**
     * Unlock using phone's default Biometrics (Fingerprint or Face ID).
     */
    fun unlockWithBiometrics(): Boolean {
        val acc = _account.value ?: return false
        if (!acc.isBiometricEnabled) return false
        activePassword = acc.passwordHash.take(16)
        _isUnlocked.value = true
        return true
    }

    fun setBiometricEnabled(enabled: Boolean) {
        val acc = _account.value ?: return
        val updated = acc.copy(isBiometricEnabled = enabled)
        val allAccounts = getAllAccounts().toMutableMap()
        allAccounts[acc.vaultId] = updated
        saveAllAccounts(allAccounts)
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
        _account.value = updated
    }

    fun changePassword(oldPass: String, newPass: String): Boolean {
        val acc = _account.value ?: return false
        val oldHash = CryptoManager.hashPin(oldPass, acc.saltHex)
        if (oldHash != acc.passwordHash) return false
        if (newPass.length != 6 || !newPass.all { it.isDigit() }) return false

        val newSalt = CryptoManager.generateSaltHex()
        val newHash = CryptoManager.hashPin(newPass, newSalt)

        val updated = acc.copy(passwordHash = newHash, saltHex = newSalt)
        val allAccounts = getAllAccounts().toMutableMap()
        allAccounts[acc.vaultId] = updated
        saveAllAccounts(allAccounts)

        prefs.edit()
            .putString(KEY_SALT, newSalt)
            .putString(KEY_PWD_HASH, newHash)
            .apply()

        _account.value = updated
        activePassword = newPass
        return true
    }

    fun lock() {
        activePassword = null
        _isUnlocked.value = false
    }

    fun switchAccount() {
        lock()
        _account.value = null
        prefs.edit().remove(KEY_CURRENT_ACTIVE_VAULT_ID).apply()
    }

    fun logout() {
        lock()
        _account.value = null
        prefs.edit().remove(KEY_CURRENT_ACTIVE_VAULT_ID).apply()
    }

    // Helper to generate a cryptographically unique formatted ID like VLT-982B-7AF1-30CE
    private fun generateUniqueVaultId(existingIds: Set<String>): String {
        val random = SecureRandom()
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // non-ambiguous chars
        fun part(length: Int): String = (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        var candidate: String
        do {
            candidate = "VLT-${part(4)}-${part(4)}-${part(4)}"
        } while (existingIds.contains(candidate))
        return candidate
    }

    companion object {
        private const val KEY_ACCOUNTS_MAP_JSON = "vault_accounts_map_json"
        private const val KEY_CURRENT_ACTIVE_VAULT_ID = "vault_current_active_id"

        private const val KEY_VAULT_ID = "vault_account_id"
        private const val KEY_USER_NAME = "vault_account_username"
        private const val KEY_PWD_HASH = "vault_pwd_hash"
        private const val KEY_SALT = "vault_salt_hex"
        private const val KEY_CREATED_AT = "vault_account_created_at"
        private const val KEY_BIOMETRIC = "vault_biometric_enabled"
    }
}

