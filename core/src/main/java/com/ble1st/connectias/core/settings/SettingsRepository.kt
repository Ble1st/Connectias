package com.ble1st.connectias.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Singleton
import androidx.core.content.edit

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
@Suppress("DEPRECATION")
class SettingsRepository(
    @ApplicationContext private val context: Context,
    private val onRecoveryWillEraseData: (() -> Unit)? = null
) : com.ble1st.connectias.common.ui.theme.ThemeSettingsProvider {
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
                    plainPrefs.edit { putString("theme", encryptedTheme) }
                    // Remove theme from encrypted prefs (no longer needed there)
                    encryptedPrefs.edit {remove("theme")}
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
    override fun getTheme(): String {
        return plainPrefs.getString("theme", "system") ?: "system"
    }

    /**
     * Sets the theme preference asynchronously.
     * Uses plain SharedPreferences as theme is not sensitive data.
     * apply() schedules the write asynchronously and returns immediately.
     */
    fun setTheme(theme: String) {
        plainPrefs.edit {putString("theme", theme)}
    }
    
    /**
     * Gets the theme style preference (Standard or Adeptus Mechanicus).
     * Uses plain SharedPreferences as theme style is not sensitive data.
     */
    override fun getThemeStyle(): String {
        return plainPrefs.getString("theme_style", "standard") ?: "standard"
    }

    /**
     * Sets the theme style preference asynchronously.
     * Uses plain SharedPreferences as theme style is not sensitive data.
     * apply() schedules the write asynchronously and returns immediately.
     */
    fun setThemeStyle(themeStyle: String) {
        plainPrefs.edit { putString("theme_style", themeStyle)}
    }
    
    /**
     * Observes theme style preference changes as a Flow.
     * Emits the current value immediately, then emits whenever the theme style changes.
     */
    override fun observeThemeStyle(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_style") {
                trySend(getThemeStyle())
            }
        }
        plainPrefs.registerOnSharedPreferenceChangeListener(listener)
        
        // Emit current value
        trySend(getThemeStyle())
        
        awaitClose {
            plainPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    /**
     * Observes theme preference changes as a Flow.
     * Emits the current value immediately, then emits whenever the theme changes.
     */
    override fun observeTheme(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme") {
                trySend(getTheme())
            }
        }
        plainPrefs.registerOnSharedPreferenceChangeListener(listener)
        // Emit current value immediately
        trySend(getTheme())
        awaitClose {
            plainPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    /**
     * Observes logging level preference changes as a Flow.
     * Emits the current value immediately, then emits whenever the logging level changes.
     */
    fun observeLoggingLevel(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "logging_level") {
                trySend(getLoggingLevel())
            }
        }
        encryptedPrefs.registerOnSharedPreferenceChangeListener(listener)
        // Emit current value immediately
        trySend(getLoggingLevel())
        awaitClose {
            encryptedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // ============================================================================
    // Plain SharedPreferences - Non-sensitive settings
    // ============================================================================

    /**
     * Gets the dynamic color preference (Material You).
     * Uses plain SharedPreferences as this is not sensitive data.
     */
    override fun getDynamicColor(): Boolean {
        return plainPrefs.getBoolean("dynamic_color", true)
    }

    /**
     * Sets the dynamic color preference.
     * Uses plain SharedPreferences as this is not sensitive data.
     */
    fun setDynamicColor(enabled: Boolean) {
        plainPrefs.edit { putBoolean("dynamic_color", enabled)}
    }
    
    /**
     * Observes dynamic color preference changes as a Flow.
     * Emits the current value immediately, then emits whenever the dynamic color setting changes.
     */
    override fun observeDynamicColor(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dynamic_color") {
                trySend(getDynamicColor())
            }
        }
        plainPrefs.registerOnSharedPreferenceChangeListener(listener)
        // Emit current value immediately
        trySend(getDynamicColor())
        awaitClose {
            plainPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // ============================================================================
    // EncryptedSharedPreferences - Sensitive settings
    // ============================================================================

    /**
     * Gets the auto-lock enabled preference.
     * Uses EncryptedSharedPreferences as this is a security setting.
     */
    fun getAutoLockEnabled(): Boolean {
        return try {
            encryptedPrefs.getBoolean("auto_lock_enabled", false)
        } catch (e: Exception) {
            Timber.w(e, "Failed to get auto_lock_enabled, returning default")
            false
        }
    }

    /**
     * Sets the auto-lock enabled preference.
     * Uses EncryptedSharedPreferences as this is a security setting.
     */
    fun setAutoLockEnabled(enabled: Boolean) {
        try {
            encryptedPrefs.edit { putBoolean("auto_lock_enabled", enabled)}
        } catch (e: Exception) {
            Timber.e(e, "Failed to set auto_lock_enabled")
        }
    }

    /**
     * Gets the RASP logging enabled preference.
     * Uses EncryptedSharedPreferences as this is a security setting.
     */
    fun getRaspLoggingEnabled(): Boolean {
        return try {
            encryptedPrefs.getBoolean("rasp_logging_enabled", true)
        } catch (e: Exception) {
            Timber.w(e, "Failed to get rasp_logging_enabled, returning default")
            true
        }
    }

    /**
     * Sets the RASP logging enabled preference.
     * Uses EncryptedSharedPreferences as this is a security setting.
     */
    fun setRaspLoggingEnabled(enabled: Boolean) {
        try {
            encryptedPrefs.edit { putBoolean("rasp_logging_enabled", enabled) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set rasp_logging_enabled")
        }
    }

    /**
     * Gets the logging level preference.
     * Uses EncryptedSharedPreferences as this may contain debug information.
     */
    fun getLoggingLevel(): String {
        return try {
            encryptedPrefs.getString("logging_level", "INFO") ?: "INFO"
        } catch (e: Exception) {
            Timber.w(e, "Failed to get logging_level, returning default")
            "INFO"
        }
    }

    /**
     * Sets the logging level preference.
     * Uses EncryptedSharedPreferences as this may contain debug information.
     */
    fun setLoggingLevel(level: String) {
        try {
            encryptedPrefs.edit {putString("logging_level", level) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set logging_level")
        }
    }

    /**
     * Gets the clipboard auto-clear preference.
     * Uses EncryptedSharedPreferences as this is a privacy setting.
     */
    fun getClipboardAutoClear(): Boolean {
        return try {
            encryptedPrefs.getBoolean("clipboard_auto_clear", false)
        } catch (e: Exception) {
            Timber.w(e, "Failed to get clipboard_auto_clear, returning default")
            false
        }
    }

    /**
     * Sets the clipboard auto-clear preference.
     * Uses EncryptedSharedPreferences as this is a privacy setting.
     */
    fun setClipboardAutoClear(enabled: Boolean) {
        try {
            encryptedPrefs.edit {putBoolean("clipboard_auto_clear", enabled) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set clipboard_auto_clear")
        }
    }

    // ============================================================================
    // Reset functionality
    // ============================================================================

    /**
     * Resets all settings to their default values.
     * This will clear both plain and encrypted preferences.
     * 
     * @param resetPlainSettings If true, resets plain settings (theme, dynamic color)
     * @param resetEncryptedSettings If true, resets encrypted settings (security, network, privacy)
     */
    fun resetAllSettings(
        resetPlainSettings: Boolean = true,
        resetEncryptedSettings: Boolean = true
    ) {
        try {
            if (resetPlainSettings) {
                plainPrefs.edit {clear()}
                Timber.d("Reset plain settings to defaults")
            }
            
            if (resetEncryptedSettings) {
                encryptedPrefs.edit { clear() }
                Timber.d("Reset encrypted settings to defaults")
            }
            
            Timber.d("Settings reset completed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset settings")
        }
    }
}

