@file:Suppress("unused") // Room Entity - used by Room annotation processor for database table generation

package com.ble1st.connectias.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for system logs.
 * Use LogEntry model for public API.
 */
@Entity(
    tableName = "system_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["level", "timestamp"])
    ]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    @ColumnInfo(name = "timestamp") 
    val timestamp: Long,
    @ColumnInfo(name = "level") 
    val level: Int,
    @ColumnInfo(name = "tag") 
    val tag: String?,
    @ColumnInfo(name = "message") 
    val message: String,
    @ColumnInfo(name = "thread_name") 
    val threadName: String,
    @ColumnInfo(name = "exception_trace") 
    val exceptionTrace: String? = null
)
