package com.ble1st.connectias.core.settings

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

    fun getTheme(): String {
        return prefs.getString("theme", "system") ?: "system"
    }

    /**
     * Sets the theme preference asynchronously.
     * SharedPreferences is thread-safe, so no explicit synchronization is needed.
     * apply() schedules the write asynchronously and returns immediately.
     */
    fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
    }
}

