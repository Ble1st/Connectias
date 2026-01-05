package com.ble1st.connectias.feature.barcode.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "barcodes")
data class BarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: BarcodeType,
    val timestamp: Long
)

@androidx.room.Dao
interface BarcodeDao {
    @androidx.room.Query("SELECT * FROM barcodes ORDER BY timestamp DESC")
    fun getAllBarcodes(): Flow<List<BarcodeEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertBarcode(barcode: BarcodeEntity)

    @androidx.room.Query("DELETE FROM barcodes")
    suspend fun clearAll()
    
    @androidx.room.Delete
    suspend fun delete(barcode: BarcodeEntity)
}
