package com.ble1st.connectias.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_alerts")
data class PluginAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluginId: String,
    val timestamp: Long,
    val alertType: String,
    val severity: String,
    val message: String,
    val metricsSnapshot: String // JSON
)
