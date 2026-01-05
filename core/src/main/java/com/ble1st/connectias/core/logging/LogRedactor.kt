package com.ble1st.connectias.core.logging

import java.util.regex.Pattern

/**
 * Utility class to redact sensitive information from logs.
 */
object LogRedactor {

    private val SENSITIVE_KEYS = listOf(
        "password", "passwd", "secret", "token", "api_key", "apikey", "access_token", "auth", "credential", "pin"
    )

    // Regex to find JSON/Key-Value pairs like "password": "value" or password=value
    // Captures the value part to be replaced.
    // Uses Java-compatible regex syntax (named groups: (?<name>...) and backreference: \k<name>)
    private val KEY_VALUE_PATTERN = Pattern.compile(
        """(${SENSITIVE_KEYS.joinToString("|") { Pattern.quote(it) }})\s*[:=]\s*(?<quote>["']?)([^"'\s,]+?)\k<quote>?""",
        Pattern.CASE_INSENSITIVE
    )

    private val EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._-]+@[a-z]+\\.[a-z]+"
    )

    // Simple IP v4 pattern
    private val IP_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    )

    // Credit Card (simple check for sequences of 13-19 digits, potentially separated)
    private val CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d[ -]*?){13,16}\\b"
    )

    fun redact(message: String): String {
        var redacted = message

        // 1. Redact Key-Value pairs (e.g. password="...")
        val kvMatcher = KEY_VALUE_PATTERN.matcher(redacted)
        val sb = StringBuffer()
        while (kvMatcher.find()) {
            // Group 1 is the key, Group 2 is the value. We replace the whole match but keep the key.
            // Replacement: key="[REDACTED]"
            val key = kvMatcher.group(1)
            kvMatcher.appendReplacement(sb, "$key=[REDACTED]")
        }
        kvMatcher.appendTail(sb)
        redacted = sb.toString()

        // 2. Redact Emails
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[EMAIL_REDACTED]")

        // 3. Redact IPs (be careful not to redact version numbers like 1.2.3, so strict IP check might be needed contextually, 
        // but for safety we redact distinct IP-like patterns)
        // Note: Disabling IP redaction by default as it might be too aggressive for network tools app,
        // unless strictly required. Let's stick to PII/Secrets for now.
        // redacted = IP_PATTERN.matcher(redacted).replaceAll("[IP_REDACTED]")

        // 4. Redact potential Credit Cards (if any)
        // redacted = CREDIT_CARD_PATTERN.matcher(redacted).replaceAll("[CC_REDACTED]")

        return redacted
    }
}
