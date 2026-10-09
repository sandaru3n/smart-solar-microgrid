package com.ead.solargrid.security

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ead.solargrid.BuildConfig

/**
 * The system side of [BiometricGate]: checks what the device can do and shows `BiometricPrompt`
 * with biometrics or the screen lock (PIN / pattern / password) as fallback.
 *
 * Create it in the activity's `onCreate` so a prompt that is up during rotation reports back
 * to the new instance.
 */
class BiometricGatePrompt(
    private val activity: FragmentActivity,
    title: CharSequence,
    subtitle: CharSequence,
    private val listener: Listener
) {

    interface Listener {
        fun onAuthSucceeded()
        fun onAuthError(errorCode: Int)
    }

    private val authenticators = allowedAuthenticators(Build.VERSION.SDK_INT)

    // No negative button: it must not be set when DEVICE_CREDENTIAL is allowed.
    private val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(authenticators)
        .build()

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                debug("succeeded")
                listener.onAuthSucceeded()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                debug("error $errorCode")
                listener.onAuthError(errorCode)
            }

            // A single rejected finger / face: the system prompt says so and stays up.
            override fun onAuthenticationFailed() = Unit
        }
    )

    /** `BiometricManager.canAuthenticate` for the same authenticators the prompt will ask for. */
    fun availability(): Int = BiometricManager.from(activity).canAuthenticate(authenticators)

    fun show() {
        prompt.authenticate(promptInfo)
    }

    /** Opens the device's security settings so the user can set a screen lock. */
    fun openSecuritySettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun debug(event: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Auth $event")
    }

    companion object {
        private const val TAG = "BiometricGate"

        /**
         * Strong biometrics or the screen lock. Per the androidx.biometric docs,
         * BIOMETRIC_STRONG | DEVICE_CREDENTIAL is not supported on API 28-29 (build() throws),
         * so those versions use BIOMETRIC_WEAK | DEVICE_CREDENTIAL instead.
         */
        fun allowedAuthenticators(sdkInt: Int): Int =
            if (sdkInt == Build.VERSION_CODES.P || sdkInt == Build.VERSION_CODES.Q) {
                BIOMETRIC_WEAK or DEVICE_CREDENTIAL
            } else {
                BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            }
    }
}
