package com.ble1st.connectias.feature.password.ui

import com.ble1st.connectias.feature.password.data.PasswordCheckResult
import com.ble1st.connectias.feature.password.data.PasswordGeneratorConfig

data class PasswordUiState(
    val passwordInput: String = "",
    val passwordCheck: PasswordCheckResult? = null,
    val generatorConfig: PasswordGeneratorConfig = PasswordGeneratorConfig(length = 16),
    val generatedPassword: String = "",
    val errorMessage: String? = null
)
