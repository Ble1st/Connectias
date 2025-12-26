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
     */
    val OPTIONAL_MODULES = listOf(
        ModuleMetadata(
            id = "privacy",
            name = "Privacy Dashboard",
            version = "1.0.0",
            fragmentClassName = "com.ble1st.connectias.feature.privacy.ui.PrivacyDashboardFragment",
            category = ModuleCategory.PRIVACY,
            description = "Privacy settings and data protection"
        ),
        ModuleMetadata(
            id = "utilities",
            name = "Utilities",
            version = "1.0.0",
            fragmentClassName = "com.ble1st.connectias.feature.utilities.ui.UtilitiesDashboardFragment",
            category = ModuleCategory.UTILITY,
            description = "Utility tools: Hash, Encoding, QR Code, Text tools"
        ),
        ModuleMetadata(
            id = "backup",
            name = "Backup & Restore",
            version = "1.0.0",
            fragmentClassName = "com.ble1st.connectias.feature.backup.ui.BackupDashboardFragment",
            category = ModuleCategory.UTILITY,
            description = "Backup and restore app data"
        ),
        ModuleMetadata(
            id = "secure_notes",
            name = "Secure Notes",
            version = "1.0.0",
            fragmentClassName = "com.ble1st.connectias.feature.securenotes.ui.SecureNotesFragment",
            category = ModuleCategory.SECURITY,
            description = "Encrypted note storage"
        ),
        ModuleMetadata(
            id = "bluetooth_scanner",
            name = "Bluetooth Scanner",
            version = "1.0.0",
            fragmentClassName = "com.ble1st.connectias.feature.bluetooth.ui.BluetoothScannerFragment",
            category = ModuleCategory.NETWORK,
            description = "Scan nearby Bluetooth devices with signal proximity"
        ),
        ModuleMetadata(
            id = "network_tools",
            name = "Network Tools",
            version = "1.0.0",
            fragmentClassName = "com.ble1st.connectias.feature.network.ui.NetworkToolsFragment",
            category = ModuleCategory.NETWORK,
            description = "WLAN, Netz, Port und SSL Scanner"
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

