package com.ble1st.connectias.feature.wasm.wasm

/**
 * Represents a WASM store (execution context).
 * Each plugin should have its own store for isolation.
 */
interface WasmStore {
    /**
     * The module associated with this store.
     */
    val module: WasmModule
    
    /**
     * Current memory usage in bytes.
     */
    val memoryUsage: Long
    
    /**
     * Current fuel consumption.
     */
    val fuelConsumption: Long
    
    /**
     * Check if store is still valid.
     */
    fun isValid(): Boolean
}

