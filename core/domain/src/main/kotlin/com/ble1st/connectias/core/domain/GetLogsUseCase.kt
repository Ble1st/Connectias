package com.ble1st.connectias.core.domain

import com.ble1st.connectias.core.data.repository.LogRepository
import com.ble1st.connectias.core.model.LogEntry
import com.ble1st.connectias.core.model.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case that retrieves and filters logs based on criteria.
 */
class GetLogsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    operator fun invoke(
        minLevel: LogLevel = LogLevel.DEBUG,
        limit: Int = 1000
    ): Flow<LogsResult> {
        return logRepository.getLogsByLevel(minLevel, limit).map { logs ->
            LogsResult(
                logs = logs,
                totalCount = logs.size,
                errorCount = logs.count { it.level == LogLevel.ERROR },
                warningCount = logs.count { it.level == LogLevel.WARN }
            )
        }
    }
}

data class LogsResult(
    val logs: List<LogEntry>,
    val totalCount: Int,
    val errorCount: Int,
    val warningCount: Int
)
