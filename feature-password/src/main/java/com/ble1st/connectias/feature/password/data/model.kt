package com.ble1st.connectias.feature.password.data

enum class PasswordStrength { WEAK, MEDIUM, STRONG, VERY_STRONG }

data class PasswordCheckResult(
    val length: Int,
    val entropy: Double,
    val score: Int, // 0-100
    val strength: PasswordStrength,
    val feedback: List<String>
)

data class PasswordGeneratorConfig(
    val length: Int,
    val includeLowercase: Boolean = true,
    val includeUppercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true
)
