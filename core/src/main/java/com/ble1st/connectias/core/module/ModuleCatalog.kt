package com.ble1st.connectias.core.module

/**
 * Central catalog of all available modules with their metadata.
 * Provides structured information for module discovery and UI integration.
 */
object ModuleCatalog {
    
    /**
     * Complete metadata for a module.
     */
    data class ModuleMetadata(
        val id: String,
        val name: String,
        val version: String,
        val fragmentClassName: String,
        val navigationRouteId: Int? = null,
        val iconResourceId: Int? = null,
        val category: ModuleCategory = ModuleCategory.UTILITY,
        val isCore: Boolean = false,
        val description: String? = null
    )
    
    /**
     * Categories for organizing modules.
     */
    enum class ModuleCategory {
        SECURITY,
        NETWORK,
        PRIVACY,
        UTILITY,
    }
    
    /**
     * Core modules that are always active.
     */
    val CORE_MODULES = emptyList<ModuleMetadata>()
    
    /**
     * Optional modules that may be compiled into the app.
     * Note: All optional feature modules have been removed and migrated to plugin system.
     * Features are now loaded dynamically via the plugin-sdk-temp directory.
     */
    val OPTIONAL_MODULES = emptyList<ModuleMetadata>()
    
    /**
     * All known modules (core + optional).
     */
    val ALL_MODULES = CORE_MODULES + OPTIONAL_MODULES
    
    /**
     * Finds module metadata by ID.
     */
    fun findById(id: String): ModuleMetadata? {
        return ALL_MODULES.find { it.id == id }
    }

    /**
     * Gets all modules in a specific category.
     */
    fun getByCategory(category: ModuleCategory): List<ModuleMetadata> {
        return ALL_MODULES.filter { it.category == category }
    }
    
    /**
     * Checks if a module is available (its fragment class exists).
     */
    fun isModuleAvailable(metadata: ModuleMetadata): Boolean {
        return try {
            Class.forName(metadata.fragmentClassName)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    /**
     * Gets all available modules (those whose fragment classes exist).
     */
    fun getAvailableModules(): List<ModuleMetadata> {
        return ALL_MODULES.filter { isModuleAvailable(it) }
    }
}

