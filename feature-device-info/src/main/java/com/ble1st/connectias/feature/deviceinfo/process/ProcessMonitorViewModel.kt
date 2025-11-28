package com.ble1st.connectias.feature.deviceinfo.process

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Process Monitor.
 */
@HiltViewModel
class ProcessMonitorViewModel @Inject constructor(
    private val processMonitorProvider: ProcessMonitorProvider
) : ViewModel() {

    private val _processState = MutableStateFlow<ProcessState>(ProcessState.Idle)
    val processState: StateFlow<ProcessState> = _processState.asStateFlow()

    /**
     * Gets running processes.
     */
    fun getRunningProcesses() {
        viewModelScope.launch {
            _processState.value = ProcessState.Loading
            val processes = processMonitorProvider.getRunningProcesses()
            val memoryStats = processMonitorProvider.getMemoryStats()
            _processState.value = ProcessState.Success(processes, memoryStats)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _processState.value = ProcessState.Idle
    }
}

/**
 * State representation for process operations.
 */
sealed class ProcessState {
    object Idle : ProcessState()
    object Loading : ProcessState()
    data class Success(val processes: List<ProcessInfo>, val memoryStats: MemoryStats) : ProcessState()
    data class Error(val message: String) : ProcessState()
}

