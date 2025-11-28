package com.ble1st.connectias.feature.wasm.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.wasm.plugin.PluginLoadException
import com.ble1st.connectias.feature.wasm.plugin.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for plugin manager screen.
 */
@HiltViewModel
class PluginManagerViewModel @Inject constructor(
    private val pluginManager: PluginManager
) : ViewModel() {
    
    private val tag = "PluginManagerViewModel"
    
    private val _uiState = MutableStateFlow(PluginManagerUiState())
    val uiState: StateFlow<PluginManagerUiState> = _uiState.asStateFlow()
    
    init {
        loadPlugins()
    }
    
    /**
     * Load all plugins.
     */
    fun loadPlugins() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                val plugins = pluginManager.getAllPlugins()
                _uiState.update { 
                    it.copy(
                        plugins = plugins,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load plugins")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load plugins: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Load a plugin from URI.
     * Note: This is a simplified implementation. In production, use ContentResolver
     * to properly read files from URIs (especially for scoped storage).
     */
    fun loadPlugin(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                // TODO: Use ContentResolver for proper file access
                // For now, simplified implementation assumes file:// URI
                val filePath = uri.path ?: throw IllegalArgumentException("Invalid URI path")
                val file = File(filePath)
                
                if (!file.exists()) {
                    throw PluginLoadException("File does not exist: $filePath")
                }
                
                pluginManager.loadPlugin(file)
                
                // Reload plugins
                loadPlugins()
            } catch (e: PluginLoadException) {
                Timber.e(e, "Failed to load plugin")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load plugin: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error loading plugin")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unexpected error: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Unload a plugin.
     */
    fun unloadPlugin(pluginId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                pluginManager.unloadPlugin(pluginId)
                
                // Reload plugins
                loadPlugins()
            } catch (e: Exception) {
                Timber.e(e, "Failed to unload plugin")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to unload plugin: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Execute a plugin command.
     */
    fun executePlugin(pluginId: String, command: String, args: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                val result = pluginManager.executePlugin(pluginId, command, args)
                Timber.d(tag, "Plugin execution result: $result")
                
                // Reload plugins to update status
                loadPlugins()
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute plugin")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to execute plugin: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

