package com.ble1st.connectias.storage.database.entity

import androidx.room.Entity

@Entity(
    tableName = "plugin_permissions",
    primaryKeys = ["pluginId", "permission"]
)
data class PluginPermissionEntity(
    val pluginId: String,
    val permission: String,
    val granted: Boolean,
    val timestamp: Long
)
