package com.ble1st.connectias.feature.calendar.data

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

    suspend fun loadUpcomingEvents(limit: Int = 20): CalendarResult<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Events.DTEND} >= ?"
        val selectionArgs = arrayOf(now.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC LIMIT $limit"
        return@withContext try {
            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            val items = buildList {
                cursor?.use {
                    val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                    val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                    val calIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                    while (it.moveToNext()) {
                        add(
                            CalendarEvent(
                                id = it.getLong(idIdx),
                                title = it.getString(titleIdx) ?: "Event",
                                start = it.getLong(startIdx),
                                end = it.getLong(endIdx),
                                calendar = it.getString(calIdx) ?: ""
                            )
                        )
                    }
                }
            }
            CalendarResult.Success(items)
        } catch (se: SecurityException) {
            CalendarResult.Error("Calendar permission missing")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to read calendar")
            CalendarResult.Error(t.message ?: "Unknown error")
        }
    }

    suspend fun addEvent(event: CalendarEventDraft): CalendarResult<Long> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val calId = getPrimaryCalendarId() ?: return@withContext CalendarResult.Error("No calendar found")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DTSTART, event.start)
            put(CalendarContract.Events.DTEND, event.end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, event.description)
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
