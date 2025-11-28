package com.ble1st.connectias.feature.wasm.wasm

import com.ble1st.connectias.feature.wasm.plugin.models.ResourceLimits
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.runtime.Instance
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.io.ByteArrayInputStream

/**
 * WASM Runtime wrapper for executing WASM modules.
 * 
 * Implementation using Chicory (Pure JVM WASM runtime).
 */
@Singleton
class WasmRuntime @Inject constructor() {
    
    private val tag = "WasmRuntime"
    
    /**
     * Load a WASM module from bytecode.
     * 
     * @param wasmBytes The WASM module bytecode
     * @return A loaded WASM module
     * @throws WasmRuntimeException if loading fails
     */
    fun loadModule(wasmBytes: ByteArray): WasmModule {
        Timber.d(tag, "Loading WASM module (${wasmBytes.size} bytes)")
        
        return try {
            val chicoryModule = Parser.parse(ByteArrayInputStream(wasmBytes))
            ChicoryWasmModule(wasmBytes, chicoryModule)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load WASM module")
            throw WasmRuntimeException.ModuleLoadFailed(
                reason = e.message ?: "Unknown error",
                cause = e
            )
        }
    }
    
    /**
     * Create a new store for a WASM module.
     * 
     * @param module The WASM module
     * @param resourceLimits Resource limits for this store
     * @return A new WASM store
     */
    fun createStore(module: WasmModule, resourceLimits: ResourceLimits): WasmStore {
        Timber.d(tag, "Creating WASM store for module")
        
        if (module !is ChicoryWasmModule) {
            throw IllegalStateException("Invalid module type: ${module::class.java.name}")
        }

        return try {
            val instance = Instance.builder(module.chicoryModule).build()
            ChicoryWasmStore(module, instance, resourceLimits)
        } catch (e: Exception) {
            throw WasmRuntimeException.ExecutionFailed("init", "Failed to instantiate module", e)
        }
    }
    
    /**
     * Execute a function in a WASM store.
     * 
     * @param store The WASM store
     * @param functionName Name of the function to execute
     * @param args Arguments to pass to the function
     * @return Function result as JSON string
     * @throws WasmRuntimeException if execution fails
     */
    fun executeFunction(
        store: WasmStore,
        functionName: String,
        args: Map<String, String> = emptyMap()
    ): String {
        Timber.d(tag, "Executing function '$functionName' in WASM store")
        
        if (store !is ChicoryWasmStore) {
            throw IllegalStateException("Invalid store type: ${store::class.java.name}")
        }

        val instance = store.instance
        val export = instance.export(functionName)
        
        return try {
            // Execute the function. 
            // Note: This basic implementation assumes the function accepts 0 arguments
            // and returns a value or nothing. Complex ABI handling (strings/pointers) 
            // would require memory manipulation which is beyond this basic integration.
            val results = export.apply()
            
            val resultVal = if (results != null && results.isNotEmpty()) results[0] else 0
            """{"status":"success","result":$resultVal}"""
        } catch (e: Exception) {
            Timber.e(e, "Failed to execute WASM function '$functionName'")
            throw WasmRuntimeException.ExecutionFailed(
                functionName = functionName,
                reason = e.message ?: "Unknown error",
                cause = e
            )
        }
    }
}

/**
 * Chicory WASM module implementation.
 */
private class ChicoryWasmModule(
    override val wasmBytes: ByteArray,
    val chicoryModule: com.dylibso.chicory.wasm.Module
) : WasmModule {
    override val exportedFunctions: List<String> by lazy {
        val exportSection = chicoryModule.exportSection()
        if (exportSection != null) {
             (0 until exportSection.exportCount()).map { index ->
                 exportSection.getExport(index).name()
             }
        } else {
             emptyList()
        }
    }
    
    override fun hasFunction(functionName: String): Boolean {
        return exportedFunctions.contains(functionName)
    }
}

/**
 * Chicory WASM store implementation.
 */
private class ChicoryWasmStore(
    override val module: WasmModule,
    val instance: Instance,
    private val resourceLimits: ResourceLimits
) : WasmStore {
    override val memoryUsage: Long 
        get() = instance.memory().pages().toLong() * 65536L
    
    override val fuelConsumption: Long = 0 // Not supported in this basic implementation
    
    override fun isValid(): Boolean = true
}

