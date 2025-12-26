package com.ble1st.connectias.feature.dnstools.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DnsHistoryEntity::class], version = 1, exportSchema = false)
abstract class DnsToolsDatabase : RoomDatabase() {
    abstract fun dnsHistoryDao(): DnsHistoryDao
}
