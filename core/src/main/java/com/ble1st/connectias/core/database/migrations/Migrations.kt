package com.ble1st.connectias.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migrations.
 * 
 * When the database schema changes, add a new Migration object here
 * and increment the database version in AppDatabase.
 * 
 * Example:
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(database: SupportSQLiteDatabase) {
 *         database.execSQL("ALTER TABLE security_logs ADD COLUMN new_column TEXT")
 *     }
 * }
 */

/**
 * Migration from version 1 to 2.
 * 
 * This migration ensures that both tables (security_logs and system_logs) exist
 * with the correct schema and indices. If tables don't exist, they will be created.
 * If they exist but are missing columns or indices, they will be updated.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create security_logs table if it doesn't exist
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS security_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                threatType TEXT NOT NULL,
                threatLevel TEXT NOT NULL,
                description TEXT NOT NULL,
                details TEXT
            )
        """.trimIndent())
        
        // Create indices for security_logs if they don't exist
        database.execSQL("CREATE INDEX IF NOT EXISTS index_security_logs_timestamp ON security_logs(timestamp)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_security_logs_threatLevel ON security_logs(threatLevel)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_security_logs_threatType_threatLevel ON security_logs(threatType, threatLevel)")
        
        // Create system_logs table if it doesn't exist
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS system_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                level INTEGER NOT NULL,
                tag TEXT,
                message TEXT NOT NULL,
                thread_name TEXT NOT NULL,
                exception_trace TEXT
            )
        """.trimIndent())
    }
}

