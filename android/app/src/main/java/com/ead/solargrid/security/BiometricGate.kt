package com.ead.solargrid.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/** Where a [BiometricGate] stands. Only [Authenticated] lets the guarded work run. */
sealed interface GateState {
    /** Locked, and no prompt is up yet: the screen should launch one. */
    data object Idle : GateState

    /** The system prompt (or the screen-lock confirmation) is showing. */
    data object Prompting : GateState

    data object Authenticated : GateState

    /** User cancelled, pressed back, or the system dismissed the prompt. */
    data object Cancelled : GateState

    /** Too many failed attempts. */
    data object LockedOut : GateState

    /** No biometrics enrolled and no PIN / pattern / password set. */
    data object NoScreenLock : GateState

    /** No usable hardware, or the device cannot run the prompt. */
    data object Unavailable : GateState

    /** Anything unexpected. Fails closed like the rest. */
    data object Error : GateState
}

/**
 * Local, on-device gate: fails closed, keeps no state beyond this object, and writes nothing to disk.
 * Pure Kotlin so it can be unit tested; [BiometricGatePrompt] feeds it the system prompt's results.
 *
 * Idle -> Prompting -> Authenticated | Cancelled | LockedOut | NoScreenLock | Unavailable | Error,
 * and [lock] takes any state back to Idle.
 */
class BiometricGate {

    var state: GateState = GateState.Idle
        private set

    val isUnlocked: Boolean get() = state == GateState.Authenticated

    /**
     * Starts an attempt from Idle with the result of `BiometricManager.canAuthenticate`.
     * Returns true when the prompt should be shown now.
     */
    fun begin(availability: Int): Boolean {
        if (state != GateState.Idle) return false
        state = when (availability) {
            BiometricManager.BIOMETRIC_SUCCESS,
            // The library docs allow trying anyway; any failure still lands in a locked state.
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> GateState.Prompting
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> GateState.NoScreenLock
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> GateState.Unavailable
            else -> GateState.Error
        }
        return state == GateState.Prompting
    }

    /** Only a prompt this gate started can unlock it; a stale callback is ignored. */
    fun onSucceeded() {
        if (state == GateState.Prompting) state = GateState.Authenticated
    }

    fun onError(errorCode: Int) {
        if (state != GateState.Prompting) return
        state = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_TIMEOUT -> GateState.Cancelled
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> GateState.LockedOut
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> GateState.NoScreenLock
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> GateState.Unavailable
            else -> GateState.Error
        }
    }

    /** Back to Idle: on leaving or backgrounding the screen, and before "Try again". */
    fun lock() {
        state = GateState.Idle
    }

    /** Runs [action] only while unlocked. Returns whether it ran. */
    inline fun runIfUnlocked(action: () -> Unit): Boolean {
        if (!isUnlocked) return false
        action()
        return true
    }
}
