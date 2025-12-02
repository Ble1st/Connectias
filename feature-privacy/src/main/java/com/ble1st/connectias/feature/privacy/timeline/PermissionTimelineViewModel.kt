package com.ble1st.connectias.feature.privacy.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.privacy.timeline.models.AnomalyReport
import com.ble1st.connectias.feature.privacy.timeline.models.PermissionUsageEvent
import com.ble1st.connectias.feature.privacy.timeline.models.TimelineFilter
import com.ble1st.connectias.feature.privacy.timeline.models.TimelineGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Permission Timeline functionality.
 */
@HiltViewModel
class PermissionTimelineViewModel @Inject constructor(
    private val timelineProvider: PermissionTimelineProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        loadTimeline()
        detectAnomalies()
    }

    /**
     * Loads the permission timeline.
     */
    fun loadTimeline(filter: TimelineFilter = TimelineFilter()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, filter = filter) }

            try {
                timelineProvider.getPermissionUsage(filter).collect { events ->
                    val grouped = timelineProvider.groupByDate(events)
                    _uiState.update { it.copy(
                        isLoading = false,
                        events = events,
                        groupedEvents = grouped
                    ) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading timeline")
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load timeline: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Detects anomalies in permission usage.
     */
    fun detectAnomalies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAnomalies = true) }

            try {
                val anomalies = timelineProvider.detectAnomalies(_uiState.value.filter)
                _uiState.update { it.copy(
                    isLoadingAnomalies = false,
                    anomalies = anomalies
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error detecting anomalies")
                _uiState.update { it.copy(isLoadingAnomalies = false) }
            }
        }
    }

    /**
     * Sets the time range filter.
     */
    fun setTimeRange(startTime: Long, endTime: Long) {
        val newFilter = _uiState.value.filter.copy(
            startTime = startTime,
            endTime = endTime
        )
        loadTimeline(newFilter)
    }

    /**
     * Sets the time range to last N hours.
     */
    fun setLastHours(hours: Int) {
        val now = System.currentTimeMillis()
        val startTime = now - (hours * 60 * 60 * 1000L)
        setTimeRange(startTime, now)
    }

    /**
     * Sets the time range to last N days.
     */
    fun setLastDays(days: Int) {
        val now = System.currentTimeMillis()
        val startTime = now - (days * 24 * 60 * 60 * 1000L)
        setTimeRange(startTime, now)
    }

    /**
     * Filters by specific permissions.
     */
    fun filterByPermissions(permissions: Set<String>) {
        val newFilter = _uiState.value.filter.copy(permissions = permissions)
        loadTimeline(newFilter)
    }

    /**
     * Filters by specific packages.
     */
    fun filterByPackages(packages: Set<String>) {
        val newFilter = _uiState.value.filter.copy(packages = packages)
        loadTimeline(newFilter)
    }

    /**
     * Toggles background-only filter.
     */
    fun toggleBackgroundOnly() {
        val newFilter = _uiState.value.filter.copy(
            showBackgroundOnly = !_uiState.value.filter.showBackgroundOnly
        )
        loadTimeline(newFilter)
    }

    /**
     * Toggles suspicious-only filter.
     */
    fun toggleSuspiciousOnly() {
        val newFilter = _uiState.value.filter.copy(
            showSuspiciousOnly = !_uiState.value.filter.showSuspiciousOnly
        )
        loadTimeline(newFilter)
    }

    /**
     * Clears all filters.
     */
    fun clearFilters() {
        loadTimeline(TimelineFilter())
    }

    /**
     * Refreshes the timeline.
     */
    fun refresh() {
        loadTimeline(_uiState.value.filter)
        detectAnomalies()
    }

    /**
     * Clears error.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Selects an event for details.
     */
    fun selectEvent(event: PermissionUsageEvent?) {
        _uiState.update { it.copy(selectedEvent = event) }
    }

    /**
     * Selects an anomaly for details.
     */
    fun selectAnomaly(anomaly: AnomalyReport?) {
        _uiState.update { it.copy(selectedAnomaly = anomaly) }
    }

    /**
     * Sets the selected tab.
     */
    fun setSelectedTab(tab: TimelineTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}

/**
 * UI state for Permission Timeline.
 */
data class TimelineUiState(
    val isLoading: Boolean = false,
    val isLoadingAnomalies: Boolean = false,
    val events: List<PermissionUsageEvent> = emptyList(),
    val groupedEvents: List<TimelineGroup> = emptyList(),
    val anomalies: List<AnomalyReport> = emptyList(),
    val filter: TimelineFilter = TimelineFilter(),
    val selectedEvent: PermissionUsageEvent? = null,
    val selectedAnomaly: AnomalyReport? = null,
    val selectedTab: TimelineTab = TimelineTab.TIMELINE,
    val error: String? = null
) {
    val hasFilters: Boolean
        get() = filter.permissions.isNotEmpty() || 
                filter.packages.isNotEmpty() || 
                filter.showBackgroundOnly || 
                filter.showSuspiciousOnly

    val eventCount: Int
        get() = events.size

    val anomalyCount: Int
        get() = anomalies.size

    val suspiciousCount: Int
        get() = events.count { it.isSuspicious }
}

/**
 * Tabs for the timeline screen.
 */
enum class TimelineTab {
    TIMELINE,
    ANOMALIES
}
