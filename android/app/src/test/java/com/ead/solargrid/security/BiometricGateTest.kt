package com.ead.solargrid.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gate's state machine: idle -> prompting -> success / cancelled / error / unavailable. */
class BiometricGateTest {

    private fun prompting() = BiometricGate().apply { begin(BiometricManager.BIOMETRIC_SUCCESS) }

    @Test
    fun `starts locked and idle`() {
        val gate = BiometricGate()
        assertEquals(GateState.Idle, gate.state)
        assertFalse(gate.isUnlocked)
    }

    @Test
    fun `available device moves to prompting and asks for the prompt`() {
        val gate = BiometricGate()
        assertTrue(gate.begin(BiometricManager.BIOMETRIC_SUCCESS))
        assertEquals(GateState.Prompting, gate.state)
    }

    @Test
    fun `unknown status still tries the prompt`() {
        val gate = BiometricGate()
        assertTrue(gate.begin(BiometricManager.BIOMETRIC_STATUS_UNKNOWN))
        assertEquals(GateState.Prompting, gate.state)
    }

    @Test
    fun `success unlocks`() {
        val gate = prompting()
        gate.onSucceeded()
        assertEquals(GateState.Authenticated, gate.state)
        assertTrue(gate.isUnlocked)
    }

    @Test
    fun `cancel, back and system dismissals are cancelled`() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_TIMEOUT
        ).forEach { code ->
            val gate = prompting()
            gate.onError(code)
            assertEquals("code $code", GateState.Cancelled, gate.state)
            assertFalse(gate.isUnlocked)
        }
    }

    @Test
    fun `lockouts are locked out`() {
        listOf(BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT).forEach { code ->
            val gate = prompting()
            gate.onError(code)
            assertEquals("code $code", GateState.LockedOut, gate.state)
        }
    }

    @Test
    fun `nothing enrolled and no screen lock never shows the prompt`() {
        val gate = BiometricGate()
        assertFalse(gate.begin(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertEquals(GateState.NoScreenLock, gate.state)
        assertFalse(gate.isUnlocked)

        listOf(BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL).forEach { code ->
            val prompted = prompting()
            prompted.onError(code)
            assertEquals("code $code", GateState.NoScreenLock, prompted.state)
        }
    }

    @Test
    fun `missing or unusable hardware is unavailable`() {
        listOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
        ).forEach { code ->
            val gate = BiometricGate()
            assertFalse(gate.begin(code))
            assertEquals("code $code", GateState.Unavailable, gate.state)
        }

        listOf(BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_HW_NOT_PRESENT).forEach { code ->
            val gate = prompting()
            gate.onError(code)
            assertEquals("code $code", GateState.Unavailable, gate.state)
        }
    }

    @Test
    fun `unexpected codes fail closed as error`() {
        val gate = BiometricGate()
        assertFalse(gate.begin(12345))
        assertEquals(GateState.Error, gate.state)

        listOf(
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_NO_SPACE,
            BiometricPrompt.ERROR_VENDOR,
            -1
        ).forEach { code ->
            val prompted = prompting()
            prompted.onError(code)
            assertEquals("code $code", GateState.Error, prompted.state)
            assertFalse(prompted.isUnlocked)
        }
    }

    @Test
    fun `success that was not prompted for does not unlock`() {
        val gate = BiometricGate()
        gate.onSucceeded()
        assertFalse(gate.isUnlocked)

        val cancelled = prompting().apply { onError(BiometricPrompt.ERROR_USER_CANCELED) }
        cancelled.onSucceeded()
        assertEquals(GateState.Cancelled, cancelled.state)
    }

    @Test
    fun `begin only starts from idle`() {
        val gate = prompting()
        assertFalse(gate.begin(BiometricManager.BIOMETRIC_SUCCESS))
        gate.onSucceeded()
        assertFalse(gate.begin(BiometricManager.BIOMETRIC_SUCCESS))
        assertTrue(gate.isUnlocked)
    }

    @Test
    fun `lock returns any state to idle, and try again prompts afresh`() {
        val gate = prompting().apply { onSucceeded() }
        gate.lock()
        assertEquals(GateState.Idle, gate.state)
        assertFalse(gate.isUnlocked)

        val cancelled = prompting().apply { onError(BiometricPrompt.ERROR_USER_CANCELED) }
        cancelled.lock()
        assertTrue(cancelled.begin(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun `runIfUnlocked only runs after success`() {
        val gate = BiometricGate()
        var runs = 0
        assertFalse(gate.runIfUnlocked { runs++ })
        gate.begin(BiometricManager.BIOMETRIC_SUCCESS)
        assertFalse(gate.runIfUnlocked { runs++ })
        gate.onSucceeded()
        assertTrue(gate.runIfUnlocked { runs++ })
        assertEquals(1, runs)
    }

    @Test
    fun `authenticators avoid strong plus credential on API 28 and 29`() {
        val weakOrCredential = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val strongOrCredential = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        assertEquals(weakOrCredential, BiometricGatePrompt.allowedAuthenticators(Build.VERSION_CODES.P))
        assertEquals(weakOrCredential, BiometricGatePrompt.allowedAuthenticators(Build.VERSION_CODES.Q))
        assertEquals(strongOrCredential, BiometricGatePrompt.allowedAuthenticators(Build.VERSION_CODES.O))
        assertEquals(strongOrCredential, BiometricGatePrompt.allowedAuthenticators(Build.VERSION_CODES.O_MR1))
        assertEquals(strongOrCredential, BiometricGatePrompt.allowedAuthenticators(Build.VERSION_CODES.R))
        assertEquals(strongOrCredential, BiometricGatePrompt.allowedAuthenticators(35))
    }
}
