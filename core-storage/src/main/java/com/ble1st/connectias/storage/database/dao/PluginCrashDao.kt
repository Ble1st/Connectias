package com.ble1st.connectias.storage.database.dao

import androidx.room.*
import com.ble1st.connectias.storage.database.entity.PluginCrashEntity

@Dao
interface PluginCrashDao {
    @Insert
    suspend fun insert(crash: PluginCrashEntity)
    
    @Query("SELECT * FROM plugin_crashes WHERE pluginId = :pluginId ORDER BY timestamp DESC")
    suspend fun getCrashesForPlugin(pluginId: String): List<PluginCrashEntity>
}
