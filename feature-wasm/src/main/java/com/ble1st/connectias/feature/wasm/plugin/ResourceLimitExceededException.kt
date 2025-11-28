package com.ble1st.connectias.feature.wasm.plugin

/**
 * Exception thrown when resource limits are exceeded.
 */
sealed class ResourceLimitExceededException(
    message: String
) : Exception(message) {
    
    /**
     * Memory limit exceeded.
     */
    data class MemoryLimit(
        val used: Long,
        val limit: Long
    ) : ResourceLimitExceededException(
        "Memory limit exceeded: used=$used bytes, limit=$limit bytes"
    )
    
    /**
     * CPU/Fuel limit exceeded.
     */
    data class FuelLimit(
        val used: Long,
        val limit: Long
    ) : ResourceLimitExceededException(
        "Fuel limit exceeded: used=$used, limit=$limit"
    )
    
    /**
     * Execution time limit exceeded.
     */
    data class TimeLimit(
        val usedMillis: Long,
        val limitMillis: Long
    ) : ResourceLimitExceededException(
        "Execution time limit exceeded: used=${usedMillis}ms, limit=${limitMillis}ms"
    )
}

