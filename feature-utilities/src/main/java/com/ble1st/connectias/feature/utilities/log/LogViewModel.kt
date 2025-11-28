package com.ble1st.connectias.feature.utilities.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Log Viewer.
 */
@HiltViewModel
class LogViewModel @Inject constructor(
    private val logProvider: LogProvider
) : ViewModel() {

    private val _logState = MutableStateFlow<LogState>(LogState.Idle)
    val logState: StateFlow<LogState> = _logState.asStateFlow()

    private var allLogs = emptyList<LogEntry>()

    /**
     * Loads system logs.
     */
    fun loadLogs(filter: String? = null, maxLines: Int = 1000) {
        viewModelScope.launch {
            _logState.value = LogState.Loading
            val logs = logProvider.readSystemLogs(filter, maxLines)
            allLogs = logs
            _logState.value = LogState.Success(logs)
        }
    }

    /**
     * Filters logs by level.
     */
    fun filterByLevel(level: LogProvider.LogLevel?) {
        val filtered = logProvider.filterLogsByLevel(allLogs, level)
        _logState.value = LogState.Success(filtered)
    }

    /**
     * Filters logs by tag.
     */
    fun filterByTag(tag: String) {
        val filtered = logProvider.filterLogsByTag(allLogs, tag)
        _logState.value = LogState.Success(filtered)
    }

    /**
     * Clears system logs.
     */
    fun clearLogs() {
        viewModelScope.launch {
            val success = logProvider.clearSystemLogs()
            if (success) {
                allLogs = emptyList()
                _logState.value = LogState.Success(emptyList())
            } else {
                _logState.value = LogState.Error("Failed to clear logs")
            }
        }
    }

    /**
     * Exports logs to text.
     */
    fun exportLogs(): String {
        return allLogs.joinToString("\n") { entry ->
            "${entry.timestamp} ${entry.level.tag}/${entry.tag}: ${entry.message}"
        }
    }
}

/**
 * State representation for log operations.
 */
sealed class LogState {
    object Idle : LogState()
    object Loading : LogState()
    data class Success(val logs: List<LogEntry>) : LogState()
    data class Error(val message: String) : LogState()
}

