package com.ble1st.connectias.feature.ssh.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.ssh.data.AuthMode
import com.ble1st.connectias.feature.ssh.data.SshProfile
import com.ble1st.connectias.feature.ssh.data.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SshViewModel @Inject constructor(
    private val repository: SshRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SshUiState())
    val state: StateFlow<SshUiState> = _state

    fun updateName(value: String) = _state.update { it.copy(name = value) }
    fun updateHost(value: String) = _state.update { it.copy(host = value) }
    fun updatePort(value: Int) = _state.update { it.copy(port = value) }
    fun updateUsername(value: String) = _state.update { it.copy(username = value) }
    fun updatePassword(value: String) = _state.update { it.copy(password = value) }
    fun updateAuthMode(mode: AuthMode) = _state.update { it.copy(authMode = mode) }

    fun saveProfile() {
        val profile = SshProfile(
            id = "",
            name = state.value.name,
            host = state.value.host,
            port = state.value.port,
            username = state.value.username,
            authMode = state.value.authMode,
            password = state.value.password
        )
        val profiles = repository.saveProfile(profile)
        _state.update { it.copy(profiles = profiles) }
    }

    fun loadProfiles() {
        _state.update { it.copy(profiles = repository.getProfiles()) }
    }

    fun testConnection(profile: SshProfile? = null) {
        val activeProfile = profile ?: SshProfile(
            id = "",
            name = state.value.name,
            host = state.value.host,
            port = state.value.port,
            username = state.value.username,
            authMode = state.value.authMode,
            password = state.value.password
        )
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.testConnection(activeProfile)
            _state.update { it.copy(connectionResult = result, isLoading = false, errorMessage = if (result.success) null else result.message) }
            loadProfiles()
        }
    }
}
