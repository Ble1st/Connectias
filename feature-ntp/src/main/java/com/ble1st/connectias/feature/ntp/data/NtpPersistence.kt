package com.ble1st.connectias.feature.ntp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ntp_history")
data class NtpHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val server: String,
    val offsetMs: Long,
    val delayMs: Long,
    val stratum: Int,
    val referenceId: String,
    val timestamp: Long
)

@androidx.room.Dao
interface NtpDao {
    @androidx.room.Query("SELECT * FROM ntp_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<NtpHistoryEntity>>

    @androidx.room.Insert
    suspend fun insert(entry: NtpHistoryEntity)

    @androidx.room.Query("DELETE FROM ntp_history")
    suspend fun clearAll()
    
    @androidx.room.Delete
    suspend fun delete(entry: NtpHistoryEntity)
}
