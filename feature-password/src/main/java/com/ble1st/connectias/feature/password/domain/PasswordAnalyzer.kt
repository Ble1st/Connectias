package com.ble1st.connectias.feature.password.domain

import com.ble1st.connectias.feature.password.data.PasswordStrength
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordAnalyzer @Inject constructor() {

    fun analyzePasswordStrength(password: String): Pair<PasswordStrength, Int> {
        if (password.isEmpty()) {
            return PasswordStrength.WEAK to 0
        }

        var score = 0
        
        // 1. Length
        if (password.length >= 8) score += 10
        if (password.length >= 12) score += 10
        if (password.length >= 16) score += 10
        if (password.length >= 20) score += 10

        // 2. Character types
        if (password.any { it.isUpperCase() }) score += 10
        if (password.any { it.isLowerCase() }) score += 10
        if (password.any { it.isDigit() }) score += 10
        if (password.any { !it.isLetterOrDigit() }) score += 15 // Special chars worth more

        // 3. Variety bonus (if all types present)
        if (password.any { it.isUpperCase() } && 
            password.any { it.isLowerCase() } && 
            password.any { it.isDigit() } && 
            password.any { !it.isLetterOrDigit() }) {
            score += 15
        }

        // 4. Penalties
        // Only digits
        if (password.all { it.isDigit() }) score -= 10
        // Only letters
        if (password.all { it.isLetter() }) score -= 10
        // Repeating characters (e.g. "aaaa") - simplified check
        if (password.zipWithNext().count { it.first == it.second } > 2) score -= 10

        // Clamp score
        score = score.coerceIn(0, 100)

        val strength = when {
            score < 40 -> PasswordStrength.WEAK
            score < 70 -> PasswordStrength.MEDIUM
            score < 90 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }

        return strength to score
    }
}
