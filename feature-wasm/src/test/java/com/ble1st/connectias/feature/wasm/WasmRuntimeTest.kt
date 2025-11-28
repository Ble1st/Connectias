package com.ble1st.connectias.feature.wasm

import com.ble1st.connectias.feature.wasm.plugin.models.ResourceLimits
import com.ble1st.connectias.feature.wasm.wasm.WasmRuntime
import com.ble1st.connectias.feature.wasm.wasm.WasmRuntimeException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WasmRuntime.
 */
class WasmRuntimeTest {
    
    private lateinit var wasmRuntime: WasmRuntime
    
    @Before
    fun setup() {
        wasmRuntime = WasmRuntime()
    }
    
    @Test
    fun `loadModule should load valid WASM module`() {
        // Given
        val wasmBytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D) // Minimal WASM header
        
        // When
        val module = wasmRuntime.loadModule(wasmBytes)
        
        // Then
        assertNotNull(module)
        assertTrue(module.wasmBytes.contentEquals(wasmBytes))
    }
    
    @Test
    fun `loadModule should throw exception for invalid WASM`() {
        // Given
        val invalidBytes = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        
        // When/Then
        try {
            wasmRuntime.loadModule(invalidBytes)
            fail("Should have thrown exception")
        } catch (e: WasmRuntimeException) {
            assertTrue(e is WasmRuntimeException.ModuleLoadFailed)
        }
    }
    
    @Test
    fun `createStore should create store with resource limits`() {
        // Given
        val wasmBytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
        val module = wasmRuntime.loadModule(wasmBytes)
        val resourceLimits = ResourceLimits.DEFAULT
        
        // When
        val store = wasmRuntime.createStore(module, resourceLimits)
        
        // Then
        assertNotNull(store)
        assertEquals(module, store.module)
    }
    
    @Test
    fun `executeFunction should execute plugin_execute function`() {
        // Given
        val wasmBytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
        val module = wasmRuntime.loadModule(wasmBytes)
        val store = wasmRuntime.createStore(module, ResourceLimits.DEFAULT)
        
        // When
        val result = wasmRuntime.executeFunction(
            store = store,
            functionName = "plugin_execute",
            args = mapOf("command" to "hello")
        )
        
        // Then
        assertNotNull(result)
        assertTrue(result.contains("success"))
    }
    
    @Test
    fun `executeFunction should throw exception for unknown function`() {
        // Given
        val wasmBytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
        val module = wasmRuntime.loadModule(wasmBytes)
        val store = wasmRuntime.createStore(module, ResourceLimits.DEFAULT)
        
        // When/Then
        try {
            wasmRuntime.executeFunction(store, "unknown_function", emptyMap())
            fail("Should have thrown exception")
        } catch (e: WasmRuntimeException) {
            assertTrue(e is WasmRuntimeException.FunctionNotFound)
        }
    }
}

