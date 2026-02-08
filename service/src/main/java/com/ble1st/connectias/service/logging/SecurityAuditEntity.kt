// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for security audit events.
 *
 * Separate table from external_logs to ensure the audit trail is independently
 * queryable and survives log purges. Audit events are never silently deleted
 * except for the standard 7-day retention policy.
 */
@Entity(
    tableName = "security_audit",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["package_name", "timestamp"]),
        Index(value = ["event_type", "timestamp"])
    ]
)
data class SecurityAuditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "details")
    val details: String
)
