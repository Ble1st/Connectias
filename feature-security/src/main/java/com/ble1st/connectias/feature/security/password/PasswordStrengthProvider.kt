package com.ble1st.connectias.feature.security.password

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for password strength analysis and generation.
 */
@Singleton
class PasswordStrengthProvider @Inject constructor() {

    /**
     * Common weak passwords list (simplified).
     */
    private val commonPasswords = setOf(
        "password", "123456", "123456789", "12345678", "12345",
        "1234567", "1234567890", "qwerty", "abc123", "password1",
        "welcome", "monkey", "1234", "letmein", "trustno1",
        "dragon", "baseball", "iloveyou", "master", "sunshine"
    )

    /**
     * Analyzes password strength.
     * 
     * @param password The password to analyze
     * @return PasswordStrength with score and feedback
     */
    suspend fun analyzePassword(password: String): PasswordStrength = withContext(Dispatchers.Default) {
        if (password.isEmpty()) {
            return@withContext PasswordStrength(
                score = 0,
                strength = Strength.VERY_WEAK,
                feedback = listOf("Password cannot be empty"),
                entropy = 0.0
            )
        }

        var score = 0
        val feedback = mutableListOf<String>()

        // Length check
        when {
            password.length >= 12 -> {
                score += 2
                feedback.add("✓ Good length (12+ characters)")
            }
            password.length >= 8 -> {
                score += 1
                feedback.add("✓ Acceptable length (8+ characters)")
            }
            else -> {
                feedback.add("✗ Too short (minimum 8 characters recommended)")
            }
        }

        // Character variety checks
        val hasLowercase = password.any { it.isLowerCase() }
        val hasUppercase = password.any { it.isUpperCase() }
        val hasDigits = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        if (hasLowercase) {
            score += 1
            feedback.add("✓ Contains lowercase letters")
        } else {
            feedback.add("✗ Missing lowercase letters")
        }

        if (hasUppercase) {
            score += 1
            feedback.add("✓ Contains uppercase letters")
        } else {
            feedback.add("✗ Missing uppercase letters")
        }

        if (hasDigits) {
            score += 1
            feedback.add("✓ Contains numbers")
        } else {
            feedback.add("✗ Missing numbers")
        }

        if (hasSpecial) {
            score += 1
            feedback.add("✓ Contains special characters")
        } else {
            feedback.add("✗ Missing special characters")
        }

        // Common password check
        if (commonPasswords.contains(password.lowercase())) {
            score -= 2
            feedback.add("✗ This is a very common password")
        }

        // Entropy calculation
        val entropy = calculateEntropy(password)

        // Determine strength
        val strength = when {
            score <= 1 -> Strength.VERY_WEAK
            score <= 3 -> Strength.WEAK
            score <= 5 -> Strength.MODERATE
            score <= 7 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }

        PasswordStrength(
            score = score.coerceIn(0, 10),
            strength = strength,
            feedback = feedback,
            entropy = entropy
        )
    }

    /**
     * Generates a secure random password.
     * 
     * @param length Password length (default: 16)
     * @param includeSpecial Include special characters (default: true)
     * @return Generated password
     */
    suspend fun generatePassword(
        length: Int = 16,
        includeSpecial: Boolean = true
    ): String = withContext(Dispatchers.Default) {
        val lowercase = "abcdefghijklmnopqrstuvwxyz"
        val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digits = "0123456789"
        val special = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val chars = buildString {
            append(lowercase)
            append(uppercase)
            append(digits)
            if (includeSpecial) {
                append(special)
            }
        }

        val random = SecureRandom()
        val password = CharArray(length)
        
        // Ensure at least one character from each category
        password[0] = lowercase[random.nextInt(lowercase.length)]
        password[1] = uppercase[random.nextInt(uppercase.length)]
        password[2] = digits[random.nextInt(digits.length)]
        if (includeSpecial) {
            password[3] = special[random.nextInt(special.length)]
        }

        // Fill the rest randomly
        val startIndex = if (includeSpecial) 4 else 3
        for (i in startIndex until length) {
            password[i] = chars[random.nextInt(chars.length)]
        }

        // Shuffle the password
        for (i in password.indices) {
            val j = random.nextInt(password.size)
            val temp = password[i]
            password[i] = password[j]
            password[j] = temp
        }

        String(password)
    }

    /**
     * Calculates password entropy (bits).
     */
    private fun calculateEntropy(password: String): Double {
        var charsetSize = 0
        if (password.any { it.isLowerCase() }) charsetSize += 26
        if (password.any { it.isUpperCase() }) charsetSize += 26
        if (password.any { it.isDigit() }) charsetSize += 10
        if (password.any { !it.isLetterOrDigit() }) charsetSize += 32 // Approximate

        if (charsetSize == 0) return 0.0

        return password.length * kotlin.math.log2(charsetSize.toDouble())
    }
}

/**
 * Password strength information.
 */
data class PasswordStrength(
    val score: Int,
    val strength: Strength,
    val feedback: List<String>,
    val entropy: Double
)

/**
 * Password strength levels.
 */
enum class Strength {
    VERY_WEAK,
    WEAK,
    MODERATE,
    STRONG,
    VERY_STRONG
}

