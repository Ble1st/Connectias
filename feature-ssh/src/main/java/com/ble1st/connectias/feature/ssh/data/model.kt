package com.ble1st.connectias.feature.ssh.data

enum class AuthMode { PASSWORD, KEY }

data class SshProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMode: AuthMode,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val keyPassword: String? = null
)

data class SshConnectionResult(
    val success: Boolean,
    val message: String
)
