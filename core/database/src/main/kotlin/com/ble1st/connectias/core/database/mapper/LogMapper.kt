package com.ble1st.connectias.core.database.mapper

import com.ble1st.connectias.core.database.entities.LogEntryEntity
import com.ble1st.connectias.core.model.LogEntry
import com.ble1st.connectias.core.model.LogLevel

/**
 * Maps between database entities and domain models.
 */

fun LogEntryEntity.toModel(): LogEntry {
    return LogEntry(
        id = id.toString(),
        timestamp = timestamp,
        level = when (level) {
            2 -> LogLevel.VERBOSE
            3 -> LogLevel.DEBUG
            4 -> LogLevel.INFO
            5 -> LogLevel.WARN
            6 -> LogLevel.ERROR
            7 -> LogLevel.ASSERT
            else -> LogLevel.DEBUG
        },
        tag = tag ?: "",
        message = message,
        throwable = exceptionTrace
    )
}

fun LogEntry.toEntity(threadName: String = Thread.currentThread().name): LogEntryEntity {
    return LogEntryEntity(
        timestamp = timestamp,
        level = when (level) {
            LogLevel.VERBOSE -> 2
            LogLevel.DEBUG -> 3
            LogLevel.INFO -> 4
            LogLevel.WARN -> 5
            LogLevel.ERROR -> 6
            LogLevel.ASSERT -> 7
        },
        tag = tag,
        message = message,
        threadName = threadName,
        exceptionTrace = throwable
    )
}
