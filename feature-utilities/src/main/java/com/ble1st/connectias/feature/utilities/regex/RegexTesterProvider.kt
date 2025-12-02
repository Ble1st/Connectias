package com.ble1st.connectias.feature.utilities.regex

import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for regex testing functionality.
 */
@Singleton
class RegexTesterProvider @Inject constructor() {

    /**
     * Tests a regex pattern against input text.
     */
    fun testPattern(
        pattern: String,
        input: String,
        flags: Set<RegexOption> = emptySet()
    ): RegexResult {
        return try {
            val regex = Regex(pattern, flags)
            val matches = regex.findAll(input).toList()

            RegexResult(
                isValid = true,
                matches = matches.map { match ->
                    MatchInfo(
                        value = match.value,
                        range = match.range,
                        groups = match.groupValues.drop(1)
                    )
                },
                matchCount = matches.size,
                fullMatch = regex.matches(input)
            )
        } catch (e: Exception) {
            Timber.e(e, "Invalid regex pattern: $pattern")
            RegexResult(
                isValid = false,
                error = e.message
            )
        }
    }

    /**
     * Finds all matches in the input.
     */
    fun findAllMatches(pattern: String, input: String): List<MatchInfo> {
        return try {
            val regex = Regex(pattern)
            regex.findAll(input).map { match ->
                MatchInfo(
                    value = match.value,
                    range = match.range,
                    groups = match.groupValues.drop(1)
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets group matches.
     */
    fun getGroupMatches(pattern: String, input: String): List<GroupMatch> {
        return try {
            val regex = Regex(pattern)
            regex.findAll(input).flatMap { match ->
                match.groups.mapIndexedNotNull { index, group ->
                    group?.let {
                        GroupMatch(
                            groupIndex = index,
                            name = null,
                            value = it.value,
                            range = it.range
                        )
                    }
                }
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Replaces matches with replacement string.
     */
    fun replace(pattern: String, input: String, replacement: String): String {
        return try {
            Regex(pattern).replace(input, replacement)
        } catch (e: Exception) {
            input
        }
    }

    /**
     * Explains a regex pattern (basic explanation).
     */
    fun explainPattern(pattern: String): PatternExplanation {
        val parts = mutableListOf<PatternPart>()

        // Simple pattern analysis
        val tokens = listOf(
            "^" to "Start of string",
            "$" to "End of string",
            "\\d" to "Any digit (0-9)",
            "\\D" to "Any non-digit",
            "\\w" to "Any word character (a-z, A-Z, 0-9, _)",
            "\\W" to "Any non-word character",
            "\\s" to "Any whitespace",
            "\\S" to "Any non-whitespace",
            "." to "Any character except newline",
            "*" to "Zero or more of previous",
            "+" to "One or more of previous",
            "?" to "Zero or one of previous",
            "\\b" to "Word boundary",
            "\\B" to "Non-word boundary"
        )

        var remaining = pattern
        while (remaining.isNotEmpty()) {
            var matched = false
            for ((token, description) in tokens) {
                if (remaining.startsWith(token)) {
                    parts.add(PatternPart(token, description))
                    remaining = remaining.removePrefix(token)
                    matched = true
                    break
                }
            }
            if (!matched) {
                val char = remaining.first().toString()
                parts.add(PatternPart(char, "Literal character '$char'"))
                remaining = remaining.drop(1)
            }
        }

        return PatternExplanation(
            pattern = pattern,
            parts = parts,
            isValid = try {
                Regex(pattern)
                true
            } catch (e: Exception) {
                false
            }
        )
    }

    /**
     * Gets common regex patterns.
     */
    fun getCommonPatterns(): List<CommonPattern> = listOf(
        CommonPattern("Email", """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""),
        CommonPattern("Phone (US)", """(\+1)?[-.\s]?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}"""),
        CommonPattern("URL", """https?://[^\s<>"{}|\\^`\[\]]+"""),
        CommonPattern("IPv4 Address", """\b(?:\d{1,3}\.){3}\d{1,3}\b"""),
        CommonPattern("IPv6 Address", """([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"""),
        CommonPattern("MAC Address", """([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"""),
        CommonPattern("Date (YYYY-MM-DD)", """\d{4}-\d{2}-\d{2}"""),
        CommonPattern("Time (HH:MM:SS)", """\d{2}:\d{2}:\d{2}"""),
        CommonPattern("Hex Color", """#[0-9A-Fa-f]{6}"""),
        CommonPattern("UUID", """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""),
        CommonPattern("Credit Card", """\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}"""),
        CommonPattern("ZIP Code (US)", """\d{5}(-\d{4})?"""),
        CommonPattern("HTML Tag", """<([a-z]+)([^<]+)*(?:>(.*)<\/\1>|\s+\/>)"""),
        CommonPattern("Username", """^[a-zA-Z0-9_]{3,16}$"""),
        CommonPattern("Password (Strong)", """^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$""")
    )
}

/**
 * Result of regex testing.
 */
@Serializable
data class RegexResult(
    val isValid: Boolean,
    val matches: List<MatchInfo> = emptyList(),
    val matchCount: Int = 0,
    val fullMatch: Boolean = false,
    val error: String? = null
)

/**
 * Information about a regex match.
 */
@Serializable
data class MatchInfo(
    val value: String,
    val range: IntRange,
    val groups: List<String> = emptyList()
)

/**
 * Information about a group match.
 */
@Serializable
data class GroupMatch(
    val groupIndex: Int,
    val name: String?,
    val value: String,
    val range: IntRange
)

/**
 * Explanation of a regex pattern.
 */
@Serializable
data class PatternExplanation(
    val pattern: String,
    val parts: List<PatternPart>,
    val isValid: Boolean
)

/**
 * Part of a regex pattern.
 */
@Serializable
data class PatternPart(
    val token: String,
    val description: String
)

/**
 * Common regex pattern.
 */
data class CommonPattern(
    val name: String,
    val pattern: String
)
