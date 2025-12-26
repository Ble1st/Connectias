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
    val calendar: String,
    val calendarId: Long = 0L,
    val location: String = "",
    val attendees: String = "",
    val reminders: String = "",
    val allDay: Boolean = false,
    val recurrenceRule: String = "",
    val timezone: String = ""
)

data class CalendarEventDraft(
    val title: String,
    val start: Long,
    val end: Long,
    val description: String = "",
    val calendarId: Long? = null,
    val location: String = "",
    val attendees: String = "",
    val reminders: String = "",
    val allDay: Boolean = false,
    val recurrenceRule: String = "",
    val timezone: String = ""
)

data class CalendarInfo(
    val id: Long,
    val name: String,
    val displayName: String,
    val color: Int,
    val accountName: String,
    val accountType: String,
    val visible: Boolean
)
