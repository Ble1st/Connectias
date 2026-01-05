package com.ble1st.connectias.feature.ntp.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NtpHistoryEntity::class], version = 1, exportSchema = false)
abstract class NtpDatabase : RoomDatabase() {
    abstract fun ntpDao(): NtpDao
}
