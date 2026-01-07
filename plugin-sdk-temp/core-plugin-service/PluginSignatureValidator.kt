package com.ble1st.connectias.core.plugin

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.io.ByteArrayInputStream

class PluginSignatureValidator(
    private val context: Context,
    private val trustedPublicKeys: List<String> = emptyList()
) {
    
    private val trustedHashes = mutableSetOf<String>()
    
    fun addTrustedHash(hash: String) {
        trustedHashes.add(hash.lowercase())
    }
    
    fun validateSignature(pluginFile: File): Result<Boolean> {
        return try {
            if (trustedPublicKeys.isEmpty()) {
                Timber.w("No trusted public keys configured - signature validation skipped")
                return Result.success(true)
            }
            
            val packageInfo = getPackageInfo(pluginFile)
            val signatures = getSignatures(packageInfo)
            
            if (signatures.isEmpty()) {
                return Result.failure(SecurityException("Plugin has no signatures"))
            }
            
            for (signature in signatures) {
                val cert = getCertificate(signature)
                val publicKey = cert.publicKey
                
                if (isKeyTrusted(publicKey)) {
                    Timber.i("Plugin signature validated successfully: ${pluginFile.name}")
                    return Result.success(true)
                }
            }
            
            Result.failure(SecurityException("Plugin signature not trusted"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate plugin signature: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    fun validateHash(pluginFile: File, expectedHash: String? = null): Result<Boolean> {
        return try {
            val actualHash = calculateSHA256(pluginFile)
            
            if (expectedHash != null) {
                val isValid = actualHash.equals(expectedHash, ignoreCase = true)
                if (isValid) {
                    Timber.i("Plugin hash validated: ${pluginFile.name}")
                } else {
                    Timber.e("Plugin hash mismatch: expected=$expectedHash, actual=$actualHash")
                }
                return Result.success(isValid)
            }
            
            if (trustedHashes.isNotEmpty()) {
                val isValid = trustedHashes.contains(actualHash.lowercase())
                if (isValid) {
                    Timber.i("Plugin hash found in trusted list: ${pluginFile.name}")
                } else {
                    Timber.e("Plugin hash not in trusted list: $actualHash")
                }
                return Result.success(isValid)
            }
            
            Timber.d("Plugin hash calculated: $actualHash (no validation performed)")
            Result.success(true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate plugin hash: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    fun getPluginHash(pluginFile: File): Result<String> {
        return try {
            val hash = calculateSHA256(pluginFile)
            Result.success(hash)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getPackageInfo(pluginFile: File): PackageInfo {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                pluginFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            ) ?: throw IllegalArgumentException("Failed to get package info")
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(
                pluginFile.absolutePath,
                PackageManager.GET_SIGNATURES
            ) ?: throw IllegalArgumentException("Failed to get package info")
        }
    }
    
    private fun getSignatures(packageInfo: PackageInfo): Array<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.let { signingInfo ->
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } ?: emptyArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures ?: emptyArray()
        }
    }
    
    private fun getCertificate(signature: Signature): Certificate {
        val certFactory = CertificateFactory.getInstance("X.509")
        val certStream = ByteArrayInputStream(signature.toByteArray())
        return certFactory.generateCertificate(certStream)
    }
    
    private fun isKeyTrusted(publicKey: PublicKey): Boolean {
        val keyString = publicKey.toString()
        return trustedPublicKeys.any { trustedKey ->
            keyString.contains(trustedKey) || publicKey.encoded.contentEquals(trustedKey.toByteArray())
        }
    }
    
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
