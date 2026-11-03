package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PhotoEntity
import com.example.data.VaultAccount
import com.example.ui.GalleryTab
import com.example.ui.GalleryViewModel
import com.example.ui.components.LocalIsDarkMode
import com.example.ui.components.PhotoGrid
import com.example.ui.components.liquidGlass
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.util.BiometricHelper

@Composable
fun VaultScreen(
    viewModel: GalleryViewModel,
    onOpenUploadDialog: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val vaultAccount by viewModel.vaultAccount.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val vaultPhotos by viewModel.vaultPhotos.collectAsStateWithLifecycle()

    var newlyCreatedAccount by remember { mutableStateOf<VaultAccount?>(null) }

    // Auto-lock whenever navigating away or leaving the Vault screen
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.lockVault(silent = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_screen")
    ) {
        when {
            vaultAccount == null -> {
                VaultAccountRegistrationView(
                    onAccountCreated = { acc ->
                        newlyCreatedAccount = acc
                    },
                    viewModel = viewModel,
                    onBackToGallery = { viewModel.setTab(GalleryTab.GALLERY) }
                )
            }
            !isVaultUnlocked -> {
                val lockoutSeconds by viewModel.lockoutRemainingSeconds.collectAsStateWithLifecycle()
                VaultLockpadView(
                    account = vaultAccount!!,
                    lockoutSeconds = lockoutSeconds,
                    onUnlock = { pwd -> viewModel.unlockVaultWithPassword(pwd) },
                    onBiometricSuccess = { viewModel.unlockVaultWithBiometrics() },
                    onSwitchAccount = { viewModel.logoutVaultAccount() },
                    onBackToGallery = { viewModel.setTab(GalleryTab.GALLERY) }
                )
            }
            else -> {
                VaultContentView(
                    vaultPhotos = vaultPhotos,
                    account = vaultAccount!!,
                    viewModel = viewModel,
                    onLock = { viewModel.lockVault() },
                    onBackToGallery = {
                        viewModel.setTab(GalleryTab.GALLERY)
                        viewModel.lockVault(silent = true)
                    },
                    onOpenUploadDialog = onOpenUploadDialog
                )
            }
        }

        // Newly created account dialog with Unique ID
        if (newlyCreatedAccount != null) {
            val acc = newlyCreatedAccount!!
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { newlyCreatedAccount = null },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Security, null, tint = AccentGold, modifier = Modifier.size(24.dp))
                        Text("Vault Account Created!", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Save your Unique Vault ID. You can use this ID and your password to unlock or restore your vault on ANY phone or device.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color(0xFF141722) else Color(0xFFF1F5F9))
                                .border(1.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "YOUR UNIQUE VAULT ID",
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = acc.vaultId,
                                    color = AccentGold,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Vault ID", acc.vaultId))
                                Toast.makeText(context, "Copied Vault ID to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0x25FFFFFF) else Color(0x10000000),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Vault ID")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { newlyCreatedAccount = null },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = OledBlack)
                    ) {
                        Text("Open My Vault", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = if (isDark) Color(0xFF181B26) else Color.White
            )
        }
    }
}

/**
 * Account registration & restoration screen (Requirement 4).
 * Nobody can put photos into the encrypted vault without an account.
 */
@Composable
private fun VaultAccountRegistrationView(
    onAccountCreated: (VaultAccount) -> Unit,
    viewModel: GalleryViewModel,
    onBackToGallery: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkMode.current
    var activeTab by remember { mutableStateOf(0) } // 0: Create, 1: Log In, 2: Restore Vault
    var userName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var loginName by remember { mutableStateOf("") }
    var loginId by remember { mutableStateOf("") }
    var restoreBackupUri by remember { mutableStateOf<Uri?>(null) }
    var restorePin by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreBackupUri = uri
            errorMessage = null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                IconButton(
                    onClick = onBackToGallery,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Gallery",
                        tint = TextPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(AccentGold.copy(alpha = 0.35f), Color(0x10FFFFFF))
                        )
                    )
                    .border(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.7f), AccentGold, Color.White.copy(alpha = 0.3f))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Encrypted Vault",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Clean Liquid Glass Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(26.dp),
                        tint = if (isDark) Color(0xFF181A24) else Color(0xFFFFFFFF),
                        tintAlpha = if (isDark) 0.8f else 0.95f,
                        borderAlpha = if (isDark) 0.35f else 0.15f
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 3-way Segmented Switch: Create Account vs Log In vs Restore Vault
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0x20FFFFFF) else Color(0x10000000))
                            .padding(4.dp)
                    ) {
                        listOf("Create", "Log In", "Restore").forEachIndexed { index, title ->
                            val isSelected = activeTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (isSelected) AccentGold else Color.Transparent)
                                    .clickable {
                                        activeTab = index
                                        errorMessage = null
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) OledBlack else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    when (activeTab) {
                        0 -> {
                            // Create Account
                            OutlinedTextField(
                                value = userName,
                                onValueChange = {
                                    userName = it
                                    errorMessage = null
                                },
                                label = { Text("Your Name") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_name_input")
                            )

                            OutlinedTextField(
                                value = pin,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                        pin = it
                                        errorMessage = null
                                    }
                                },
                                label = { Text("6-Digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_password_input")
                            )

                            OutlinedTextField(
                                value = confirmPin,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                        confirmPin = it
                                        errorMessage = null
                                    }
                                },
                                label = { Text("Confirm 6-Digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_password_input")
                            )
                        }
                        1 -> {
                            // Log In (local account switch)
                            OutlinedTextField(
                                value = loginName,
                                onValueChange = {
                                    loginName = it
                                    errorMessage = null
                                },
                                label = { Text("Your Name") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_name_input")
                            )

                            OutlinedTextField(
                                value = loginId,
                                onValueChange = {
                                    loginId = it
                                    errorMessage = null
                                },
                                label = { Text("Vault ID (e.g. VLT-XXXX)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_vault_id_input")
                            )

                            OutlinedTextField(
                                value = pin,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                        pin = it
                                        errorMessage = null
                                    }
                                },
                                label = { Text("6-Digit Password / PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input")
                            )
                        }
                        2 -> {
                            // Restore from Encrypted Backup Archive (.vaultbackup)
                            Text(
                                text = "Restore an existing encrypted Vault from a .vaultbackup file (reinstalls and device migrations).",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )

                            Button(
                                onClick = { backupPicker.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0x25FFFFFF) else Color(0x10000000),
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("select_backup_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (restoreBackupUri != null) "Backup File Selected" else "Select .vaultbackup file",
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedTextField(
                                value = restorePin,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                        restorePin = it
                                        errorMessage = null
                                    }
                                },
                                label = { Text("6-Digit Backup PIN / Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = AccentGold,
                                    unfocusedBorderColor = if (isDark) Color(0x30FFFFFF) else Color(0x30000000)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("restore_password_input")
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = AccentRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            when (activeTab) {
                                0 -> {
                                    if (userName.isBlank()) {
                                        errorMessage = "Please enter your name"
                                        return@Button
                                    }
                                    if (pin.length != 6 || !pin.all { it.isDigit() }) {
                                        errorMessage = "PIN must be exactly 6 digits"
                                        return@Button
                                    }
                                    if (pin != confirmPin) {
                                        errorMessage = "PINs do not match"
                                        return@Button
                                    }
                                    val acc = viewModel.createVaultAccount(userName, pin)
                                    if (acc != null) {
                                        onAccountCreated(acc)
                                    } else {
                                        errorMessage = "Could not create account"
                                    }
                                }
                                1 -> {
                                    if (loginName.isBlank()) {
                                        errorMessage = "Please enter your Name"
                                        return@Button
                                    }
                                    if (loginId.isBlank()) {
                                        errorMessage = "Please enter your Vault ID"
                                        return@Button
                                    }
                                    if (pin.length != 6 || !pin.all { it.isDigit() }) {
                                        errorMessage = "Enter your 6-digit password/PIN"
                                        return@Button
                                    }
                                    val ok = viewModel.loginWithVaultCredentials(loginName, loginId, pin)
                                    if (!ok) {
                                        errorMessage = "Invalid credentials. Unknown Vault ID or incorrect PIN."
                                    }
                                }
                                2 -> {
                                    if (restoreBackupUri == null) {
                                        errorMessage = "Please select a .vaultbackup file"
                                        return@Button
                                    }
                                    if (restorePin.length != 6 || !restorePin.all { it.isDigit() }) {
                                        errorMessage = "Enter the 6-digit PIN used when creating the backup"
                                        return@Button
                                    }
                                    isRestoring = true
                                    errorMessage = null
                                    viewModel.restoreBackupArchive(restoreBackupUri!!, restorePin) { success, msg ->
                                        isRestoring = false
                                        if (success) {
                                            Toast.makeText(context, "Vault restored: $msg", Toast.LENGTH_LONG).show()
                                        } else {
                                            errorMessage = "Restore failed: $msg"
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = OledBlack
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_account_button")
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(color = OledBlack, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                text = when (activeTab) {
                                    0 -> "Create Account"
                                    1 -> "Log In"
                                    else -> "Verify & Restore Vault"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Liquid Glass Lock Screen with Biometric Unlock (Requirement 4).
 */
@Composable
private fun VaultLockpadView(
    account: VaultAccount,
    lockoutSeconds: Long = 0L,
    onUnlock: (String) -> Boolean,
    onBiometricSuccess: () -> Unit,
    onSwitchAccount: () -> Unit,
    onBackToGallery: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val activity = context as? FragmentActivity
    val isBiometricReady = remember(context) { BiometricHelper.isBiometricAvailable(context) }
    var hasPromptedBiometric by remember { mutableStateOf(false) }

    LaunchedEffect(account.vaultId) {
        if (!hasPromptedBiometric && activity != null && isBiometricReady && account.isBiometricEnabled) {
            hasPromptedBiometric = true
            BiometricHelper.promptBiometric(
                activity = activity,
                title = "Unlock Encrypted Vault",
                subtitle = "Verify fingerprint or face to decrypt photos",
                description = "Account: ${account.userName} (${account.vaultId})",
                negativeButtonText = "Use PIN",
                onSuccessResult = { onBiometricSuccess() },
                onErrorCode = { _, err ->
                    if (!err.contains("cancel", ignoreCase = true)) {
                        errorText = err
                    }
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            IconButton(
                onClick = onBackToGallery,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Gallery",
                    tint = TextPrimary
                )
            }
        }

        // Vault Icon & ID Badge
        Box(
            modifier = Modifier
                .size(68.dp)
                .liquidGlass(
                    shape = CircleShape,
                    tint = if (isDark) Color(0xFF222634) else Color(0xFFFFFFFF),
                    tintAlpha = if (isDark) 0.8f else 0.95f,
                    borderAlpha = if (isDark) 0.45f else 0.25f
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Locked Vault",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Unique ID indicator pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0x20FFFFFF) else Color(0x10000000))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${account.userName} • ${account.vaultId}",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6-Digit Display Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                val isFilled = index < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) AccentGold else if (isDark) Color(0x25FFFFFF) else Color(0x18000000))
                        .border(1.dp, if (isFilled) AccentGold else if (isDark) Color(0x40FFFFFF) else Color(0x25000000), CircleShape)
                )
            }
        }

        if (lockoutSeconds > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentRose.copy(alpha = 0.15f))
                    .border(1.dp, AccentRose.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AccentRose,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Security Lockout: Retry in ${lockoutSeconds}s",
                    color = AccentRose,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (errorText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText!!,
                color = AccentRose,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Biometric Unlock Button
        if (activity != null && isBiometricReady && account.isBiometricEnabled) {
            Button(
                onClick = {
                    BiometricHelper.promptBiometric(
                        activity = activity,
                        title = "Unlock Encrypted Vault",
                        subtitle = "Verify your fingerprint or face",
                        onSuccess = { onBiometricSuccess() },
                        onError = { err -> errorText = err }
                    )
                },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0x25FFFFFF) else Color(0x10000000),
                    contentColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, if (isDark) Color(0x35FFFFFF) else Color(0x20000000), RoundedCornerShape(18.dp))
                    .testTag("biometric_unlock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = Color(0xFF64D2FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock with Fingerprint / Face ID",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Numeric Keypad for 6-Digit PIN
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (isBiometricReady && account.isBiometricEnabled) "bio" else "clear", "0", "delete")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isDark) Color(0x18FFFFFF) else Color(0x0C000000))
                                .border(1.dp, if (isDark) Color(0x15FFFFFF) else Color(0x15000000), RoundedCornerShape(20.dp))
                            .clickable {
                                if (lockoutSeconds > 0) return@clickable
                                when (key) {
                                    "delete" -> {
                                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        errorText = null
                                    }
                                    "clear" -> {
                                        enteredPin = ""
                                        errorText = null
                                    }
                                    "bio" -> {
                                        if (activity != null) {
                                            BiometricHelper.promptBiometric(
                                                activity = activity,
                                                title = "Unlock Encrypted Vault",
                                                subtitle = "Verify fingerprint or face to decrypt photos",
                                                onSuccess = { onBiometricSuccess() },
                                                onError = { err -> errorText = err }
                                            )
                                        }
                                    }
                                    else -> {
                                        if (enteredPin.length < 6) {
                                            val next = enteredPin + key
                                            enteredPin = next
                                            errorText = null
                                            if (next.length == 6) {
                                                val success = onUnlock(next)
                                                if (!success) {
                                                    errorText = "Incorrect 6-Digit PIN"
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .testTag("keypad_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "delete" -> Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            "bio" -> Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Biometrics",
                                tint = Color(0xFF64D2FF),
                                modifier = Modifier.size(24.dp)
                            )
                            "clear" -> Text("C", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            else -> Text(key, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

        Spacer(modifier = Modifier.height(18.dp))

        TextButton(
            onClick = onSwitchAccount,
            modifier = Modifier.testTag("switch_vault_account_button")
        ) {
            Text(
                text = "Log In with Different Account",
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Vault Content Screen (Unlocked state)
 */
@Composable
private fun VaultContentView(
    vaultPhotos: List<PhotoEntity>,
    account: VaultAccount,
    viewModel: GalleryViewModel,
    onLock: () -> Unit,
    onBackToGallery: () -> Unit,
    onOpenUploadDialog: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_content_view")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Liquid Glass
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBackToGallery,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit to Gallery",
                                tint = TextPrimary
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Encrypted Vault",
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentGold.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AES-256",
                                        color = AccentGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "${account.vaultId} • ${vaultPhotos.size} encrypted items",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Button(
                        onClick = onLock,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0x25FFFFFF) else Color(0x10000000),
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .border(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.15f))
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("lock_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = AccentGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Photos Grid
            PhotoGrid(
                photos = vaultPhotos,
                repository = viewModel.repository,
                isVaultUnlocked = true,
                onPhotoClick = { viewModel.selectPhoto(it) },
                onUploadClick = onOpenUploadDialog,
                emptyTitle = "Vault is Empty",
                emptySubtitle = "Photos stored in this vault are encrypted with AES-256-GCM. You can move photos from your mobile gallery here anytime."
            )
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = onOpenUploadDialog,
            containerColor = AccentGold,
            contentColor = OledBlack,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 86.dp)
                .testTag("add_to_vault_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Encrypted Photo",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
