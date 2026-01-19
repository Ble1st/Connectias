package com.ble1st.connectias.core.data.security

import com.ble1st.connectias.core.model.SecurityCheckResult

/**
 * Abstraction for performing security checks from the data layer.
 */
interface SecurityCheckProvider {
    suspend fun performSecurityCheck(): SecurityCheckResult
}
