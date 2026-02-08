// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * DAO for security audit events.
 *
 * Provides insert and retrieval without bulk delete — the audit trail must
 * not be silently purged except via the standard retention policy.
 */
@Dao
interface SecurityAuditDao {

    @Insert
    suspend fun insert(event: SecurityAuditEntity)

    /**
     * Get recent audit events ordered by timestamp descending.
     */
    @Query("SELECT * FROM security_audit ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAuditEvents(limit: Int = 500): List<SecurityAuditEntity>

    /**
     * Get count of audit events (for metrics).
     */
    @Query("SELECT COUNT(*) FROM security_audit")
    suspend fun getAuditEventCount(): Int

    /**
     * Delete audit events older than the given threshold (retention policy).
     * Returns number of deleted rows.
     */
    @Query("DELETE FROM security_audit WHERE timestamp < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int
}
