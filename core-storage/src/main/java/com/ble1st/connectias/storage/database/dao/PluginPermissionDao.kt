package com.ble1st.connectias.storage.database.dao

import androidx.room.*
import com.ble1st.connectias.storage.database.entity.PluginPermissionEntity

@Dao
interface PluginPermissionDao {
    @Query("SELECT * FROM plugin_permissions WHERE pluginId = :pluginId AND permission = :permission")
    suspend fun getPermission(pluginId: String, permission: String): PluginPermissionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PluginPermissionEntity)
    
    @Query("DELETE FROM plugin_permissions WHERE pluginId = :pluginId")
    suspend fun deleteAllForPlugin(pluginId: String)
}
