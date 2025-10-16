package com.ble1st.connectias.ui.plugin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.plugin.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PluginInstallationViewModel @Inject constructor(
    private val context: Context,
    private val pluginManager: PluginManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PluginInstallationUiState())
    val uiState: StateFlow<PluginInstallationUiState> = _uiState.asStateFlow()
    
    fun installPlugin(pluginUri: Uri) {
        Timber.d("PluginInstallationViewModel: Starting plugin installation")
        Timber.d("PluginInstallationViewModel: URI = $pluginUri")
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true, error = null)
            
            try {
                Timber.d("PluginInstallationViewModel: Calling pluginManager.installPlugin()")
                val result = pluginManager.installPlugin(pluginUri)
                
                result.fold(
                    onSuccess = { pluginInfo ->
                        Timber.i("PluginInstallationViewModel: Plugin installation successful")
                        Timber.i("PluginInstallationViewModel: Plugin ID = ${pluginInfo.id}")
                        Timber.i("PluginInstallationViewModel: Plugin Name = ${pluginInfo.name}")
                        
                        _uiState.value = _uiState.value.copy(
                            isInstalling = false,
                            installResult = "Success"
                        )
                    },
                    onFailure = { exception ->
                        Timber.e("PluginInstallationViewModel: Plugin installation failed")
                        Timber.e(exception, "PluginInstallationViewModel: Error = ${exception.message}")
                        
                        _uiState.value = _uiState.value.copy(
                            isInstalling = false,
                            error = exception.message ?: "Unknown error occurred"
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e("PluginInstallationViewModel: Unexpected exception during installation")
                Timber.e(e, "PluginInstallationViewModel: Exception = ${e.message}")
                
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    fun startPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                // Simplified plugin start
                Timber.d("Starting plugin: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "Error starting plugin: $pluginId")
            }
        }
    }
    
    fun stopPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                // Simplified plugin stop
                Timber.d("Stopping plugin: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "Error stopping plugin: $pluginId")
            }
        }
    }
    
    fun uninstallPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                // Simplified plugin uninstall
                Timber.d("Uninstalling plugin: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "Error uninstalling plugin: $pluginId")
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class PluginInstallationUiState(
    val isInstalling: Boolean = false,
    val installResult: String? = null,
    val error: String? = null
)