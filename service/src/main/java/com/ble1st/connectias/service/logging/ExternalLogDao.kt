// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for external app logs in the isolated process.
 * Used by LoggingService to store and retrieve logs from external apps.
 */
@Dao
interface ExternalLogDao {
    
    /**
     * Insert a single log entry.
     */
    @Insert
    suspend fun insert(log: ExternalLogEntity)
    
    /**
     * Insert multiple log entries (batch).
     */
    @Insert
    suspend fun insertAll(logs: List<ExternalLogEntity>)
    
    /**
     * Get recent logs ordered by timestamp descending.
     * Used by Service-Log-Viewer via IPC.
     */
    @Query("SELECT * FROM external_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 1000): List<ExternalLogEntity>
    
    /**
     * Get logs for a specific package.
     */
    @Query("SELECT * FROM external_logs WHERE package_name = :packageName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByPackage(packageName: String, limit: Int = 1000): List<ExternalLogEntity>
    
    /**
     * Get logs by level (e.g., ERROR, WARN).
     */
    @Query("SELECT * FROM external_logs WHERE level = :level ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByLevel(level: String, limit: Int = 1000): List<ExternalLogEntity>
    
    /**
     * Delete old logs to prevent database growth.
     * Call periodically (e.g., logs older than 7 days).
     *
     * @return Number of rows deleted
     */
    @Query("DELETE FROM external_logs WHERE timestamp < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int

    /**
     * Delete the oldest N log entries (used for hard cap enforcement).
     *
     * @param count Number of entries to delete
     * @return Number of rows deleted
     */
    @Query("DELETE FROM external_logs WHERE id IN (SELECT id FROM external_logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestN(count: Int): Int

    /**
     * Get total count of logs.
     */
    @Query("SELECT COUNT(*) FROM external_logs")
    suspend fun getLogCount(): Int

    /**
     * Get the timestamp of the oldest log entry (for metrics).
     * Returns null if the table is empty.
     */
    @Query("SELECT MIN(timestamp) FROM external_logs")
    suspend fun getOldestTimestamp(): Long?

    /**
     * Delete all logs (for testing or reset).
     */
    @Query("DELETE FROM external_logs")
    suspend fun deleteAll()
}
