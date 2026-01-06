package com.ble1st.connectias.feature.securenotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.crypto.SecretKey

/**
 * Rust-based high-performance encryption/decryption.
 * 
 * This class provides a JNI bridge to the Rust encryption implementation,
 * which offers better performance and security than Kotlin implementation.
 * 
 * Note: Key management (Android Keystore) remains in Kotlin layer.
 */
class RustEncryption {
    
    companion object {
        init {
            try {
                System.loadLibrary("connectias_secure_notes")
                Timber.d("Rust encryption library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load Rust encryption library")
                throw RuntimeException("Rust encryption library not available", e)
            }
        }
    }

    /**
     * Native method to encrypt using Rust implementation.
     * 
     * @param plaintext Plaintext string to encrypt
     * @param key 32-byte AES-256 key
     * @return Base64-encoded encrypted data
     */
    private external fun nativeEncrypt(plaintext: String, key: ByteArray): String

    /**
     * Native method to decrypt using Rust implementation.
     * 
     * @param encryptedText Base64-encoded encrypted data
     * @param key 32-byte AES-256 key
     * @return Decrypted plaintext string
     */
    private external fun decrypt(encryptedText: String, key: ByteArray): String

    /**
     * Initialize Rust logging (Android-specific)
     */
    private external fun nativeInit()

    init {
        try {
            nativeInit()
        } catch (e: Exception) {
            Timber.w(e, "Failed to initialize Rust logging (non-critical)")
        }
    }

    /**
     * Encrypt content using AES-256-GCM.
     * 
     * @param plainText Plaintext to encrypt
     * @param secretKey Secret key from Android Keystore
     * @return Base64-encoded encrypted data
     */
    suspend fun encrypt(plainText: String, secretKey: SecretKey): String = withContext(Dispatchers.Default) {
        val rustStartTime = System.currentTimeMillis()
        
        try {
            Timber.d("🔴 [RustEncryption] Calling native Rust implementation for encryption")
            
            // Extract key bytes (32 bytes for AES-256)
            val keyBytes = secretKey.encoded
            if (keyBytes.size != 32) {
                throw IllegalArgumentException("Key must be 32 bytes for AES-256")
            }
            
            // Call native Rust implementation
            val result = nativeEncrypt(plainText, keyBytes)
            
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.i("🔴 [RustEncryption] Encryption completed in ${rustDuration}ms")
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustEncryption] Rust encryption failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }

    /**
     * Decrypt content using AES-256-GCM.
     * 
     * @param encryptedText Base64-encoded encrypted data
     * @param secretKey Secret key from Android Keystore
     * @return Decrypted plaintext
     */
    suspend fun decrypt(encryptedText: String, secretKey: SecretKey): String = withContext(Dispatchers.Default) {
        val rustStartTime = System.currentTimeMillis()
        
        try {
            Timber.d("🔴 [RustEncryption] Calling native Rust implementation for decryption")
            
            // Extract key bytes (32 bytes for AES-256)
            val keyBytes = secretKey.encoded
            if (keyBytes.size != 32) {
                throw IllegalArgumentException("Key must be 32 bytes for AES-256")
            }
            
            // Call native Rust implementation
            val result = decrypt(encryptedText, keyBytes)
            
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.i("🔴 [RustEncryption] Decryption completed in ${rustDuration}ms")
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustEncryption] Rust decryption failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
}

