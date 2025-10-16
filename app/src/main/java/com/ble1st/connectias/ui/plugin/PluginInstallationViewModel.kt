package com.ble1st.connectias.ui.plugin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.api.PluginInfo
import com.ble1st.connectias.plugin.PluginManager
import com.ble1st.connectias.storage.database.entity.PluginEntity
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true, error = null)
            
            try {
                val result = pluginManager.installPlugin(pluginUri)
                when (result) {
                    is PluginManager.PluginInstallResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isInstalling = false,
                            isSuccess = true,
                            installedPlugin = result.pluginInfo
                        )
                        Timber.i("Plugin installed successfully: ${result.pluginInfo.id}")
                    }
                    is PluginManager.PluginInstallResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isInstalling = false,
                            error = result.message
                        )
                        Timber.e("Plugin installation failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    error = "Installation failed: ${e.message}"
                )
                Timber.e(e, "Plugin installation exception")
            }
        }
    }
    
    fun startPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                val success = pluginManager.startPlugin(pluginId)
                if (success) {
                    Timber.i("Plugin started: $pluginId")
                } else {
                    Timber.e("Failed to start plugin: $pluginId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception starting plugin: $pluginId")
            }
        }
    }
    
    fun stopPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                val success = pluginManager.stopPlugin(pluginId)
                if (success) {
                    Timber.i("Plugin stopped: $pluginId")
                } else {
                    Timber.e("Failed to stop plugin: $pluginId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception stopping plugin: $pluginId")
            }
        }
    }
    
    fun uninstallPlugin(pluginId: String) {
        viewModelScope.launch {
            try {
                val success = pluginManager.uninstallPlugin(pluginId)
                if (success) {
                    Timber.i("Plugin uninstalled: $pluginId")
                } else {
                    Timber.e("Failed to uninstall plugin: $pluginId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception uninstalling plugin: $pluginId")
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun resetState() {
        _uiState.value = PluginInstallationUiState()
    }
}

data class PluginInstallationUiState(
    val isInstalling: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val installedPlugin: PluginInfo? = null
)
