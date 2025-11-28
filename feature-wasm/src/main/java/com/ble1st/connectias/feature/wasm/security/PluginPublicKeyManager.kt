package com.ble1st.connectias.feature.wasm.security

import android.content.Context
import android.content.res.Resources
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.InputStream
import java.security.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages public keys for plugin signature verification.
 * 
 * Public keys can be loaded from:
 * 1. App resources (raw/plugin_public_key.pem)
 * 2. Secure storage (future enhancement)
 * 3. Remote server (future enhancement)
 */
@Singleton
class PluginPublicKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signatureVerifier: PluginSignatureVerifier
) {
    
    private val tag = "PluginPublicKeyManager"
    private var cachedPublicKey: PublicKey? = null
    
    /**
     * Get the default public key for plugin verification.
     * 
     * @return Public key or null if not available
     */
    fun getDefaultPublicKey(): PublicKey? {
        if (cachedPublicKey != null) {
            return cachedPublicKey
        }
        
        return try {
            // Try to load from resources first
            val publicKey = loadPublicKeyFromResources()
            cachedPublicKey = publicKey
            publicKey
        } catch (e: Exception) {
            Timber.w(e, "Failed to load public key from resources")
            null
        }
    }
    
    /**
     * Load public key from app resources.
     * Expected location: res/raw/plugin_public_key.pem
     */
    private fun loadPublicKeyFromResources(): PublicKey? {
        return try {
            val resources: Resources = context.resources
            val resourceId = resources.getIdentifier(
                "plugin_public_key",
                "raw",
                context.packageName
            )
            
            if (resourceId == 0) {
                Timber.w("Public key resource not found: res/raw/plugin_public_key.pem")
                return null
            }
            
            val inputStream: InputStream = resources.openRawResource(resourceId)
            val pemString = inputStream.bufferedReader().use { it.readText() }
            
            signatureVerifier.loadPublicKeyFromPem(pemString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load public key from resources")
            null
        }
    }
    
    /**
     * Set a custom public key (e.g., from secure storage or remote).
     */
    fun setPublicKey(publicKey: PublicKey) {
        cachedPublicKey = publicKey
    }
    
    /**
     * Clear cached public key.
     */
    fun clearCache() {
        cachedPublicKey = null
    }
}

