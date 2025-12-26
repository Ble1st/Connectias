package com.ble1st.connectias.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "security_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["threatLevel"]),
        Index(value = ["threatType", "threatLevel"]) // Composite index for queries filtering by both
    ]
)data class SecurityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val threatType: String,
    val threatLevel: String,
    val description: String,
    val details: String? = null
)

