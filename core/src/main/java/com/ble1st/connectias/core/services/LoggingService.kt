package com.ble1st.connectias.core.services

import com.ble1st.connectias.core.database.dao.SecurityLogDao
import com.ble1st.connectias.core.database.entities.SecurityLogEntity
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import com.ble1st.connectias.core.security.models.SecurityThreat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internal data class for threat log information.
 */
private data class ThreatLogInfo(
    val threatType: String,
    val threatLevel: String,
    val description: String,
    val details: String
)

/**
 * Service for logging security events to the database.
 * Provides automatic logging of security checks and log management functionality.
 * 
 * Features:
 * - Automatic logging of SecurityCheckResult
 * - Log rotation (automatic cleanup of old logs)
 * - Query functions for retrieving logs
 * - Thread-safe operations using coroutines
 */
@Singleton
class LoggingService @Inject constructor(
    private val securityLogDao: SecurityLogDao
) {
    companion object {
        // Default retention: 30 days
        private const val DEFAULT_RETENTION_DAYS = 30L
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
    
    // Use SupervisorJob to prevent child job failures from cancelling the scope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Logs a security check result to the database.
     * Each threat is logged as a separate entry.
     * If the check is secure, a single entry is logged indicating no threats.
     * 
     * @param result The SecurityCheckResult to log
     */
    fun logSecurityCheck(result: SecurityCheckResult) {
        serviceScope.launch {
            try {
                if (result.threats.isEmpty() && result.failedChecks.isEmpty()) {
                    // Log successful check
                    val logEntry = SecurityLogEntity(
                        timestamp = result.timestamp,
                        threatType = "SECURITY_CHECK",
                        threatLevel = "INFO",
                        description = "Security check completed - No threats detected",
                        details = "All security checks passed successfully"
                    )
                    securityLogDao.insert(logEntry)
                    Timber.d("Logged successful security check")
                } else {
                    // Log each threat individually
                    result.threats.forEach { threat ->
                        val (threatType, threatLevel, description, details) = when (threat) {
                            is SecurityThreat.RootDetected -> ThreatLogInfo(
                                threatType = "ROOT_DETECTED",
                                threatLevel = "CRITICAL",
                                description = "Root access detected",
                                details = "Detection method: ${threat.method}"
                            )
                            is SecurityThreat.DebuggerDetected -> ThreatLogInfo(
                                threatType = "DEBUGGER_DETECTED",
                                threatLevel = "HIGH",
                                description = "Debugger attached",
                                details = "Detection method: ${threat.method}"
                            )
                            is SecurityThreat.EmulatorDetected -> ThreatLogInfo(
                                threatType = "EMULATOR_DETECTED",
                                threatLevel = "MEDIUM",
                                description = "Running on emulator",
                                details = "Detection method: ${threat.method}"
                            )
                            is SecurityThreat.TamperDetected -> ThreatLogInfo(
                                threatType = "TAMPER_DETECTED",
                                threatLevel = "CRITICAL",
                                description = "Application tampering detected",
                                details = "Detection method: ${threat.method}"
                            )
                            is SecurityThreat.HookDetected -> ThreatLogInfo(
                                threatType = "HOOK_DETECTED",
                                threatLevel = "HIGH",
                                description = "Code hooking detected",
                                details = "Detection method: ${threat.method}"
                            )
                        }
                        
                        val logEntry = SecurityLogEntity(
                            timestamp = result.timestamp,
                            threatType = threatType,
                            threatLevel = threatLevel,
                            description = description,
                            details = details
                        )
                        securityLogDao.insert(logEntry)
                        Timber.d("Logged threat: $threatType")
                    }
                    
                    // Log failed checks
                    result.failedChecks.forEach { failedCheck ->
                        val logEntry = SecurityLogEntity(
                            timestamp = result.timestamp,
                            threatType = "CHECK_FAILED",
                            threatLevel = "WARNING",
                            description = "Security check failed: $failedCheck",
                            details = "The security check component '$failedCheck' failed to complete"
                        )
                        securityLogDao.insert(logEntry)
                        Timber.d("Logged failed check: $failedCheck")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to log security check")
                // Don't throw - logging failures should not break the app
            }
        }
    }

    /**
     * Performs log rotation by deleting logs older than the specified retention period.
     * 
     * @param retentionDays Number of days to retain logs (default: 30)
     */
    fun rotateLogs(retentionDays: Long = DEFAULT_RETENTION_DAYS) {
        serviceScope.launch {
            try {
                val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * MILLIS_PER_DAY)
                securityLogDao.deleteOldLogs(cutoffTimestamp)
                Timber.d("Log rotation completed: deleted logs older than $retentionDays days")
            } catch (e: Exception) {
                Timber.e(e, "Failed to rotate logs")
            }
        }
    }

}

