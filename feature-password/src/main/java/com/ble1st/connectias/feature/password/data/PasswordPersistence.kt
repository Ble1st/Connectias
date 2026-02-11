package com.ble1st.connectias.feature.password.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "password_history")
data class PasswordHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val password: String,
    val type: String, // "CHARACTER" or "PASSPHRASE"
    val strength: String, // WEAK, MEDIUM, STRONG
    val timestamp: Long
)

@Dao
interface PasswordDao {
    @Query("SELECT * FROM password_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PasswordHistoryEntity>>

    @Insert
    suspend fun insert(entry: PasswordHistoryEntity)

    @Query("DELETE FROM password_history")
    suspend fun clearAll()
    
    @Delete
    suspend fun delete(entry: PasswordHistoryEntity)
}

@Database(entities = [PasswordHistoryEntity::class], version = 1, exportSchema = false)
abstract class PasswordDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
}
