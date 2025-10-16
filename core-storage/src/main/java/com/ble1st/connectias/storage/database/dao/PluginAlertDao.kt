package com.ble1st.connectias.storage.database.dao

import androidx.room.*
import com.ble1st.connectias.storage.database.entity.PluginAlertEntity

@Dao
interface PluginAlertDao {
    @Insert
    suspend fun insert(alert: PluginAlertEntity)
    
    @Query("SELECT * FROM plugin_alerts WHERE pluginId = :pluginId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAlertsForPlugin(pluginId: String, limit: Int = 100): List<PluginAlertEntity>
    
    @Query("DELETE FROM plugin_alerts WHERE timestamp < :cutoffTime")
    suspend fun deleteOldAlerts(cutoffTime: Long)
}
