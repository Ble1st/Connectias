// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for external app logs in the isolated process.
 * This database is separate from the main app database (ConnectiasDatabase)
 * and lives only in the isolated :logging process.
 *
 * The database passphrase is provided via IPC (setDatabaseKey) from the
 * main process KeyManager after binding. Database initialization is deferred
 * until the key is received.
 *
 * Version history:
 *   1 → 2: Added security_audit table for structured audit event storage.
 */
@Database(
    entities = [ExternalLogEntity::class, SecurityAuditEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ExternalLogDatabase : RoomDatabase() {
    abstract fun externalLogDao(): ExternalLogDao
    abstract fun securityAuditDao(): SecurityAuditDao

    companion object {
        /**
         * Migration 1 → 2: Add security_audit table.
         * Additive only — no existing columns altered.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `security_audit` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `event_type` TEXT NOT NULL,
                        `details` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_security_audit_timestamp` " +
                    "ON `security_audit` (`timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_security_audit_package_name_timestamp` " +
                    "ON `security_audit` (`package_name`, `timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_security_audit_event_type_timestamp` " +
                    "ON `security_audit` (`event_type`, `timestamp`)"
                )
            }
        }
    }
}
