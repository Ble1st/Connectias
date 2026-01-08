package com.ble1st.connectias.core.model

/**
 * Result of a security check operation.
 * Immutable data class with defensive copying.
 */
data class SecurityCheckResult private constructor(
    val isSecure: Boolean,
    val threats: List<SecurityThreat>,
    val failedChecks: List<String>,
    val allChecksCompleted: Boolean,
    val timestamp: Long
) {
    init {
        require(!isSecure || threats.isEmpty()) {
            "isSecure can only be true when threats is empty"
        }
        require(!(!allChecksCompleted || failedChecks.isNotEmpty()) || !isSecure) {
            "isSecure cannot be true when checks failed or did not complete"
        }
    }
    
    companion object {
        /**
         * Factory method that creates SecurityCheckResult with defensive copy of threats.
         * This ensures the threats list cannot be modified after construction.
         */
        fun create(
            isSecure: Boolean,
            threats: Collection<SecurityThreat>,
            failedChecks: Collection<String> = emptyList(),
            allChecksCompleted: Boolean = true,
            timestamp: Long = System.currentTimeMillis()
        ): SecurityCheckResult {
            val finalIsSecure = isSecure && allChecksCompleted && failedChecks.isEmpty()
            
            val immutableThreats = threats.toList()
            val immutableFailedChecks = failedChecks.toList()
            
            return SecurityCheckResult(
                isSecure = finalIsSecure,
                threats = immutableThreats,
                failedChecks = immutableFailedChecks,
                allChecksCompleted = allChecksCompleted,
                timestamp = timestamp
            )
        }
    }
}
