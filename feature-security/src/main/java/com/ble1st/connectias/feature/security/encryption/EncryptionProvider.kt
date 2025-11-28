package com.ble1st.connectias.feature.security.encryption

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for encryption/decryption operations.
 * Uses AES-256-GCM for encryption.
 */
@Singleton
class EncryptionProvider @Inject constructor() {

    private val algorithm = "AES"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12 // 96 bits for GCM

    /**
     * Encrypts text using AES-256-GCM.
     * 
     * @param plaintext The text to encrypt
     * @param password The password for key derivation
     * @return EncryptionResult with encrypted data and IV
     */
    suspend fun encryptText(
        plaintext: String,
        password: String
    ): EncryptionResult = withContext(Dispatchers.IO) {
        try {
            // Generate IV
            val iv = ByteArray(ivLength)
            SecureRandom().nextBytes(iv)

            // Derive key from password (simplified - in production use PBKDF2)
            val key = deriveKey(password)

            // Initialize cipher
            val cipher = Cipher.getInstance(transformation)
            val parameterSpec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)

            // Encrypt
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Encode to Base64
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            EncryptionResult(
                encryptedData = encryptedBase64,
                iv = ivBase64,
                success = true,
                error = null
            )
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            EncryptionResult(
                encryptedData = "",
                iv = "",
                success = false,
                error = e.message ?: "Encryption failed"
            )
        }
    }

    /**
     * Decrypts text using AES-256-GCM.
     * 
     * @param encryptedData Base64 encoded encrypted data
     * @param iv Base64 encoded IV
     * @param password The password for key derivation
     * @return DecryptionResult with decrypted text
     */
    suspend fun decryptText(
        encryptedData: String,
        iv: String,
        password: String
    ): DecryptionResult = withContext(Dispatchers.IO) {
        try {
            // Decode from Base64
            val encryptedBytes = Base64.decode(encryptedData, Base64.NO_WRAP)
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)

            // Derive key from password
            val key = deriveKey(password)

            // Initialize cipher
            val cipher = Cipher.getInstance(transformation)
            val parameterSpec = GCMParameterSpec(gcmTagLength, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)

            // Decrypt
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val plaintext = String(decryptedBytes, Charsets.UTF_8)

            DecryptionResult(
                plaintext = plaintext,
                success = true,
                error = null
            )
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            DecryptionResult(
                plaintext = "",
                success = false,
                error = e.message ?: "Decryption failed"
            )
        }
    }

    /**
     * Generates a random encryption key.
     * 
     * @return Base64 encoded key
     */
    suspend fun generateKey(): String = withContext(Dispatchers.IO) {
        try {
            val keyGenerator = KeyGenerator.getInstance(algorithm)
            keyGenerator.init(256) // AES-256
            val secretKey = keyGenerator.generateKey()
            Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Key generation failed")
            ""
        }
    }

    /**
     * Derives a key from a password (simplified - use PBKDF2 in production).
     * This is a simplified version for demonstration purposes.
     */
    private fun deriveKey(password: String): SecretKey {
        // Simplified key derivation - in production, use PBKDF2 with salt
        val keyBytes = password.toByteArray(Charsets.UTF_8)
        val paddedKey = ByteArray(32) // 256 bits
        System.arraycopy(keyBytes, 0, paddedKey, 0, minOf(keyBytes.size, 32))
        return SecretKeySpec(paddedKey, algorithm)
    }
}

/**
 * Encryption result.
 */
data class EncryptionResult(
    val encryptedData: String,
    val iv: String,
    val success: Boolean,
    val error: String?
)

/**
 * Decryption result.
 */
data class DecryptionResult(
    val plaintext: String,
    val success: Boolean,
    val error: String?
)

