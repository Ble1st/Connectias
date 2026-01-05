package com.ble1st.connectias.feature.calendar.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var contentObserver: CalendarContentObserver? = null

    fun registerContentObserver(onChange: () -> Unit) {
        unregisterContentObserver()
        contentObserver = CalendarContentObserver(onChange)
        context.contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,
            contentObserver!!
        )
        Timber.d("Calendar content observer registered")
    }

    fun unregisterContentObserver() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
            Timber.d("Calendar content observer unregistered")
        }
    }

    suspend fun loadEventsForRange(start: Long, end: Long, calendarIds: Set<Long>? = null): CalendarResult<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE
        )
        // Events that overlap with the range: Start < RangeEnd AND End > RangeStart
        val selectionBuilder = StringBuilder("(${CalendarContract.Events.DTSTART} < ?) AND (${CalendarContract.Events.DTEND} > ?)")
        val selectionArgsList = mutableListOf(end.toString(), start.toString())
        
        // Add calendar filter if provided
        if (!calendarIds.isNullOrEmpty()) {
            val placeholders = calendarIds.joinToString(",") { "?" }
            selectionBuilder.append(" AND ${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)")
            selectionArgsList.addAll(calendarIds.map { it.toString() })
        }
        
        val selection = selectionBuilder.toString()
        val selectionArgs = selectionArgsList.toTypedArray()
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"
        
        return@withContext try {
            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            val items = buildList {
                cursor?.use {
                    val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                    val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                    val calIdIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                    val calIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                    
                    while (it.moveToNext()) {
                        add(
                            CalendarEvent(
                                id = it.getLong(idIdx),
                                title = it.getString(titleIdx) ?: "Event",
                                description = it.getString(descIdx) ?: "",
                                start = it.getLong(startIdx),
                                end = it.getLong(endIdx),
                                calendar = it.getString(calIdx) ?: "",
                                calendarId = it.getLong(calIdIdx)
                            )
                        )
                    }
                }
            }
            CalendarResult.Success(items)
        } catch (se: SecurityException) {
            CalendarResult.Error("Calendar permission missing")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to read calendar events for range")
            CalendarResult.Error(t.message ?: "Unknown error")
        }
    }

    suspend fun addEvent(event: CalendarEventDraft): CalendarResult<Long> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val calId = event.calendarId ?: getPrimaryCalendarId() ?: return@withContext CalendarResult.Error("No calendar found")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.DTSTART, event.start)
            put(CalendarContract.Events.DTEND, event.end)
            put(CalendarContract.Events.EVENT_TIMEZONE, event.timezone.ifBlank { TimeZone.getDefault().id })
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            if (event.location.isNotBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
            }
            if (event.recurrenceRule.isNotBlank()) {
                put(CalendarContract.Events.RRULE, event.recurrenceRule)
            }
        }
        return@withContext try {
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id != null) CalendarResult.Success(id) else CalendarResult.Error("Insert failed")
        } catch (se: SecurityException) {
            CalendarResult.Error("Calendar permission missing")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to insert event")
            CalendarResult.Error(t.message ?: "Unknown error")
        }
    }

    suspend fun loadEventDetails(eventId: Long): CalendarResult<CalendarEvent> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE
        )
        
        return@withContext try {
            val cursor = resolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                    val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                    val calIdIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                    val calIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                    val locationIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    val allDayIdx = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                    val timezoneIdx = it.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                    val rruleIdx = it.getColumnIndex(CalendarContract.Events.RRULE)
                    
                    CalendarResult.Success(
                        CalendarEvent(
                            id = it.getLong(idIdx),
                            title = it.getString(titleIdx) ?: "Event",
                            description = it.getString(descIdx) ?: "",
                            start = it.getLong(startIdx),
                            end = it.getLong(endIdx),
                            calendar = it.getString(calIdx) ?: "",
                            calendarId = it.getLong(calIdIdx),
                            location = it.getString(locationIdx) ?: "",
                            allDay = it.getInt(allDayIdx) == 1,
                            timezone = it.getString(timezoneIdx) ?: "",
                            recurrenceRule = it.getString(rruleIdx) ?: ""
                        )
                    )
                } else {
                    CalendarResult.Error("Event not found")
                }
            } ?: CalendarResult.Error("Event not found")
        } catch (se: SecurityException) {
            CalendarResult.Error("Calendar permission missing")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to load event details")
            CalendarResult.Error(t.message ?: "Unknown error")
        }
    }

    suspend fun updateEvent(eventId: Long, draft: CalendarEventDraft): CalendarResult<Unit> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, draft.title)
            put(CalendarContract.Events.DESCRIPTION, draft.description)
            put(CalendarContract.Events.DTSTART, draft.start)
            put(CalendarContract.Events.DTEND, draft.end)
            put(CalendarContract.Events.EVENT_TIMEZONE, draft.timezone.ifBlank { TimeZone.getDefault().id })
            put(CalendarContract.Events.ALL_DAY, if (draft.allDay) 1 else 0)
            if (draft.location.isNotBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, draft.location)
            } else {
                putNull(CalendarContract.Events.EVENT_LOCATION)
            }
            if (draft.recurrenceRule.isNotBlank()) {
                put(CalendarContract.Events.RRULE, draft.recurrenceRule)
            } else {
                putNull(CalendarContract.Events.RRULE)
            }
            if (draft.calendarId != null) {
                put(CalendarContract.Events.CALENDAR_ID, draft.calendarId)
            }
        }
        
        return@withContext try {
            val rows = resolver.update(uri, values, null, null)
            if (rows > 0) CalendarResult.Success(Unit) else CalendarResult.Error("Event not found or update failed")
        } catch (se: SecurityException) {
            CalendarResult.Error("Calendar permission missing")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to update event")
            CalendarResult.Error(t.message ?: "Unknown error")
        }
    }

    suspend fun deleteEvent(eventId: Long): CalendarResult<Unit> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        
        return@withContext try {
            val rows = resolver.delete(uri, null, null)
            if (rows > 0) CalendarResult.Success(Unit) else CalendarResult.Error("Event not found or delete failed")
        } catch (se: SecurityException) {
            CalendarResult.Error("Calendar permission missing")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to delete event")
            CalendarResult.Error(t.message ?: "Unknown error")
        }
    }

    suspend fun loadAvailableCalendars(): CalendarResult<List<CalendarInfo>> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = ?"
        val selectionArgs = arrayOf("1")
        val sortOrder = "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        
        return@withContext try {
            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            val calendars = buildList {
                cursor?.use {
                    val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
                    val nameIdx = it.getColumnIndex(CalendarContract.Calendars.NAME)
                    val displayNameIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    val colorIdx = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                    val accountNameIdx = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                    val accountTypeIdx = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                    val visibleIdx = it.getColumnIndex(CalendarContract.Calendars.VISIBLE)
                    
                    while (it.moveToNext()) {
                        add(
                            CalendarInfo(
                                id = it.getLong(idIdx),
                                name = it.getString(nameIdx) ?: "",
                                displayName = it.getString(displayNameIdx) ?: "",
                                color = it.getInt(colorIdx),
                                accountName = it.getString(accountNameIdx) ?: "",
                                accountType = it.getString(accountTypeIdx) ?: "",
                                visible = it.getInt(visibleIdx) == 1
                            )
                        )
                    }
                }
            }
            CalendarResult.Success(calendars)
        } catch (se: SecurityException) {
            CalendarResult.Error("Calendar permission missing")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to load calendars")
            CalendarResult.Error(t.message ?: "Unknown error")
        }
    }

    private fun getPrimaryCalendarId(): Long? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val uri = CalendarContract.Calendars.CONTENT_URI
        return try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                while (cursor.moveToNext()) {
                    val isPrimary = cursor.getInt(primaryIdx) == 1
                    val id = cursor.getLong(idIdx)
                    if (isPrimary) return id
                    if (idIdx >= 0 && primaryIdx < 0) return id
                }
                null
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to find calendar id")
            null
        }
    }
}
