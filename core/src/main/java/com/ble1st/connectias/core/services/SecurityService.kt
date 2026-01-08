package com.ble1st.connectias.core.services

import android.os.Process
import com.ble1st.connectias.core.BuildConfig
import com.ble1st.connectias.core.security.RaspManager
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for performing security checks.
 * Automatically logs all security check results to the database.
 */
@Singleton
class SecurityService @Inject constructor(
    private val raspManager: RaspManager
) {

    /**
     * Performs comprehensive security checks.
     * 
     * @return SecurityCheckResult containing all detected threats
     */
    suspend fun performSecurityCheck(): SecurityCheckResult {
        val result = raspManager.performSecurityChecks()
        
        // Note: Logging moved to Domain Layer (PerformSecurityCheckUseCase)
        // Ensure logs are written before termination
        delay(100) // Small delay to ensure log write
        
        // Terminate if threats detected (except in debug builds)
        if (result.threats.isNotEmpty() && !BuildConfig.DEBUG) {
            Timber.e("Security threat detected - terminating app. Threats: ${result.threats.size}")
            Process.killProcess(Process.myPid())
        }        
        return result
    }
}

