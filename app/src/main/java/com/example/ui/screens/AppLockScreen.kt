package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.GalleryViewModel
import com.example.ui.components.GlassmorphismBackground
import com.example.ui.components.LocalIsDarkMode
import com.example.ui.components.liquidGlass
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentRose
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.BiometricHelper

/**
 * High-security application lock overlay powered by androidx.biometric.
 * Guards user photos and app state until biometric or PIN identity verification succeeds.
 */
@Composable
fun AppLockScreen(
    viewModel: GalleryViewModel,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val isDark = LocalIsDarkMode.current
    val isBiometricReady = remember(context) { BiometricHelper.isBiometricAvailable(context) }
    val vaultAccount by viewModel.vaultAccount.collectAsStateWithLifecycle()

    var showPinPad by remember { mutableStateOf(!isBiometricReady) }
    var enteredPin by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var hasAutoTriggered by remember { mutableStateOf(false) }

    fun requestBiometric() {
        if (activity == null || !isBiometricReady) {
            authError = "Biometrics not available. Please unlock using PIN."
            showPinPad = true
            return
        }
        authError = null
        BiometricHelper.promptBiometric(
            activity = activity,
            title = "Unlock Cloud Gallery",
            subtitle = "Verify your fingerprint or face to access your photos",
            negativeButtonText = "Use PIN",
            onSuccessResult = {
                authError = null
                onUnlock()
            },
            onErrorCode = { errorCode, errString ->
                // If user clicks negative button ("Use PIN"), switch to PIN pad
                if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    showPinPad = true
                } else if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) {
                    authError = errString
                }
            },
            onFailed = {
                authError = "Biometric not recognized. Please try again."
            }
        )
    }

    // Auto-prompt biometric on display
    LaunchedEffect(Unit) {
        if (!hasAutoTriggered && isBiometricReady && activity != null) {
            hasAutoTriggered = true
            requestBiometric()
        }
    }

    GlassmorphismBackground(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("app_lock_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Shield Emblem in Liquid Glass Orb
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .liquidGlass(
                        shape = CircleShape,
                        tint = if (isDark) Color(0xFF1E2332) else Color.White,
                        tintAlpha = if (isDark) 0.85f else 0.95f,
                        borderAlpha = if (isDark) 0.55f else 0.45f,
                        elevation = 16.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "App Lock Icon",
                    tint = AccentGold,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Cloud Gallery Secured",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isBiometricReady) {
                    "Biometric authentication or PIN is required to access your gallery"
                } else {
                    "Enter your security PIN to continue"
                },
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )

            authError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentRose.copy(alpha = 0.15f))
                        .border(1.dp, AccentRose.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = err,
                        color = AccentRose,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PIN Dots Indicator if in PIN mode
            AnimatedVisibility(visible = showPinPad, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        repeat(6) { index ->
                            val isFilled = index < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .liquidGlass(
                                        shape = CircleShape,
                                        tint = if (isFilled) AccentGold else (if (isDark) Color(0xFF1B202E) else Color.White),
                                        tintAlpha = if (isFilled) 0.95f else (if (isDark) 0.60f else 0.85f),
                                        borderAlpha = if (isFilled) 0.80f else 0.40f,
                                        elevation = if (isFilled) 6.dp else 2.dp
                                    )
                            )
                        }
                    }

                    // Numeric PIN Pad
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("bio", "0", "delete")
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    if (isDark) listOf(Color(0x28FFFFFF), Color(0x10FFFFFF))
                                                    else listOf(Color(0x45FFFFFF), Color(0x15FFFFFF))
                                                )
                                            )
                                            .border(
                                                width = 1.2.dp,
                                                brush = Brush.linearGradient(
                                                    if (isDark) listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.15f),
                                                        Color.White.copy(alpha = 0.05f),
                                                        Color.White.copy(alpha = 0.35f)
                                                    ) else listOf(
                                                        Color.White.copy(alpha = 0.90f),
                                                        Color(0x20000000),
                                                        Color(0x08000000),
                                                        Color.White.copy(alpha = 0.60f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .clickable {
                                                when (key) {
                                                    "delete" -> {
                                                        if (enteredPin.isNotEmpty()) {
                                                            enteredPin = enteredPin.dropLast(1)
                                                            authError = null
                                                        }
                                                    }
                                                    "bio" -> {
                                                        if (isBiometricReady) {
                                                            requestBiometric()
                                                        }
                                                    }
                                                    else -> {
                                                        if (enteredPin.length < 6) {
                                                            val updated = enteredPin + key
                                                            enteredPin = updated
                                                            authError = null
                                                            
                                                            // Strict 6-digit PIN verification
                                                            if (updated.length == 6) {
                                                                val ok = viewModel.verifyAppLockWithPin(updated)
                                                                if (ok) {
                                                                    onUnlock()
                                                                    return@clickable
                                                                } else {
                                                                    authError = "Incorrect 6-Digit PIN"
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (key) {
                                            "delete" -> Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Delete",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            "bio" -> {
                                                if (isBiometricReady) {
                                                    Icon(
                                                        imageVector = Icons.Default.Fingerprint,
                                                        contentDescription = "Biometrics",
                                                        tint = Color(0xFF64D2FF),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            else -> Text(
                                                text = key,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Biometric Primary Prompt Button
            if (isBiometricReady) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { requestBiometric() },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGold,
                        contentColor = OledBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(52.dp)
                        .testTag("app_biometric_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Icon",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Unlock with Biometrics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (!showPinPad) {
                    TextButton(
                        onClick = { showPinPad = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Or unlock with 6-digit PIN",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
