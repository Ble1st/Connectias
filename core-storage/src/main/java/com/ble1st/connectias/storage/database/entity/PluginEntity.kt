package com.ble1st.connectias.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val author: String,
    val installPath: String,
    val enabled: Boolean,
    val installedAt: Long,
    val lastUpdated: Long
)
