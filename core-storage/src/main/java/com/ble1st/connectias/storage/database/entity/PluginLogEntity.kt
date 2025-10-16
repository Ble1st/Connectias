package com.ble1st.connectias.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_logs")
data class PluginLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluginId: String,
    val timestamp: Long,
    val level: Int,
    val tag: String?,
    val message: String,
    val throwable: String?
)
