package com.ble1st.connectias.feature.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.calendar.data.CalendarEventDraft
import com.ble1st.connectias.feature.calendar.data.CalendarRepository
import com.ble1st.connectias.feature.calendar.data.CalendarResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state

    fun setPermission(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) {
            refresh()
        }
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(titleInput = value) }
    }

    fun updateDescription(value: String) {
        _state.update { it.copy(descriptionInput = value) }
    }

    fun updateStartOffset(value: Int) {
        _state.update { it.copy(startOffsetMinutes = value) }
    }

    fun updateDuration(value: Int) {
        _state.update { it.copy(durationMinutes = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            when (val result = repository.loadUpcomingEvents()) {
                is CalendarResult.Success -> _state.update { it.copy(events = result.data, isLoading = false) }
                is CalendarResult.Error -> _state.update { it.copy(errorMessage = result.message, isLoading = false) }
            }
        }
    }

    fun addEvent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val now = System.currentTimeMillis()
            val start = now + state.value.startOffsetMinutes * 60 * 1000L
            val end = start + state.value.durationMinutes * 60 * 1000L
            val draft = CalendarEventDraft(
                title = state.value.titleInput,
                description = state.value.descriptionInput,
                start = start,
                end = end
            )
            when (val result = repository.addEvent(draft)) {
                is CalendarResult.Success -> {
                    _state.update { it.copy(successMessage = "Event added", isLoading = false) }
                    refresh()
                }
                is CalendarResult.Error -> _state.update { it.copy(errorMessage = result.message, isLoading = false) }
            }
        }
    }
}
