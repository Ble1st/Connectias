package com.ble1st.connectias.core.data.repository

import com.ble1st.connectias.core.model.LogEntry
import com.ble1st.connectias.core.model.LogLevel
import kotlinx.coroutines.flow.Flow

/**
 * Repository for system logging operations.
 * Public API for log data access.
 */
interface LogRepository {
    /**
     * Logs a message with the specified level.
     */
    suspend fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * Gets recent log entries as a Flow.
     */
    fun getRecentLogs(limit: Int = 1000): Flow<List<LogEntry>>
    
    /**
     * Gets logs filtered by minimum level.
     */
    fun getLogsByLevel(minLevel: LogLevel, limit: Int = 1000): Flow<List<LogEntry>>
    
    /**
     * Deletes logs older than the specified timestamp.
     */
    suspend fun deleteOldLogs(olderThan: Long)
    
    /**
     * Gets the total count of logs.
     */
    suspend fun getLogCount(): Int
}
