package com.ble1st.connectias.core.data.repository

import com.ble1st.connectias.core.model.SecurityCheckResult
import com.ble1st.connectias.core.model.SecurityThreat
import kotlinx.coroutines.flow.Flow

/**
 * Repository for security-related operations.
 * Public API for security data access.
 */
interface SecurityRepository {
    /**
     * Performs a comprehensive security check.
     */
    suspend fun performSecurityCheck(): SecurityCheckResult
    
    /**
     * Logs a security threat to persistent storage.
     */
    suspend fun logThreat(threat: SecurityThreat)
    
    /**
     * Gets recent security threats as a Flow.
     */
    fun getRecentThreats(limit: Int = 100): Flow<List<SecurityThreat>>
    
    /**
     * Deletes security logs older than the specified timestamp.
     */
    suspend fun deleteOldLogs(olderThan: Long)
}
