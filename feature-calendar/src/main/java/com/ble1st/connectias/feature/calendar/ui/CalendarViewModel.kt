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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
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
            loadEvents()
        }
    }

    fun onNextMonth() {
        _state.update { it.copy(currentYearMonth = it.currentYearMonth.plusMonths(1)) }
        loadEvents()
    }

    fun onPrevMonth() {
        _state.update { it.copy(currentYearMonth = it.currentYearMonth.minusMonths(1)) }
        loadEvents()
    }

    fun onDateSelected(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    fun showAddEventDialog() {
        _state.update { it.copy(isAddEventDialogVisible = true, titleInput = "", descriptionInput = "") }
    }

    fun hideAddEventDialog() {
        _state.update { it.copy(isAddEventDialogVisible = false) }
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(titleInput = value) }
    }

    fun updateDescription(value: String) {
        _state.update { it.copy(descriptionInput = value) }
    }

    private fun loadEvents() {
        if (!_state.value.hasPermission) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            
            val month = _state.value.currentYearMonth
            // Fetch from start of month to end of month
            val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = month.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            when (val result = repository.loadEventsForRange(start, end)) {
                is CalendarResult.Success -> _state.update { it.copy(events = result.data, isLoading = false) }
                is CalendarResult.Error -> _state.update { it.copy(errorMessage = result.message, isLoading = false) }
            }
        }
    }

    fun addEvent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            
            val selectedDate = state.value.selectedDate
            val nowTime = LocalTime.now()
            
            // Default to selected date at current time (or 9 AM if you prefer)
            val startDateTime = LocalDateTime.of(selectedDate, nowTime)
            val endDateTime = startDateTime.plusHours(1)
            
            val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val draft = CalendarEventDraft(
                title = state.value.titleInput.ifBlank { "New Event" },
                description = state.value.descriptionInput,
                start = startMillis,
                end = endMillis
            )
            
            when (val result = repository.addEvent(draft)) {
                is CalendarResult.Success -> {
                    _state.update { 
                        it.copy(
                            successMessage = "Event added", 
                            isLoading = false,
                            isAddEventDialogVisible = false
                        ) 
                    }
                    loadEvents()
                }
                is CalendarResult.Error -> _state.update { it.copy(errorMessage = result.message, isLoading = false) }
            }
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            when (val result = repository.deleteEvent(eventId)) {
                is CalendarResult.Success -> {
                    _state.update { it.copy(successMessage = "Event deleted", isLoading = false) }
                    loadEvents()
                }
                is CalendarResult.Error -> _state.update { it.copy(errorMessage = result.message, isLoading = false) }
            }
        }
    }
}
