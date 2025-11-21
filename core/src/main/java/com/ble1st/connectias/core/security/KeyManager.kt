package com.ble1st.connectias.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportSQLCipher
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure key generation and storage for database encryption.
 * Uses Android Keystore and EncryptedSharedPreferences to securely store
 * the database passphrase.
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "connectias_secure_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val PASSPHRASE_LENGTH = 32 // ~196 bits of entropy    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Gets or generates a secure passphrase for database encryption.
     * The passphrase is stored in EncryptedSharedPreferences and is unique per installation.
     * 
     * Note: This method uses CharArray internally to minimize memory exposure window.
     * However, EncryptedSharedPreferences requires String, so transient String allocations
     * are unavoidable during storage operations.
     *
     * @return Byte array representing the passphrase for SQLCipher
     */
    fun getDatabasePassphrase(): ByteArray {
        return try {
            val storedPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
            
            if (storedPassphrase != null) {
                // Use existing passphrase - convert to CharArray immediately
                Timber.d("Using existing database passphrase from secure storage")
                val passphraseChars = storedPassphrase.toCharArray()
                try {
                    return SupportSQLCipher.getBytes(passphraseChars)
                } finally {
                    // Zero-fill CharArray to minimize memory exposure
                    passphraseChars.fill('\u0000')
                }
            } else {
                // Generate and store new passphrase
                Timber.d("Generating new database passphrase")
                val newPassphraseChars = generateSecurePassphrase()
                
                // Convert to String only for storage (EncryptedSharedPreferences limitation)
                val passphraseString = String(newPassphraseChars)
                
                // Synchronously commit to ensure consistency
                val committed = encryptedPrefs.edit()
                    .putString(KEY_DB_PASSPHRASE, passphraseString)
                    .commit()
                
                if (!committed) {
                    Timber.e("Failed to commit passphrase to EncryptedSharedPreferences")
                    // Try to read again in case another thread wrote it
                    val retryPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
                    if (retryPassphrase != null) {
                        val retryChars = retryPassphrase.toCharArray()
                        try {
                            return SupportSQLCipher.getBytes(retryChars)
                        } finally {
                            retryChars.fill('\u0000')
                        }
                    }
                    // If still null, throw exception
                    throw IllegalStateException("Failed to store database passphrase")
                }
                
                // Clear String reference (though GC will handle it)
                // Convert to ByteArray and zero-fill CharArray
                try {
                    return SupportSQLCipher.getBytes(newPassphraseChars)
                } finally {
                    // Zero-fill CharArray to minimize memory exposure
                    newPassphraseChars.fill('\u0000')
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving database passphrase")
            // Try to read any already-stored passphrase again
            try {
                val retryPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
                if (retryPassphrase != null) {
                    val retryChars = retryPassphrase.toCharArray()
                    try {
                        return SupportSQLCipher.getBytes(retryChars)
                    } finally {
                        retryChars.fill('\u0000')
                    }
                }
            } catch (e2: Exception) {
                Timber.e(e2, "Error reading stored passphrase on retry")
            }
            
            // Last resort: generate new passphrase and commit synchronously
            val fallbackChars = generateSecurePassphrase()
            val fallbackString = String(fallbackChars)
            
            try {
                val committed = encryptedPrefs.edit()
                    .putString(KEY_DB_PASSPHRASE, fallbackString)
                    .commit()
                
                if (!committed) {
                    throw IllegalStateException("Failed to store fallback passphrase", e)
                }
                
                try {
                    return SupportSQLCipher.getBytes(fallbackChars)
                } finally {
                    fallbackChars.fill('\u0000')
                }
            } catch (e3: Exception) {
                // If commit fails, we cannot proceed - throw exception
                fallbackChars.fill('\u0000')
                throw IllegalStateException("Failed to store database passphrase after retry", e3)
            }
        }
    }

    /**
     * Generates a cryptographically secure random passphrase.
     * Uses SecureRandom for generation.
     * Returns CharArray to minimize memory exposure window.
     *
     * @return Secure random passphrase as CharArray
     */
    private fun generateSecurePassphrase(): CharArray {
        val random = SecureRandom()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return CharArray(PASSPHRASE_LENGTH) {
            chars[random.nextInt(chars.length)]
        }
    }
}

