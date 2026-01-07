package com.ble1st.connectias.plugin.barcode

import com.ble1st.connectias.plugin.IPlugin
import com.ble1st.connectias.plugin.PluginCategory
import com.ble1st.connectias.plugin.PluginContext
import com.ble1st.connectias.plugin.PluginMetadata
import com.ble1st.connectias.plugin.annotations.ConnectiasPlugin

/**
 * Barcode Plugin - QR Code and Barcode Scanner/Generator
 */
@ConnectiasPlugin(
    id = "barcode_tools",
    name = "Barcode Tools",
    version = "1.0.0",
    author = "Ble1st",
    category = "UTILITY"
)
class BarcodePlugin : IPlugin {
    
    private lateinit var context: PluginContext
    private var isEnabled = false
    
    override fun getMetadata(): PluginMetadata = PluginMetadata(
        pluginId = "barcode_tools",
        pluginName = "Barcode Tools",
        version = "1.0.0",
        author = "Ble1st",
        minApiLevel = 33,
        maxApiLevel = 36,
        minAppVersion = "1.0.0",
        nativeLibraries = emptyList(),
        category = PluginCategory.UTILITY,
        description = "QR Code and Barcode Scanner/Generator",
        permissions = listOf(
            "android.permission.CAMERA"
        ),
        dependencies = emptyList(),
        fragmentClassName = "com.ble1st.connectias.plugin.barcode.ui.BarcodeFragment"
    )
    
    override fun onLoad(context: PluginContext): Boolean {
        this.context = context
        context.logDebug("BarcodePlugin.onLoad()")
        
        // Initialize plugin resources if needed
        // Load native libraries if any
        // Register services
        
        return true
    }
    
    override fun onEnable(): Boolean {
        isEnabled = true
        context.logDebug("BarcodePlugin enabled")
        return true
    }
    
    override fun onDisable(): Boolean {
        isEnabled = false
        context.logDebug("BarcodePlugin disabled")
        return true
    }
    
    override fun onUnload(): Boolean {
        context.logDebug("BarcodePlugin.onUnload()")
        // Cleanup resources
        return true
    }
    
    override fun onPause() {
        context.logDebug("BarcodePlugin paused")
    }
    
    override fun onResume() {
        context.logDebug("BarcodePlugin resumed")
    }
}
