package com.ble1st.connectias.feature.wasm.wasm

/**
 * Represents a loaded WASM module.
 * This is a wrapper around the actual WASM module implementation.
 */
interface WasmModule {
    /**
     * The raw WASM bytecode.
     */
    val wasmBytes: ByteArray
    
    /**
     * List of exported function names.
     */
    val exportedFunctions: List<String>
    
    /**
     * Check if a function is exported.
     */
    fun hasFunction(functionName: String): Boolean
}

