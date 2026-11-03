package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettingsManager
import com.example.data.DeviceMediaStoreManager
import com.example.data.LocalMediaPhoto
import com.example.data.PhotoEntity
import com.example.data.PhotoRepository
import com.example.data.VaultAccount
import com.example.data.VaultManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GalleryTab {
    GALLERY,
    VAULT,
    SETTINGS
}

enum class GalleryFilter {
    ALL,
    DEVICE_PHOTOS,
    FAVORITES
}

class GalleryViewModel(
    application: Application,
    val repository: PhotoRepository,
    val vaultManager: VaultManager,
    val settingsManager: AppSettingsManager
) : AndroidViewModel(application) {

    private val _currentTab = MutableStateFlow(GalleryTab.GALLERY)
    val currentTab: StateFlow<GalleryTab> = _currentTab.asStateFlow()

    private val _activeFilter = MutableStateFlow(GalleryFilter.ALL)
    val activeFilter: StateFlow<GalleryFilter> = _activeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val vaultAccount: StateFlow<VaultAccount?> = vaultManager.account
    val isVaultUnlocked: StateFlow<Boolean> = vaultManager.isUnlocked

    // Preferences & Customization
    val gridColumns: StateFlow<Int> = settingsManager.gridColumns
    val themePreset: StateFlow<String> = settingsManager.themePreset
    val darkModeSetting: StateFlow<String> = settingsManager.darkModeSetting
    val autoCloudBackup: StateFlow<Boolean> = settingsManager.autoCloudBackup
    val biometricEnabled: StateFlow<Boolean> = settingsManager.biometricEnabled
    val appLockEnabled: StateFlow<Boolean> = settingsManager.appLockEnabled
    val hasAppLockPin: StateFlow<Boolean> = settingsManager.hasAppLockPin

    val syncedCount: StateFlow<Int> = repository.syncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val localOnlyCount: StateFlow<Int> = repository.localOnlyCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // On initial launch, the app always opens normally. App lock is only engaged if the user configured a PIN.
    private val _isAppLocked = MutableStateFlow(
        settingsManager.appLockEnabled.value && settingsManager.hasAppLockPin.value
    )
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Local Device Media Photos
    private val _devicePhotos = MutableStateFlow<List<LocalMediaPhoto>>(emptyList())
    val devicePhotos: StateFlow<List<LocalMediaPhoto>> = _devicePhotos.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(
        DeviceMediaStoreManager.hasStoragePermission(application)
    )
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val publicPhotos: StateFlow<List<PhotoEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.publicPhotos
        } else {
            repository.searchPublicPhotos(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultPhotos: StateFlow<List<PhotoEntity>> = repository.vaultPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePhotos: StateFlow<List<PhotoEntity>> = repository.favoritePhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPhoto = MutableStateFlow<PhotoEntity?>(null)
    val selectedPhoto: StateFlow<PhotoEntity?> = _selectedPhoto.asStateFlow()

    private val _decryptedPreview = MutableStateFlow<Bitmap?>(null)
    val decryptedPreview: StateFlow<Bitmap?> = _decryptedPreview.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _lockoutRemainingSeconds = MutableStateFlow(settingsManager.getLockoutRemainingSeconds())
    val lockoutRemainingSeconds: StateFlow<Long> = _lockoutRemainingSeconds.asStateFlow()

    private val _isPartialAccessOnly = MutableStateFlow(
        DeviceMediaStoreManager.isPartialAccessOnly(application)
    )
    val isPartialAccessOnly: StateFlow<Boolean> = _isPartialAccessOnly.asStateFlow()

    init {
        refreshDevicePhotos()
        viewModelScope.launch {
            while (true) {
                val remaining = settingsManager.getLockoutRemainingSeconds()
                _lockoutRemainingSeconds.value = remaining
                kotlinx.coroutines.delay(1000)
            }
        }
        viewModelScope.launch {
            if (settingsManager.isFirstLaunch) {
                repository.importSamplePhoto(isEncrypted = false, autoUploadToCloud = false)
                settingsManager.markFirstLaunchComplete()
            } else if (publicPhotos.value.isEmpty() && _devicePhotos.value.isEmpty()) {
                repository.importSamplePhoto(isEncrypted = false, autoUploadToCloud = false)
            }
        }
    }

    fun setTab(tab: GalleryTab) {
        if (_currentTab.value == GalleryTab.VAULT && tab != GalleryTab.VAULT) {
            lockVault(silent = true)
        }
        _currentTab.value = tab
    }

    fun setFilter(filter: GalleryFilter) {
        _activeFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Customization Setters
    fun setGridColumns(cols: Int) = settingsManager.setGridColumns(cols)
    fun zoomGridIn() = settingsManager.setGridColumns(gridColumns.value - 1)
    fun zoomGridOut() = settingsManager.setGridColumns(gridColumns.value + 1)
    fun setThemePreset(preset: String) = settingsManager.setThemePreset(preset)
    fun setDarkModeSetting(mode: String) = settingsManager.setDarkModeSetting(mode)
    fun setAutoCloudBackup(enabled: Boolean) = settingsManager.setAutoCloudBackup(enabled)
    fun setBiometricSetting(enabled: Boolean) {
        settingsManager.setBiometricEnabled(enabled)
        vaultManager.setBiometricEnabled(enabled)
    }

    fun setAppLockSetting(enabled: Boolean) {
        if (enabled && !settingsManager.hasAppLockPin.value) {
            _isAppLocked.value = false
            return
        }
        settingsManager.setAppLockEnabled(enabled)
        if (!enabled) {
            _isAppLocked.value = false
        }
    }

    fun setAppLockPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        return try {
            settingsManager.setAppLockPin(pin)
            _statusMessage.value = "6-Digit App Lock PIN set successfully!"
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disableAppLock() {
        settingsManager.disableAppLock()
        _isAppLocked.value = false
        _statusMessage.value = "App Lock disabled"
    }

    fun lockApp() {
        if (settingsManager.appLockEnabled.value && settingsManager.hasAppLockPin.value) {
            _isAppLocked.value = true
        } else {
            _isAppLocked.value = false
        }
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun verifyAppLockWithPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false

        // 1. Primary check: Verify against dedicated App Lock PIN set by user
        if (settingsManager.hasAppLockPin()) {
            val valid = settingsManager.verifyAppLockPin(pin)
            if (valid) {
                _isAppLocked.value = false
                return true
            }
        }

        // 2. Secondary check: If user configured a Vault Account with a PIN, allow that as alternative
        val acc = vaultAccount.value
        if (acc != null) {
            val validVault = vaultManager.verifyAndUnlock(pin)
            if (validVault) {
                _isAppLocked.value = false
                return true
            }
        }

        // Must reject all other inputs — no arbitrary password unlock!
        return false
    }

    // Device Photos scanning & offline Room caching
    fun refreshDevicePhotos() {
        viewModelScope.launch {
            val hasPerm = DeviceMediaStoreManager.hasStoragePermission(getApplication())
            _hasStoragePermission.value = hasPerm
            if (hasPerm) {
                val media = DeviceMediaStoreManager.fetchDevicePhotos(getApplication())
                _devicePhotos.value = media
                // Automatically persist device photos metadata into Room database for offline-first access!
                repository.syncDevicePhotosToDatabase(media)
            }
        }
    }

    fun onStoragePermissionGranted() {
        _hasStoragePermission.value = true
        refreshDevicePhotos()
    }

    // Account Creation & Vault Access
    fun createVaultAccount(userName: String, passwordOrPin: String): VaultAccount? {
        return try {
            val acc = vaultManager.createAccount(userName, passwordOrPin)
            _statusMessage.value = "Vault account created! Unique ID: ${acc.vaultId}"
            acc
        } catch (e: Exception) {
            _statusMessage.value = e.localizedMessage ?: "Failed to create account"
            null
        }
    }

    fun loginWithVaultCredentials(userName: String, vaultId: String, passwordOrPin: String): Boolean {
        val success = vaultManager.loginWithCredentials(userName, vaultId, passwordOrPin)
        if (success) {
            _statusMessage.value = "Vault unlocked successfully for $userName!"
        } else {
            _statusMessage.value = "Invalid credentials. Enter correct Name, Vault ID and 6-digit PIN"
        }
        return success
    }

    fun loginWithVaultCredentials(vaultId: String, passwordOrPin: String): Boolean {
        val remaining = settingsManager.getLockoutRemainingSeconds()
        if (remaining > 0) {
            _statusMessage.value = "Too many failed attempts. Locked out for ${remaining}s"
            _lockoutRemainingSeconds.value = remaining
            return false
        }

        val success = vaultManager.loginWithExistingAccount(vaultId, passwordOrPin)
        if (success) {
            _statusMessage.value = "Vault unlocked successfully!"
            _lockoutRemainingSeconds.value = 0L
        } else {
            val all = vaultManager.getAllAccounts()
            val target = if (vaultId.startsWith("VLT-", true)) vaultId.uppercase() else "VLT-${vaultId.uppercase()}"
            if (!all.containsKey(target)) {
                _statusMessage.value = "Vault not found on device. Use 'Restore from Backup' to import your vault."
            } else {
                val newLock = settingsManager.getLockoutRemainingSeconds()
                _lockoutRemainingSeconds.value = newLock
                _statusMessage.value = if (newLock > 0) "Locked out for ${newLock}s" else "Incorrect 6-Digit PIN"
            }
        }
        return success
    }

    fun unlockVaultWithPassword(passwordOrPin: String): Boolean {
        val remaining = settingsManager.getLockoutRemainingSeconds()
        if (remaining > 0) {
            _statusMessage.value = "Too many failed attempts. Locked out for ${remaining}s"
            _lockoutRemainingSeconds.value = remaining
            return false
        }

        val success = vaultManager.verifyAndUnlock(passwordOrPin)
        if (success) {
            _statusMessage.value = "Vault unlocked"
            _lockoutRemainingSeconds.value = 0L
        } else {
            val newLockout = settingsManager.getLockoutRemainingSeconds()
            _lockoutRemainingSeconds.value = newLockout
            if (newLockout > 0) {
                _statusMessage.value = "Too many failed attempts. Locked out for ${newLockout}s"
            } else {
                _statusMessage.value = "Incorrect 6-Digit PIN"
            }
        }
        return success
    }

    fun exportBackupArchive(
        includeGallery: Boolean,
        backupPasswordOrPin: String,
        onComplete: (java.io.File?) -> Unit
    ) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val app = getApplication<android.app.Application>()
                val backupBytes = com.example.data.BackupManager.createEncryptedBackup(
                    context = app,
                    account = vaultAccount.value,
                    vaultPhotos = vaultPhotos.value,
                    galleryPhotos = publicPhotos.value,
                    includeGalleryPhotos = includeGallery,
                    backupPasswordOrPin = backupPasswordOrPin
                )
                val backupDir = java.io.File(app.cacheDir, "backups").apply { mkdirs() }
                val backupFile = java.io.File(backupDir, "gallery_vault_${System.currentTimeMillis()}.vaultbackup")
                backupFile.outputStream().use { it.write(backupBytes) }
                _statusMessage.value = "Backup created & verified! (${backupBytes.size / 1024} KB)"
                onComplete(backupFile)
            } catch (e: Exception) {
                _statusMessage.value = "Backup failed: ${e.localizedMessage}"
                onComplete(null)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun restoreBackupArchive(
        backupUri: Uri,
        backupPasswordOrPin: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val app = getApplication<android.app.Application>()
                val rawBytes = app.contentResolver.openInputStream(backupUri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Could not read backup file from device.")

                val db = com.example.data.AppDatabase.getDatabase(app)
                val result = com.example.data.BackupManager.restoreEncryptedBackup(
                    context = app,
                    backupBytes = rawBytes,
                    backupPasswordOrPin = backupPasswordOrPin,
                    vaultManager = vaultManager,
                    photoDao = db.photoDao()
                )

                _statusMessage.value = "Restored ${result.restoredMediaCount} item(s) from backup!"
                onComplete(true, "Restored ${result.restoredMediaCount} item(s) successfully")
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Failed to restore backup"
                _statusMessage.value = "Restore failed: $err"
                onComplete(false, err)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun unlockVaultWithBiometrics(): Boolean {
        val success = vaultManager.unlockWithBiometrics()
        if (success) {
            _statusMessage.value = "Unlocked via Biometrics"
        } else {
            _statusMessage.value = "Biometric unlock unavailable or disabled"
        }
        return success
    }

    fun lockVault(silent: Boolean = false) {
        vaultManager.lock()
        _decryptedPreview.value = null
        if (!silent) {
            _statusMessage.value = "Vault locked"
        }
    }

    fun logoutVaultAccount() {
        vaultManager.logout()
        _decryptedPreview.value = null
        _statusMessage.value = "Logged out from Vault account"
    }

    fun changeVaultPassword(oldPass: String, newPass: String): Boolean {
        val success = vaultManager.changePassword(oldPass, newPass)
        if (success) {
            _statusMessage.value = "6-Digit PIN updated successfully"
        } else {
            _statusMessage.value = "Old PIN was incorrect or new PIN not 6 digits"
        }
        return success
    }

    fun updatePhotoTitle(photo: PhotoEntity, newTitle: String) {
        viewModelScope.launch {
            val updated = photo.copy(title = newTitle.ifBlank { photo.title })
            repository.updatePhoto(updated)
            _selectedPhoto.value = updated
            _statusMessage.value = "Photo title updated"
        }
    }

    fun saveEditedPhoto(photo: PhotoEntity, newBitmap: Bitmap, newTitle: String) {
        viewModelScope.launch {
            try {
                val app = getApplication<android.app.Application>()
                val isEncrypted = photo.isEncrypted
                val masterKey = vaultManager.getActiveMasterKey()
                
                val dir = java.io.File(app.filesDir, if (isEncrypted) "vault" else "gallery").apply { mkdirs() }
                val ext = if (isEncrypted) "vlt" else "jpg"
                val file = java.io.File(dir, "edited_${System.currentTimeMillis()}.$ext")
                
                val rawBytes = java.io.ByteArrayOutputStream().use { stream ->
                    newBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    stream.toByteArray()
                }
                
                val finalBytes = if (isEncrypted && masterKey != null) {
                    com.example.crypto.CryptoManager.encryptWithMasterKey(rawBytes, masterKey)
                } else {
                    rawBytes
                }
                
                file.outputStream().use { it.write(finalBytes) }
                
                val updated = photo.copy(
                    title = newTitle.ifBlank { photo.title },
                    localUri = Uri.fromFile(file).toString(),
                    localFilePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    uploadStatus = "PENDING"
                )
                repository.updatePhoto(updated)
                _selectedPhoto.value = updated
                if (isEncrypted) {
                    _decryptedPreview.value = newBitmap
                }
                _statusMessage.value = "Photo edited & saved successfully!"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to save edit: ${e.localizedMessage}"
            }
        }
    }

    // Move device photo to Encrypted Vault
    fun moveDevicePhotoToVault(mediaPhoto: LocalMediaPhoto) {
        if (!vaultManager.hasAccount()) {
            _statusMessage.value = "Please create a Vault Account first to encrypt photos!"
            return
        }
        if (!vaultManager.isUnlocked.value) {
            _statusMessage.value = "Please unlock the Vault first to add encrypted photos!"
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            try {
                repository.importPhoto(
                    uri = Uri.parse(mediaPhoto.contentUri),
                    title = mediaPhoto.displayName,
                    isEncrypted = true,
                    autoUploadToCloud = settingsManager.autoCloudBackup.value
                )
                _statusMessage.value = "Encrypted & moved '${mediaPhoto.displayName}' into Vault"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to encrypt: ${e.localizedMessage}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    // Import from picker or camera
    fun importPhotos(
        uris: List<Uri>,
        title: String,
        isEncrypted: Boolean,
        autoUpload: Boolean,
        pin: String? = null
    ) {
        if (uris.isEmpty()) return
        if (isEncrypted && !vaultManager.hasAccount()) {
            _statusMessage.value = "Account required to use Encrypted Vault!"
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgress.value = 10
            var count = 0
            var lastError: String? = null
            for (uri in uris) {
                try {
                    val customTitle = if (title.isNotBlank()) {
                        if (uris.size > 1) "$title (${count + 1})" else title
                    } else ""
                    repository.importPhoto(
                        uri = uri,
                        title = customTitle,
                        isEncrypted = isEncrypted,
                        autoUploadToCloud = autoUpload,
                        pin = pin
                    )
                    count++
                    _uploadProgress.value = ((count * 100) / uris.size)
                } catch (e: Exception) {
                    lastError = e.localizedMessage ?: "Unknown error"
                }
            }
            _isUploading.value = false
            if (count > 0) {
                _statusMessage.value = if (isEncrypted) {
                    "Saved $count photo(s) securely into Encrypted Vault"
                } else {
                    "Imported $count photo(s) to Gallery"
                }
            } else {
                _statusMessage.value = "Failed to import photo: ${lastError ?: "Could not read image file"}"
            }
        }
    }

    fun importSamplePhoto(isEncrypted: Boolean, pin: String? = null) {
        if (isEncrypted && !vaultManager.hasAccount()) {
            _statusMessage.value = "Please create a Vault Account first to use encrypted vault!"
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgress.value = 30
            try {
                repository.importSamplePhoto(
                    isEncrypted = isEncrypted,
                    autoUploadToCloud = settingsManager.autoCloudBackup.value,
                    pin = pin
                )
                _statusMessage.value = if (isEncrypted) {
                    "Sample encrypted photo added to Secure Vault"
                } else {
                    "Sample photo added to Gallery"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to add sample photo: ${e.localizedMessage}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun selectPhoto(photo: PhotoEntity?) {
        _selectedPhoto.value = photo
        _decryptedPreview.value = null
        if (photo != null) {
            viewModelScope.launch {
                val bmp = if (photo.isEncrypted) {
                    repository.loadDecryptedBitmap(photo)
                } else {
                    repository.loadPlainBitmap(photo)
                }
                _decryptedPreview.value = bmp
            }
        }
    }

    suspend fun decryptToTempPlaybackFile(photo: PhotoEntity): java.io.File? {
        return repository.decryptToTempPlaybackFile(photo)
    }

    fun toggleVaultProtection(photo: PhotoEntity, pin: String? = null) {
        if (!photo.isEncrypted && !vaultManager.hasAccount()) {
            _statusMessage.value = "Create a Vault Account first to encrypt photos!"
            return
        }
        viewModelScope.launch {
            val wasEncrypted = photo.isEncrypted
            val success = repository.toggleVaultProtection(photo, pin)
            if (success) {
                _statusMessage.value = if (wasEncrypted) {
                    "Photo decrypted and moved to Public Gallery"
                } else {
                    "Photo encrypted with AES-256 and moved to Vault"
                }
                _selectedPhoto.value = repository.getPhotoById(photo.id)
            } else {
                _statusMessage.value = "Failed to update encryption. Check Vault PIN."
            }
        }
    }

    fun uploadToCloud(photo: PhotoEntity) {
        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgress.value = 0
            val success = repository.uploadToCloud(photo) { progress ->
                _uploadProgress.value = progress
            }
            _isUploading.value = false
            if (success) {
                _statusMessage.value = "Uploaded to cloud successfully!"
                if (_selectedPhoto.value?.id == photo.id) {
                    _selectedPhoto.value = repository.getPhotoById(photo.id)
                }
            } else {
                _statusMessage.value = "Upload failed. Check internet connection."
            }
        }
    }

    fun uploadAllPending() {
        viewModelScope.launch {
            val pendingList = publicPhotos.value.filter { it.cloudUrl.isNullOrBlank() }
            if (pendingList.isEmpty()) {
                _statusMessage.value = "All photos already synced to cloud!"
                return@launch
            }
            _isUploading.value = true
            var uploaded = 0
            for (photo in pendingList) {
                val success = repository.uploadToCloud(photo)
                if (success) uploaded++
                _uploadProgress.value = (uploaded * 100) / pendingList.size
            }
            _isUploading.value = false
            _statusMessage.value = "Cloud Sync: $uploaded/${pendingList.size} photos uploaded"
        }
    }

    fun toggleFavorite(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(photo)
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = photo.copy(isFavorite = !photo.isFavorite)
            }
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = null
            }
            _statusMessage.value = "Photo deleted"
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(application)
                    val vaultManager = VaultManager(application)
                    val settingsManager = AppSettingsManager(application)
                    val repository = PhotoRepository(application, db.photoDao(), vaultManager)
                    return GalleryViewModel(application, repository, vaultManager, settingsManager) as T
                }
            }
    }
}
