package com.ble1st.connectias.core.services

import com.ble1st.connectias.core.security.RaspManager
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityService @Inject constructor(
    private val raspManager: RaspManager
) {
    suspend fun performSecurityCheck(): SecurityCheckResult {
        return raspManager.performSecurityChecks()
    }
}

