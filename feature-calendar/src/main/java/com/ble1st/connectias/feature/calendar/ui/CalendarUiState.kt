package com.ble1st.connectias.feature.calendar.ui

import com.ble1st.connectias.feature.calendar.data.CalendarEvent

data class CalendarUiState(
    val hasPermission: Boolean = false,
    val events: List<CalendarEvent> = emptyList(),
    val titleInput: String = "New Event",
    val descriptionInput: String = "",
    val startOffsetMinutes: Int = 0,
    val durationMinutes: Int = 60,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isLoading: Boolean = false
)
