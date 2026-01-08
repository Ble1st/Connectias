package com.ble1st.connectias.core.data.repository.impl

import com.ble1st.connectias.core.data.repository.LogRepository
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.mapper.toEntity
import com.ble1st.connectias.core.database.mapper.toModel
import com.ble1st.connectias.core.model.LogEntry
import com.ble1st.connectias.core.model.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LogRepository using Room database.
 */
@Singleton
class LogRepositoryImpl @Inject constructor(
    private val systemLogDao: SystemLogDao
) : LogRepository {
    
    override suspend fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val logEntry = LogEntry(
            id = "0",
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable?.stackTraceToString()
        )
        systemLogDao.insertLog(logEntry.toEntity())
    }
    
    override fun getRecentLogs(limit: Int): Flow<List<LogEntry>> {
        return systemLogDao.getRecentLogs(limit).map { entities ->
            entities.map { it.toModel() }
        }
    }
    
    override fun getLogsByLevel(minLevel: LogLevel, limit: Int): Flow<List<LogEntry>> {
        val minLevelInt = when (minLevel) {
            LogLevel.VERBOSE -> 2
            LogLevel.DEBUG -> 3
            LogLevel.INFO -> 4
            LogLevel.WARN -> 5
            LogLevel.ERROR -> 6
            LogLevel.ASSERT -> 7
        }
        return systemLogDao.getLogsByLevel(minLevelInt, limit).map { entities ->
            entities.map { it.toModel() }
        }
    }
    
    override suspend fun deleteOldLogs(olderThan: Long) {
        systemLogDao.deleteOldLogs(olderThan)
    }
    
    override suspend fun getLogCount(): Int {
        return systemLogDao.getLogCount()
    }
}
