@file:Suppress("unused") // Room Entity - used by Room annotation processor for database table generation

package com.ble1st.connectias.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for security logs.
 * Use SecurityThreat model for public API.
 */
@Entity(
    tableName = "security_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["threatLevel"]),
        Index(value = ["threatType", "threatLevel"])
    ]
)
data class SecurityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val threatType: String,
    val threatLevel: String,
    val description: String,
    val details: String? = null
)
