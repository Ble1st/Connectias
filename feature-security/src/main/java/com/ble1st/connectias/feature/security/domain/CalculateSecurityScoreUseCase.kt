package com.ble1st.connectias.feature.security.domain

import com.ble1st.connectias.core.services.SecurityService
import com.ble1st.connectias.feature.security.scanner.VulnerabilityScannerProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class UnifiedSecurityScore(
    val totalScore: Int, // 0-100
    val raspScore: Int, // 0-100
    val vulnerabilityScore: Int, // 0-100
    val criticalThreats: List<String>
)

@Singleton
class CalculateSecurityScoreUseCase @Inject constructor(
    private val securityService: SecurityService, // RASP (Core)
    private val scannerProvider: VulnerabilityScannerProvider // CVEs/Config
) {

    suspend fun execute(): UnifiedSecurityScore = coroutineScope {
        val raspDeferred = async { securityService.performSecurityCheck() }
        val scannerDeferred = async { scannerProvider.performSystemSecurityChecks() }

        val raspResult = raspDeferred.await()
        val scannerResult = scannerDeferred.await()

        // Scoring Logic
        var raspScore = 100
        if (raspResult.threats.isNotEmpty()) raspScore = 0 // RASP is binary: Secure or Compromised

        // Vuln Score Logic
        var vulnScore = 100
        val failedChecks = scannerResult.filter { !it.passed }
        if (failedChecks.isNotEmpty()) {
            // Deduct based on severity
            // Critical: 20, High: 10, Medium: 5
            val penalty = failedChecks.sumOf { 
                when(it.severity.name) {
                    "CRITICAL" -> 20
                    "HIGH" -> 10
                    else -> 5
                }.toInt() 
            }
            vulnScore -= penalty
        }
        vulnScore = vulnScore.coerceIn(0, 100)

        // Weighted Total Score
        // RASP is most critical (60%), then Vulns (40%)
        // Privacy is currently disabled in this calculation until provider is fixed
        val totalScore = (raspScore * 0.6 + vulnScore * 0.4).roundToInt()

        val threats = mutableListOf<String>()
        threats.addAll(raspResult.threats.map { it.name })
        threats.addAll(failedChecks.map { "${it.checkName}: ${it.details}" })

        UnifiedSecurityScore(
            totalScore = totalScore,
            raspScore = raspScore,
            vulnerabilityScore = vulnScore,
            criticalThreats = threats
        )
    }
}