package com.ble1st.connectias.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ble1st.connectias.core.database.dao.SecurityLogDao
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.entities.LogEntryEntity
import com.ble1st.connectias.core.database.entities.SecurityLogEntity

@Database(
    entities = [
        SecurityLogEntity::class,
        LogEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ConnectiasDatabase : RoomDatabase() {
    abstract fun securityLogDao(): SecurityLogDao
    abstract fun systemLogDao(): SystemLogDao
}
