package com.ble1st.connectias.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ble1st.connectias.storage.database.dao.PluginDao
import com.ble1st.connectias.storage.database.dao.PluginPermissionDao
import com.ble1st.connectias.storage.database.dao.PluginAlertDao
import com.ble1st.connectias.storage.database.dao.PluginLogDao
import com.ble1st.connectias.storage.database.dao.PluginCrashDao
import com.ble1st.connectias.storage.database.entity.PluginEntity
import com.ble1st.connectias.storage.database.entity.PluginPermissionEntity
import com.ble1st.connectias.storage.database.entity.PluginAlertEntity
import com.ble1st.connectias.storage.database.entity.PluginLogEntity
import com.ble1st.connectias.storage.database.entity.PluginCrashEntity

@Database(
    entities = [
        PluginEntity::class,
        PluginPermissionEntity::class,
        PluginAlertEntity::class,
        PluginLogEntity::class,
        PluginCrashEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PluginDatabase : RoomDatabase() {
    abstract fun pluginDao(): PluginDao
    abstract fun pluginPermissionDao(): PluginPermissionDao
    abstract fun pluginAlertDao(): PluginAlertDao
    abstract fun pluginLogDao(): PluginLogDao
    abstract fun pluginCrashDao(): PluginCrashDao
}
