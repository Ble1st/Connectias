package com.ble1st.connectias.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "security_logs")
data class SecurityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val threatType: String,
    val threatLevel: String,
    val description: String,
    val details: String? = null
)

