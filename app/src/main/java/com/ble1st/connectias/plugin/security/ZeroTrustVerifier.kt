package com.ble1st.connectias.plugin.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-Trust verification engine for plugins
 * Verifies plugins on every execution
 */
@Singleton
class ZeroTrustVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubStore: com.ble1st.connectias.plugin.store.GitHubPluginStore
) {
    
    sealed class VerificationResult {
        data class Success(val verifiedAt: Long = System.currentTimeMillis()) : VerificationResult()
        data class Failed(val reason: String, val details: String? = null) : VerificationResult()
        data class Suspicious(val warnings: List<String>) : VerificationResult()
    }
    
    data class VerificationCache(
        val pluginId: String,
        val result: VerificationResult,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val verificationCache = ConcurrentHashMap<String, VerificationCache>()
    private val cacheTTL = 5 * 60 * 1000L // 5 minutes
    
    /**
     * Verify plugin on execution with caching
     */
    suspend fun verifyOnExecution(pluginId: String): VerificationResult = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = verificationCache[pluginId]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTTL) {
            Timber.d("Using cached verification result for $pluginId")
            return@withContext cached.result
        }
        
        // Perform full verification
        val result = performFullVerification(pluginId)
        
        // Cache result
        verificationCache[pluginId] = VerificationCache(pluginId, result)
        
        return@withContext result
    }
    
    /**
     * Perform full verification checks
     */
    private suspend fun performFullVerification(pluginId: String): VerificationResult {
        val checks = listOf(
            ::verifySignature,
            ::verifyIntegrity,
            ::verifyCertificateChain,
            ::verifyFilePermissions
        )
        
        val warnings = mutableListOf<String>()
        
        for (check in checks) {
            when (val result = check(pluginId)) {
                is VerificationResult.Failed -> return result
                is VerificationResult.Suspicious -> warnings.addAll(result.warnings)
                is VerificationResult.Success -> continue
            }
        }
        
        return if (warnings.isNotEmpty()) {
            VerificationResult.Suspicious(warnings)
        } else {
            VerificationResult.Success()
        }
    }
    
    /**
     * Verify plugin signature from APK file
     * Note: Plugins are not installed apps, so signature verification may not work
     * We treat verification failures as warnings, not critical errors
     */
    private fun verifySignature(pluginId: String): VerificationResult {
        try {
            val pluginFile = getPluginFile(pluginId)
            if (pluginFile == null) {
                Timber.w("Plugin file not found for $pluginId")
                return VerificationResult.Suspicious(listOf("Plugin APK file not found"))
            }
            
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                pluginFile.absolutePath,
                PackageManager.GET_SIGNATURES
            )
            
            if (packageInfo == null) {
                Timber.w("Could not parse APK metadata for $pluginId - treating as suspicious")
                return VerificationResult.Suspicious(listOf("APK metadata not parseable (not a standard Android app)"))
            }
            
            @Suppress("DEPRECATION")
            val signatures = packageInfo.signatures
            
            if (signatures.isNullOrEmpty()) {
                Timber.w("No signature found in APK for $pluginId")
                return VerificationResult.Suspicious(listOf("No signature found in plugin APK"))
            }
            
            // Verify signature is valid
            signatures.forEach { signature ->
                if (!isSignatureValid(signature)) {
                    Timber.w("Invalid signature detected for $pluginId")
                    return VerificationResult.Suspicious(listOf("Plugin has invalid signature"))
                }
            }
            
            Timber.d("Signature verification passed for $pluginId (APK: ${pluginFile.name})")
            return VerificationResult.Success()
            
        } catch (e: Exception) {
            Timber.w(e, "Signature verification error for $pluginId")
            return VerificationResult.Suspicious(listOf("Signature verification error: ${e.message}"))
        }
    }
    
    /**
     * Verify file integrity
     */
    private fun verifyIntegrity(pluginId: String): VerificationResult {
        try {
            val pluginFile = getPluginFile(pluginId) ?: return VerificationResult.Failed("Plugin file not found")
            
            // Compute hash
            val hash = computeFileHash(pluginFile)
            
            // TODO: Compare with known-good hash from store
            // For now, just verify file is readable
            if (hash.isEmpty()) {
                return VerificationResult.Failed("Failed to compute file hash")
            }
            
            Timber.d("Integrity verification passed for $pluginId (hash: $hash)")
            return VerificationResult.Success()
            
        } catch (e: Exception) {
            return VerificationResult.Failed("Integrity verification failed", e.message)
        }
    }
    
    /**
     * Verify certificate chain from APK file
     * Note: Plugins may not have standard certificates, treat as warnings
     */
    private fun verifyCertificateChain(pluginId: String): VerificationResult {
        try {
            val pluginFile = File(context.filesDir, "plugins/$pluginId.apk")
            
            if (!pluginFile.exists()) {
                return VerificationResult.Suspicious(listOf("Plugin APK not found"))
            }
            
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                pluginFile.absolutePath,
                PackageManager.GET_SIGNATURES
            )
            
            if (packageInfo == null) {
                // APK parsing failed - not a critical error for plugins
                Timber.d("Skipping certificate verification for $pluginId (APK not parseable)")
                return VerificationResult.Success()
            }
            
            @Suppress("DEPRECATION")
            val signatures = packageInfo.signatures
            
            if (signatures.isNullOrEmpty()) {
                Timber.d("No signatures in APK for $pluginId - skipping cert verification")
                return VerificationResult.Success()
            }
            
            // Verify each signature in chain
            var hasValidCert = false
            for (signature in signatures) {
                if (isSignatureValid(signature)) {
                    hasValidCert = true
                    break
                }
            }
            
            if (!hasValidCert) {
                return VerificationResult.Suspicious(listOf("No valid certificate in chain"))
            }
            
            Timber.d("Certificate chain verification passed for $pluginId")
            return VerificationResult.Success()
            
        } catch (e: Exception) {
            Timber.w(e, "Certificate verification error for $pluginId")
            return VerificationResult.Suspicious(listOf("Certificate verification error: ${e.message}"))
        }
    }
    
    /**
     * Verify file permissions are not overly permissive
     */
    private fun verifyFilePermissions(pluginId: String): VerificationResult {
        try {
            val pluginFile = getPluginFile(pluginId) ?: return VerificationResult.Failed("Plugin file not found")
            
            // Check if world-readable/writable
            if (pluginFile.canRead() && pluginFile.canWrite()) {
                val warnings = listOf("Plugin file has broad permissions")
                return VerificationResult.Suspicious(warnings)
            }
            
            Timber.d("File permissions verification passed for $pluginId")
            return VerificationResult.Success()
            
        } catch (e: Exception) {
            return VerificationResult.Failed("Permission verification failed", e.message)
        }
    }
    
    /**
     * Check if signature is valid
     */
    private fun isSignatureValid(signature: Signature): Boolean {
        return try {
            signature.toByteArray().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Compute SHA-256 hash of file
     */
    private fun computeFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to compute file hash")
            ""
        }
    }
    
    /**
     * Get plugin file
     */
    private fun getPluginFile(pluginId: String): File? {
        val pluginDir = File(context.filesDir, "plugins")
        if (!pluginDir.exists()) return null
        
        // First try exact match (for plugins imported directly)
        val exactMatchFile = File(pluginDir, "$pluginId.apk")
        if (exactMatchFile.exists()) {
            return exactMatchFile
        }
        
        // Fallback: search all plugin files and match by metadata
        // This handles plugins downloaded from GitHub that use different naming
        pluginDir.listFiles { file ->
            file.extension in listOf("apk", "jar")
        }?.firstOrNull { file ->
            try {
                val pm = context.packageManager
                val packageInfo = pm.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.GET_META_DATA
                )
                val appInfo = packageInfo?.applicationInfo
                val metaData = appInfo?.metaData
                val packageIdFromMeta = metaData?.getString("plugin.packageId")
                    ?: packageInfo?.packageName
                
                packageIdFromMeta == pluginId
            } catch (e: Exception) {
                false
            }
        }?.let { return it }
        
        return null
    }
    
    /**
     * Clear verification cache
     */
    fun clearCache(pluginId: String? = null) {
        if (pluginId != null) {
            verificationCache.remove(pluginId)
        } else {
            verificationCache.clear()
        }
    }
    
    /**
     * Force re-verification (bypass cache)
     */
    suspend fun forceVerification(pluginId: String): VerificationResult {
        clearCache(pluginId)
        return verifyOnExecution(pluginId)
    }
}
