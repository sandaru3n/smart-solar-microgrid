package com.ead.solargrid.qr

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.ead.solargrid.api.ApiService
import com.ead.solargrid.security.BiometricGate
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * GET /api/reservations/{id}/qr is only called once the biometric gate is unlocked, the same way
 * ReservationQrViewModel.load() guards it.
 */
class QrGateFlowTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: QrRepository
    private lateinit var gate: BiometricGate

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        repository = QrRepository(api)
        gate = BiometricGate()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** What the ViewModel does on start, "Refresh QR" and retry. */
    private suspend fun loadIfUnlocked(): QrResult<*>? {
        var result: QrResult<*>? = null
        gate.runIfUnlocked { result = repository.getQr(RESERVATION_ID) }
        return result
    }

    @Test
    fun `qr endpoint is not called before authentication succeeds`() = runTest {
        server.enqueue(qrOk())

        loadIfUnlocked()
        assertEquals("idle", 0, server.requestCount)

        gate.begin(BiometricManager.BIOMETRIC_SUCCESS)
        loadIfUnlocked()
        assertEquals("prompting", 0, server.requestCount)

        gate.onSucceeded()
        val result = loadIfUnlocked()
        assertEquals(1, server.requestCount)
        assertTrue(result is QrResult.Success)
        assertEquals("/api/reservations/$RESERVATION_ID/qr", server.takeRequest().path)
    }

    @Test
    fun `qr endpoint is never called after a failed, cancelled or unavailable gate`() = runTest {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_VENDOR
        ).forEach { code ->
            gate.lock()
            gate.begin(BiometricManager.BIOMETRIC_SUCCESS)
            gate.onError(code)
            loadIfUnlocked()
        }

        listOf(
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        ).forEach { availability ->
            gate.lock()
            gate.begin(availability)
            loadIfUnlocked()
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refresh after success needs no new prompt, but backgrounding locks again`() = runTest {
        server.enqueue(qrOk())
        server.enqueue(qrOk())

        gate.begin(BiometricManager.BIOMETRIC_SUCCESS)
        gate.onSucceeded()
        loadIfUnlocked()
        loadIfUnlocked() // "Refresh QR" after the countdown expires
        assertEquals(2, server.requestCount)

        gate.lock() // onStop / screen left
        loadIfUnlocked()
        assertEquals(2, server.requestCount)
    }

    private fun qrOk() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"message":"QR code issued.","reservationId":"$RESERVATION_ID","qr":{
                "payload":"abc.def","issuedAtUtc":"2026-09-29T09:00:00Z",
                "validFromUtc":"2026-09-29T09:30:00Z","expiresAtUtc":"2026-09-29T11:15:00Z"}}"""
        )

    private companion object {
        const val RESERVATION_ID = "66f9a1c2e4b0a1b2c3d4e5f6"
    }
}
