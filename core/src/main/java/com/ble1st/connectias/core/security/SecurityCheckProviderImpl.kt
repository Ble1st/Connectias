package com.ble1st.connectias.core.security

import com.ble1st.connectias.core.data.security.SecurityCheckProvider
import com.ble1st.connectias.core.model.SecurityCheckResult
import com.ble1st.connectias.core.model.SecurityThreat
import com.ble1st.connectias.core.security.models.SecurityCheckResult as CoreSecurityCheckResult
import com.ble1st.connectias.core.security.models.SecurityThreat as CoreSecurityThreat
import com.ble1st.connectias.core.services.SecurityService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges core security checks to the data layer.
 */
@Singleton
class SecurityCheckProviderImpl @Inject constructor(
    private val securityService: SecurityService
) : SecurityCheckProvider {

    override suspend fun performSecurityCheck(): SecurityCheckResult {
        return securityService.performSecurityCheck().toModel()
    }
}

private fun CoreSecurityCheckResult.toModel(): SecurityCheckResult {
    val modelThreats = threats.map { it.toModel() }

    return SecurityCheckResult.create(
        isSecure = isSecure,
        threats = modelThreats,
        failedChecks = failedChecks,
        allChecksCompleted = allChecksCompleted
    )
}

private fun CoreSecurityThreat.toModel(): SecurityThreat {
    return when (this) {
        is CoreSecurityThreat.RootDetected -> SecurityThreat.RootDetected()
        is CoreSecurityThreat.DebuggerDetected -> SecurityThreat.DebuggerDetected()
        is CoreSecurityThreat.EmulatorDetected -> SecurityThreat.EmulatorDetected()
        is CoreSecurityThreat.TamperDetected -> SecurityThreat.TamperDetected()
        is CoreSecurityThreat.HookDetected -> SecurityThreat.HookDetected()
    }
}
