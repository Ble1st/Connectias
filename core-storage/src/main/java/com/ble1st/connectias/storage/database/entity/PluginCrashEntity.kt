package com.ble1st.connectias.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_crashes")
data class PluginCrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluginId: String,
    val timestamp: Long,
    val errorMessage: String,
    val stackTrace: String,
    val errorType: String
)
