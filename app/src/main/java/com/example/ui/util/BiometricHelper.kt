package com.example.ui.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Detailed status of Biometric Hardware & Enrollment on the current device.
 */
enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED
}

/**
 * Utility wrapper around androidx.biometric library for securing application
 * access and encrypted vault operations.
 */
object BiometricHelper {

    fun getBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return getBiometricStatus(context) == BiometricStatus.AVAILABLE
    }

    fun getStatusDescription(context: Context): String {
        return when (getBiometricStatus(context)) {
            BiometricStatus.AVAILABLE -> "Biometric hardware is active & enrolled"
            BiometricStatus.NOT_ENROLLED -> "No fingerprint or face registered on device"
            BiometricStatus.NO_HARDWARE -> "No biometric sensor detected on this device"
            BiometricStatus.HARDWARE_UNAVAILABLE -> "Biometric sensor is temporarily busy"
            BiometricStatus.SECURITY_UPDATE_REQUIRED -> "Security patch update required for biometrics"
            BiometricStatus.UNSUPPORTED -> "Biometric authentication unsupported"
        }
    }

    /**
     * Prompts standard androidx.biometric prompt on the hosting FragmentActivity.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Secure Vault",
        subtitle: String = "Verify your fingerprint or face",
        description: String? = null,
        negativeButtonText: String = "Cancel",
        onSuccessResult: (BiometricPrompt.AuthenticationResult) -> Unit = {},
        onErrorCode: (errorCode: Int, errString: String) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (!isBiometricAvailable(activity)) {
            onErrorCode(
                BiometricPrompt.ERROR_HW_NOT_PRESENT,
                getStatusDescription(activity)
            )
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccessResult(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onErrorCode(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )

        if (!description.isNullOrBlank()) {
            promptInfoBuilder.setDescription(description)
        }

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onErrorCode(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, e.localizedMessage ?: "Biometric prompt error")
        }
    }

    /**
     * Convenience overload for simpler callback lambdas.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Secure Vault",
        subtitle: String = "Verify your fingerprint or face",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        promptBiometric(
            activity = activity,
            title = title,
            subtitle = subtitle,
            description = null,
            negativeButtonText = "Cancel",
            onSuccessResult = { onSuccess() },
            onErrorCode = { _, err -> onError(err) },
            onFailed = {}
        )
    }
}
