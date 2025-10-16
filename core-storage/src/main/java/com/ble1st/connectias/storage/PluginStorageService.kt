package com.ble1st.connectias.storage

import com.ble1st.connectias.api.StorageService
import com.ble1st.connectias.storage.database.PluginDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.reflect.KClass

class PluginStorageService(
    private val pluginId: String,
    private val database: PluginDatabase,
    private val sanitizer: InputSanitizer,
    private val quotaManager: StorageQuotaManager
) : StorageService {
    
    private val tableName = sanitizeTableName(pluginId)
    
    override suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        // 1. Input-Validierung & Sanitization
        val sanitizedKey = sanitizer.sanitizeKey(key)
        val sanitizedValue = sanitizer.sanitizeValue(value)
        
        // 2. Quota-Check
        quotaManager.checkQuota(pluginId, sanitizedValue.length)
        
        // 3. Speichern mit Prepared Statement
        val query = """
            INSERT OR REPLACE INTO plugin_data_$tableName 
            (key, value, value_size_bytes, created_at, updated_at) 
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        
        database.openHelper.writableDatabase.execSQL(
            query,
            arrayOf(
                sanitizedKey,
                sanitizedValue,
                sanitizedValue.toByteArray().size,
                System.currentTimeMillis(),
                System.currentTimeMillis()
            )
        )
        
        quotaManager.updateUsage(pluginId)
    }
    
    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        val sanitizedKey = sanitizer.sanitizeKey(key)
        
        // Prepared Statement für SQL-Injection-Schutz
        val cursor = database.openHelper.readableDatabase.rawQuery(
            "SELECT value FROM plugin_data_$tableName WHERE key = ? LIMIT 1",
            arrayOf(sanitizedKey)
        )
        
        cursor.use {
            if (it.moveToFirst()) {
                it.getString(0)
            } else null
        }
    }
    
    override suspend fun putObject(key: String, value: Any) {
        val sanitizedKey = sanitizer.sanitizeKey(key)
        
        // JSON-Serialisierung mit Type-Checking
        val json = try {
            Json.encodeToString(value)
        } catch (e: Exception) {
            throw IllegalArgumentException("Object cannot be serialized: ${e.message}")
        }
        
        // Content-Scanning (keine executables, scripts)
        sanitizer.validateContent(json)
        
        putString(sanitizedKey, json)
    }
    
    override suspend fun <T> getObject(key: String, type: KClass<T>): T? {
        val json = getString(key) ?: return null
        return try {
            Json.decodeFromString(type.java, json)
        } catch (e: Exception) {
            Timber.e("Failed to deserialize object: ${e.message}")
            null
        }
    }
    
    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        val sanitizedKey = sanitizer.sanitizeKey(key)
        
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM plugin_data_$tableName WHERE key = ?",
            arrayOf(sanitizedKey)
        )
        
        quotaManager.updateUsage(pluginId)
    }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.execSQL("DELETE FROM plugin_data_$tableName")
        quotaManager.resetUsage(pluginId)
    }
    
    private fun sanitizeTableName(pluginId: String): String {
        return pluginId.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }
}
