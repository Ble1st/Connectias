package com.ble1st.connectias.core.domain

import com.ble1st.connectias.core.data.repository.LogRepository
import com.ble1st.connectias.core.data.repository.SecurityRepository
import javax.inject.Inject

/**
 * Use case that performs cleanup of old data across all repositories.
 * Should be called periodically (e.g., via WorkManager).
 */
class CleanupOldDataUseCase @Inject constructor(
    private val logRepository: LogRepository,
    private val securityRepository: SecurityRepository
) {
    suspend operator fun invoke(retentionDays: Int = 30) {
        val threshold = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        
        // Cleanup old logs
        logRepository.deleteOldLogs(threshold)
        
        // Cleanup old security logs
        securityRepository.deleteOldLogs(threshold)
    }
}
