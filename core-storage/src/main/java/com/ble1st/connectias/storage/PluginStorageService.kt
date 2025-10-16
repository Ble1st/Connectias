package com.ble1st.connectias.storage

import com.ble1st.connectias.api.StorageService
import com.ble1st.connectias.storage.database.PluginDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ContentValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
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
        
        val contentValues = ContentValues().apply {
            put("key", sanitizedKey)
            put("value", sanitizedValue)
            put("value_size_bytes", sanitizedValue.toByteArray().size)
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        }
        database.openHelper.writableDatabase.insertWithOnConflict(
            "plugin_data_$tableName",
            null,
            contentValues,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
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
    
    @OptIn(kotlinx.serialization.InternalSerializationApi::class)
    override suspend fun putObject(key: String, value: Any) {
        val sanitizedKey = sanitizer.sanitizeKey(key)
        
        // JSON-Serialisierung mit Type-Checking
        val json = try {
            val serializer = value::class.serializer()
            Json.encodeToString(serializer, value)
        } catch (e: Exception) {
            throw IllegalArgumentException("Object cannot be serialized: ${e.message}")
        }
        
        // Content-Scanning (keine executables, scripts)
        sanitizer.validateContent(json)
        
        putString(sanitizedKey, json)
    }
    
    @OptIn(kotlinx.serialization.InternalSerializationApi::class)
    override suspend fun <T : Any> getObject(key: String, type: KClass<T>): T? {
        val json = getString(key) ?: return null
        return try {
            val serializer = type.serializer()
            Json.decodeFromString(serializer, json)
        } catch (e: Exception) {
            Timber.e("Failed to deserialize object: ${e.message}")
            null
        }
    }
    
    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        val sanitizedKey = sanitizer.sanitizeKey(key)
        
        database.openHelper.writableDatabase.delete(
            "plugin_data_$tableName",
            "key = ?",
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
