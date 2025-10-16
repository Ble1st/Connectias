package com.ble1st.connectias.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugin_data")
data class PluginDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pluginId: String,
    val key: String,
    val value: String,
    val timestamp: Long
)
