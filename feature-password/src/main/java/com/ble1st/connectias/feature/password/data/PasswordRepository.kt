package com.ble1st.connectias.feature.password.data

import java.security.SecureRandom
import javax.inject.Inject
import kotlin.math.log2

class PasswordRepository @Inject constructor() {
    private val secureRandom = SecureRandom()

    fun checkPassword(password: String): PasswordCheckResult {
        val length = password.length
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val symbols = password.count { !it.isLetterOrDigit() }
        val charSpace = (if (hasLower) 26 else 0) +
            (if (hasUpper) 26 else 0) +
            (if (hasDigit) 10 else 0) +
            (if (symbols > 0) 32 else 0)
        val entropy = if (charSpace > 0) length * log2(charSpace.toDouble()) else 0.0
        val feedback = mutableListOf<String>()
        if (length < 12) feedback.add("Use at least 12 characters.")
        if (!hasUpper) feedback.add("Add uppercase letters.")
        if (!hasLower) feedback.add("Add lowercase letters.")
        if (!hasDigit) feedback.add("Add digits.")
        if (symbols == 0) feedback.add("Add symbols for higher entropy.")
        val strength = when {
            entropy >= 80 -> PasswordStrength.STRONG
            entropy >= 60 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
        return PasswordCheckResult(
            length = length,
            entropy = entropy,
            strength = strength,
            feedback = feedback
        )
    }

    fun generatePassword(config: PasswordGeneratorConfig): String {
        val length = config.length.coerceIn(8, 256)
        val pool = buildString {
            if (config.includeLowercase) append(LOWER)
            if (config.includeUppercase) append(UPPER)
            if (config.includeDigits) append(DIGITS)
            if (config.includeSymbols) append(SYMBOLS)
        }
        if (pool.isEmpty()) return ""
        val chars = CharArray(length) {
            pool[secureRandom.nextInt(pool.length)]
        }
        return String(chars)
    }

    companion object {
        private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private const val SYMBOLS = "!@#\$%^&*()-_=+[{]}|;:'\",<.>/?`~"
    }
}
