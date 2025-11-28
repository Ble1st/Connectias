package com.ble1st.connectias.feature.wasm

import com.ble1st.connectias.feature.wasm.plugin.ResourceLimitExceededException
import com.ble1st.connectias.feature.wasm.plugin.ResourceMonitor
import com.ble1st.connectias.feature.wasm.plugin.models.ResourceLimits
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ResourceMonitor.
 */
class ResourceMonitorTest {
    
    private lateinit var resourceMonitor: ResourceMonitor
    
    @Before
    fun setup() {
        resourceMonitor = ResourceMonitor()
    }
    
    @Test
    fun `enforceLimits should allow execution within limits`() = runTest {
        val resourceLimits = ResourceLimits.DEFAULT
        
        val result = resourceMonitor.enforceLimits("test-plugin", resourceLimits) {
            "success"
        }
        
        assertEquals("success", result)
    }
    
    @Test
    fun `enforceLimits should throw exception on timeout`() = runTest {
        val resourceLimits = ResourceLimits(
            maxMemory = Long.MAX_VALUE,
            maxExecutionTimeSeconds = 1, // 1 second timeout
            maxFuel = Long.MAX_VALUE
        )
        
        try {
            resourceMonitor.enforceLimits("test-plugin", resourceLimits) {
                delay(2000) // Delay longer than timeout
                "should not reach here"
            }
            fail("Should have thrown TimeoutCancellationException")
        } catch (e: ResourceLimitExceededException.TimeLimit) {
            assertNotNull(e)
        }
    }
}

