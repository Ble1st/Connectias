@file:Suppress("unused") // Room DAO - methods used via annotation processing at compile-time

package com.ble1st.connectias.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ble1st.connectias.core.database.entities.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemLogDao {
    @Insert
    suspend fun insertLog(log: LogEntryEntity)

    @Insert
    suspend fun insertLogs(logs: List<LogEntryEntity>)

    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 1000): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM system_logs WHERE level >= :minLevel ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByLevel(minLevel: Int, limit: Int = 1000): Flow<List<LogEntryEntity>>

    @Query("DELETE FROM system_logs WHERE timestamp < :threshold")
    suspend fun deleteOldLogs(threshold: Long)
    
    @Query("SELECT COUNT(*) FROM system_logs")
    suspend fun getLogCount(): Int
}
