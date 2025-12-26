package com.ble1st.connectias.core.module

import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime information about a module.
 * This is the active state representation, while ModuleCatalog contains static metadata.
 */
data class ModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val isActive: Boolean
)

/**
 * Registry for active modules at runtime.
 * Modules are registered here after discovery from ModuleCatalog.
 */
class ModuleRegistry {
    private val modules = ConcurrentHashMap<String, ModuleInfo>()
    
    fun registerModule(moduleInfo: ModuleInfo) {
        modules[moduleInfo.id] = moduleInfo
    }

    fun getActiveModules(): List<ModuleInfo> {
        return modules.values.filter { it.isActive }
    }
    
    /**
     * Registers a module from ModuleCatalog metadata.
     */
    fun registerFromMetadata(metadata: ModuleCatalog.ModuleMetadata, isActive: Boolean = true) {
        registerModule(
            ModuleInfo(
                id = metadata.id,
                name = metadata.name,
                version = metadata.version,
                isActive = isActive
            )
        )
    }
}

