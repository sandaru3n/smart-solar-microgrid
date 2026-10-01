package com.ead.solargrid.ui.operator.stations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.models.SolarStation
import com.ead.solargrid.ui.operator.ApiResult
import com.ead.solargrid.ui.operator.OperatorApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class StationListState(
    /** Null until the first successful load. */
    val stations: List<SolarStation>? = null,
    val query: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
) {
    /** Stations matching [query] by name or address. The list is small, so it is filtered on device. */
    val visible: List<SolarStation>
        get() {
            val all = stations.orEmpty()
            val q = query.trim()
            if (q.isEmpty()) return all
            return all.filter { it.name.contains(q, ignoreCase = true) || it.address?.contains(q, ignoreCase = true) == true }
        }
}

class OperatorStationsViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiClient.getApiService(application)

    private val _state = MutableLiveData(StationListState())
    val state: LiveData<StationListState> = _state

    private var loadJob: Job? = null

    private val current get() = _state.value ?: StationListState()

    fun loadIfNeeded() {
        if (current.stations == null) refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        _state.value = current.copy(isLoading = true, errorMessage = null)
        loadJob = viewModelScope.launch {
            _state.value = when (val result = OperatorApi.call { api.getStations() }) {
                is ApiResult.Success -> current.copy(
                    stations = result.data.sortedBy { it.name.lowercase() },
                    isLoading = false
                )
                is ApiResult.Failure -> current.copy(
                    isLoading = false,
                    errorMessage = result.message,
                    sessionExpired = result.isSessionExpired
                )
            }
        }
    }

    fun setQuery(query: String) {
        if (query != current.query) _state.value = current.copy(query = query)
    }
}
