package com.ble1st.connectias.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
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
    
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "Failed to create EncryptedSharedPreferences")
            // Attempt recovery by deleting corrupted prefs
            if (deleteCorruptedPrefs()) {
                Timber.d("Retrying EncryptedSharedPreferences creation after deleting corrupted prefs")
                try {
                    return@lazy createEncryptedPrefs()
                } catch (e2: Exception) {
                    Timber.e(e2, "Failed to create EncryptedSharedPreferences after recovery attempt")
                    throw IllegalStateException("Cannot initialize encrypted storage after recovery", e2)
                }
            }
            throw IllegalStateException("Cannot initialize encrypted storage", e)
        } catch (e: IOException) {
            Timber.e(e, "IO error creating EncryptedSharedPreferences")
            // Attempt recovery by deleting corrupted prefs
            if (deleteCorruptedPrefs()) {
                Timber.d("Retrying EncryptedSharedPreferences creation after deleting corrupted prefs")
                try {
                    return@lazy createEncryptedPrefs()
                } catch (e2: Exception) {
                    Timber.e(e2, "Failed to create EncryptedSharedPreferences after recovery attempt")
                    throw IllegalStateException("Cannot initialize encrypted storage after recovery", e2)
                }
            }
            throw IllegalStateException("Cannot initialize encrypted storage", e)
        }
    }
    
    /**
     * Creates EncryptedSharedPreferences instance.
     * Extracted to a separate method to allow retry after recovery.
     */
    private fun createEncryptedPrefs(): SharedPreferences {
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
        
        performMigration(encrypted)
        
        return encrypted
    }
    
    /**
     * Attempts to delete corrupted encrypted preferences file.
     * This is used for recovery when keystore corruption is detected (e.g., after OS updates,
     * backup restores, or device migrations).
     * 
     * @return true if deletion was successful or file didn't exist, false otherwise
     */
    private fun deleteCorruptedPrefs(): Boolean {
        return try {
            val prefsFile = File(context.filesDir.parent, "shared_prefs/connectias_settings_encrypted.xml")
            if (prefsFile.exists()) {
                val deleted = prefsFile.delete()
                if (deleted) {
                    Timber.d("Deleted corrupted encrypted preferences file")
                } else {
                    Timber.w("Failed to delete corrupted encrypted preferences file")
                }
                deleted
            } else {
                Timber.d("Encrypted preferences file does not exist, nothing to delete")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Error attempting to delete corrupted encrypted preferences")
            false
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

