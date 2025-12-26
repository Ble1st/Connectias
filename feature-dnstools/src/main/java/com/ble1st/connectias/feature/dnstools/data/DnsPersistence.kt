package com.ble1st.connectias.feature.dnstools.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow

enum class ToolType {
    DNS
}

@Entity(tableName = "dns_history")
data class DnsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolType: ToolType,
    val query: String,
    val resultSummary: String, // Short summary for list
    val fullResult: String, // Full JSON or text content
    val timestamp: Long
)

@androidx.room.Dao
interface DnsHistoryDao {
    @androidx.room.Query("SELECT * FROM dns_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<DnsHistoryEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DnsHistoryEntity)

    @androidx.room.Query("DELETE FROM dns_history")
    suspend fun clearAll()
    
    @androidx.room.Delete
    suspend fun delete(entry: DnsHistoryEntity)
}
