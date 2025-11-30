package com.ble1st.connectias.feature.usb.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cryptographic provider for USB data using BouncyCastle (OpenSSL-compatible).
 */
@Singleton
class UsbCryptoProvider @Inject constructor() {
    
    init {
        // Add BouncyCastle provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
            Timber.d("BouncyCastle provider added")
        }
    }
    
    /**
     * Encrypts data using AES-256-GCM (OpenSSL-compatible).
     */
    fun encrypt(data: ByteArray, key: SecretKey): EncryptedData {
        try {
            Timber.d("Encrypting data: ${data.size} bytes")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleProvider.PROVIDER_NAME)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            val encrypted = cipher.doFinal(data)
            val iv = cipher.iv
            
            Timber.d("Encryption complete: ${encrypted.size} bytes")
            
            return EncryptedData(
                data = encrypted,
                iv = iv
            )
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw e
        }
    }
    
    /**
     * Decrypts data using AES-256-GCM (OpenSSL-compatible).
     */
    fun decrypt(encryptedData: EncryptedData, key: SecretKey): ByteArray {
        try {
            Timber.d("Decrypting data: ${encryptedData.data.size} bytes")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleProvider.PROVIDER_NAME)
            val spec = GCMParameterSpec(128, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            val decrypted = cipher.doFinal(encryptedData.data)
            Timber.d("Decryption complete: ${decrypted.size} bytes")
            
            return decrypted
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw e
        }
    }
    
    /**
     * Generates a new AES-256 key.
     */
    fun generateKey(): SecretKey {
        try {
            Timber.d("Generating AES-256 key")
            val keyGenerator = KeyGenerator.getInstance("AES", BouncyCastleProvider.PROVIDER_NAME)
            keyGenerator.init(256)
            val key = keyGenerator.generateKey()
            Timber.d("Key generated successfully")
            return key
        } catch (e: Exception) {
            Timber.e(e, "Key generation failed")
            throw e
        }
    }
    
    /**
     * Creates a SecretKey from raw bytes.
     */
    fun createKey(keyBytes: ByteArray): SecretKey {
        return SecretKeySpec(keyBytes, "AES")
    }
}

/**
 * Encrypted data with IV.
 */
data class EncryptedData(
    val data: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as EncryptedData
        
        if (!data.contentEquals(other.data)) return false
        if (!iv.contentEquals(other.iv)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
