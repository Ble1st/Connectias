package com.ble1st.connectias.feature.calendar.data

sealed interface CalendarResult<out T> {
    data class Success<T>(val data: T) : CalendarResult<T>
    data class Error(val message: String) : CalendarResult<Nothing>
}

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    val calendar: String
)

data class CalendarEventDraft(
    val title: String,
    val start: Long,
    val end: Long,
    val description: String = ""
)
