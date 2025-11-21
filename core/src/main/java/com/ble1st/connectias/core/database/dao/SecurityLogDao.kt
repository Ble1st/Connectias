package com.ble1st.connectias.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ble1st.connectias.core.database.entities.SecurityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityLogDao {
    @Insert
    suspend fun insert(log: SecurityLogEntity): Long

    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<SecurityLogEntity>>

    @Query("SELECT * FROM security_logs WHERE threatType = :threatType ORDER BY timestamp DESC")
    fun getLogsByThreatType(threatType: String): Flow<List<SecurityLogEntity>>

    @Query("DELETE FROM security_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldLogs(beforeTimestamp: Long)
}

