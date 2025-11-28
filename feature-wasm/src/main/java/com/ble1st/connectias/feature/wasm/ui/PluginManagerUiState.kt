package com.ble1st.connectias.feature.wasm.ui

import com.ble1st.connectias.feature.wasm.plugin.models.WasmPlugin

/**
 * UI state for plugin manager screen.
 */
data class PluginManagerUiState(
    val plugins: List<WasmPlugin> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val hasPlugins: Boolean
        get() = plugins.isNotEmpty()
}

