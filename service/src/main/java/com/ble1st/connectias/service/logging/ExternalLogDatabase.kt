// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for external app logs in the isolated process.
 * This database is separate from the main app database (ConnectiasDatabase)
 * and lives only in the isolated :logging process.
 * 
 * Note: Isolated process cannot access KeyManager or app-level SQLCipher setup.
 * Database can be optionally encrypted if passphrase is provided via IPC at bind time.
 */
@Database(
    entities = [ExternalLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ExternalLogDatabase : RoomDatabase() {
    abstract fun externalLogDao(): ExternalLogDao
}
