package com.ead.solargrid.ui.home.qr

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.qr.QrCodeRenderer
import com.ead.solargrid.qr.QrRepository
import com.ead.solargrid.qr.QrResult
import com.ead.solargrid.qr.QrValidity
import com.ead.solargrid.security.BiometricGate
import com.ead.solargrid.security.GateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

sealed interface ReservationQrState {
    /** Waiting on [BiometricGate]; nothing has been fetched. [gate] says why it is still locked. */
    data class Locked(val gate: GateState) : ReservationQrState

    data object Loading : ReservationQrState

    /** [qrImage] is the only form the payload is kept in, and only in memory. */
    data class Ready(
        val qrImage: Bitmap,
        val validity: QrValidity,
        val validFrom: Instant?
    ) : ReservationQrState

    /** 409 / 400 / 404: not Approved any more, or the slot has ended. */
    data object Inactive : ReservationQrState

    /** 403: signed-in prosumer does not own this reservation. */
    data object NotOwner : ReservationQrState

    data object SessionExpired : ReservationQrState

    data class Error(val isNetwork: Boolean, val serverMessage: String?) : ReservationQrState
}

class ReservationQrViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QrRepository(ApiClient.getApiService(application))

    private val _state = MutableLiveData<ReservationQrState>()
    val state: LiveData<ReservationQrState> = _state

    /** Lives only as long as this screen instance, so every new "Show QR" starts locked. */
    private val gate = BiometricGate()

    private var reservationId: String? = null
    private var loadJob: Job? = null

    /** Starts locked; the QR is fetched only after [onAuthSucceeded]. A rotation keeps the current state. */
    fun start(id: String) {
        if (reservationId == id && _state.value != null) return
        reservationId = id
        publishGate()
    }

    /** True when the screen should check the device and show the prompt. */
    val needsPrompt: Boolean get() = gate.state == GateState.Idle

    val isPrompting: Boolean get() = gate.state == GateState.Prompting

    /** Takes `canAuthenticate`'s result; returns true when the prompt should be shown now. */
    fun beginAuth(availability: Int): Boolean {
        val show = gate.begin(availability)
        publishGate()
        return show
    }

    fun onAuthSucceeded() {
        gate.onSucceeded()
        if (gate.isUnlocked) load() else publishGate()
    }

    fun onAuthError(errorCode: Int) {
        gate.onError(errorCode)
        publishGate()
    }

    /** "Try again" after a cancel or error: back to Idle so the screen prompts again. */
    fun retryAuth() {
        if (gate.state == GateState.Prompting || gate.isUnlocked) return
        gate.lock()
        publishGate()
    }

    /**
     * Retry after an error, or "Refresh QR" after expiry: asks the server for a new payload.
     * Does nothing until the gate is unlocked; once it is, no new prompt is needed on this screen.
     */
    fun load() {
        val id = reservationId ?: return
        if (loadJob?.isActive == true) return

        gate.runIfUnlocked { fetch(id) }
    }

    private fun fetch(id: String) {
        loadJob = viewModelScope.launch {
            _state.value = ReservationQrState.Loading
            _state.value = when (val result = repository.getQr(id)) {
                is QrResult.Success -> {
                    val receivedAt = SystemClock.elapsedRealtime()
                    val qr = result.data
                    val validity = QrValidity.from(
                        qr.issuedAtUtc,
                        qr.expiresAtUtc,
                        receivedAt,
                        System.currentTimeMillis()
                    )
                    val payload = qr.payload
                    if (validity == null || payload.isNullOrBlank()) {
                        ReservationQrState.Error(isNetwork = false, serverMessage = null)
                    } else {
                        val image = withContext(Dispatchers.Default) {
                            QrCodeRenderer.render(payload, QR_SIZE_PX)
                        }
                        ReservationQrState.Ready(image, validity, QrValidity.parseUtc(qr.validFromUtc))
                    }
                }
                QrResult.NetworkError -> ReservationQrState.Error(isNetwork = true, serverMessage = null)
                is QrResult.HttpError -> when (result.code) {
                    401 -> ReservationQrState.SessionExpired
                    403 -> ReservationQrState.NotOwner
                    400, 404, 409 -> ReservationQrState.Inactive
                    else -> ReservationQrState.Error(isNetwork = false, serverMessage = result.serverMessage)
                }
            }
        }
    }

    /** Drops the QR image and locks again: when the screen is left or goes to the background. */
    fun lock() {
        loadJob?.cancel()
        gate.lock()
        publishGate()
    }

    override fun onCleared() {
        loadJob?.cancel()
        gate.lock()
        _state.value = ReservationQrState.Locked(gate.state)
    }

    private fun publishGate() {
        _state.value = ReservationQrState.Locked(gate.state)
    }

    private companion object {
        const val QR_SIZE_PX = 720
    }
}
