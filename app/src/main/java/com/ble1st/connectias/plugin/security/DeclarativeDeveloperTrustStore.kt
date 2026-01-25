package com.ble1st.connectias.plugin.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import timber.log.Timber

/**
 * Stores trusted developer public keys for declarative plugins.
 *
 * NOTE: This is a host-side allowlist. Missing keys should be treated as "deny" for third-party packages.
 */
@Singleton
class DeclarativeDeveloperTrustStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("declarative_truststore_v1", Context.MODE_PRIVATE)
    private val keyName = "trusted_keys_json"

    data class DeveloperKey(
        val developerId: String,
        val publicKeyBase64: String
    )

    fun list(): List<DeveloperKey> {
        val json = prefs.getString(keyName, null) ?: return emptyList()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().mapNotNull { devId ->
                val keyB64 = obj.optString(devId, null) ?: return@mapNotNull null
                DeveloperKey(devId, keyB64)
            }.toList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to read declarative truststore")
            emptyList()
        }
    }

    fun getPublicKeyBase64(developerId: String): String? {
        val json = prefs.getString(keyName, null) ?: return null
        return try {
            JSONObject(json).optString(developerId, null)
        } catch (_: Exception) {
            null
        }
    }

    fun addOrReplace(developerId: String, publicKeyBase64: String): Boolean {
        if (developerId.isBlank() || publicKeyBase64.isBlank()) return false
        return try {
            val current = prefs.getString(keyName, null)?.let { JSONObject(it) } ?: JSONObject()
            current.put(developerId, publicKeyBase64)
            prefs.edit().putString(keyName, current.toString()).apply()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to update declarative truststore")
            false
        }
    }

    fun remove(developerId: String): Boolean {
        return try {
            val current = prefs.getString(keyName, null)?.let { JSONObject(it) } ?: return true
            current.remove(developerId)
            prefs.edit().putString(keyName, current.toString()).apply()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove developer key from truststore")
            false
        }
    }

    fun decodePublicKey(publicKeyBase64: String): PublicKey? {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("EC")
            kf.generatePublic(spec)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode developer public key")
            null
        }
    }
}

