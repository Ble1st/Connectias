package com.ble1st.connectias.feature.deviceinfo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "temperature_history")
data class TemperatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: Float,
    val timestamp: Long
)

@Dao
interface TemperatureDao {
    @Query("SELECT * FROM temperature_history ORDER BY timestamp ASC") // ASC for Graph
    fun getAllHistory(): Flow<List<TemperatureEntity>>

    @Insert
    suspend fun insert(entry: TemperatureEntity)

    @Query("DELETE FROM temperature_history")
    suspend fun clearAll()
}

@Database(entities = [TemperatureEntity::class], version = 1, exportSchema = false)
abstract class DeviceDatabase : RoomDatabase() {
    abstract fun temperatureDao(): TemperatureDao
}
