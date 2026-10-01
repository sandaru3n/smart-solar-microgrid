package com.ead.solargrid.ui.operator.stations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.models.EnergyBookingSlotDto
import com.ead.solargrid.models.SolarStation
import com.ead.solargrid.models.StationSchedule
import com.ead.solargrid.qr.QrValidity
import com.ead.solargrid.ui.operator.ApiResult
import com.ead.solargrid.ui.operator.OperatorApi
import com.ead.solargrid.ui.operator.OperatorReservationStatus
import com.ead.solargrid.ui.operator.dataOr
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

data class StationDetailState(
    val station: SolarStation? = null,
    val schedules: List<StationSchedule>? = null,
    val pendingCount: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedDay: LocalDate = LocalDate.now(StationFormat.ZONE),
    /** Stored slots that start on [selectedDay] (local), soonest first. Null while loading. */
    val slots: List<EnergyBookingSlotDto>? = null,
    val slotsError: String? = null,
    val sessionExpired: Boolean = false
)

class OperatorStationDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.getApiService(application)

    private val _state = MutableLiveData(StationDetailState())
    val state: LiveData<StationDetailState> = _state

    private var stationId: String? = null
    private var loadJob: Job? = null
    private var slotsJob: Job? = null

    private val current get() = _state.value ?: StationDetailState()

    fun init(id: String) {
        if (stationId != null) return
        stationId = id
        refresh()
    }

    fun refresh() {
        val id = stationId ?: return
        if (loadJob?.isActive == true) return
        _state.value = current.copy(isLoading = true, errorMessage = null)

        loadJob = viewModelScope.launch {
            val stationCall = async { OperatorApi.call { api.getStation(id) } }
            val schedulesCall = async { OperatorApi.call { api.getStationSchedules(id) } }
            val pendingCall = async {
                OperatorApi.call {
                    api.getReservations(
                        status = OperatorReservationStatus.PENDING.apiValue,
                        stationId = id,
                        page = 1,
                        pageSize = 1
                    )
                }
            }
            val station = stationCall.await()
            val schedules = schedulesCall.await()
            val pending = pendingCall.await()
            val failures = listOf(station, schedules, pending).filterIsInstance<ApiResult.Failure>()

            _state.value = current.copy(
                station = station.dataOr(current.station),
                schedules = schedules.dataOr(current.schedules),
                pendingCount = pending.dataOr(null)?.totalCount ?: current.pendingCount,
                isLoading = false,
                errorMessage = failures.firstOrNull()?.message,
                sessionExpired = failures.any { it.isSessionExpired }
            )
        }
        loadSlots()
    }

    fun selectDay(day: LocalDate) {
        if (day == current.selectedDay && current.slots != null) return
        _state.value = current.copy(selectedDay = day)
        loadSlots()
    }

    private fun loadSlots() {
        val id = stationId ?: return
        val day = current.selectedDay
        slotsJob?.cancel()
        _state.value = current.copy(slots = null, slotsError = null)

        slotsJob = viewModelScope.launch {
            // The API filters by UTC day, so ask for every UTC day the local day overlaps.
            val start = day.atStartOfDay(StationFormat.ZONE).toInstant()
            val end = day.plusDays(1).atStartOfDay(StationFormat.ZONE).toInstant()
            val utcDays = generateSequence(start.atZone(ZoneOffset.UTC).toLocalDate()) { it.plusDays(1) }
                .takeWhile { !it.isAfter(end.minusMillis(1).atZone(ZoneOffset.UTC).toLocalDate()) }
                .toList()

            val results = utcDays.map { utc ->
                async { OperatorApi.call { api.getStationSlots(id, utc.toString(), includeFull = true) } }
            }.awaitAll()

            val failure = results.filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) {
                _state.value = current.copy(slotsError = failure.message, sessionExpired = failure.isSessionExpired)
                return@launch
            }
            val slots = results.flatMap { it.dataOr(null).orEmpty() }
                .distinctBy { it.id }
                .mapNotNull { slot -> QrValidity.parseUtc(slot.startTimeUtc)?.let { slot to it } }
                .filter { (_, startsAt) -> !startsAt.isBefore(start) && startsAt.isBefore(end) }
                .sortedBy { (_, startsAt) -> startsAt }
                .map { (slot, _) -> slot }
            _state.value = current.copy(slots = slots)
        }
    }
}
