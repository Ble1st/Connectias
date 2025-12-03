package com.ble1st.connectias.feature.network.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Traceroute screen.
 */
@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracerouteProvider: TracerouteProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(TracerouteUiState())
    val uiState: StateFlow<TracerouteUiState> = _uiState.asStateFlow()

    private var traceJob: Job? = null

    /**
     * Starts a traceroute to the target host.
     */
    fun startTraceroute(host: String, maxHops: Int = 30) {
        traceJob?.cancel()
        
        _uiState.update { it.copy(
            targetHost = host,
            isTracing = true,
            hops = emptyList(),
            error = null
        ) }

        traceJob = viewModelScope.launch {
            tracerouteProvider.runTraceroute(host, maxHops).collect { hop ->
                _uiState.update { state ->
                    state.copy(
                        hops = state.hops + hop,
                        isTracing = hop.status != HopStatus.DESTINATION_REACHED
                    )
                }
            }
            _uiState.update { it.copy(isTracing = false) }
        }
    }

    /**
     * Stops the current traceroute.
     */
    fun stopTraceroute() {
        traceJob?.cancel()
        traceJob = null
        _uiState.update { it.copy(isTracing = false) }
    }

    /**
     * Clears the results.
     */
    fun clearResults() {
        _uiState.update { TracerouteUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        traceJob?.cancel()
    }
}

/**
 * UI state for Traceroute screen.
 */
data class TracerouteUiState(
    val targetHost: String = "",
    val isTracing: Boolean = false,
    val hops: List<TracerouteHop> = emptyList(),
    val error: String? = null
)

