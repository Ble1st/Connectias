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
 * 
 * **WARNING: Recovery Mechanism**
 * 
 * If encryption initialization fails (e.g., due to keystore corruption after OS updates,
 * backup restores, or device migrations), the recovery mechanism will **unconditionally delete**
 * the entire EncryptedSharedPreferences file. This causes **total loss of any encrypted settings**.
 * 
 * To be notified before data loss occurs, provide an [onRecoveryWillEraseData] callback
 * that can notify users or back up data before the deletion happens.
 * 
 * @param context Application context
 * @param onRecoveryWillEraseData Optional callback invoked immediately before deleting
 *        encrypted preferences during recovery. Use this to notify users or back up data.
 *        If null, recovery proceeds without notification.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onRecoveryWillEraseData: (() -> Unit)? = null
) {
    // Plain SharedPreferences for non-sensitive settings like theme
    private val plainPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("connectias_settings", Context.MODE_PRIVATE)
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val prefs = createEncryptedPrefs()
            performMigration(prefs)
            prefs
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "Failed to create EncryptedSharedPreferences")
            recoverAndCreateEncryptedPrefs(e)
        } catch (e: IOException) {
            Timber.e(e, "IO error creating EncryptedSharedPreferences")
            recoverAndCreateEncryptedPrefs(e)
        }
    }
    
    /**
     * Creates EncryptedSharedPreferences instance.
     * Extracted to a separate method to allow retry after recovery.
     * Note: Migration is performed separately to avoid triggering recovery
     * when migration fails (which is not a corruption issue).
     */
    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            "connectias_settings_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Performs recovery by deleting corrupted encrypted preferences and retrying creation.
     * Invokes [onRecoveryWillEraseData] callback before deletion if provided.
     * 
     * @param originalException The original exception that triggered recovery
     * @return The newly created EncryptedSharedPreferences instance
     * @throws IllegalStateException if recovery fails
     */
    private fun recoverAndCreateEncryptedPrefs(originalException: Exception): SharedPreferences {
        // Notify callback before deletion if provided
        onRecoveryWillEraseData?.invoke()
        
        // Attempt recovery by deleting corrupted prefs
        if (deleteCorruptedPrefs()) {
            Timber.d("Retrying EncryptedSharedPreferences creation after deleting corrupted prefs")
            try {
                val prefs = createEncryptedPrefs()
                performMigration(prefs)
                return prefs
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to create EncryptedSharedPreferences after recovery attempt")
                throw IllegalStateException("Cannot initialize encrypted storage after recovery", e2)
            }
        }
        throw IllegalStateException("Cannot initialize encrypted storage", originalException)
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
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            val prefsFile = File(prefsDir, "connectias_settings_encrypted.xml")
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
    
    /**
     * Audits which keys are currently stored in encrypted preferences.
     * This helps verify that no unexpected user data will be lost during recovery.
     * 
     * @return Set of all keys currently stored in encrypted preferences, or empty set if
     *         encrypted preferences cannot be accessed (e.g., not yet initialized or corrupted)
     */
    fun auditEncryptedPrefsKeys(): Set<String> {
        return try {
            val allEntries = encryptedPrefs.all
            val keys = allEntries.keys
            Timber.d("Encrypted preferences audit: Found ${keys.size} key(s): ${keys.joinToString()}")
            keys
        } catch (e: Exception) {
            Timber.w(e, "Cannot audit encrypted preferences (may not be initialized or corrupted)")
            emptySet()
        }
    }
}

