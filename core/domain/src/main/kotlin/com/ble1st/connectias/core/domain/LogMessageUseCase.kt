package com.ble1st.connectias.core.domain

import com.ble1st.connectias.core.data.repository.LogRepository
import com.ble1st.connectias.core.model.LogLevel
import javax.inject.Inject

/**
 * Use case that logs a message with automatic cleanup of old logs.
 */
class LogMessageUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        // Log the message
        logRepository.log(level, tag, message, throwable)
        
        // Cleanup old logs if count exceeds threshold
        val count = logRepository.getLogCount()
        if (count > MAX_LOG_COUNT) {
            val threshold = System.currentTimeMillis() - LOG_RETENTION_MS
            logRepository.deleteOldLogs(threshold)
        }
    }

    companion object {
        private const val MAX_LOG_COUNT = 10000
        private const val LOG_RETENTION_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }
}
