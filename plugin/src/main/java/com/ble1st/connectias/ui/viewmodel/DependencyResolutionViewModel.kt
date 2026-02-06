package com.ble1st.connectias.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.plugin.PluginDependencyResolverV2
import com.ble1st.connectias.plugin.dependency.DependencyResolutionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DependencyResolutionViewModel @Inject constructor(
    private val pluginDependencyResolver: PluginDependencyResolverV2
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<DependencyResolutionUiState>(
        DependencyResolutionUiState.Loading("Initializing...")
    )
    val uiState: StateFlow<DependencyResolutionUiState> = _uiState.asStateFlow()
    
    fun loadDependencies(pluginId: String) {
        viewModelScope.launch {
            _uiState.value = DependencyResolutionUiState.Loading("Resolving dependencies...")
            
            try {
                val result = pluginDependencyResolver.resolveDependencies(
                    pluginId = pluginId,
                    onProgress = { message ->
                        _uiState.value = DependencyResolutionUiState.Loading(message)
                    }
                ).getOrThrow()
                
                _uiState.value = DependencyResolutionUiState.Success(result)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error resolving dependencies")
                _uiState.value = DependencyResolutionUiState.Error(
                    e.message ?: "Unexpected error occurred"
                )
            }
        }
    }
    
    fun retry(pluginId: String) {
        loadDependencies(pluginId)
    }
}

sealed class DependencyResolutionUiState {
    data class Loading(val message: String) : DependencyResolutionUiState()
    data class Success(val result: DependencyResolutionResult) : DependencyResolutionUiState()
    data class Error(val message: String) : DependencyResolutionUiState()
}
