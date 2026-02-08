// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.servicestate

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for enabled/disabled state of app services (Service Dashboard).
 * Persists state in EncryptedSharedPreferences; exposes Flow for reactive UI.
 */
@Singleton
class ServiceStateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = initEncryptedPrefs()

    private val _stateFlow = MutableStateFlow(buildCurrentState())
    val observeState: Flow<Map<String, Boolean>> = _stateFlow.asStateFlow()

    fun isEnabled(serviceId: String): Boolean {
        return try {
            prefs.getBoolean(key(serviceId), defaultEnabled(serviceId))
        } catch (e: Exception) {
            Timber.w(e, "[ServiceStateRepository] Failed to read state for $serviceId, using default")
            defaultEnabled(serviceId)
        }
    }

    fun setEnabled(serviceId: String, enabled: Boolean) {
        prefs.edit().putBoolean(key(serviceId), enabled).apply()
        _stateFlow.value = buildCurrentState()
    }

    private fun key(serviceId: String): String = "service_enabled_$serviceId"

    private fun defaultEnabled(serviceId: String): Boolean = true

    private fun buildCurrentState(): Map<String, Boolean> {
        return ServiceIds.ALL.associateWith { isEnabled(it) }
    }

    private fun initEncryptedPrefs(): SharedPreferences {
        // Migrate from legacy plaintext prefs if they exist
        migrateLegacyPrefs()
        return try {
            createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "[ServiceStateRepository] Keystore error, attempting recovery")
            recoverAndCreateEncryptedPrefs()
        } catch (e: IOException) {
            Timber.e(e, "[ServiceStateRepository] IO error reading prefs, attempting recovery")
            recoverAndCreateEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun recoverAndCreateEncryptedPrefs(): SharedPreferences {
        deleteCorruptedPrefs()
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            Timber.e(e, "[ServiceStateRepository] Recovery failed")
            throw IllegalStateException("Cannot initialize encrypted service state storage", e)
        }
    }

    private fun deleteCorruptedPrefs() {
        try {
            val prefsFile = context.getSharedPreferencesPath(PREFS_NAME)
            if (prefsFile.exists()) {
                prefsFile.delete()
                Timber.d("[ServiceStateRepository] Deleted corrupted prefs file")
            }
        } catch (e: Exception) {
            Timber.w(e, "[ServiceStateRepository] Failed to delete corrupted prefs")
        }
    }

    private fun migrateLegacyPrefs() {
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacyPrefs.all.isEmpty()) return
        try {
            val encryptedPrefs = createEncryptedPrefs()
            val editor = encryptedPrefs.edit()
            for ((k, v) in legacyPrefs.all) {
                if (v is Boolean) editor.putBoolean(k, v)
            }
            editor.apply()
            legacyPrefs.edit().clear().apply()
            val legacyFile = context.getSharedPreferencesPath(LEGACY_PREFS_NAME)
            if (legacyFile.exists()) legacyFile.delete()
            Timber.i("[ServiceStateRepository] Migrated ${legacyPrefs.all.size} entries from legacy plaintext prefs")
        } catch (e: Exception) {
            Timber.w(e, "[ServiceStateRepository] Migration from legacy prefs failed, will use defaults")
        }
    }

    // Helper: resolve shared_prefs file path (not available before API 24, but minSdk >= 33)
    private fun Context.getSharedPreferencesPath(name: String): java.io.File {
        return java.io.File(applicationInfo.dataDir, "shared_prefs/$name.xml")
    }

    companion object {
        private const val PREFS_NAME = "connectias_service_state_encrypted"
        private const val LEGACY_PREFS_NAME = "connectias_service_state"
    }
}
