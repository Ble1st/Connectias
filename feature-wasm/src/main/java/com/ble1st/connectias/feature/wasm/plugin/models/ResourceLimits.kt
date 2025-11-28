package com.ble1st.connectias.feature.wasm.plugin.models

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Resource limits for a WASM plugin.
 */
@Serializable
data class ResourceLimits(
    /**
     * Maximum memory usage in bytes.
     * Default: 100MB
     */
    val maxMemory: Long = 100 * 1024 * 1024,
    
    /**
     * Maximum execution time.
     * Default: 30 seconds
     */
    val maxExecutionTimeSeconds: Long = 30,
    
    /**
     * Maximum fuel consumption.
     * Default: 1M fuel units
     */
    val maxFuel: Long = 1_000_000
) {
    /**
     * Get max execution time as Duration.
     */
    val maxExecutionTime: Duration
        get() = maxExecutionTimeSeconds.seconds
    
    companion object {
        /**
         * Default resource limits.
         */
        val DEFAULT = ResourceLimits()
    }
}

