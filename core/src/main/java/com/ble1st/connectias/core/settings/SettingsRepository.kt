package com.ble1st.connectias.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for application settings.
 * Uses plain SharedPreferences for non-sensitive settings (e.g., theme)
 * and EncryptedSharedPreferences for sensitive settings.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Plain SharedPreferences for non-sensitive settings like theme
    private val plainPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("connectias_settings", Context.MODE_PRIVATE)
    }
    
    // Encrypted SharedPreferences for sensitive settings
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encrypted = EncryptedSharedPreferences.create(
                context,
                "connectias_settings_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            // One-time migration: migrate theme from old encrypted prefs to plain prefs if needed
            performMigration(encrypted)
            
            encrypted
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to plain SharedPreferences")
            // Fallback to plain SharedPreferences if encryption fails
            plainPrefs
        } catch (e: IOException) {
            Timber.e(e, "IO error creating EncryptedSharedPreferences, falling back to plain SharedPreferences")
            // Fallback to plain SharedPreferences if IO error occurs
            plainPrefs
        }
    }
    
    /**
     * Performs one-time migration from old encrypted prefs to plain prefs.
     * Migrates theme from encrypted storage (old implementation) to plain storage (new implementation).
     * Only migrates if plain prefs don't have theme and encrypted prefs do.
     */
    private fun performMigration(encryptedPrefs: SharedPreferences) {
        try {
            // Check if plain prefs already have theme (migration already done or not needed)
            val plainTheme = plainPrefs.getString("theme", null)
            if (plainTheme == null) {
                // Check if old encrypted prefs contain theme value (from previous implementation)
                val encryptedTheme = encryptedPrefs.getString("theme", null)
                if (encryptedTheme != null) {
                    Timber.d("Migrating theme preference from encrypted to plain storage")
                    // Copy theme to plain prefs (new location)
                    plainPrefs.edit().putString("theme", encryptedTheme).apply()
                    // Remove theme from encrypted prefs (no longer needed there)
                    encryptedPrefs.edit().remove("theme").apply()
                    Timber.d("Theme preference migration completed")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during preference migration")
            // Continue execution even if migration fails
        }
    }

    /**
     * Gets the theme preference.
     * Uses plain SharedPreferences as theme is not sensitive data.
     */
    fun getTheme(): String {
        return plainPrefs.getString("theme", "system") ?: "system"
    }

    /**
     * Sets the theme preference asynchronously.
     * Uses plain SharedPreferences as theme is not sensitive data.
     * apply() schedules the write asynchronously and returns immediately.
     */
    fun setTheme(theme: String) {
        plainPrefs.edit().putString("theme", theme).apply()
    }
}

