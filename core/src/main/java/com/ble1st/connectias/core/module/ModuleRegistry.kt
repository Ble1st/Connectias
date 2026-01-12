package com.ble1st.connectias.core.module

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _modulesFlow = MutableStateFlow<List<ModuleInfo>>(emptyList())
    val modulesFlow: StateFlow<List<ModuleInfo>> = _modulesFlow.asStateFlow()
    
    fun registerModule(moduleInfo: ModuleInfo) {
        modules[moduleInfo.id] = moduleInfo
        updateFlow()
    }

    fun getActiveModules(): List<ModuleInfo> {
        return modules.values.filter { it.isActive }
    }
    
    fun getAllModules(): List<ModuleInfo> {
        return modules.values.toList()
    }
    
    /**
     * Updates the active state of a module.
     * Returns true if the module was found and updated, false otherwise.
     */
    fun updateModuleState(moduleId: String, isActive: Boolean): Boolean {
        val existingModule = modules[moduleId] ?: return false
        val updatedModule = existingModule.copy(isActive = isActive)
        modules[moduleId] = updatedModule
        updateFlow()
        return true
    }
    
    /**
     * Unregisters a module from the registry.
     */
    fun unregisterModule(moduleId: String): Boolean {
        val removed = modules.remove(moduleId) != null
        if (removed) {
            updateFlow()
        }
        return removed
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
    
    private fun updateFlow() {
        _modulesFlow.value = modules.values.toList()
    }
}

