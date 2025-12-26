package com.ble1st.connectias.feature.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.calendar.data.CalendarEvent
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

    override fun onCleared() {
        super.onCleared()
        repository.unregisterContentObserver()
    }

    fun setPermission(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) {
            // Register content observer for live updates only after permissions are granted
            repository.registerContentObserver {
                if (_state.value.hasPermission) {
                    loadEvents()
                }
            }
            loadCalendars()
            loadEvents()
        } else {
            // Unregister observer if permissions are revoked
            repository.unregisterContentObserver()
        }
    }

    fun loadCalendars() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            
            when (val result = repository.loadAvailableCalendars()) {
                is CalendarResult.Success -> {
                    val calendars = result.data
                    val calendarColors = calendars.associate { it.id to it.color }
                    val selectedIds = _state.value.selectedCalendarIds.ifEmpty {
                        // If no calendars selected, select all visible calendars
                        calendars.filter { it.visible }.map { it.id }.toSet()
                    }
                    
                    _state.update { 
                        it.copy(
                            availableCalendars = calendars,
                            selectedCalendarIds = selectedIds,
                            calendarColors = calendarColors,
                            isLoading = false
                        ) 
                    }
                }
                is CalendarResult.Error -> {
                    _state.update { it.copy(errorMessage = result.message, isLoading = false) }
                }
            }
        }
    }

    fun toggleCalendarSelection(calendarId: Long) {
        _state.update { state ->
            val newSelection = if (state.selectedCalendarIds.contains(calendarId)) {
                state.selectedCalendarIds - calendarId
            } else {
                state.selectedCalendarIds + calendarId
            }
            state.copy(selectedCalendarIds = newSelection)
        }
        loadEvents()
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

    fun jumpToToday() {
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()
        _state.update { 
            it.copy(
                selectedDate = today,
                currentYearMonth = currentMonth
            ) 
        }
        loadEvents()
    }

    fun showAddEventDialog() {
        _state.update { 
            it.copy(
                isAddEventDialogVisible = true,
                titleInput = "",
                descriptionInput = "",
                locationInput = "",
                allDayInput = false,
                recurrenceRuleInput = ""
            ) 
        }
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

    fun updateLocation(value: String) {
        _state.update { it.copy(locationInput = value) }
    }

    fun updateAllDay(value: Boolean) {
        _state.update { it.copy(allDayInput = value) }
    }

    fun updateSelectedCalendarId(calendarId: Long?) {
        if (calendarId != null) {
            _state.update { it.copy(selectedCalendarIds = setOf(calendarId)) }
        }
    }

    fun selectEvent(event: CalendarEvent) {
        viewModelScope.launch {
            _state.update { it.copy(selectedEvent = event) }
            // Load full event details
            when (val result = repository.loadEventDetails(event.id)) {
                is CalendarResult.Success -> {
                    _state.update { it.copy(selectedEvent = result.data) }
                }
                is CalendarResult.Error -> {
                    // Keep the event from the list if details loading fails
                }
            }
        }
    }

    fun hideEventDetails() {
        _state.update { it.copy(selectedEvent = null) }
    }

    fun showEditEventDialog() {
        val event = _state.value.selectedEvent
        if (event != null) {
            _state.update {
                it.copy(
                    isEditEventDialogVisible = true,
                    titleInput = event.title,
                    descriptionInput = event.description,
                    locationInput = event.location,
                    allDayInput = event.allDay,
                    recurrenceRuleInput = event.recurrenceRule
                )
            }
        }
    }

    fun hideEditEventDialog() {
        _state.update { it.copy(isEditEventDialogVisible = false) }
    }

    fun updateEvent() {
        val event = _state.value.selectedEvent ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            
            val draft = CalendarEventDraft(
                title = state.value.titleInput.ifBlank { "New Event" },
                description = state.value.descriptionInput,
                location = state.value.locationInput,
                allDay = state.value.allDayInput,
                recurrenceRule = state.value.recurrenceRuleInput,
                start = event.start,
                end = event.end,
                calendarId = event.calendarId,
                timezone = event.timezone
            )
            
            when (val result = repository.updateEvent(event.id, draft)) {
                is CalendarResult.Success -> {
                    _state.update { 
                        it.copy(
                            successMessage = "Event updated", 
                            isLoading = false,
                            isEditEventDialogVisible = false,
                            selectedEvent = null
                        ) 
                    }
                    loadEvents()
                }
                is CalendarResult.Error -> _state.update { it.copy(errorMessage = result.message, isLoading = false) }
            }
        }
    }

    private fun loadEvents() {
        if (!_state.value.hasPermission) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            
            val month = _state.value.currentYearMonth
            // Fetch from start of month to end of month
            val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = month.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val selectedCalendarIds = _state.value.selectedCalendarIds.ifEmpty {
                null // Load all calendars if none selected
            }

            when (val result = repository.loadEventsForRange(start, end, selectedCalendarIds)) {
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

            // Use first selected calendar, or null to use default
            val calendarId = state.value.selectedCalendarIds.firstOrNull()

            val draft = CalendarEventDraft(
                title = state.value.titleInput.ifBlank { "New Event" },
                description = state.value.descriptionInput,
                location = state.value.locationInput,
                allDay = state.value.allDayInput,
                recurrenceRule = state.value.recurrenceRuleInput,
                start = startMillis,
                end = endMillis,
                calendarId = calendarId
            )
            
            when (val result = repository.addEvent(draft)) {
                is CalendarResult.Success -> {
                    _state.update { 
                        it.copy(
                            successMessage = "Event added", 
                            isLoading = false,
                            isAddEventDialogVisible = false,
                            titleInput = "",
                            descriptionInput = "",
                            locationInput = "",
                            allDayInput = false
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
