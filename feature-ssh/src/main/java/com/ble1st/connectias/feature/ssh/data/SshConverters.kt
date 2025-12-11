package com.ble1st.connectias.feature.ssh.data

import androidx.room.TypeConverter

class SshConverters {
    @TypeConverter
    fun fromAuthMode(mode: AuthMode): String = mode.name

    @TypeConverter
    fun toAuthMode(value: String): AuthMode = try {
        AuthMode.valueOf(value)
    } catch (e: Exception) {
        AuthMode.PASSWORD
    }
}
