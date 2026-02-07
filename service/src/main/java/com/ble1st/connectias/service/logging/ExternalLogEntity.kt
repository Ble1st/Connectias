// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for external app logs collected by LoggingService.
 * This entity lives in the isolated process (:logging) with its own database.
 */
@Entity(
    tableName = "external_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["package_name", "timestamp"]),
        Index(value = ["level", "timestamp"])
    ]
)
data class ExternalLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "package_name")
    val packageName: String,
    
    @ColumnInfo(name = "level")
    val level: String,
    
    @ColumnInfo(name = "tag")
    val tag: String,
    
    @ColumnInfo(name = "message")
    val message: String,
    
    @ColumnInfo(name = "exception_trace")
    val exceptionTrace: String? = null
)
