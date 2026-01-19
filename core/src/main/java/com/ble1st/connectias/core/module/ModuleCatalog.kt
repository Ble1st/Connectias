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
    val CORE_MODULES = listOf(
        ModuleMetadata(
            id = "settings",
            name = "Settings",
            version = "1.0",
            fragmentClassName = "com.ble1st.connectias.feature.settings.ui.SettingsFragment",
            category = ModuleCategory.UTILITY,
            isCore = true,
            description = "Application settings"
        ),
        ModuleMetadata(
            id = "log_viewer",
            name = "Log Viewer",
            version = "1.0",
            fragmentClassName = "com.ble1st.connectias.ui.logging.LogViewerFragment",
            category = ModuleCategory.SECURITY,
            isCore = true,
            description = "System log viewer"
        ),
        ModuleMetadata(
            id = "plugin_management",
            name = "Plugin Management",
            version = "1.0",
            fragmentClassName = "com.ble1st.connectias.ui.plugin.PluginManagementFragment",
            category = ModuleCategory.UTILITY,
            isCore = true,
            description = "Manage installed plugins"
        ),
        ModuleMetadata(
            id = "plugin_store",
            name = "Plugin Store",
            version = "1.0",
            fragmentClassName = "com.ble1st.connectias.ui.plugin.store.PluginStoreFragment",
            category = ModuleCategory.NETWORK,
            isCore = true,
            description = "Browse and install plugins"
        )
    )
    
    /**
     * Optional modules that may be compiled into the app.
     * Note: All optional feature modules have been removed and migrated to plugin system.
     * Features are now loaded dynamically via the plugin-sdk-temp directory.
     */
    val OPTIONAL_MODULES = listOf(
        ModuleMetadata(
            id = "utilities",
            name = "Utilities",
            version = "1.0",
            fragmentClassName = "com.ble1st.connectias.ui.utilities.UtilitiesFragment",
            category = ModuleCategory.UTILITY,
            isCore = false,
            description = "Utility tools"
        ),
        ModuleMetadata(
            id = "backup",
            name = "Backup",
            version = "1.0",
            fragmentClassName = "com.ble1st.connectias.ui.backup.BackupFragment",
            category = ModuleCategory.UTILITY,
            isCore = false,
            description = "Backup and restore utilities"
        )
    )
    
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

