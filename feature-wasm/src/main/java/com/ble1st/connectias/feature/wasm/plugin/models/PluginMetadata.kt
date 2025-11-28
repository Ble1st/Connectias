package com.ble1st.connectias.feature.wasm.plugin.models

import kotlinx.serialization.Serializable

/**
 * Metadata for a WASM plugin (from plugin.json).
 */
@Serializable
data class PluginMetadata(
    /**
     * Unique plugin identifier.
     */
    val id: String,
    
    /**
     * Plugin name.
     */
    val name: String,
    
    /**
     * Plugin version.
     */
    val version: String,
    
    /**
     * Plugin author.
     */
    val author: String,
    
    /**
     * Plugin description.
     */
    val description: String,
    
    /**
     * Required permissions.
     */
    val permissions: List<String> = emptyList(),
    
    /**
     * Resource limits for this plugin.
     */
    val resourceLimits: ResourceLimits = ResourceLimits.DEFAULT,
    
    /**
     * Minimum required core version.
     */
    val minCoreVersion: String? = null,
    
    /**
     * Maximum supported core version.
     */
    val maxCoreVersion: String? = null,
    
    /**
     * Plugin entry point (WASM file name).
     */
    val entryPoint: String = "main.wasm",
    
    /**
     * Plugin dependencies (other plugin IDs).
     */
    val dependencies: List<String>? = null
)

