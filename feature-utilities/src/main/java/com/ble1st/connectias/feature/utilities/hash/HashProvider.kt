package com.ble1st.connectias.feature.utilities.hash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for hash and checksum operations.
 * Supports MD5, SHA-1, SHA-256, and SHA-512 algorithms.
 */
@Singleton
class HashProvider @Inject constructor() {

    /**
     * Supported hash algorithms.
     */
    enum class HashAlgorithm(val algorithmName: String) {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA512("SHA-512")
    }

    /**
     * Calculates hash for the given text string.
     * 
     * @param text The text to hash
     * @param algorithm The hash algorithm to use
     * @return The hexadecimal hash string, or null if error occurred
     */
    suspend fun calculateTextHash(text: String, algorithm: HashAlgorithm): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance(algorithm.algorithmName)
            val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate text hash with algorithm ${algorithm.algorithmName}")
            null
        }
    }

    /**
     * Calculates hash for the given file.
     * 
     * @param filePath The path to the file
     * @param algorithm The hash algorithm to use
     * @return The hexadecimal hash string, or null if error occurred
     */
    suspend fun calculateFileHash(filePath: String, algorithm: HashAlgorithm): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                Timber.w("File does not exist or is not a file: $filePath")
                return@withContext null
            }

            val digest = MessageDigest.getInstance(algorithm.algorithmName)
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate file hash for $filePath with algorithm ${algorithm.algorithmName}")
            null
        }
    }

    /**
     * Verifies a hash against given text.
     * 
     * @param text The text to verify
     * @param expectedHash The expected hash value
     * @param algorithm The hash algorithm to use
     * @return True if hash matches, false otherwise
     */
    suspend fun verifyTextHash(text: String, expectedHash: String, algorithm: HashAlgorithm): Boolean {
        val calculatedHash = calculateTextHash(text, algorithm) ?: return false
        return calculatedHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Verifies a hash against given file.
     * 
     * @param filePath The path to the file
     * @param expectedHash The expected hash value
     * @param algorithm The hash algorithm to use
     * @return True if hash matches, false otherwise
     */
    suspend fun verifyFileHash(filePath: String, expectedHash: String, algorithm: HashAlgorithm): Boolean {
        val calculatedHash = calculateFileHash(filePath, algorithm) ?: return false
        return calculatedHash.equals(expectedHash, ignoreCase = true)
    }
}

