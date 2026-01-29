package com.ble1st.connectias.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure key generation and storage for database encryption.
 * Uses Android Keystore and EncryptedSharedPreferences to securely store
 * the database passphrase.
 * 
 * Note: EncryptedSharedPreferences and MasterKey are deprecated but still the recommended
 * approach until Google provides an official replacement. Suppressing deprecation warnings
 * until migration path is available.
 */
@Singleton
@Suppress("DEPRECATION")
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "connectias_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val PASSPHRASE_LENGTH = 32 // ~196 bits of entropy
        
        init {
            try {
                System.loadLibrary("connectias_root_detector")
                Timber.d("Rust KeyManager library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to load Rust KeyManager library, using Kotlin fallback")
            }
        }
    }
    
    /**
     * Native method to generate passphrase using Rust implementation.
     * Implementation is in Rust (libconnectias_root_detector.so), not C/C++.
     */
    @Suppress("JNI_MISSING_IMPLEMENTATION")
    private external fun nativeGeneratePassphrase(length: Int): String
    
    /**
     * Converts CharArray to ByteArray using CharsetEncoder to avoid creating intermediate String objects.
     * All buffers are explicitly zeroed after use to minimize memory exposure.
     * 
     * @param charArray The CharArray to convert
     * @return ByteArray representation in UTF-8 encoding
     */
    private fun charArrayToByteArray(charArray: CharArray): ByteArray {
        val encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
        val charBuffer = CharBuffer.wrap(charArray)
        // UTF-8 max 4 bytes per char, but typically 1 byte for ASCII
        val byteBuffer = ByteBuffer.allocate(charArray.size * 4)
        
        try {
            val encodeResult = encoder.encode(charBuffer, byteBuffer, true)
            if (encodeResult.isError) {
                encodeResult.throwException()
            }
            val flushResult = encoder.flush(byteBuffer)
            if (flushResult.isError) {
                flushResult.throwException()
            }
            byteBuffer.flip()
            
            val result = ByteArray(byteBuffer.remaining())
            byteBuffer.get(result)
            return result        } finally {
            // Explicitly zero ByteBuffer to minimize memory exposure
            if (byteBuffer.hasArray()) {
                val array = byteBuffer.array()
                java.util.Arrays.fill(array, 0.toByte())
            } else {
                // Direct buffer - clear and overwrite with zeros
                val capacity = byteBuffer.capacity()
                byteBuffer.clear()
                for (i in 0 until capacity) {
                    byteBuffer.put(i, 0.toByte())
                }
            }
            byteBuffer.clear()
            charBuffer.clear()
            // Note: CharBuffer.wrap() doesn't own the array, so the original charArray
            // must be zeroed separately by the caller
        }
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            if (e !is GeneralSecurityException && e !is IOException) {
                throw e
            }
            Timber.e(e, "Failed to create EncryptedSharedPreferences")
            // Attempt recovery by deleting corrupted prefs
            if (deleteCorruptedPrefs()) {
                Timber.d("Retrying EncryptedSharedPreferences creation after deleting corrupted prefs")
                try {
                    return@lazy createEncryptedPrefs()
                } catch (e2: Exception) {
                    Timber.e(e2, "Failed to create EncryptedSharedPreferences after recovery attempt")
                    throw IllegalStateException("Cannot initialize encrypted storage after recovery", e2)
                }
            }
            throw IllegalStateException("Cannot initialize encrypted storage", e)
        }
    }
    
    /**
     * Creates EncryptedSharedPreferences instance.
     * Extracted to a separate method to allow retry after recovery.
     */
    private fun createEncryptedPrefs(): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Attempts to delete corrupted encrypted preferences file.
     * This is used for recovery when keystore corruption is detected (e.g., after OS updates,
     * backup restores, or device migrations).
     * 
     * @return true if deletion was successful or file didn't exist, false otherwise
     */
    private fun deleteCorruptedPrefs(): Boolean {
        return try {
            // Use API 24+ method for reliable shared preferences deletion
            try {
                context.deleteSharedPreferences(PREFS_NAME)
                Timber.d("Deleted corrupted encrypted preferences file using deleteSharedPreferences()")
                true
            } catch (e: Exception) {
                Timber.e(e, "Error deleting shared preferences using deleteSharedPreferences()")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error attempting to delete corrupted encrypted preferences")
            false
        }
    }

    /**
     * Gets or generates a secure passphrase for database encryption.
     * The passphrase is stored in EncryptedSharedPreferences and is unique per installation.
     * 
     * Note: This method uses CharArray internally to minimize memory exposure window.
     * However, EncryptedSharedPreferences requires String, so transient String allocations
     * are unavoidable during storage operations.
     *
     * @return Byte array representing the passphrase for SQLCipher
     */
    fun getDatabasePassphrase(): ByteArray {
        try {
            val storedPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
            
            if (storedPassphrase != null) {
                // Use existing passphrase - convert to CharArray immediately
                Timber.d("Using existing database passphrase from secure storage")
                val passphraseChars = storedPassphrase.toCharArray()
                try {
                    return charArrayToByteArray(passphraseChars)
                } finally {
                    // Zero-fill CharArray to minimize memory exposure
                    passphraseChars.fill('\u0000')
                }
            } else {
                // Generate and store new passphrase
                Timber.d("Generating new database passphrase")
                val newPassphraseChars = generateSecurePassphrase()
                
                // Convert to String only for storage (EncryptedSharedPreferences limitation)
                val passphraseString = String(newPassphraseChars)
                
                // Synchronously commit to ensure consistency
                val committed = encryptedPrefs.edit()
                    .putString(KEY_DB_PASSPHRASE, passphraseString)
                    .commit()
                
                if (!committed) {
                    Timber.e("Failed to commit passphrase to EncryptedSharedPreferences")
                    // Try to read again in case another thread wrote it
                    val retryPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
                    if (retryPassphrase != null) {
                        val retryChars = retryPassphrase.toCharArray()
                        try {
                            return charArrayToByteArray(retryChars)
                        } finally {
                            retryChars.fill('\u0000')
                        }
                    }
                    // If still null, throw exception
                    throw IllegalStateException("Failed to store database passphrase")
                }
                
                // Convert to ByteArray using CharsetEncoder (avoids String intermediate)
                // Zero-fill CharArray after conversion
                try {
                    return charArrayToByteArray(newPassphraseChars)
                } finally {
                    // Zero-fill CharArray to minimize memory exposure
                    newPassphraseChars.fill('\u0000')
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving database passphrase")
            // Try to read any already-stored passphrase again
            try {
                val retryPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
                if (retryPassphrase != null) {
                    val retryChars = retryPassphrase.toCharArray()
                    try {
                        return charArrayToByteArray(retryChars)
                    } finally {
                        retryChars.fill('\u0000')
                    }
                }
            } catch (e2: Exception) {
                Timber.e(e2, "Error reading stored passphrase on retry")
            }
            
            // Last resort: generate new passphrase and commit synchronously
            val fallbackChars = generateSecurePassphrase()
            val fallbackString = String(fallbackChars)
            
            try {
                val committed = encryptedPrefs.edit()
                    .putString(KEY_DB_PASSPHRASE, fallbackString)
                    .commit()
                
                if (!committed) {
                    throw IllegalStateException("Failed to store fallback passphrase", e)
                }
                
                try {
                    return charArrayToByteArray(fallbackChars)
                } finally {
                    fallbackChars.fill('\u0000')
                }
            } catch (e3: Exception) {
                // If commit fails, we cannot proceed - throw exception
                fallbackChars.fill('\u0000')
                throw IllegalStateException("Failed to store database passphrase after retry", e3)
            }
        }
    }

    /**
     * Generates a cryptographically secure random passphrase.
     * Uses Rust implementation (primary) with fallback to Kotlin SecureRandom.
     * Returns CharArray to minimize memory exposure window.
     *
     * @return Secure random passphrase as CharArray
     */
    private fun generateSecurePassphrase(): CharArray {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster and more secure)
        try {
            Timber.i("🔴 [KeyManager] Using RUST implementation for passphrase generation")
            val rustStartTime = System.currentTimeMillis()
            
            val passphrase = nativeGeneratePassphrase(PASSPHRASE_LENGTH)
            
            val rustDuration = System.currentTimeMillis() - rustStartTime
            val totalDuration = System.currentTimeMillis() - startTime
            
            Timber.i("✅ [KeyManager] RUST passphrase generation completed in ${rustDuration}ms")
            Timber.d("📊 [KeyManager] Total time (including overhead): ${totalDuration}ms")
            
            return passphrase.toCharArray()
        } catch (e: UnsatisfiedLinkError) {
            val rustDuration = System.currentTimeMillis() - startTime
            Timber.w(e, "❌ [KeyManager] RUST passphrase generation failed (native library/link error) after ${rustDuration}ms, falling back to Kotlin")
            // Fall through to Kotlin implementation
        } catch (e: Throwable) {
            val rustDuration = System.currentTimeMillis() - startTime
            Timber.w(e, "❌ [KeyManager] RUST passphrase generation failed after ${rustDuration}ms, falling back to Kotlin")
            // Fall through to Kotlin implementation
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [KeyManager] Using KOTLIN implementation for passphrase generation")
        val kotlinStartTime = System.currentTimeMillis()
        
        val random = SecureRandom()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        val result = CharArray(PASSPHRASE_LENGTH) {
            chars[random.nextInt(chars.length)]
        }
        
        val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
        val totalDuration = System.currentTimeMillis() - startTime
        
        Timber.i("✅ [KeyManager] KOTLIN passphrase generation completed in ${kotlinDuration}ms")
        Timber.d("📊 [KeyManager] Total time (including overhead): ${totalDuration}ms")
        
        return result
    }
}

