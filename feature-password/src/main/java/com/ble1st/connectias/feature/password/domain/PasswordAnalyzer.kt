package com.ble1st.connectias.feature.password.domain

import com.ble1st.connectias.feature.password.data.PasswordStrength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordAnalyzer @Inject constructor() {
    
    private val rustGenerator = try {
        RustPasswordGenerator()
    } catch (e: Exception) {
        null // Fallback to Kotlin if Rust not available
    }

    suspend fun analyzePasswordStrength(password: String): Pair<PasswordStrength, Int> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster)
        if (rustGenerator != null) {
            try {
                Timber.i("🔴 [PasswordAnalyzer] Using RUST implementation for password analysis")
                val rustStartTime = System.currentTimeMillis()
                
                val result = rustGenerator.analyzePasswordStrength(password)
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [PasswordAnalyzer] RUST password analysis completed in ${rustDuration}ms")
                Timber.d("📊 [PasswordAnalyzer] Total time (including overhead): ${totalDuration}ms")
                
                return@withContext result
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [PasswordAnalyzer] RUST password analysis failed after ${rustDuration}ms, falling back to Kotlin")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [PasswordAnalyzer] Rust generator not available, using Kotlin")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [PasswordAnalyzer] Using KOTLIN implementation for password analysis")
        val kotlinStartTime = System.currentTimeMillis()
        
        val result = analyzePasswordStrengthKotlin(password)
        
        val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
        val totalDuration = System.currentTimeMillis() - startTime
        
        Timber.i("✅ [PasswordAnalyzer] KOTLIN password analysis completed in ${kotlinDuration}ms")
        Timber.d("📊 [PasswordAnalyzer] Total time (including overhead): ${totalDuration}ms")
        
        return@withContext result
    }
    
    private fun analyzePasswordStrengthKotlin(password: String): Pair<PasswordStrength, Int> {
        return analyzePasswordStrengthSync(password)
    }
    
    /**
     * Synchronous version for non-suspend contexts (e.g., checkPassword)
     */
    fun analyzePasswordStrengthSync(password: String): Pair<PasswordStrength, Int> {
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
