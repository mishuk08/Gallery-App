package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.crypto.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_customization_prefs", Context.MODE_PRIVATE)

    private val _gridColumns = MutableStateFlow(prefs.getInt(KEY_GRID_COLUMNS, 3))
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _themePreset = MutableStateFlow(prefs.getString(KEY_THEME_PRESET, "amber") ?: "amber")
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    private val _darkModeSetting = MutableStateFlow(prefs.getString(KEY_DARK_MODE, "dark") ?: "dark")
    val darkModeSetting: StateFlow<String> = _darkModeSetting.asStateFlow()

    private val _autoCloudBackup = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CLOUD, true))
    val autoCloudBackup: StateFlow<Boolean> = _autoCloudBackup.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRICS, true))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val hasPin = prefs.getString(KEY_APP_LOCK_PIN_HASH, null) != null
    private val _hasAppLockPin = MutableStateFlow(hasPin)
    val hasAppLockPin: StateFlow<Boolean> = _hasAppLockPin.asStateFlow()

    // App lock can ONLY be enabled if the user has explicitly created a PIN!
    // On first open, this is guaranteed to be false so the app opens normally.
    private val _appLockEnabled = MutableStateFlow(hasPin && prefs.getBoolean(KEY_APP_LOCK, false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    init {
        // Clean up: if no PIN was ever set, ensure app lock is strictly disabled in preferences
        if (!hasPin && prefs.getBoolean(KEY_APP_LOCK, false)) {
            prefs.edit().putBoolean(KEY_APP_LOCK, false).apply()
        }
    }

    fun setGridColumns(columns: Int) {
        val safe = columns.coerceIn(1, 5)
        _gridColumns.value = safe
        prefs.edit().putInt(KEY_GRID_COLUMNS, safe).apply()
    }

    fun setThemePreset(preset: String) {
        _themePreset.value = preset
        prefs.edit().putString(KEY_THEME_PRESET, preset).apply()
    }

    fun setDarkModeSetting(mode: String) {
        _darkModeSetting.value = mode
        prefs.edit().putString(KEY_DARK_MODE, mode).apply()
    }

    fun setAutoCloudBackup(enabled: Boolean) {
        _autoCloudBackup.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_CLOUD, enabled).apply()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BIOMETRICS, enabled).apply()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        if (enabled && !_hasAppLockPin.value) {
            _appLockEnabled.value = false
            prefs.edit().putBoolean(KEY_APP_LOCK, false).apply()
            return
        }
        _appLockEnabled.value = enabled
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }

    /**
     * Securely sets the App Lock PIN using cryptographic PBKDF2 hashing with salt.
     * Enforces strictly 6 digits as required.
     */
    fun setAppLockPin(pin: String) {
        require(pin.length == 6 && pin.all { it.isDigit() }) {
            "App Lock PIN must be exactly 6 digits"
        }
        val salt = CryptoManager.generateSaltHex()
        val hash = CryptoManager.hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_APP_LOCK_PIN_HASH, hash)
            .putString(KEY_APP_LOCK_PIN_SALT, salt)
            .putBoolean(KEY_APP_LOCK, true)
            .apply()
        _appLockEnabled.value = true
        _hasAppLockPin.value = true
    }

    /**
     * Verifies whether the entered PIN matches the securely stored hash.
     * Enforces strictly 6 digits.
     */
    fun verifyAppLockPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        val hash = prefs.getString(KEY_APP_LOCK_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_APP_LOCK_PIN_SALT, null) ?: return false
        return try {
            val testHash = CryptoManager.hashPin(pin, salt)
            testHash == hash
        } catch (e: Exception) {
            false
        }
    }

    fun hasAppLockPin(): Boolean {
        return prefs.getString(KEY_APP_LOCK_PIN_HASH, null) != null
    }

    fun disableAppLock() {
        prefs.edit().putBoolean(KEY_APP_LOCK, false).apply()
        _appLockEnabled.value = false
    }

    fun removeAppLockPin() {
        prefs.edit()
            .remove(KEY_APP_LOCK_PIN_HASH)
            .remove(KEY_APP_LOCK_PIN_SALT)
            .putBoolean(KEY_APP_LOCK, false)
            .apply()
        _appLockEnabled.value = false
        _hasAppLockPin.value = false
    }

    val isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)

    fun markFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    companion object {
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch_v2"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_DARK_MODE = "dark_mode_setting"
        private const val KEY_AUTO_CLOUD = "auto_cloud"
        private const val KEY_BIOMETRICS = "biometrics_enabled"
        private const val KEY_APP_LOCK = "app_lock_biometrics_enabled"
        private const val KEY_APP_LOCK_PIN_HASH = "app_lock_pin_hash_v2"
        private const val KEY_APP_LOCK_PIN_SALT = "app_lock_pin_salt_v2"
    }
}
