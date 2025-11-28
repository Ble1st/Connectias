package com.ble1st.connectias.feature.privacy.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Tracker Detection.
 */
@HiltViewModel
class TrackerDetectionViewModel @Inject constructor(
    private val trackerDetectionProvider: TrackerDetectionProvider
) : ViewModel() {

    private val _trackerState = MutableStateFlow<TrackerState>(TrackerState.Idle)
    val trackerState: StateFlow<TrackerState> = _trackerState.asStateFlow()

    /**
     * Detects trackers in installed apps.
     */
    fun detectTrackers() {
        viewModelScope.launch {
            _trackerState.value = TrackerState.Loading
            val trackers = trackerDetectionProvider.detectTrackers()
            val domains = trackerDetectionProvider.analyzeNetworkRequests()
            _trackerState.value = TrackerState.Success(trackers, domains)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _trackerState.value = TrackerState.Idle
    }
}

/**
 * State representation for tracker detection operations.
 */
sealed class TrackerState {
    object Idle : TrackerState()
    object Loading : TrackerState()
    data class Success(val trackers: List<TrackerInfo>, val domains: List<TrackerDomain>) : TrackerState()
    data class Error(val message: String) : TrackerState()
}

