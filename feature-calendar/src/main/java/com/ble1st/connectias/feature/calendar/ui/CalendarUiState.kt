package com.ble1st.connectias.feature.calendar.ui

import com.ble1st.connectias.feature.calendar.data.CalendarEvent
import com.ble1st.connectias.feature.calendar.data.CalendarInfo
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val hasPermission: Boolean = false,
    val currentYearMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<CalendarEvent> = emptyList(), // Events for the current month view
    val isAddEventDialogVisible: Boolean = false,
    
    // Multi-Calendar Support
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val selectedCalendarIds: Set<Long> = emptySet(),
    val calendarColors: Map<Long, Int> = emptyMap(),
    
    // Event Details
    val selectedEvent: CalendarEvent? = null,
    val isEditEventDialogVisible: Boolean = false,
    
    // Add/Edit Event Form State
    val titleInput: String = "",
    val descriptionInput: String = "",
    val locationInput: String = "",
    val allDayInput: Boolean = false,
    val recurrenceRuleInput: String = "",
    val startOffsetMinutes: Int = 0,
    val durationMinutes: Int = 60,
    
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isLoading: Boolean = false
)
