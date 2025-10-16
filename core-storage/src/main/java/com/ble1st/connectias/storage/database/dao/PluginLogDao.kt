package com.ble1st.connectias.storage.database.dao

import androidx.room.*
import com.ble1st.connectias.storage.database.entity.PluginLogEntity

@Dao
interface PluginLogDao {
    @Insert
    suspend fun insert(log: PluginLogEntity)
    
    @Query("SELECT * FROM plugin_logs WHERE pluginId = :pluginId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsForPlugin(pluginId: String, limit: Int = 1000): List<PluginLogEntity>
    
    @Query("DELETE FROM plugin_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLogs(cutoffTime: Long)
}
