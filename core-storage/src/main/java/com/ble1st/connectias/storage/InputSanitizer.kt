package com.ble1st.connectias.storage

class InputSanitizer {
    companion object {
        const val MAX_KEY_LENGTH = 128
        const val MAX_VALUE_SIZE_BYTES = 1_048_576 // 1MB
        
        // Whitelist-Regex für Keys: Alphanumerisch + Underscore/Dash
        private val KEY_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
        
        // SQL-Keywords Blacklist
        private val SQL_KEYWORDS = setOf(
            "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
            "EXEC", "EXECUTE", "UNION", "SCRIPT", "JAVASCRIPT", "ONERROR"
        )
    }
    
    fun sanitizeKey(key: String): String {
        // 1. Längen-Check
        if (key.length > MAX_KEY_LENGTH) {
            throw IllegalArgumentException("Key too long: ${key.length} > $MAX_KEY_LENGTH")
        }
        
        // 2. Whitelist-Pattern-Check
        if (!KEY_PATTERN.matches(key)) {
            throw IllegalArgumentException("Key contains invalid characters: $key")
        }
        
        // 3. SQL-Keyword-Check
        if (SQL_KEYWORDS.any { key.uppercase().contains(it) }) {
            throw IllegalArgumentException("Key contains SQL keyword: $key")
        }
        
        // 4. Keine Whitespace
        if (key.contains(Regex("\\s"))) {
            throw IllegalArgumentException("Key contains whitespace: $key")
        }
        
        return key
    }
    
    fun sanitizeValue(value: String): String {
        // 1. Größen-Check
        val sizeBytes = value.toByteArray().size
        if (sizeBytes > MAX_VALUE_SIZE_BYTES) {
            throw IllegalArgumentException("Value too large: $sizeBytes > $MAX_VALUE_SIZE_BYTES bytes")
        }
        
        // 2. SQL-Escape (zusätzlich zu Prepared Statements)
        return value.replace("'", "''")
    }
    
    fun validateContent(json: String) {
        // Content-Scanning: Keine executable Binaries, Scripts
        if (json.contains("<script>", ignoreCase = true) ||
            json.contains("javascript:", ignoreCase = true) ||
            json.contains("onerror=", ignoreCase = true) ||
            json.startsWith("MZ") || // PE executable
            json.startsWith("\u007FELF") // ELF executable
        ) {
            throw SecurityException("Content contains forbidden patterns")
        }
    }
}
