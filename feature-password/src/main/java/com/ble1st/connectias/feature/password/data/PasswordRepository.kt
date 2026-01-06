package com.ble1st.connectias.feature.password.data

import com.ble1st.connectias.feature.password.domain.PasswordAnalyzer
import com.ble1st.connectias.feature.password.domain.RustPasswordGenerator
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log2

@Singleton
class PasswordRepository @Inject constructor(
    private val passwordDao: PasswordDao,
    private val passwordAnalyzer: PasswordAnalyzer
) {
    private val secureRandom = SecureRandom()
    
    private val rustGenerator = try {
        RustPasswordGenerator()
    } catch (e: Exception) {
        null // Fallback to Kotlin if Rust not available
    }
    
    val history: Flow<List<PasswordHistoryEntity>> = passwordDao.getAllHistory()

    suspend fun clearHistory() = passwordDao.clearAll()
    suspend fun deleteHistoryItem(item: PasswordHistoryEntity) = passwordDao.delete(item)

    fun checkPassword(password: String): PasswordCheckResult {
        // Calculate Entropy (Keep existing logic as it provides objective metric)
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
        
        // Use new Analyzer for Score and Classification
        // Note: Use synchronous version since checkPassword is not suspend
        val (strength, score) = passwordAnalyzer.analyzePasswordStrengthSync(password)

        val feedback = mutableListOf<String>()
        if (length < 12) feedback.add("Use at least 12 characters.")
        if (!hasUpper) feedback.add("Add uppercase letters.")
        if (!hasLower) feedback.add("Add lowercase letters.")
        if (!hasDigit) feedback.add("Add digits.")
        if (symbols == 0) feedback.add("Add symbols for higher security.")
        
        return PasswordCheckResult(
            length = length,
            entropy = entropy,
            score = score,
            strength = strength,
            feedback = feedback
        )
    }

    suspend fun generatePassword(config: PasswordGeneratorConfig): String {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster and more secure)
        if (rustGenerator != null) {
            try {
                Timber.i("🔴 [PasswordRepository] Using RUST implementation for password generation")
                val rustStartTime = System.currentTimeMillis()
                
                val password = rustGenerator.generatePassword(config)
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [PasswordRepository] RUST password generation completed in ${rustDuration}ms")
                Timber.d("📊 [PasswordRepository] Total time (including overhead): ${totalDuration}ms")
                
                val check = checkPassword(password)
                passwordDao.insert(
                    PasswordHistoryEntity(
                        password = password,
                        type = "CHARACTER",
                        strength = check.strength.name,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                return password
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [PasswordRepository] RUST password generation failed after ${rustDuration}ms, falling back to Kotlin")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [PasswordRepository] Rust generator not available, using Kotlin")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [PasswordRepository] Using KOTLIN implementation for password generation")
        val kotlinStartTime = System.currentTimeMillis()
        
        val length = config.length.coerceIn(8, 256)
        val pool = buildString {
            if (config.includeLowercase) append(LOWER)
            if (config.includeUppercase) append(UPPER)
            if (config.includeDigits) append(DIGITS)
            if (config.includeSymbols) append(SYMBOLS)
        }
        val finalPool = pool.ifEmpty { LOWER }
        
        val chars = CharArray(length) {
            finalPool[secureRandom.nextInt(finalPool.length)]
        }
        val password = String(chars)
        
        val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
        val totalDuration = System.currentTimeMillis() - startTime
        
        Timber.i("✅ [PasswordRepository] KOTLIN password generation completed in ${kotlinDuration}ms")
        Timber.d("📊 [PasswordRepository] Total time (including overhead): ${totalDuration}ms")
        
        val check = checkPassword(password)
        passwordDao.insert(
            PasswordHistoryEntity(
                password = password,
                type = "CHARACTER",
                strength = check.strength.name,
                timestamp = System.currentTimeMillis()
            )
        )
        
        return password
    }
    
    suspend fun generatePassphrase(wordCount: Int = 4, separator: String = "-"): String {
        val words = List(wordCount) {
            WORD_LIST[secureRandom.nextInt(WORD_LIST.size)]
        }
        val passphrase = words.joinToString(separator)
        
        val check = checkPassword(passphrase)
        // Passphrases usually have high entropy if length is high, but checkPassword might underestimate them if no symbols/digits.
        // Let's rely on checkPassword logic for consistency or override strength for long passphrases.
        
        passwordDao.insert(
            PasswordHistoryEntity(
                password = passphrase,
                type = "PASSPHRASE",
                strength = check.strength.name,
                timestamp = System.currentTimeMillis()
            )
        )
        
        return passphrase
    }

    companion object {
        private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private const val SYMBOLS = "!@#$%^&*()-_=+[{]}|;:'\",<.>/?`~"
        
        private val WORD_LIST = listOf(
            "apple", "bridge", "cloud", "dance", "eagle", "forest", "grape", "house", "island", "jungle",
            "kite", "lemon", "mountain", "night", "ocean", "piano", "queen", "river", "stone", "tiger",
            "umbrella", "violet", "water", "xylophone", "yellow", "zebra", "amber", "brave", "crisp", "dawn",
            "elite", "flame", "glow", "honor", "image", "jump", "knack", "light", "mirth", "noble",
            "orbit", "pride", "quest", "royal", "shine", "truth", "unity", "value", "wisdom", "youth",
            "alpha", "beta", "gamma", "delta", "echo", "foxtrot", "golf", "hotel", "india", "juliet",
            "kilo", "lima", "mike", "november", "oscar", "papa", "quebec", "romeo", "sierra", "tango",
            "uniform", "victor", "whiskey", "xray", "yankee", "zulu", "north", "south", "east", "west"
        )
    }
}

