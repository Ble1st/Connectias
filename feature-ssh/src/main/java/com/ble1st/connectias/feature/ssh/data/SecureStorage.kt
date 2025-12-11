package com.ble1st.connectias.feature.ssh.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_ssh_creds",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(profileId: String, password: String) {
        sharedPreferences.edit().putString("${profileId}_pwd", password).apply()
    }

    fun getPassword(profileId: String): String? {
        return sharedPreferences.getString("${profileId}_pwd", null)
    }

    fun savePrivateKeyPath(profileId: String, path: String) {
        sharedPreferences.edit().putString("${profileId}_key", path).apply()
    }

    fun getPrivateKeyPath(profileId: String): String? {
        return sharedPreferences.getString("${profileId}_key", null)
    }
    
    fun saveKeyPassword(profileId: String, password: String) {
        sharedPreferences.edit().putString("${profileId}_key_pwd", password).apply()
    }

    fun getKeyPassword(profileId: String): String? {
        return sharedPreferences.getString("${profileId}_key_pwd", null)
    }
    
    fun clearCredentials(profileId: String) {
        sharedPreferences.edit()
            .remove("${profileId}_pwd")
            .remove("${profileId}_key")
            .remove("${profileId}_key_pwd")
            .apply()
    }
}
