package com.ble1st.connectias.storage.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.ble1st.connectias.storage.database.entity.PluginDataEntity

@Dao
interface PluginDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertData(data: PluginDataEntity)
    
    @Query("SELECT * FROM plugin_data WHERE pluginId = :pluginId AND key = :key")
    suspend fun getData(pluginId: String, key: String): PluginDataEntity?
    
    @Query("SELECT key FROM plugin_data WHERE pluginId = :pluginId")
    suspend fun getAllKeys(pluginId: String): List<String>
    
    @Query("DELETE FROM plugin_data WHERE pluginId = :pluginId AND key = :key")
    suspend fun deleteData(pluginId: String, key: String)
    
    @Query("DELETE FROM plugin_data WHERE pluginId = :pluginId")
    suspend fun clearAllData(pluginId: String)
}
