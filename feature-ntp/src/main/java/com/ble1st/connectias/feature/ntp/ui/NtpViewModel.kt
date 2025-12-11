package com.ble1st.connectias.feature.ntp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.ntp.data.NtpHistoryEntity
import com.ble1st.connectias.feature.ntp.data.NtpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NtpViewModel @Inject constructor(
    private val repository: NtpRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NtpUiState())
    val state: StateFlow<NtpUiState> = _state

    init {
        viewModelScope.launch {
            repository.history.collect { history ->
                _state.update { it.copy(history = history) }
            }
        }
    }

    fun updateServer(value: String) {
        _state.update { it.copy(server = value) }
    }

    fun checkNtp() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.queryOffset(state.value.server)
            _state.update {
                it.copy(
                    result = result,
                    isLoading = false,
                    errorMessage = result.error
                )
            }
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
    
    fun deleteHistoryItem(item: NtpHistoryEntity) {
        viewModelScope.launch {
            repository.deleteHistoryItem(item)
        }
    }
}

