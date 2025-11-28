package com.ble1st.connectias.feature.utilities.text

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for text manipulation operations.
 */
@Singleton
class TextProvider @Inject constructor() {

    /**
     * Text case conversion types.
     */
    enum class CaseType {
        UPPER,
        LOWER,
        TITLE,
        SENTENCE
    }

    /**
     * Converts text to the specified case.
     */
    suspend fun convertCase(text: String, caseType: CaseType): String = withContext(Dispatchers.Default) {
        when (caseType) {
            CaseType.UPPER -> text.uppercase()
            CaseType.LOWER -> text.lowercase()
            CaseType.TITLE -> text.split(" ").joinToString(" ") { 
                it.lowercase().replaceFirstChar { char -> char.uppercaseChar() }
            }
            CaseType.SENTENCE -> text.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
    }

    /**
     * Counts words and characters in text.
     */
    suspend fun countWordsAndChars(text: String): WordCharCount = withContext(Dispatchers.Default) {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        WordCharCount(
            wordCount = words.size,
            charCount = text.length,
            charCountNoSpaces = text.replace(" ", "").length,
            lineCount = text.lines().size
        )
    }

    /**
     * Tests regex pattern against text.
     */
    suspend fun testRegex(text: String, pattern: String): RegexTestResult = withContext(Dispatchers.Default) {
        try {
            val regex = Regex(pattern)
            val matches = regex.findAll(text).map { it.value }.toList()
            val groups = regex.findAll(text).flatMap { it.groupValues.drop(1) }.toList()
            
            RegexTestResult(
                matches = matches,
                matchCount = matches.size,
                groups = groups,
                isValid = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Invalid regex pattern: $pattern")
            RegexTestResult(
                matches = emptyList(),
                matchCount = 0,
                groups = emptyList(),
                isValid = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Formats JSON text with proper indentation.
     */
    suspend fun formatJson(jsonText: String): String? = withContext(Dispatchers.Default) {
        try {
            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }
            val parsed = json.parseToJsonElement(jsonText)
            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)
        } catch (e: Exception) {
            Timber.e(e, "Failed to format JSON")
            null
        }
    }

    /**
     * Validates JSON text.
     */
    suspend fun validateJson(jsonText: String): Boolean = withContext(Dispatchers.Default) {
        try {
            kotlinx.serialization.json.Json.parseToJsonElement(jsonText)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Result for word and character counting.
 */
data class WordCharCount(
    val wordCount: Int,
    val charCount: Int,
    val charCountNoSpaces: Int,
    val lineCount: Int
)

/**
 * Result for regex testing.
 */
data class RegexTestResult(
    val matches: List<String>,
    val matchCount: Int,
    val groups: List<String>,
    val isValid: Boolean,
    val errorMessage: String? = null
)

