package com.ble1st.connectias.feature.ssh.ui

import com.ble1st.connectias.feature.ssh.data.AuthMode
import com.ble1st.connectias.feature.ssh.data.SshConnectionResult
import com.ble1st.connectias.feature.ssh.data.SshProfile

data class SshUiState(
    val profiles: List<SshProfile> = emptyList(),
    val name: String = "Server",
    val host: String = "example.com",
    val port: Int = 22,
    val username: String = "user",
    val password: String = "",
    val authMode: AuthMode = AuthMode.PASSWORD,
    val connectionResult: SshConnectionResult? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)
