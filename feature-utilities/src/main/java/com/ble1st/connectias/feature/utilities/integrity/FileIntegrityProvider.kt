package com.ble1st.connectias.feature.utilities.integrity

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for file integrity checking functionality.
 *
 * Features:
 * - Hash calculation (MD5, SHA-1, SHA-256, SHA-512)
 * - File comparison
 * - Integrity monitoring
 * - Baseline management
 */
@Singleton
class FileIntegrityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _baselines = MutableStateFlow<List<FileBaseline>>(emptyList())
    val baselines: StateFlow<List<FileBaseline>> = _baselines.asStateFlow()

    private val _verificationResults = MutableStateFlow<List<VerificationResult>>(emptyList())
    val verificationResults: StateFlow<List<VerificationResult>> = _verificationResults.asStateFlow()

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
     * Calculates hash of a file.
     */
    suspend fun calculateHash(
        file: File,
        algorithm: HashAlgorithm = HashAlgorithm.SHA256
    ): HashResult = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext HashResult.Error("File does not exist")
            }

            val startTime = System.currentTimeMillis()
            val digest = MessageDigest.getInstance(algorithm.algorithmName)
            val inputStream = FileInputStream(file)
            val hash = calculateHashFromStream(inputStream, digest)
            val duration = System.currentTimeMillis() - startTime

            HashResult.Success(
                hash = hash,
                algorithm = algorithm,
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                calculationTime = duration
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating hash for ${file.absolutePath}")
            HashResult.Error("Failed to calculate hash: ${e.message}", e)
        }
    }

    /**
     * Calculates hash from URI.
     */
    suspend fun calculateHashFromUri(
        uri: Uri,
        algorithm: HashAlgorithm = HashAlgorithm.SHA256
    ): HashResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext HashResult.Error("Could not open file")

            val startTime = System.currentTimeMillis()
            val digest = MessageDigest.getInstance(algorithm.algorithmName)
            val hash = calculateHashFromStream(inputStream, digest)
            val duration = System.currentTimeMillis() - startTime

            HashResult.Success(
                hash = hash,
                algorithm = algorithm,
                fileName = uri.lastPathSegment ?: "unknown",
                filePath = uri.toString(),
                fileSize = -1,
                calculationTime = duration
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating hash for URI")
            HashResult.Error("Failed to calculate hash: ${e.message}", e)
        }
    }

    /**
     * Calculates multiple hashes.
     */
    suspend fun calculateAllHashes(file: File): Map<HashAlgorithm, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<HashAlgorithm, String>()

        for (algorithm in HashAlgorithm.entries) {
            val result = calculateHash(file, algorithm)
            if (result is HashResult.Success) {
                results[algorithm] = result.hash
            }
        }

        results
    }

    /**
     * Calculates hash with progress.
     */
    fun calculateHashWithProgress(
        file: File,
        algorithm: HashAlgorithm = HashAlgorithm.SHA256
    ): Flow<HashProgress> = flow {
        if (!file.exists()) {
            emit(HashProgress.Error("File does not exist"))
            return@flow
        }

        emit(HashProgress.Started(file.name, file.length()))

        try {
            val digest = MessageDigest.getInstance(algorithm.algorithmName)
            val buffer = ByteArray(8192)
            val fileSize = file.length()
            var bytesRead = 0L

            FileInputStream(file).use { inputStream ->
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                    bytesRead += read
                    emit(HashProgress.Progress(bytesRead, fileSize))
                }
            }

            val hash = digest.digest().toHexString()
            emit(HashProgress.Completed(hash, algorithm))
        } catch (e: Exception) {
            Timber.e(e, "Error calculating hash")
            emit(HashProgress.Error("Failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Compares two files.
     */
    suspend fun compareFiles(
        file1: File,
        file2: File,
        algorithm: HashAlgorithm = HashAlgorithm.SHA256
    ): ComparisonResult = withContext(Dispatchers.IO) {
        val hash1Result = calculateHash(file1, algorithm)
        val hash2Result = calculateHash(file2, algorithm)

        if (hash1Result is HashResult.Error) {
            return@withContext ComparisonResult.Error("Error hashing first file: ${hash1Result.message}")
        }
        if (hash2Result is HashResult.Error) {
            return@withContext ComparisonResult.Error("Error hashing second file: ${hash2Result.message}")
        }

        val hash1 = (hash1Result as HashResult.Success).hash
        val hash2 = (hash2Result as HashResult.Success).hash

        ComparisonResult.Success(
            file1 = file1.absolutePath,
            file2 = file2.absolutePath,
            hash1 = hash1,
            hash2 = hash2,
            isMatch = hash1 == hash2,
            algorithm = algorithm
        )
    }

    /**
     * Verifies file against expected hash.
     */
    suspend fun verifyHash(
        file: File,
        expectedHash: String,
        algorithm: HashAlgorithm = HashAlgorithm.SHA256
    ): VerificationResult = withContext(Dispatchers.IO) {
        val result = calculateHash(file, algorithm)

        when (result) {
            is HashResult.Success -> {
                val isMatch = result.hash.equals(expectedHash, ignoreCase = true)
                VerificationResult(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    expectedHash = expectedHash,
                    actualHash = result.hash,
                    algorithm = algorithm,
                    isValid = isMatch,
                    verifiedAt = System.currentTimeMillis()
                )
            }
            is HashResult.Error -> {
                VerificationResult(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    expectedHash = expectedHash,
                    actualHash = null,
                    algorithm = algorithm,
                    isValid = false,
                    error = result.message,
                    verifiedAt = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Creates a baseline for monitoring.
     */
    suspend fun createBaseline(
        name: String,
        files: List<File>,
        algorithm: HashAlgorithm = HashAlgorithm.SHA256
    ): FileBaseline = withContext(Dispatchers.IO) {
        val entries = files.mapNotNull { file ->
            val result = calculateHash(file, algorithm)
            if (result is HashResult.Success) {
                BaselineEntry(
                    path = file.absolutePath,
                    hash = result.hash,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            } else null
        }

        val baseline = FileBaseline(
            name = name,
            algorithm = algorithm,
            entries = entries,
            createdAt = System.currentTimeMillis()
        )

        _baselines.update { it + baseline }
        baseline
    }

    /**
     * Verifies files against a baseline.
     */
    suspend fun verifyBaseline(baselineId: String): BaselineVerificationResult = 
        withContext(Dispatchers.IO) {
            val baseline = _baselines.value.find { it.id == baselineId }
                ?: return@withContext BaselineVerificationResult(
                    baselineId = baselineId,
                    verified = emptyList(),
                    modified = emptyList(),
                    missing = emptyList(),
                    added = emptyList()
                )

            val verified = mutableListOf<String>()
            val modified = mutableListOf<FileChange>()
            val missing = mutableListOf<String>()

            for (entry in baseline.entries) {
                val file = File(entry.path)
                if (!file.exists()) {
                    missing.add(entry.path)
                    continue
                }

                val result = calculateHash(file, baseline.algorithm)
                if (result is HashResult.Success) {
                    if (result.hash == entry.hash) {
                        verified.add(entry.path)
                    } else {
                        modified.add(
                            FileChange(
                                path = entry.path,
                                originalHash = entry.hash,
                                currentHash = result.hash,
                                originalSize = entry.size,
                                currentSize = file.length()
                            )
                        )
                    }
                }
            }

            BaselineVerificationResult(
                baselineId = baselineId,
                verified = verified,
                modified = modified,
                missing = missing,
                added = emptyList(),
                verifiedAt = System.currentTimeMillis()
            )
        }

    /**
     * Deletes a baseline.
     */
    fun deleteBaseline(baselineId: String) {
        _baselines.update { it.filter { b -> b.id != baselineId } }
    }

    private fun calculateHashFromStream(inputStream: InputStream, digest: MessageDigest): String {
        val buffer = ByteArray(8192)
        inputStream.use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

/**
 * Hash calculation result.
 */
sealed class HashResult {
    data class Success(
        val hash: String,
        val algorithm: FileIntegrityProvider.HashAlgorithm,
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val calculationTime: Long
    ) : HashResult()

    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : HashResult()
}

/**
 * Hash calculation progress.
 */
sealed class HashProgress {
    data class Started(val fileName: String, val fileSize: Long) : HashProgress()
    data class Progress(val bytesProcessed: Long, val totalBytes: Long) : HashProgress() {
        val percentage: Float get() = if (totalBytes > 0) bytesProcessed.toFloat() / totalBytes else 0f
    }
    data class Completed(val hash: String, val algorithm: FileIntegrityProvider.HashAlgorithm) : HashProgress()
    data class Error(val message: String) : HashProgress()
}

/**
 * File comparison result.
 */
sealed class ComparisonResult {
    data class Success(
        val file1: String,
        val file2: String,
        val hash1: String,
        val hash2: String,
        val isMatch: Boolean,
        val algorithm: FileIntegrityProvider.HashAlgorithm
    ) : ComparisonResult()

    data class Error(val message: String) : ComparisonResult()
}

/**
 * Hash verification result.
 */
@Serializable
data class VerificationResult(
    val fileName: String,
    val filePath: String,
    val expectedHash: String,
    val actualHash: String?,
    val algorithm: FileIntegrityProvider.HashAlgorithm,
    val isValid: Boolean,
    val error: String? = null,
    val verifiedAt: Long
)

/**
 * File baseline for integrity monitoring.
 */
@Serializable
data class FileBaseline(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val algorithm: FileIntegrityProvider.HashAlgorithm,
    val entries: List<BaselineEntry>,
    val createdAt: Long
)

/**
 * Baseline entry.
 */
@Serializable
data class BaselineEntry(
    val path: String,
    val hash: String,
    val size: Long,
    val lastModified: Long
)

/**
 * Baseline verification result.
 */
@Serializable
data class BaselineVerificationResult(
    val baselineId: String,
    val verified: List<String>,
    val modified: List<FileChange>,
    val missing: List<String>,
    val added: List<String>,
    val verifiedAt: Long = System.currentTimeMillis()
) {
    val isValid: Boolean get() = modified.isEmpty() && missing.isEmpty() && added.isEmpty()
}

/**
 * File change detected.
 */
@Serializable
data class FileChange(
    val path: String,
    val originalHash: String,
    val currentHash: String,
    val originalSize: Long,
    val currentSize: Long
)
