package com.ble1st.connectias.ui.logging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.domain.GetLogsUseCase
import com.ble1st.connectias.core.model.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Log Viewer screen.
 * Provides log entries from the database using GetLogsUseCase.
 */
@HiltViewModel
class LogEntryViewModel @Inject constructor(
    private val getLogsUseCase: GetLogsUseCase
) : ViewModel() {
    
    private var currentMinLevel: LogLevel = LogLevel.DEBUG
    private var currentLimit: Int = 1000
    
    val logsResult: StateFlow<com.ble1st.connectias.core.domain.LogsResult?> = 
        getLogsUseCase(
            minLevel = currentMinLevel,
            limit = currentLimit
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    /**
     * Updates the minimum log level filter.
     */
    fun setMinLevel(level: LogLevel) {
        currentMinLevel = level
        // The StateFlow will automatically update via the UseCase
    }
    
    /**
     * Updates the log limit.
     */
    fun setLimit(limit: Int) {
        currentLimit = limit
        // The StateFlow will automatically update via the UseCase
    }
}
