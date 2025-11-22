package com.ble1st.connectias.core.services

import com.ble1st.connectias.core.security.RaspManager
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for performing security checks.
 * Automatically logs all security check results to the database.
 */
@Singleton
class SecurityService @Inject constructor(
    private val raspManager: RaspManager,
    private val loggingService: LoggingService
) {
    /**
     * Performs a comprehensive security check and automatically logs the result.
     * 
     * @return SecurityCheckResult containing the check results
     */
    suspend fun performSecurityCheck(): SecurityCheckResult {
        val result = raspManager.performSecurityChecks()
        
        // Automatically log the security check result
        loggingService.logSecurityCheck(result)
        
        return result
    }
}

