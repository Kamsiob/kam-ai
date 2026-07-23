package com.kamsiob.kamai.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper over the platform biometric prompt. PART 3.
 *
 * Device mode authenticates with a fingerprint, face, or the phone's own PIN or
 * pattern, all through the one system prompt, which is what makes it recoverable
 * through the device.
 */
object Biometric {

    /** What the phone can offer. */
    enum class Availability { READY, NONE_ENROLLED, UNAVAILABLE }

    private const val DEVICE_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(activity: FragmentActivity): Availability {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(DEVICE_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            else -> Availability.UNAVAILABLE
        }
    }

    /** True when at least a device credential (PIN, pattern, or biometric) is set. */
    fun canAuthenticate(activity: FragmentActivity): Boolean =
        availability(activity) == Availability.READY

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    // User cancelled or a hard error. Either way, stay locked.
                    onError(message.toString())
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(DEVICE_AUTHENTICATORS)
            .build()

        prompt.authenticate(info)
    }
}
