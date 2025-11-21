package com.ble1st.connectias.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ble1st.connectias.core.database.Converters
import com.ble1st.connectias.core.database.dao.SecurityLogDao
import com.ble1st.connectias.core.database.entities.SecurityLogEntity
@Database(
    entities = [
        SecurityLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun securityLogDao(): SecurityLogDao
}

