package com.ble1st.connectias.core.plugin.declarative

/**
 * Very small template renderer for declarative UI/flow strings.
 *
 * Supports:
 * - {{key}} to insert state[key]
 */
object DeclarativeTemplate {

    // NOTE: '}' must be escaped in Java/Android regex (unescaped '}' is a syntax error).
    private val tokenRegex = Regex("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}")

    fun render(input: String, state: Map<String, Any?>): String {
        return input.replace(tokenRegex) { m ->
            val key = m.groupValues.getOrNull(1) ?: return@replace m.value
            val normalizedKey = key.removePrefix("state.")
            state[normalizedKey]?.toString() ?: ""
        }
    }
}

