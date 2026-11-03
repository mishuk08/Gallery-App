package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.GalleryViewModel
import com.example.ui.components.liquidGlass
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun UploadDialog(
    initialEncrypted: Boolean = false,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit
) {
    var isEncrypted by remember { mutableStateOf(initialEncrypted) }
    var title by remember { mutableStateOf("") }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var enteredPin by remember { mutableStateOf("") }

    val vaultAccount by viewModel.vaultAccount.collectAsState()
    val isVaultConfigured = vaultAccount != null
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .liquidGlass(
                    shape = RoundedCornerShape(28.dp),
                    tint = Color(0xFF161822),
                    tintAlpha = 0.88f,
                    borderAlpha = 0.4f,
                    elevation = 16.dp
                )
                .padding(22.dp)
                .testTag("upload_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon & close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isEncrypted) AccentGold.copy(alpha = 0.2f) else Color(0x25FFFFFF)
                                )
                                .border(
                                    1.dp,
                                    if (isEncrypted) AccentGold.copy(alpha = 0.5f) else Color(0x35FFFFFF),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = if (isEncrypted) AccentGold else Color(0xFF64D2FF),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isEncrypted) "Add to Vault" else "Add Photos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isEncrypted) "Encrypted photo storage" else "Local gallery collection",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Modern Segmented Destination Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x20FFFFFF))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (!isEncrypted) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { isEncrypted = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = if (!isEncrypted) TextPrimary else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Local Gallery",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (!isEncrypted) TextPrimary else TextSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isEncrypted) AccentGold.copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isEncrypted) AccentGold.copy(alpha = 0.6f) else Color.Transparent,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { isEncrypted = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isEncrypted) AccentGold else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Encrypted Vault",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isEncrypted) AccentGold else TextSecondary
                            )
                        }
                    }
                }

                // If Vault is selected without an account
                if (isEncrypted && !isVaultConfigured) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentRose.copy(alpha = 0.15f))
                            .border(1.dp, AccentRose.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "A Vault account is required before adding encrypted photos. Please create an account in the Vault tab.",
                            color = AccentRose,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                } else if (isEncrypted && !isVaultUnlocked) {
                    // Quick PIN unlock field if vault is locked
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) enteredPin = it },
                        label = { Text("6-Digit Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color(0x30FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_pin_input")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Photo Selection Zone
                if (selectedUris.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x10FFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 26.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = if (isEncrypted) AccentGold else Color(0xFF64D2FF),
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Tap to choose photos",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Select from device storage or camera roll",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isEncrypted) AccentGold else Color(0xFF64D2FF),
                                        contentColor = OledBlack
                                    ),
                                    modifier = Modifier.testTag("pick_photos_button")
                                ) {
                                    Text("Browse Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val pinToUse = if (isEncrypted && !isVaultUnlocked) enteredPin.ifBlank { null } else null
                                        viewModel.importSamplePhoto(isEncrypted = isEncrypted, pin = pinToUse)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("add_sample_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Demo Photo", color = TextPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Selected photos pill indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x18FFFFFF))
                            .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${selectedUris.size} photo(s) selected",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Ready to add",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Text("Change", color = Color(0xFF64D2FF), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optional title/caption
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Photo Title (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = if (isEncrypted) AccentGold else Color(0xFF64D2FF),
                        unfocusedBorderColor = Color(0x30FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upload_title_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom actions
                val canConfirm = selectedUris.isNotEmpty() && (!isEncrypted || isVaultUnlocked || enteredPin.length == 6)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        enabled = canConfirm,
                        onClick = {
                            val pinToUse = if (isEncrypted && !isVaultUnlocked) enteredPin else null
                            viewModel.importPhotos(
                                uris = selectedUris,
                                title = title,
                                isEncrypted = isEncrypted,
                                autoUpload = false,
                                pin = pinToUse
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEncrypted) AccentGold else Color(0xFF64D2FF),
                            contentColor = OledBlack
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("confirm_upload_button")
                    ) {
                        Text(
                            text = if (isEncrypted) "Encrypt & Save to Vault" else "Add to Gallery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
