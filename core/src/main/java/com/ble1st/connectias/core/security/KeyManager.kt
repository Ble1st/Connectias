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
        private const val PASSPHRASE_LENGTH = 32 // 256 bits
    }

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
     * @return Byte array representing the passphrase for SQLCipher
     */
    fun getDatabasePassphrase(): ByteArray {
        return try {
            val storedPassphrase = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
            
            if (storedPassphrase != null) {
                // Use existing passphrase
                Timber.d("Using existing database passphrase from secure storage")
                SupportSQLCipher.getBytes(storedPassphrase.toCharArray())
            } else {
                // Generate and store new passphrase
                Timber.d("Generating new database passphrase")
                val newPassphrase = generateSecurePassphrase()
                encryptedPrefs.edit()
                    .putString(KEY_DB_PASSPHRASE, newPassphrase)
                    .apply()
                
                SupportSQLCipher.getBytes(newPassphrase.toCharArray())
            }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving database passphrase, generating new one")
            // Fallback: generate passphrase but don't store it (less secure but prevents crash)
            val fallbackPassphrase = generateSecurePassphrase()
            SupportSQLCipher.getBytes(fallbackPassphrase.toCharArray())
        }
    }

    /**
     * Generates a cryptographically secure random passphrase.
     * Uses SecureRandom for generation.
     *
     * @return Secure random passphrase string
     */
    private fun generateSecurePassphrase(): String {
        val random = SecureRandom()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return buildString(PASSPHRASE_LENGTH) {
            repeat(PASSPHRASE_LENGTH) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }
}

