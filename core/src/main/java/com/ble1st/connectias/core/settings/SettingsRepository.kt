package com.ble1st.connectias.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for application settings.
 * Uses EncryptedSharedPreferences for secure storage of settings.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "connectias_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getTheme(): String {
        return encryptedPrefs.getString("theme", "system") ?: "system"
    }

    /**
     * Sets the theme preference asynchronously.
     * EncryptedSharedPreferences is thread-safe, so no explicit synchronization is needed.
     * apply() schedules the write asynchronously and returns immediately.
     */
    fun setTheme(theme: String) {
        encryptedPrefs.edit().putString("theme", theme).apply()
    }
}

