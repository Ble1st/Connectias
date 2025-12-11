package com.ble1st.connectias.feature.password.ui

import com.ble1st.connectias.feature.password.data.PasswordCheckResult
import com.ble1st.connectias.feature.password.data.PasswordGeneratorConfig
import com.ble1st.connectias.feature.password.data.PasswordHistoryEntity

data class PasswordUiState(
    val passwordInput: String = "",
    val generatedPassword: String = "",
    val passwordCheck: PasswordCheckResult? = null,
    val generatorConfig: PasswordGeneratorConfig = PasswordGeneratorConfig(length = 12),
    
    val isPassphraseMode: Boolean = false,
    val passphraseWordCount: Int = 4,
    val passphraseSeparator: String = "-",
    
    val history: List<PasswordHistoryEntity> = emptyList()
)
