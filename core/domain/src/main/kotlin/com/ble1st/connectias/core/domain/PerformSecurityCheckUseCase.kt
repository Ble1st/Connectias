package com.ble1st.connectias.core.domain

import com.ble1st.connectias.core.data.repository.SecurityRepository
import com.ble1st.connectias.core.model.SecurityCheckResult
import javax.inject.Inject

/**
 * Use case that performs a comprehensive security check
 * and logs any detected threats.
 */
class PerformSecurityCheckUseCase @Inject constructor(
    private val securityRepository: SecurityRepository
) {
    suspend operator fun invoke(): SecurityCheckResult {
        val result = securityRepository.performSecurityCheck()
        
        // Log all detected threats
        result.threats.forEach { threat ->
            securityRepository.logThreat(threat)
        }
        
        return result
    }
}
