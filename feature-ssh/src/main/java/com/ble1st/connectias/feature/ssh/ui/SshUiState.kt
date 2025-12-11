package com.ble1st.connectias.feature.ssh.ui

import com.ble1st.connectias.feature.ssh.data.AuthMode
import com.ble1st.connectias.feature.ssh.data.SshConnectionResult
import com.ble1st.connectias.feature.ssh.data.SshProfile

data class SshUiState(
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    val authMode: AuthMode = AuthMode.PASSWORD,
    
    val privateKeyPath: String = "",
    val keyPassword: String = "",
    
    val profiles: List<SshProfile> = emptyList(),
    val connectionResult: SshConnectionResult? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
