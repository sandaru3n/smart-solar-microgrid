package com.ead.solargrid.ui.operator

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.models.ReservationSummaryResponse
import com.ead.solargrid.models.SolarStation
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

data class InfrastructureTotals(
    val stations: Int,
    val capacityKw: Double,
    val batterySlots: Int
)

/**
 * Everything the Grid Operator home shows. A value stays null until it has loaded once;
 * a failed refresh keeps the last good value and reports errorMessage instead.
 */
data class OperatorHomeState(
    val isLoading: Boolean = false,
    val summary: ReservationSummaryResponse? = null,
    val reservationsToday: Long? = null,
    val completedToday: Long? = null,
    val infrastructure: InfrastructureTotals? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
    /** Wall-clock time of the last fully successful refresh, for the "Updated" label. */
    val updatedAtMillis: Long? = null
)

class GridOperatorHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.getApiService(application)

    private val _state = MutableLiveData(OperatorHomeState())
    val state: LiveData<OperatorHomeState> = _state

    private var loadJob: Job? = null
    private var lastLoadedElapsed = 0L

    /** Called from onResume, so returning to the screen refreshes old numbers without spamming the API. */
    fun refreshIfStale() {
        if (lastLoadedElapsed == 0L || SystemClock.elapsedRealtime() - lastLoadedElapsed > STALE_AFTER_MS) {
            refresh()
        }
    }

    fun refresh() {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            _state.value = currentState().copy(isLoading = true)

            // "Today" is the UTC day of the slot start, which is what the API filters on (same as the web dashboard).
            val todayUtc = LocalDate.now(ZoneOffset.UTC).toString()

            val summaryCall = async { OperatorApi.call { api.getReservationSummary() } }
            val todayCall = async { OperatorApi.call { api.getReservations(dateUtc = todayUtc, page = 1, pageSize = 1) } }
            val completedCall = async {
                OperatorApi.call { api.getReservations(status = STATUS_COMPLETED, dateUtc = todayUtc, page = 1, pageSize = 1) }
            }
            val stationsCall = async { OperatorApi.call { api.getStations() } }

            val summary = summaryCall.await()
            val today = todayCall.await()
            val completed = completedCall.await()
            val stations = stationsCall.await()

            val failures = listOf(summary, today, completed, stations).filterIsInstance<ApiResult.Failure>()
            val previous = currentState()

            if (failures.isEmpty()) {
                lastLoadedElapsed = SystemClock.elapsedRealtime()
            }

            _state.value = previous.copy(
                isLoading = false,
                summary = summary.dataOr(previous.summary),
                reservationsToday = today.dataOr(null)?.totalCount ?: previous.reservationsToday,
                completedToday = completed.dataOr(null)?.totalCount ?: previous.completedToday,
                infrastructure = stations.dataOr(null)?.let(::totals) ?: previous.infrastructure,
                errorMessage = failures.firstOrNull()?.message,
                sessionExpired = failures.any { it.isSessionExpired },
                updatedAtMillis = if (failures.isEmpty()) System.currentTimeMillis() else previous.updatedAtMillis
            )
        }
    }

    private fun currentState() = _state.value ?: OperatorHomeState()

    private fun totals(stations: List<SolarStation>) = InfrastructureTotals(
        stations = stations.size,
        capacityKw = stations.sumOf { it.capacityKw },
        batterySlots = stations.sumOf { it.batteryStorageSlots }
    )

    companion object {
        private const val STALE_AFTER_MS = 30_000L
        private const val STATUS_COMPLETED = "Completed"
    }
}
