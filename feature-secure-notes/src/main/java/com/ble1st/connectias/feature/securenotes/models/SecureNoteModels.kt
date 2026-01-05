package com.ble1st.connectias.feature.securenotes.models

import kotlinx.serialization.Serializable

/**
 * A secure note with encrypted content.
 */
@Serializable
data class SecureNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String, // Encrypted
    val category: NoteCategory = NoteCategory.GENERAL,
    val tags: List<String> = emptyList(),
    val color: NoteColor = NoteColor.DEFAULT,
    val isPinned: Boolean = false,
    val isLocked: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null // For soft delete
)

/**
 * Note categories.
 */
enum class NoteCategory {
    GENERAL,
    PERSONAL,
    WORK,
    FINANCIAL,
    MEDICAL,
    LEGAL,
    PASSWORDS,
    OTHER
}

/**
 * Note color options.
 */
enum class NoteColor(val hex: String) {
    DEFAULT("#FFFFFF"),
}

/**
 * Note encryption settings.
 */
@Serializable
data class EncryptionSettings(
    val algorithm: String = "AES/GCM/NoPadding",
    val keySize: Int = 256,
    val useBiometric: Boolean = true,
    val autoLockTimeout: Long = 5 * 60 * 1000, // 5 minutes
    val requireReauth: Boolean = true
)

/**
 * Note search result.
 */
@Serializable
data class NoteSearchResult(
    val note: SecureNote,
    val matchedIn: MatchLocation,
    val snippet: String
)

/**
 * Where the search matched.
 */
enum class MatchLocation {
    TITLE,
    CONTENT,
    TAGS
}

/**
 * Note export format.
 */
enum class ExportFormat {
    ENCRYPTED_JSON,
    PLAIN_TEXT,
    MARKDOWN,
    PDF
}

