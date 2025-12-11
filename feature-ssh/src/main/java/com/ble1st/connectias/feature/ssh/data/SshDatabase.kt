package com.ble1st.connectias.feature.ssh.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SshProfileEntity::class], version = 1, exportSchema = false)
@TypeConverters(SshConverters::class)
abstract class SshDatabase : RoomDatabase() {
    abstract fun sshDao(): SshDao
}
