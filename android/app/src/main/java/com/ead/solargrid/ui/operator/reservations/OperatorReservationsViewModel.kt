package com.ead.solargrid.ui.operator.reservations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.models.ReservationActionRequest
import com.ead.solargrid.models.ReservationActionResponse
import com.ead.solargrid.models.ReservationItem
import com.ead.solargrid.ui.operator.ApiResult
import com.ead.solargrid.ui.operator.OperatorApi
import com.ead.solargrid.ui.operator.OperatorReservationStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import retrofit2.Response

/** What the list is filtered by. All fields map 1:1 to GET /api/reservations query params. */
data class ReservationFilter(
    val status: OperatorReservationStatus? = null,
    /** UTC day of the slot start, yyyy-MM-dd. */
    val dateUtc: String? = null,
    val stationId: String? = null,
    val stationName: String? = null,
    val query: String = ""
) {
    val isFiltered get() = status != null || dateUtc != null || stationId != null || query.isNotBlank()
}

data class ReservationListState(
    val filter: ReservationFilter = ReservationFilter(),
    val items: List<ReservationItem> = emptyList(),
    val totalCount: Long? = null,
    /** True while page 1 is loading (first load, pull-to-refresh or a filter change). */
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val canLoadMore: Boolean = false,
    /** Set when page 1 failed; rows already on screen are kept. */
    val errorMessage: String? = null,
    /** Reservations with an approve / reject call in flight. */
    val busyIds: Set<String> = emptySet(),
    val sessionExpired: Boolean = false
) {
    val hasLoaded get() = totalCount != null
}

sealed interface ReservationEvent {
    data class Message(val text: String) : ReservationEvent
    /** A reservation changed status, so the caller's numbers are out of date. */
    data object Changed : ReservationEvent
}

class OperatorReservationsViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.getApiService(application)

    private val _state = MutableLiveData(ReservationListState())
    val state: LiveData<ReservationListState> = _state

    private val _events = Channel<ReservationEvent>(Channel.BUFFERED)
    val events: Flow<ReservationEvent> = _events.receiveAsFlow()

    private var initialized = false
    private var pageJob: Job? = null
    private var searchJob: Job? = null

    private val current get() = _state.value ?: ReservationListState()

    /** Sets the starting filter once; later calls (e.g. after rotation) are ignored. */
    fun init(filter: ReservationFilter) {
        if (initialized) return
        initialized = true
        _state.value = current.copy(filter = filter)
        refresh()
    }

    fun refresh() = loadFirstPage()

    fun setStatus(status: OperatorReservationStatus?) {
        if (status == current.filter.status) return
        applyFilter(current.filter.copy(status = status))
    }

    fun setDate(dateUtc: String?) {
        if (dateUtc == current.filter.dateUtc) return
        applyFilter(current.filter.copy(dateUtc = dateUtc))
    }

    fun clearStation() {
        if (current.filter.stationId == null) return
        applyFilter(current.filter.copy(stationId = null, stationName = null))
    }

    /** Debounced so typing doesn't fire a request per keystroke. */
    fun setQuery(text: String) {
        val query = text.trim()
        searchJob?.cancel()
        if (query == current.filter.query) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            applyFilter(current.filter.copy(query = query))
        }
    }

    fun clearFilters(keepStatus: Boolean) {
        searchJob?.cancel()
        val status = if (keepStatus) current.filter.status else null
        applyFilter(ReservationFilter(status = status))
    }

    private fun applyFilter(filter: ReservationFilter) {
        // Drop rows from the old filter so they are never shown under the new chips.
        _state.value = current.copy(filter = filter, items = emptyList(), totalCount = null, canLoadMore = false)
        loadFirstPage()
    }

    private fun loadFirstPage() {
        // A newer filter always wins over an older request still in flight.
        pageJob?.cancel()
        val filter = current.filter
        _state.value = current.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false, errorMessage = null)

        pageJob = viewModelScope.launch {
            when (val result = fetch(filter, page = 1)) {
                is ApiResult.Success -> {
                    val body = result.data
                    _state.value = current.copy(
                        items = body.items.orEmpty(),
                        totalCount = body.totalCount,
                        canLoadMore = body.items.orEmpty().size < body.totalCount,
                        isRefreshing = false
                    )
                }
                is ApiResult.Failure -> _state.value = current.copy(
                    isRefreshing = false,
                    errorMessage = result.message,
                    sessionExpired = result.isSessionExpired
                )
            }
        }
    }

    /** Called as the list nears its end. */
    fun loadMore() {
        val state = current
        if (!state.canLoadMore || state.isRefreshing || state.isLoadingMore) return
        val filter = state.filter
        // Derived from what is loaded, so rows removed after approve / reject don't make us skip any.
        val page = state.items.size / PAGE_SIZE + 1
        _state.value = state.copy(isLoadingMore = true, loadMoreFailed = false)

        pageJob = viewModelScope.launch {
            when (val result = fetch(filter, page)) {
                is ApiResult.Success -> {
                    // Pages can overlap after removals or new bookings; skip rows already shown.
                    val known = current.items.mapTo(HashSet()) { it.id }
                    val fresh = result.data.items.orEmpty().filterNot { it.id in known }
                    val merged = current.items + fresh
                    _state.value = current.copy(
                        items = merged,
                        totalCount = result.data.totalCount,
                        canLoadMore = fresh.isNotEmpty() && merged.size < result.data.totalCount,
                        isLoadingMore = false
                    )
                }
                is ApiResult.Failure -> _state.value = current.copy(
                    isLoadingMore = false,
                    loadMoreFailed = true,
                    sessionExpired = result.isSessionExpired
                )
            }
        }
    }

    private suspend fun fetch(filter: ReservationFilter, page: Int) = OperatorApi.call {
        api.getReservations(
            status = filter.status?.apiValue,
            dateUtc = filter.dateUtc,
            page = page,
            pageSize = PAGE_SIZE,
            stationId = filter.stationId,
            query = filter.query.ifBlank { null }
        )
    }

    fun approve(item: ReservationItem) = changeStatus(item, OperatorReservationStatus.APPROVED) { body ->
        api.approveReservation(item.id, body)
    }

    fun reject(item: ReservationItem) = changeStatus(item, OperatorReservationStatus.REJECTED) { body ->
        api.rejectReservation(item.id, body)
    }

    private fun changeStatus(
        item: ReservationItem,
        target: OperatorReservationStatus,
        request: suspend (ReservationActionRequest) -> Response<ReservationActionResponse>
    ) {
        if (item.id in current.busyIds) return
        _state.value = current.copy(busyIds = current.busyIds + item.id)

        viewModelScope.launch {
            val result = OperatorApi.call { request(ReservationActionRequest(item.version)) }
            _state.value = current.copy(busyIds = current.busyIds - item.id)

            when (result) {
                is ApiResult.Success -> {
                    val updated = result.data.reservation
                    val status = updated?.status ?: target.apiValue
                    val stillMatches = current.filter.status == null ||
                        current.filter.status == OperatorReservationStatus.from(status)
                    _state.value = if (stillMatches) {
                        current.copy(items = current.items.map {
                            if (it.id == item.id) it.copy(status = status, version = updated?.version ?: it.version) else it
                        })
                    } else {
                        // e.g. approved from the Pending list: it no longer belongs here.
                        current.copy(
                            items = current.items.filterNot { it.id == item.id },
                            totalCount = current.totalCount?.minus(1)?.coerceAtLeast(0)
                        )
                    }
                    _events.send(ReservationEvent.Changed)
                    _events.send(ReservationEvent.Message(result.data.message ?: target.apiValue))
                }
                is ApiResult.Failure -> {
                    if (result.isSessionExpired) {
                        _state.value = current.copy(sessionExpired = true)
                        return@launch
                    }
                    _events.send(ReservationEvent.Message(result.message))
                    // 409 / 404: someone else changed or removed it. Reload so the row shows the truth.
                    if (result.code == OperatorApi.HTTP_CONFLICT || result.code == OperatorApi.HTTP_NOT_FOUND) {
                        loadFirstPage()
                    }
                }
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val SEARCH_DEBOUNCE_MS = 350L
    }
}
