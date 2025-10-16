package com.ble1st.connectias.storage

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.storage.database.PluginDatabase
import timber.log.Timber

class PluginDatabaseManager(
    private val context: Context
) {
    private val createdTables = mutableSetOf<String>()
    
    companion object {
        const val MAX_STORAGE_SIZE_MB = 50L
        const val MAX_STORAGE_ENTRIES = 10000
    }
    
    private val database: PluginDatabase by lazy {
        Room.databaseBuilder(
            context,
            PluginDatabase::class.java,
            "plugin_database"
        ).build()
    }
    
    suspend fun createPluginTable(pluginId: String) {
        val tableName = sanitizeTableName(pluginId)
        
        if (createdTables.contains(tableName)) return
        
        // Dynamisch Tabelle erstellen
        database.openHelper.writableDatabase.execSQL("""
            CREATE TABLE IF NOT EXISTS plugin_data_$tableName (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT NOT NULL,
                value TEXT NOT NULL,
                value_size_bytes INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(key)
            )
        """.trimIndent())
        
        createdTables.add(tableName)
        Timber.i("Created isolated table for plugin: $pluginId")
    }
    
    suspend fun deletePluginTable(pluginId: String) {
        val tableName = sanitizeTableName(pluginId)
        database.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS plugin_data_$tableName")
        createdTables.remove(tableName)
        Timber.i("Deleted table for plugin: $pluginId")
    }
    
    fun getPluginDatabase(): PluginDatabase = database
    
    private fun sanitizeTableName(pluginId: String): String {
        // Nur alphanumerische Zeichen und Underscore erlauben
        return pluginId.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }
}
