package com.ble1st.connectias.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_logs")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "level") val level: Int, // 2=VERBOSE, 3=DEBUG, 4=INFO, 5=WARN, 6=ERROR
    @ColumnInfo(name = "tag") val tag: String?,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "thread_name") val threadName: String,
    @ColumnInfo(name = "exception_trace") val exceptionTrace: String? = null // Stacktrace if available
)
