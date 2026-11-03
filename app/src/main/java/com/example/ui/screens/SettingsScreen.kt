package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.GalleryViewModel
import com.example.ui.components.LocalGlassStyle
import com.example.ui.components.LocalIsDarkMode
import com.example.ui.components.liquidGlass
import com.example.ui.components.liquidGlassThemed
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.util.BiometricHelper

@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val darkModeSetting by viewModel.darkModeSetting.collectAsStateWithLifecycle()
    val autoCloudBackup by viewModel.autoCloudBackup.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val hasAppLockPin by viewModel.hasAppLockPin.collectAsStateWithLifecycle()
    val vaultAccount by viewModel.vaultAccount.collectAsStateWithLifecycle()

    val syncedCount by viewModel.syncedCount.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val localOnlyCount by viewModel.localOnlyCount.collectAsStateWithLifecycle()

    val isDark = LocalIsDarkMode.current
    val isBiometricReady = remember(context) { BiometricHelper.isBiometricAvailable(context) }
    val biometricDesc = remember(context) { BiometricHelper.getStatusDescription(context) }

    var showSetAppLockPinDialog by remember { mutableStateOf(false) }
    var showChangeAppLockPinDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeveloperAccountDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }

    val screenBg = if (isDark) Color(0xFF10121A) else Color(0xFFF4F6F9)
    val cardBg = if (isDark) Color(0xFF1B1E28) else Color.White
    val cardBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x10000000)

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("settings_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mobile Settings Top Header
            item(key = "header") {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text(
                        text = "Settings",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Preferences, security & vault management",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // SECTION 1: ACCOUNT & VAULT
            item(key = "section_account") {
                SettingsCategoryLabel("ACCOUNT & VAULT")
                Spacer(modifier = Modifier.height(6.dp))
                SettingsCard(cardBg = cardBg, cardBorder = cardBorder) {
                    if (vaultAccount != null) {
                        val acc = vaultAccount!!
                        // Profile Banner Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(AccentGold.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.userName,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Vault Active • Cross-Device Protected",
                                    color = AccentEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        SettingsDivider(isDark)

                        // Unique Vault ID Row
                        SettingsListRow(
                            icon = Icons.Default.Security,
                            iconColor = AccentGold,
                            title = "Unique Vault ID",
                            subtitle = acc.vaultId,
                            subtitleFontFamily = FontFamily.Monospace,
                            trailing = {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Vault ID", acc.vaultId))
                                        Toast.makeText(context, "Vault ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("copy_vault_id_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Vault ID",
                                        tint = AccentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Vault ID", acc.vaultId))
                                Toast.makeText(context, "Vault ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        )

                        SettingsDivider(isDark)

                        // Change PIN Row
                        SettingsListRow(
                            icon = Icons.Default.Lock,
                            iconColor = Color(0xFF60A5FA),
                            title = "Change Master PIN",
                            subtitle = "Update 6-digit vault passcode",
                            trailing = { SettingsChevron() },
                            onClick = { showChangePasswordDialog = true }
                        )

                        SettingsDivider(isDark)

                        // Log Out Row
                        SettingsListRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconColor = AccentRose,
                            title = "Log Out from Vault",
                            subtitle = "Exit current vault session",
                            trailing = { SettingsChevron() },
                            onClick = { showLogoutDialog = true }
                        )
                    } else {
                        SettingsListRow(
                            icon = Icons.Default.Security,
                            iconColor = AccentGold,
                            title = "Set Up Encrypted Vault",
                            subtitle = "Create account & receive your Unique Vault ID",
                            trailing = { SettingsChevron() },
                            onClick = { viewModel.setTab(com.example.ui.GalleryTab.VAULT) }
                        )
                    }
                }
            }

            // SECTION 2: SECURITY & APP LOCK
            item(key = "section_security") {
                SettingsCategoryLabel("SECURITY & APP LOCK")
                Spacer(modifier = Modifier.height(6.dp))
                SettingsCard(cardBg = cardBg, cardBorder = cardBorder) {
                    // App Lock Protection Toggle
                    SettingsListRow(
                        icon = Icons.Default.Shield,
                        iconColor = AccentGold,
                        title = "App Lock Protection",
                        subtitle = if (hasAppLockPin) "PIN Protection Active" else "Set a PIN to lock app on open",
                        trailing = {
                            Switch(
                                checked = appLockEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (!hasAppLockPin) {
                                            // Must enter and confirm PIN first!
                                            showSetAppLockPinDialog = true
                                        } else {
                                            viewModel.setAppLockSetting(true)
                                            Toast.makeText(context, "App Lock enabled", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        viewModel.setAppLockSetting(false)
                                        Toast.makeText(context, "App Lock disabled", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentGold,
                                    uncheckedThumbColor = TextTertiary,
                                    uncheckedTrackColor = Color(0x30FFFFFF)
                                ),
                                modifier = Modifier.testTag("app_lock_switch")
                            )
                        }
                    )

                    // If App Lock is enabled, show Change App Lock PIN
                    if (appLockEnabled || hasAppLockPin) {
                        SettingsDivider(isDark)
                        SettingsListRow(
                            icon = Icons.Default.LockReset,
                            iconColor = Color(0xFFF59E0B),
                            title = "Change App Lock PIN",
                            subtitle = "Update your security passcode",
                            trailing = { SettingsChevron() },
                            onClick = { showChangeAppLockPinDialog = true }
                        )
                    }

                    SettingsDivider(isDark)

                    // Vault Biometric Unlock
                    SettingsListRow(
                        icon = Icons.Default.Fingerprint,
                        iconColor = Color(0xFF38BDF8),
                        title = "Vault Biometric Unlock",
                        subtitle = "Unlock encrypted photos with fingerprint or face",
                        trailing = {
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = { viewModel.setBiometricSetting(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentEmerald,
                                    uncheckedThumbColor = TextTertiary,
                                    uncheckedTrackColor = Color(0x30FFFFFF)
                                ),
                                modifier = Modifier.testTag("biometric_switch")
                            )
                        }
                    )

                    SettingsDivider(isDark)

                    // Biometric Sensor Hardware Status & Test
                    SettingsListRow(
                        icon = Icons.Default.Security,
                        iconColor = if (isBiometricReady) AccentEmerald else TextTertiary,
                        title = "Biometric Hardware Sensor",
                        subtitle = biometricDesc,
                        trailing = {
                            if (isBiometricReady && activity != null) {
                                TextButton(
                                    onClick = {
                                        BiometricHelper.promptBiometric(
                                            activity = activity,
                                            title = "Biometric Sensor Test",
                                            subtitle = "Touch sensor to verify authentication",
                                            onSuccess = {
                                                Toast.makeText(context, "Biometric verified successfully!", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Result: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                ) {
                                    Text("Test", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    )

                    SettingsDivider(isDark)

                    // Secret Vault Gesture Row
                    SettingsListRow(
                        icon = Icons.Default.Security,
                        iconColor = AccentGold,
                        title = "Secret Vault Gesture Trigger",
                        subtitle = "Drag from bottom-left corner to top-right corner & hold for 7s in Gallery to open Vault"
                    )
                }
            }

            // SECTION 3: DISPLAY & LAYOUT
            item(key = "section_display") {
                SettingsCategoryLabel("DISPLAY & LAYOUT")
                Spacer(modifier = Modifier.height(6.dp))
                SettingsCard(cardBg = cardBg, cardBorder = cardBorder) {
                    val currentModeLabel = when (darkModeSetting) {
                        "light" -> "Light Mode"
                        "system" -> "System Default"
                        else -> "Dark Mode"
                    }
                    val currentModeIcon = when (darkModeSetting) {
                        "light" -> Icons.Default.Brightness7
                        "system" -> Icons.Default.BrightnessAuto
                        else -> Icons.Default.Brightness4
                    }

                    // Theme Mode Selection Row
                    SettingsListRow(
                        icon = currentModeIcon,
                        iconColor = Color(0xFF818CF8),
                        title = "Theme Mode",
                        subtitle = currentModeLabel,
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = currentModeLabel,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                SettingsChevron()
                            }
                        },
                        onClick = { showThemeDialog = true }
                    )

                    SettingsDivider(isDark)

                    // Grid Columns Selection Row
                    SettingsListRow(
                        icon = Icons.Default.GridView,
                        iconColor = Color(0xFF34D399),
                        title = "Gallery Grid Columns",
                        subtitle = "$gridColumns columns per row",
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${gridColumns}x",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                SettingsChevron()
                            }
                        },
                        onClick = { showGridDialog = true }
                    )
                }
            }

            // SECTION 4: STORAGE & SYNC (Room Database Offline-First)
            item(key = "section_storage") {
                SettingsCategoryLabel("ROOM DATABASE & SYNC")
                Spacer(modifier = Modifier.height(6.dp))
                SettingsCard(cardBg = cardBg, cardBorder = cardBorder) {
                    // Local Database Cache Status
                    SettingsListRow(
                        icon = Icons.Default.Storage,
                        iconColor = Color(0xFF10B981),
                        title = "Offline Room Database",
                        subtitle = "Local SQLite persistent cache active • Fast offline access"
                    )

                    SettingsDivider(isDark)

                    // Local media indexed
                    SettingsListRow(
                        icon = Icons.Default.PhoneAndroid,
                        iconColor = Color(0xFF60A5FA),
                        title = "Local Device Photos Indexed",
                        subtitle = "$localOnlyCount photo(s) stored locally on device"
                    )

                    SettingsDivider(isDark)

                    // Cloud Sync Stats
                    SettingsListRow(
                        icon = Icons.Default.CloudSync,
                        iconColor = Color(0xFFF59E0B),
                        title = "Cloud Synchronization Status",
                        subtitle = "$syncedCount Synced • $pendingSyncCount Pending Upload"
                    )

                    SettingsDivider(isDark)

                    // Auto Cloud Backup Switch
                    SettingsListRow(
                        icon = Icons.Default.CloudDone,
                        iconColor = Color(0xFF38BDF8),
                        title = "Auto Cloud Backup",
                        subtitle = "Automatically sync encrypted photos to cloud",
                        trailing = {
                            Switch(
                                checked = autoCloudBackup,
                                onCheckedChange = { viewModel.setAutoCloudBackup(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentEmerald,
                                    uncheckedThumbColor = TextTertiary,
                                    uncheckedTrackColor = Color(0x30FFFFFF)
                                ),
                                modifier = Modifier.testTag("auto_cloud_switch")
                            )
                        }
                    )

                    SettingsDivider(isDark)

                    // Rescan Mobile Device Photos
                    SettingsListRow(
                        icon = Icons.Default.Refresh,
                        iconColor = Color(0xFFFBBF24),
                        title = "Rescan Device Photos",
                        subtitle = "Scan device storage & update Room database",
                        trailing = { SettingsChevron() },
                        onClick = {
                            viewModel.refreshDevicePhotos()
                            Toast.makeText(context, "Scanning & caching photos to Room DB...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // SECTION 5: ABOUT & DEVELOPER
            item(key = "section_about") {
                SettingsCategoryLabel("ABOUT & DEVELOPER")
                Spacer(modifier = Modifier.height(6.dp))
                SettingsCard(cardBg = cardBg, cardBorder = cardBorder) {
                    // Developer Row
                    SettingsListRow(
                        icon = Icons.Default.Person,
                        iconColor = AccentGold,
                        title = "Developed by Mishuk Babu Shoukhin",
                        subtitle = "mishukbabushoukhin@gmail.com",
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Developer",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                SettingsChevron()
                            }
                        },
                        onClick = {
                            showDeveloperAccountDialog = true
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:mishukbabushoukhin@gmail.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "Inquiry for Mishuk Babu Shoukhin - Cloud Gallery Developer")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:mishukbabushoukhin@gmail.com"))
                                    context.startActivity(fallback)
                                } catch (ignored: Exception) {}
                            }
                        },
                        modifier = Modifier.testTag("developer_profile_footer")
                    )

                    SettingsDivider(isDark)

                    // Security Architecture
                    SettingsListRow(
                        icon = Icons.Default.Security,
                        iconColor = AccentEmerald,
                        title = "Security Architecture",
                        subtitle = "AES-256-GCM cipher • PBKDF2 derivation • Room Offline-First"
                    )

                    SettingsDivider(isDark)

                    // App Version
                    SettingsListRow(
                        icon = Icons.Default.Info,
                        iconColor = TextSecondary,
                        title = "Cloud Gallery",
                        subtitle = "Version 2.4.0 • Build 42"
                    )
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Set App Lock PIN Dialog (Enter PIN + Confirm PIN)
    if (showSetAppLockPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSetAppLockPinDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = AccentGold)
                    Text("Set App Lock PIN", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a 4 to 6-digit PIN and confirm it. This PIN will be required to open Cloud Gallery.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                pinInput = it
                                errorMessage = null
                            }
                        },
                        label = { Text("Enter 6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                confirmPinInput = it
                                errorMessage = null
                            }
                        },
                        label = { Text("Confirm 6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = AccentRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 6 || !pinInput.all { it.isDigit() }) {
                            errorMessage = "PIN must be exactly 6 digits."
                            return@Button
                        }
                        if (pinInput != confirmPinInput) {
                            errorMessage = "PINs do not match. Please re-enter."
                            return@Button
                        }
                        val ok = viewModel.setAppLockPin(pinInput)
                        if (ok) {
                            showSetAppLockPinDialog = false
                            Toast.makeText(context, "6-Digit App Lock PIN set successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = "Failed to set PIN. Must be 6 digits."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OledBlack)
                ) {
                    Text("Confirm & Enable", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetAppLockPinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1E26) else Color.White
        )
    }

    // 2. Change App Lock PIN Dialog
    if (showChangeAppLockPinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmNewPin by remember { mutableStateOf("") }
        var changeError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangeAppLockPinDialog = false },
            title = { Text("Change App Lock PIN", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                currentPin = it
                                changeError = null
                            }
                        },
                        label = { Text("Current 6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                newPin = it
                                changeError = null
                            }
                        },
                        label = { Text("New 6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmNewPin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                confirmNewPin = it
                                changeError = null
                            }
                        },
                        label = { Text("Confirm New 6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (changeError != null) {
                        Text(changeError!!, color = AccentRose, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isCurrentValid = viewModel.verifyAppLockWithPin(currentPin)
                        if (!isCurrentValid) {
                            changeError = "Current PIN is incorrect."
                            return@Button
                        }
                        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
                            changeError = "New PIN must be exactly 6 digits."
                            return@Button
                        }
                        if (newPin != confirmNewPin) {
                            changeError = "New PINs do not match."
                            return@Button
                        }
                        val ok = viewModel.setAppLockPin(newPin)
                        if (ok) {
                            showChangeAppLockPinDialog = false
                            Toast.makeText(context, "6-Digit App Lock PIN updated successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            changeError = "Failed to update PIN."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OledBlack)
                ) {
                    Text("Save PIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeAppLockPinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1E26) else Color.White
        )
    }

    // 3. Theme Mode Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "Choose Theme Mode",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val themeOptions = listOf(
                    Triple("dark", "Dark Mode", Icons.Default.Brightness4),
                    Triple("light", "Light Mode", Icons.Default.Brightness7),
                    Triple("system", "System Default", Icons.Default.BrightnessAuto)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    themeOptions.forEach { (mode, label, icon) ->
                        val isSelected = darkModeSetting == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setDarkModeSetting(mode)
                                    showThemeDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .testTag("theme_mode_$mode"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setDarkModeSetting(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentGold,
                                    unselectedColor = TextTertiary
                                )
                            )
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) AccentGold else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = label,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = AccentGold)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1E26) else Color.White
        )
    }

    // 4. Grid Columns Selection Dialog
    if (showGridDialog) {
        AlertDialog(
            onDismissRequest = { showGridDialog = false },
            title = {
                Text(
                    text = "Gallery Grid Columns",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val columnOptions = listOf(
                    1 to "1 Column (Large Full-Width)",
                    2 to "2 Columns (Comfortable)",
                    3 to "3 Columns (Standard Grid)",
                    4 to "4 Columns (Compact)",
                    5 to "5 Columns (High Density)"
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    columnOptions.forEach { (cols, label) ->
                        val isSelected = gridColumns == cols
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setGridColumns(cols)
                                    showGridDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .testTag("grid_col_$cols"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setGridColumns(cols)
                                    showGridDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentGold,
                                    unselectedColor = TextTertiary
                                )
                            )
                            Text(
                                text = label,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridDialog = false }) {
                    Text("Close", color = AccentGold)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1E26) else Color.White
        )
    }

    // 5. Developer Account Profile Dialog
    if (showDeveloperAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperAccountDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AccentGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text("Mishuk Babu Shoukhin", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Lead Developer & Architect", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Official creator of Cloud Gallery. Contact for inquiries, custom features, or feedback.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0x18FFFFFF) else Color(0x0A000000))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Account Email", color = TextTertiary, fontSize = 11.sp)
                            Text("mishukbabushoukhin@gmail.com", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Developer Email", "mishukbabushoukhin@gmail.com"))
                                Toast.makeText(context, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Email", tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeveloperAccountDialog = false
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:mishukbabushoukhin@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Message for Mishuk Babu Shoukhin")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:mishukbabushoukhin@gmail.com"))
                            context.startActivity(fallback)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Mail, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Contact Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeveloperAccountDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1E26) else Color.White
        )
    }

    // 6. Change Vault Password Dialog
    if (showChangePasswordDialog) {
        var oldPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Master PIN", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it },
                        label = { Text("Current PIN/Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                newPass = it
                                errorMsg = null
                            }
                        },
                        label = { Text("New 6-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = AccentRose, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.length != 6 || !newPass.all { it.isDigit() }) {
                            errorMsg = "New PIN must be exactly 6 digits"
                            return@Button
                        }
                        val success = viewModel.changeVaultPassword(oldPass, newPass)
                        if (success) {
                            showChangePasswordDialog = false
                            Toast.makeText(context, "6-Digit Master PIN changed successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMsg = "Incorrect current password/PIN"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OledBlack)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1E26) else Color.White
        )
    }

    // 7. Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out from Vault Account?", color = TextPrimary) },
            text = {
                Text(
                    "You will need your Unique Vault ID and Password to unlock or restore the vault again.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logoutVaultAccount()
                        showLogoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = if (isDark) Color(0xFF1C1E26) else Color.White
        )
    }
}

@Composable
private fun SettingsCategoryLabel(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(
    cardBg: Color,
    cardBorder: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassThemed(shape = RoundedCornerShape(24.dp), elevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsListRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    subtitleFontFamily: FontFamily? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(12.dp),
                        tint = iconColor,
                        tintAlpha = 0.22f,
                        borderAlpha = 0.50f,
                        elevation = 3.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = subtitleFontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                trailing()
            }
        }
    }
}

@Composable
private fun SettingsDivider(isDark: Boolean) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        thickness = 0.8.dp,
        color = if (isDark) Color(0x12FFFFFF) else Color(0x0E000000)
    )
}

@Composable
private fun SettingsChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
        contentDescription = null,
        tint = TextTertiary,
        modifier = Modifier.size(14.dp)
    )
}
