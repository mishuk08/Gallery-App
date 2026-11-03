package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.crypto.CryptoException
import com.example.crypto.CryptoManager
import com.example.crypto.KeystoreHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale

class VaultManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("secure_vault_prefs", Context.MODE_PRIVATE)
    private val settingsManager = AppSettingsManager(context)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _account = MutableStateFlow<VaultAccount?>(null)
    val account: StateFlow<VaultAccount?> = _account.asStateFlow()

    // In-memory active Master Vault Key (256-bit AES key)
    // NEVER persisted in plaintext to disk
    private var activeMasterKey: ByteArray? = null

    // Session-only cached PIN (cleared on lock)
    private var activePin: String? = null

    init {
        migrateLegacyAccountIfNeeded()
        _account.value = loadActiveAccount()
    }

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
                    wrappedMasterKeyHex = obj.optString("wrappedMasterKeyHex", ""),
                    keystoreWrappedMasterKeyHex = if (obj.has("keystoreWrappedMasterKeyHex") && !obj.isNull("keystoreWrappedMasterKeyHex")) {
                        obj.getString("keystoreWrappedMasterKeyHex")
                    } else null,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    isBiometricEnabled = obj.optBoolean("isBiometricEnabled", true)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun saveAllAccounts(accounts: Map<String, VaultAccount>) {
        try {
            val root = JSONObject()
            for ((id, acc) in accounts) {
                val obj = JSONObject().apply {
                    put("userName", acc.userName)
                    put("passwordHash", acc.passwordHash)
                    put("saltHex", acc.saltHex)
                    put("wrappedMasterKeyHex", acc.wrappedMasterKeyHex)
                    if (acc.keystoreWrappedMasterKeyHex != null) {
                        put("keystoreWrappedMasterKeyHex", acc.keystoreWrappedMasterKeyHex)
                    }
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
                    wrappedMasterKeyHex = "",
                    keystoreWrappedMasterKeyHex = null,
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

    fun getActivePin(): String? = activePin

    fun getActiveMasterKey(): ByteArray? = activeMasterKey

    /**
     * Creates a new vault account with a cryptographically generated Master Vault Key,
     * wrapped using a 6-digit PIN and Android Keystore.
     */
    fun createAccount(userName: String, pin: String): VaultAccount {
        require(pin.length == 6 && pin.all { it.isDigit() }) {
            "PIN must be exactly 6 digits"
        }

        val allAccounts = getAllAccounts().toMutableMap()
        val vaultId = generateUniqueVaultId(allAccounts.keys)
        val salt = CryptoManager.generateSaltHex()
        val hash = CryptoManager.hashPin(pin, salt)
        val now = System.currentTimeMillis()

        // 1. Generate 256-bit Master Vault Key
        val masterKey = CryptoManager.generateMasterKey()

        // 2. Wrap Master Key with PIN-derived Key-Encryption-Key (KEK)
        val wrappedByPin = CryptoManager.wrapMasterKeyWithPin(
            masterKey = masterKey,
            pin = pin,
            salt = CryptoManager.hexToBytes(salt)
        )
        val wrappedByPinHex = CryptoManager.bytesToHex(wrappedByPin)

        // 3. Wrap Master Key with Android Keystore for biometrics
        val keystoreAlias = "vault_master_key_$vaultId"
        val wrappedByBio = KeystoreHelper.wrapMasterKey(keystoreAlias, masterKey)
        val wrappedByBioHex = wrappedByBio?.let { CryptoManager.bytesToHex(it) }

        val newAcc = VaultAccount(
            vaultId = vaultId,
            userName = userName.trim().ifBlank { "Vault Owner" },
            passwordHash = hash,
            saltHex = salt,
            wrappedMasterKeyHex = wrappedByPinHex,
            keystoreWrappedMasterKeyHex = wrappedByBioHex,
            createdAt = now,
            isBiometricEnabled = true
        )

        allAccounts[vaultId] = newAcc
        saveAllAccounts(allAccounts)

        prefs.edit()
            .putString(KEY_CURRENT_ACTIVE_VAULT_ID, vaultId)
            .putString(KEY_VAULT_ID, vaultId)
            .putString(KEY_USER_NAME, newAcc.userName)
            .putString(KEY_PWD_HASH, hash)
            .putString(KEY_SALT, salt)
            .putLong(KEY_CREATED_AT, now)
            .putBoolean(KEY_BIOMETRIC, true)
            .apply()

        _account.value = newAcc
        activeMasterKey = masterKey
        activePin = pin
        _isUnlocked.value = true
        settingsManager.resetFailedPinAttempts()
        return newAcc
    }

    /**
     * Verifies the 6-digit PIN, unwraps the Master Vault Key, and unlocks the session.
     * Enforces rate-limiting and temporary lockout protection.
     */
    fun verifyAndUnlock(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false

        // Rate-limiting check
        if (settingsManager.isLockedOut()) {
            return false
        }

        val acc = _account.value ?: return false
        val isValid = CryptoManager.verifyPin(pin, acc.saltHex, acc.passwordHash)

        if (!isValid) {
            settingsManager.recordFailedPinAttempt()
            return false
        }

        // Authentication succeeded: reset failed attempts
        settingsManager.resetFailedPinAttempts()

        // Unwrap Master Key
        val saltBytes = CryptoManager.hexToBytes(acc.saltHex)
        val masterKey: ByteArray = if (acc.wrappedMasterKeyHex.isNotBlank()) {
            try {
                val wrappedBytes = CryptoManager.hexToBytes(acc.wrappedMasterKeyHex)
                CryptoManager.unwrapMasterKeyWithPin(wrappedBytes, pin, saltBytes)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        } else {
            // Upgrade legacy account on-the-fly
            val newMasterKey = CryptoManager.generateMasterKey()
            val wrapped = CryptoManager.wrapMasterKeyWithPin(newMasterKey, pin, saltBytes)
            val wrappedHex = CryptoManager.bytesToHex(wrapped)

            val keystoreAlias = "vault_master_key_${acc.vaultId}"
            val wrappedBio = KeystoreHelper.wrapMasterKey(keystoreAlias, newMasterKey)
            val wrappedBioHex = wrappedBio?.let { CryptoManager.bytesToHex(it) }

            val updated = acc.copy(
                wrappedMasterKeyHex = wrappedHex,
                keystoreWrappedMasterKeyHex = wrappedBioHex
            )
            val allAccounts = getAllAccounts().toMutableMap()
            allAccounts[acc.vaultId] = updated
            saveAllAccounts(allAccounts)
            _account.value = updated
            newMasterKey
        }

        activeMasterKey = masterKey
        activePin = pin
        _isUnlocked.value = true
        return true
    }

    /**
     * Unlocks using Biometrics by unwrapping the Master Vault Key from Android Keystore.
     */
    fun unlockWithBiometrics(): Boolean {
        val acc = _account.value ?: return false
        if (!acc.isBiometricEnabled) return false

        val keystoreAlias = "vault_master_key_${acc.vaultId}"
        val wrappedBioHex = acc.keystoreWrappedMasterKeyHex ?: return false

        val wrappedBioBytes = try {
            CryptoManager.hexToBytes(wrappedBioHex)
        } catch (_: Exception) {
            return false
        }

        val unwrappedMasterKey = KeystoreHelper.unwrapMasterKey(keystoreAlias, wrappedBioBytes)
            ?: return false

        activeMasterKey = unwrappedMasterKey
        activePin = null
        _isUnlocked.value = true
        settingsManager.resetFailedPinAttempts()
        return true
    }

    /**
     * Securely changes the 6-digit PIN.
     * Re-wraps the Master Vault Key with the new PIN so all existing encrypted
     * photos remain 100% accessible and undamaged.
     */
    fun changePassword(oldPin: String, newPin: String): Boolean {
        val acc = _account.value ?: return false
        if (settingsManager.isLockedOut()) return false

        val isValid = CryptoManager.verifyPin(oldPin, acc.saltHex, acc.passwordHash)
        if (!isValid) {
            settingsManager.recordFailedPinAttempt()
            return false
        }
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) return false

        val saltBytes = CryptoManager.hexToBytes(acc.saltHex)
        val currentMasterKey = activeMasterKey ?: try {
            if (acc.wrappedMasterKeyHex.isNotBlank()) {
                CryptoManager.unwrapMasterKeyWithPin(
                    CryptoManager.hexToBytes(acc.wrappedMasterKeyHex),
                    oldPin,
                    saltBytes
                )
            } else {
                CryptoManager.generateMasterKey()
            }
        } catch (_: Exception) {
            return false
        }

        // Generate new salt and verification hash for new PIN
        val newSalt = CryptoManager.generateSaltHex()
        val newSaltBytes = CryptoManager.hexToBytes(newSalt)
        val newHash = CryptoManager.hashPin(newPin, newSalt)

        // Re-wrap Master Key with new PIN KEK
        val newWrappedMasterKey = CryptoManager.wrapMasterKeyWithPin(
            masterKey = currentMasterKey,
            pin = newPin,
            salt = newSaltBytes
        )
        val newWrappedMasterKeyHex = CryptoManager.bytesToHex(newWrappedMasterKey)

        // Update Keystore wrapping
        val keystoreAlias = "vault_master_key_${acc.vaultId}"
        val wrappedBio = KeystoreHelper.wrapMasterKey(keystoreAlias, currentMasterKey)
        val wrappedBioHex = wrappedBio?.let { CryptoManager.bytesToHex(it) }

        val updated = acc.copy(
            passwordHash = newHash,
            saltHex = newSalt,
            wrappedMasterKeyHex = newWrappedMasterKeyHex,
            keystoreWrappedMasterKeyHex = wrappedBioHex ?: acc.keystoreWrappedMasterKeyHex
        )

        val allAccounts = getAllAccounts().toMutableMap()
        allAccounts[acc.vaultId] = updated
        saveAllAccounts(allAccounts)

        prefs.edit()
            .putString(KEY_SALT, newSalt)
            .putString(KEY_PWD_HASH, newHash)
            .apply()

        _account.value = updated
        activeMasterKey = currentMasterKey
        activePin = newPin
        settingsManager.resetFailedPinAttempts()
        return true
    }

    /**
     * Enables or disables biometric unlock.
     * When enabling, ensures the Master Key is wrapped by Android Keystore.
     */
    fun setBiometricEnabled(enabled: Boolean) {
        val acc = _account.value ?: return
        var wrappedBioHex = acc.keystoreWrappedMasterKeyHex

        if (enabled && activeMasterKey != null) {
            val keystoreAlias = "vault_master_key_${acc.vaultId}"
            val wrappedBio = KeystoreHelper.wrapMasterKey(keystoreAlias, activeMasterKey!!)
            if (wrappedBio != null) {
                wrappedBioHex = CryptoManager.bytesToHex(wrappedBio)
            }
        } else if (!enabled) {
            val keystoreAlias = "vault_master_key_${acc.vaultId}"
            KeystoreHelper.deleteKey(keystoreAlias)
            wrappedBioHex = null
        }

        val updated = acc.copy(
            isBiometricEnabled = enabled,
            keystoreWrappedMasterKeyHex = wrappedBioHex
        )
        val allAccounts = getAllAccounts().toMutableMap()
        allAccounts[acc.vaultId] = updated
        saveAllAccounts(allAccounts)
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
        _account.value = updated
    }

    /**
     * Restores an account from a valid backup archive or switches between local accounts.
     */
    fun restoreAccountFromBackup(account: VaultAccount, masterKey: ByteArray, pin: String) {
        val allAccounts = getAllAccounts().toMutableMap()
        val keystoreAlias = "vault_master_key_${account.vaultId}"
        val wrappedBio = KeystoreHelper.wrapMasterKey(keystoreAlias, masterKey)
        val wrappedBioHex = wrappedBio?.let { CryptoManager.bytesToHex(it) }

        val finalized = account.copy(
            keystoreWrappedMasterKeyHex = wrappedBioHex ?: account.keystoreWrappedMasterKeyHex
        )
        allAccounts[finalized.vaultId] = finalized
        saveAllAccounts(allAccounts)

        prefs.edit()
            .putString(KEY_CURRENT_ACTIVE_VAULT_ID, finalized.vaultId)
            .putString(KEY_VAULT_ID, finalized.vaultId)
            .putString(KEY_USER_NAME, finalized.userName)
            .putString(KEY_PWD_HASH, finalized.passwordHash)
            .putString(KEY_SALT, finalized.saltHex)
            .apply()

        _account.value = finalized
        activeMasterKey = masterKey
        activePin = pin
        _isUnlocked.value = true
        settingsManager.resetFailedPinAttempts()
    }

    fun loginWithExistingAccount(vaultId: String, pin: String): Boolean {
        val cleanInputId = vaultId.trim().uppercase(Locale.ROOT)
        val targetId = if (cleanInputId.startsWith("VLT-")) cleanInputId else "VLT-$cleanInputId"
        val allAccounts = getAllAccounts()
        val existing = allAccounts[targetId] ?: return false

        _account.value = existing
        prefs.edit().putString(KEY_CURRENT_ACTIVE_VAULT_ID, targetId).apply()
        return verifyAndUnlock(pin)
    }

    fun loginWithCredentials(userName: String, vaultId: String, pin: String): Boolean {
        val cleanInputId = vaultId.trim().uppercase(Locale.ROOT)
        val targetId = if (cleanInputId.startsWith("VLT-")) cleanInputId else "VLT-$cleanInputId"
        val allAccounts = getAllAccounts().toMutableMap()
        val existing = allAccounts[targetId] ?: return false

        val updated = existing.copy(userName = userName.trim().ifBlank { existing.userName })
        allAccounts[targetId] = updated
        saveAllAccounts(allAccounts)
        prefs.edit().putString(KEY_CURRENT_ACTIVE_VAULT_ID, targetId).apply()
        _account.value = updated
        return verifyAndUnlock(pin)
    }

    fun lock() {
        // Zero in-memory master key
        activeMasterKey?.fill(0)
        activeMasterKey = null
        activePin = null
        _isUnlocked.value = false
    }

    fun logout() {
        lock()
        _account.value = null
        prefs.edit().remove(KEY_CURRENT_ACTIVE_VAULT_ID).apply()
    }

    private fun generateUniqueVaultId(existingIds: Set<String>): String {
        val random = SecureRandom()
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
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
