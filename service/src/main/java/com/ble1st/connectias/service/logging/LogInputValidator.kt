// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import timber.log.Timber

/**
 * Input validator for log submissions to prevent abuse and injection attacks.
 *
 * Security measures:
 * - Size limits: Prevent memory exhaustion and database bloat
 * - Format validation: Ensure log levels are valid
 * - Sanitization: Prevent log injection via newlines/control characters
 * - Package name validation: Basic format check
 *
 * All validation errors are logged for audit purposes.
 */
internal object LogInputValidator {

    // Size limits (bytes)
    const val MAX_MESSAGE_SIZE = 4096 // 4KB per log message
    const val MAX_TAG_SIZE = 128
    const val MAX_EXCEPTION_TRACE_SIZE = 8192 // 8KB for stack traces
    const val MAX_PACKAGE_NAME_SIZE = 256

    // Valid Android log levels
    private val VALID_LEVELS = setOf(
        "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT",
        "V", "D", "I", "W", "E", "A" // Short forms
    )

    /**
     * Validated and sanitized log entry.
     */
    data class ValidatedLog(
        val packageName: String,
        val level: String,
        val tag: String,
        val message: String,
        val exceptionTrace: String?
    )

    /**
     * Validate and sanitize a log submission.
     *
     * @return ValidatedLog if successful, null if validation fails critically
     */
    fun validate(
        packageName: String,
        level: String,
        tag: String,
        message: String,
        exceptionTrace: String?
    ): ValidatedLog? {
        // Validate package name
        val validPackage = validatePackageName(packageName) ?: run {
            Timber.e("[LOG_VALIDATOR] Invalid package name (too long or empty)")
            return null
        }

        // Validate and normalize log level
        val validLevel = validateLevel(level)

        // Validate and truncate tag
        val validTag = validateTag(tag)

        // Validate and truncate message
        val validMessage = validateMessage(message)

        // Validate and truncate exception trace
        val validException = validateExceptionTrace(exceptionTrace)

        return ValidatedLog(
            packageName = validPackage,
            level = validLevel,
            tag = validTag,
            message = validMessage,
            exceptionTrace = validException
        )
    }

    /**
     * Validate package name format and length.
     */
    private fun validatePackageName(packageName: String): String? {
        val trimmed = packageName.trim()

        if (trimmed.isBlank()) {
            return null
        }

        if (trimmed.length > MAX_PACKAGE_NAME_SIZE) {
            Timber.w("[LOG_VALIDATOR] Package name too long: ${trimmed.length} bytes")
            return trimmed.take(MAX_PACKAGE_NAME_SIZE)
        }

        // Basic format check: should contain at least one dot
        if (!trimmed.contains('.')) {
            Timber.w("[LOG_VALIDATOR] Suspicious package name format: $trimmed")
            // Allow it, but log for audit
        }

        return trimmed
    }

    /**
     * Validate log level and normalize to uppercase standard form.
     */
    private fun validateLevel(level: String): String {
        val normalized = level.uppercase().trim()

        if (normalized !in VALID_LEVELS) {
            Timber.w("[LOG_VALIDATOR] Invalid log level '$level', defaulting to INFO")
            return "INFO"
        }

        // Normalize short forms to full names
        return when (normalized) {
            "V" -> "VERBOSE"
            "D" -> "DEBUG"
            "I" -> "INFO"
            "W" -> "WARN"
            "E" -> "ERROR"
            "A" -> "ASSERT"
            else -> normalized
        }
    }

    /**
     * Validate and sanitize tag.
     */
    private fun validateTag(tag: String): String {
        var sanitized = tag.trim()

        if (sanitized.isEmpty()) {
            sanitized = "ExternalApp"
        }

        if (sanitized.length > MAX_TAG_SIZE) {
            Timber.w("[LOG_VALIDATOR] Tag too long: ${sanitized.length} bytes, truncating")
            sanitized = sanitized.take(MAX_TAG_SIZE) + "..."
        }

        // Sanitize control characters and newlines to prevent log injection
        sanitized = sanitizeControlCharacters(sanitized)

        return sanitized
    }

    /**
     * Validate and sanitize message.
     */
    private fun validateMessage(message: String): String {
        var sanitized = message.trim()

        if (sanitized.isEmpty()) {
            sanitized = "(empty message)"
        }

        if (sanitized.length > MAX_MESSAGE_SIZE) {
            Timber.w("[LOG_VALIDATOR] Message too long: ${sanitized.length} bytes, truncating")
            sanitized = sanitized.take(MAX_MESSAGE_SIZE) + "... [TRUNCATED]"
        }

        // Keep newlines in messages but sanitize other control chars
        sanitized = sanitizeControlCharacters(sanitized, allowNewlines = true)

        return sanitized
    }

    /**
     * Validate and sanitize exception trace.
     */
    private fun validateExceptionTrace(exceptionTrace: String?): String? {
        if (exceptionTrace.isNullOrBlank()) {
            return null
        }

        var sanitized = exceptionTrace.trim()

        if (sanitized.length > MAX_EXCEPTION_TRACE_SIZE) {
            Timber.w("[LOG_VALIDATOR] Exception trace too long: ${sanitized.length} bytes, truncating")
            sanitized = sanitized.take(MAX_EXCEPTION_TRACE_SIZE) + "\n... [TRUNCATED]"
        }

        // Keep newlines in stack traces
        sanitized = sanitizeControlCharacters(sanitized, allowNewlines = true)

        return sanitized
    }

    /**
     * Sanitize control characters to prevent log injection attacks.
     *
     * Replaces:
     * - Null bytes (0x00)
     * - Other control characters except newlines (if allowed)
     * - ANSI escape sequences
     *
     * @param allowNewlines If true, keeps \n and \r
     */
    private fun sanitizeControlCharacters(input: String, allowNewlines: Boolean = false): String {
        val result = StringBuilder(input.length)

        for (char in input) {
            when {
                // Always block null bytes
                char == '\u0000' -> result.append("\\0")

                // Allow newlines if specified
                allowNewlines && (char == '\n' || char == '\r') -> result.append(char)

                // Replace other control characters (0x00-0x1F, except tab)
                char.code in 0..31 && char != '\t' -> result.append("\\x${char.code.toString(16).padStart(2, '0')}")

                // Block DEL character (0x7F)
                char == '\u007F' -> result.append("\\x7f")

                // Block ANSI escape sequences (ESC = 0x1B)
                char == '\u001B' -> result.append("\\x1b")

                // Keep everything else
                else -> result.append(char)
            }
        }

        return result.toString()
    }

    /**
     * Check if a string contains suspicious patterns (for audit logging).
     */
    fun hasSuspiciousPatterns(message: String): Boolean {
        // Check for SQL injection attempts
        val sqlKeywords = listOf(
            "DROP TABLE", "DELETE FROM", "UNION SELECT",
            "--", "/*", "*/",
            " OR ", "'OR'", "\"OR\"" // Common SQL injection patterns
        )
        if (sqlKeywords.any { message.contains(it, ignoreCase = true) }) {
            return true
        }

        // Check for excessive special characters (potential encoding attacks)
        // Count before any escape processing
        val specialChars = setOf('<', '>', '%', '$', '{', '}', '\\')
        val specialCharCount = message.count { it in specialChars }
        if (message.isNotEmpty() && specialCharCount > message.length / 4) {
            return true
        }

        // Check for null bytes
        if (message.contains('\u0000')) {
            return true
        }

        return false
    }
}
