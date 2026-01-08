package com.ble1st.connectias.core.domain

import com.ble1st.connectias.core.data.repository.SecurityRepository
import com.ble1st.connectias.core.model.SecurityThreat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case that combines security check results with threat history
 * to provide a comprehensive security status.
 */
class GetSecurityStatusUseCase @Inject constructor(
    private val securityRepository: SecurityRepository
) {
    operator fun invoke(): Flow<SecurityStatus> {
        return securityRepository.getRecentThreats(limit = 100).map { threats ->
            SecurityStatus(
                currentThreats = threats.filter { isRecentThreat(it) },
                threatHistory = threats.takeLast(10),
                riskLevel = calculateRiskLevel(threats),
                recommendations = generateRecommendations(threats)
            )
        }
    }

    private fun isRecentThreat(threat: SecurityThreat): Boolean {
        // Consider threats from last 24 hours as current
        // This is a placeholder - actual implementation would check timestamp
        return true
    }

    private fun calculateRiskLevel(threats: List<SecurityThreat>): RiskLevel {
        val recentThreats = threats.filter { isRecentThreat(it) }
        
        return when {
            recentThreats.any { it is SecurityThreat.RootDetected } -> RiskLevel.CRITICAL
            recentThreats.any { it is SecurityThreat.TamperDetected } -> RiskLevel.CRITICAL
            recentThreats.any { it is SecurityThreat.DebuggerDetected } -> RiskLevel.HIGH
            recentThreats.any { it is SecurityThreat.EmulatorDetected } -> RiskLevel.MEDIUM
            recentThreats.isEmpty() -> RiskLevel.LOW
            else -> RiskLevel.MEDIUM
        }
    }

    private fun generateRecommendations(threats: List<SecurityThreat>): List<String> {
        val recommendations = mutableListOf<String>()
        
        threats.forEach { threat ->
            when (threat) {
                is SecurityThreat.RootDetected -> {
                    recommendations.add("Device is rooted. Consider using on a non-rooted device.")
                }
                is SecurityThreat.DebuggerDetected -> {
                    recommendations.add("Debugger detected. Close debugging tools.")
                }
                is SecurityThreat.EmulatorDetected -> {
                    recommendations.add("Running on emulator. Use physical device for production.")
                }
                is SecurityThreat.TamperDetected -> {
                    recommendations.add("App tampering detected. Reinstall from official source.")
                }
                is SecurityThreat.HookDetected -> {
                    recommendations.add("Hooking framework detected. Remove suspicious apps.")
                }
            }
        }
        
        return recommendations.distinct()
    }
}

data class SecurityStatus(
    val currentThreats: List<SecurityThreat>,
    val threatHistory: List<SecurityThreat>,
    val riskLevel: RiskLevel,
    val recommendations: List<String>
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
