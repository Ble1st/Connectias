package com.ble1st.connectias.storage.database.dao

import androidx.room.*
import com.ble1st.connectias.storage.database.entity.PluginEntity

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins WHERE enabled = 1")
    suspend fun getEnabledPlugins(): List<PluginEntity>
    
    @Query("SELECT * FROM plugins")
    suspend fun getAllPlugins(): List<PluginEntity>
    
    @Query("SELECT * FROM plugins WHERE id = :pluginId")
    suspend fun getPlugin(pluginId: String): PluginEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugin(plugin: PluginEntity)
    
    @Update
    suspend fun updatePlugin(plugin: PluginEntity)
    
    @Delete
    suspend fun deletePlugin(plugin: PluginEntity)
    
    @Query("DELETE FROM plugins WHERE id = :pluginId")
    suspend fun deletePluginById(pluginId: String)
}
