package com.ble1st.connectias.feature.barcode.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BarcodeEntity::class], version = 1, exportSchema = false)
abstract class BarcodeDatabase : RoomDatabase() {
    abstract fun barcodeDao(): BarcodeDao
}
