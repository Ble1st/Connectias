package com.ble1st.connectias.feature.wasm.wasm

/**
 * Exception thrown by WASM runtime operations.
 */
sealed class WasmRuntimeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Failed to load WASM module.
     */
    data class ModuleLoadFailed(
        val reason: String,
        override val cause: Throwable? = null
    ) : WasmRuntimeException("Failed to load WASM module: $reason", cause)
    
    /**
     * Failed to execute WASM function.
     */
    data class ExecutionFailed(
        val functionName: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : WasmRuntimeException("Failed to execute function '$functionName': $reason", cause)
    
    /**
     * Memory limit exceeded.
     */
    data class MemoryLimitExceeded(
        val used: Long,
        val limit: Long
    ) : WasmRuntimeException("Memory limit exceeded: used=$used, limit=$limit")
    
    /**
     * Fuel limit exceeded.
     */
    data class FuelLimitExceeded(
        val used: Long,
        val limit: Long
    ) : WasmRuntimeException("Fuel limit exceeded: used=$used, limit=$limit")
    
    /**
     * Function not found in WASM module.
     */
    data class FunctionNotFound(
        val functionName: String
    ) : WasmRuntimeException("Function '$functionName' not found in WASM module")
}

