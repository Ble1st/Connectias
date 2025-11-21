package com.ble1st.connectias.feature.settings.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "connectias_settings",
        Context.MODE_PRIVATE
    )
    
    // Lock object for thread-safe writes
    private val prefsLock = Any()

    fun getTheme(): String {
        return prefs.getString("theme", "system") ?: "system"
    }

    fun setTheme(theme: String) {
        synchronized(prefsLock) {
            // Use apply() for asynchronous write to avoid blocking the main thread
            // apply() schedules the write asynchronously and returns immediately
            prefs.edit().putString("theme", theme).apply()
        }
    }
}

