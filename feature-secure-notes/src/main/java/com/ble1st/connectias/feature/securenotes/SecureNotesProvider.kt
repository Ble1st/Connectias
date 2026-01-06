package com.ble1st.connectias.feature.securenotes

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import com.ble1st.connectias.feature.securenotes.models.EncryptionSettings
import com.ble1st.connectias.feature.securenotes.models.ExportFormat
import com.ble1st.connectias.feature.securenotes.models.MatchLocation
import com.ble1st.connectias.feature.securenotes.models.NoteCategory
import com.ble1st.connectias.feature.securenotes.models.NoteSearchResult
import com.ble1st.connectias.feature.securenotes.models.SecureNote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.SecretKeyFactory
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for secure notes functionality.
 *
 * Features:
 * - AES-256-GCM encryption
 * - Biometric authentication
 * - Secure key storage (Android Keystore)
 * - Note management (CRUD)
 * - Search and categorization
 */
@Singleton
class SecureNotesProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    
    private val keyAlias = "secure_notes_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    private val rustEncryption = try {
        RustEncryption()
    } catch (e: Exception) {
        null // Fallback to Kotlin if Rust not available
    }

    private val _notes = MutableStateFlow<List<SecureNote>>(emptyList())
    val notes: StateFlow<List<SecureNote>> = _notes.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _settings = MutableStateFlow(EncryptionSettings())
    val settings: StateFlow<EncryptionSettings> = _settings.asStateFlow()

    init {
        ensureKeyExists()
    }

    /**
     * Ensures the encryption key exists.
     */
    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(keyAlias)) {
            generateKey()
            return
        }

        // If the existing key enforces per-operation auth (duration = 0), regenerate
        // with a grace window to avoid repeated failures.
        val requiresPerUseAuth = runCatching { keyRequiresPerUseAuth() }.getOrDefault(false)
        if (requiresPerUseAuth) {
            keyStore.deleteEntry(keyAlias)
            generateKey()
        }
    }

    private fun keyRequiresPerUseAuth(): Boolean {
        val secretKey = getSecretKey()
        val factory = SecretKeyFactory.getInstance(secretKey.algorithm, "AndroidKeyStore")
        val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java)
        val method = runCatching {
            KeyInfo::class.java.getMethod("getUserAuthenticationValidityDurationSeconds")
        }.getOrNull()

        val duration = method?.let { m ->
            (runCatching { m.invoke(keyInfo) as? Int }.getOrNull()) ?: -1
        } ?: -1

        return duration == 0
    }

    /**
     * Generates a new encryption key.
     */
    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Require device authentication; allow reuse for a limited time window after auth
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                300, // seconds validity after successful auth
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .build()

        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    /**
     * Gets the secret key from keystore.
     */
    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Encrypts content using AES-256-GCM.
     * Uses Rust implementation (primary) with fallback to Kotlin implementation.
     */
    suspend fun encrypt(plainText: String): String = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster and more secure)
        if (rustEncryption != null) {
            try {
                Timber.i("🔴 [SecureNotesProvider] Using RUST implementation for encryption")
                val rustStartTime = System.currentTimeMillis()
                
                val result = rustEncryption.encrypt(plainText, getSecretKey())
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [SecureNotesProvider] RUST encryption completed in ${rustDuration}ms")
                Timber.d("📊 [SecureNotesProvider] Total time (including overhead): ${totalDuration}ms")
                
                return@withContext result
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [SecureNotesProvider] RUST encryption failed after ${rustDuration}ms, falling back to Kotlin")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [SecureNotesProvider] Rust encryption not available, using Kotlin")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [SecureNotesProvider] Using KOTLIN implementation for encryption")
        val kotlinStartTime = System.currentTimeMillis()
        
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            val result = Base64.getEncoder().encodeToString(combined)
            
            val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
            val totalDuration = System.currentTimeMillis() - startTime
            
            Timber.i("✅ [SecureNotesProvider] KOTLIN encryption completed in ${kotlinDuration}ms")
            Timber.d("📊 [SecureNotesProvider] Total time (including overhead): ${totalDuration}ms")
            
            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw mapKeyStoreAuthError(e)
        }
    }

    /**
     * Decrypts content using AES-256-GCM.
     * Uses Rust implementation (primary) with fallback to Kotlin implementation.
     */
    suspend fun decrypt(encryptedText: String): String = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster and more secure)
        if (rustEncryption != null) {
            try {
                Timber.i("🔴 [SecureNotesProvider] Using RUST implementation for decryption")
                val rustStartTime = System.currentTimeMillis()
                
                val result = rustEncryption.decrypt(encryptedText, getSecretKey())
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [SecureNotesProvider] RUST decryption completed in ${rustDuration}ms")
                Timber.d("📊 [SecureNotesProvider] Total time (including overhead): ${totalDuration}ms")
                
                return@withContext result
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [SecureNotesProvider] RUST decryption failed after ${rustDuration}ms, falling back to Kotlin")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [SecureNotesProvider] Rust decryption not available, using Kotlin")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [SecureNotesProvider] Using KOTLIN implementation for decryption")
        val kotlinStartTime = System.currentTimeMillis()
        
        try {
            val combined = Base64.getDecoder().decode(encryptedText)

            val iv = combined.copyOfRange(0, 12) // GCM IV is 12 bytes
            val encryptedBytes = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val result = String(decryptedBytes, Charsets.UTF_8)
            
            val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
            val totalDuration = System.currentTimeMillis() - startTime
            
            Timber.i("✅ [SecureNotesProvider] KOTLIN decryption completed in ${kotlinDuration}ms")
            Timber.d("📊 [SecureNotesProvider] Total time (including overhead): ${totalDuration}ms")
            
            return@withContext result
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw mapKeyStoreAuthError(e)
        }
    }

    /**
     * Maps keystore auth errors to a clearer exception so the UI can request re-auth.
     */
    private fun mapKeyStoreAuthError(original: Exception): Exception {
        val message = original.message.orEmpty()
        val causeMessage = original.cause?.message.orEmpty()
        val isAuthIssue = message.contains("KEY_USER_NOT_AUTHENTICATED", ignoreCase = true) ||
            causeMessage.contains("KEY_USER_NOT_AUTHENTICATED", ignoreCase = true)

        return if (isAuthIssue) {
            IllegalStateException("Device authentication required. Please unlock again.", original)
        } else {
            original
        }
    }

    /**
     * Creates a new secure note.
     */
    suspend fun createNote(
        title: String,
        content: String,
        category: NoteCategory = NoteCategory.GENERAL,
        tags: List<String> = emptyList()
    ): SecureNote = withContext(Dispatchers.IO) {
        val encryptedContent = encrypt(content)
        val note = SecureNote(
            title = title,
            content = encryptedContent,
            category = category,
            tags = tags
        )
        _notes.update { it + note }
        note
    }

    /**
     * Updates an existing note.
     */
    suspend fun updateNote(
        id: String,
        title: String? = null,
        content: String? = null,
        category: NoteCategory? = null,
        tags: List<String>? = null,
        isPinned: Boolean? = null
    ): SecureNote? = withContext(Dispatchers.IO) {
        val existingNote = _notes.value.find { it.id == id } ?: return@withContext null

        val updatedNote = existingNote.copy(
            title = title ?: existingNote.title,
            content = content?.let { encrypt(it) } ?: existingNote.content,
            category = category ?: existingNote.category,
            tags = tags ?: existingNote.tags,
            isPinned = isPinned ?: existingNote.isPinned,
            modifiedAt = System.currentTimeMillis()
        )

        _notes.update { notes ->
            notes.map { if (it.id == id) updatedNote else it }
        }

        updatedNote
    }

    /**
     * Deletes a note (soft delete).
     */
    suspend fun deleteNote(id: String): Boolean = withContext(Dispatchers.IO) {
        val note = _notes.value.find { it.id == id } ?: return@withContext false

        val deletedNote = note.copy(deletedAt = System.currentTimeMillis())
        _notes.update { notes ->
            notes.map { if (it.id == id) deletedNote else it }
        }

        true
    }

    /**
     * Permanently deletes a note.
     */
    suspend fun permanentlyDeleteNote(id: String): Boolean = withContext(Dispatchers.IO) {
        val exists = _notes.value.any { it.id == id }
        if (exists) {
            _notes.update { it.filter { note -> note.id != id } }
        }
        exists
    }

    /**
     * Gets decrypted note content.
     */
    suspend fun getNoteContent(id: String): String? = withContext(Dispatchers.IO) {
        val note = _notes.value.find { it.id == id } ?: return@withContext null
        decrypt(note.content)
    }

    /**
     * Searches notes.
     */
    suspend fun searchNotes(query: String): List<NoteSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NoteSearchResult>()
        val lowerQuery = query.lowercase()

        for (note in _notes.value.filter { it.deletedAt == null }) {
            // Search in title
            if (note.title.lowercase().contains(lowerQuery)) {
                results.add(
                    NoteSearchResult(
                        note = note,
                        matchedIn = MatchLocation.TITLE,
                        snippet = note.title
                    )
                )
                continue
            }

            // Search in tags
            if (note.tags.any { it.lowercase().contains(lowerQuery) }) {
                results.add(
                    NoteSearchResult(
                        note = note,
                        matchedIn = MatchLocation.TAGS,
                        snippet = note.tags.joinToString(", ")
                    )
                )
                continue
            }

            // Search in content (requires decryption)
            try {
                val content = decrypt(note.content)
                if (content.lowercase().contains(lowerQuery)) {
                    val index = content.lowercase().indexOf(lowerQuery)
                    val start = maxOf(0, index - 20)
                    val end = minOf(content.length, index + query.length + 20)
                    val snippet = "..." + content.substring(start, end) + "..."

                    results.add(
                        NoteSearchResult(
                            note = note,
                            matchedIn = MatchLocation.CONTENT,
                            snippet = snippet
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Error searching in note ${note.id}")
            }
        }

        results
    }

    /**
     * Gets deleted notes (trash).
     */
    fun getDeletedNotes(): List<SecureNote> {
        return _notes.value.filter { it.deletedAt != null }
    }

    /**
     * Exports notes.
     */
    suspend fun exportNotes(
        noteIds: List<String>,
        format: ExportFormat
    ): String = withContext(Dispatchers.IO) {
        val notesToExport = _notes.value.filter { it.id in noteIds }

        if (format == ExportFormat.PLAIN_TEXT || format == ExportFormat.MARKDOWN) {
            // Require the vault to be unlocked before exporting decrypted content
            check(_isUnlocked.value) { "Vault must be unlocked to export decrypted notes." }
        }

        when (format) {
            ExportFormat.ENCRYPTED_JSON -> {
                json.encodeToString(notesToExport)
            }
            ExportFormat.PLAIN_TEXT -> {
                buildString {
                    for (note in notesToExport) {
                        appendLine("# ${note.title}")
                        appendLine()
                        appendLine(decrypt(note.content))
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                }
            }
            ExportFormat.MARKDOWN -> {
                buildString {
                    for (note in notesToExport) {
                        appendLine("# ${note.title}")
                        appendLine()
                        appendLine("**Category:** ${note.category.name}")
                        appendLine("**Tags:** ${note.tags.joinToString(", ")}")
                        appendLine()
                        appendLine(decrypt(note.content))
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                }
            }
            ExportFormat.PDF -> {
                // PDF export would require additional library
                "PDF export not implemented"
            }
        }
    }

    /**
     * Checks if biometric authentication is available.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Unlocks the secure notes vault.
     */
    fun unlock() {
        _isUnlocked.value = true
    }

    /**
     * Locks the secure notes vault.
     */
    fun lock() {
        _isUnlocked.value = false
    }

}
