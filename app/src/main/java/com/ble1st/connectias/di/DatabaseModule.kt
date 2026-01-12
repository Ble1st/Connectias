package com.ble1st.connectias.di

import android.app.Application
import android.content.Context
import com.ble1st.connectias.core.database.ConnectiasDatabase
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
// Try alternative import paths for SQLCipher SupportFactory
// import net.sqlcipher.database.SupportFactory
// import net.zetetic.database.sqlcipher.SupportFactory
import javax.inject.Singleton

/**
 * Hilt module that provides database and DAO instances.
 * Located in app module to avoid circular dependencies between core and core:database.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private fun isPluginSandboxProcess(context: Context): Boolean {
        return try {
            val processName = Application.getProcessName()
            processName.contains(":plugin_sandbox")
        } catch (e: Exception) {
            false
        }
    }
    
    @Provides
    @Singleton
    fun provideKeyManager(
        @ApplicationContext context: Context
    ): KeyManager {
        // CRITICAL: Sandbox process cannot access KeyManager
        if (isPluginSandboxProcess(context)) {
            throw UnsupportedOperationException(
                "KeyManager not available in sandbox process."
            )
        }
        return KeyManager(context)
    }
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: KeyManager
    ): ConnectiasDatabase {
        // CRITICAL: Sandbox process cannot access Database
        // It runs in isolated process without KeyStore access
        if (isPluginSandboxProcess(context)) {
            throw UnsupportedOperationException(
                "Database not available in sandbox process. " +
                "Sandbox uses minimal initialization without DB/KeyStore."
            )
        }
        val passphrase = keyManager.getDatabasePassphrase()
        
        // Load SQLCipher native library first
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.w("DatabaseModule", "Failed to load SQLCipher native library", e)
        }
        
        // SQLCipher 4.12.0 uses SupportOpenHelperFactory instead of SupportFactory
        // Try the new class name first, then fallback to old names for compatibility
        val factory = try {
            // New API in sqlcipher-android 4.12.0
            val supportFactoryClass = Class.forName("net.zetetic.database.sqlcipher.SupportOpenHelperFactory")
            supportFactoryClass.getConstructor(ByteArray::class.java)
                .newInstance(passphrase) as androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
        } catch (e: ClassNotFoundException) {
            // Fallback to old SupportFactory name (for older versions)
            try {
                val supportFactoryClass = Class.forName("net.sqlcipher.database.SupportFactory")
                supportFactoryClass.getConstructor(ByteArray::class.java)
                    .newInstance(passphrase) as androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
            } catch (e2: ClassNotFoundException) {
                throw IllegalStateException(
                    "SQLCipher SupportOpenHelperFactory/SupportFactory not found. " +
                    "Please ensure SQLCipher dependency (net.zetetic:sqlcipher-android:4.12.0) is correctly configured.",
                    e2
                )
            }
        }
        
        return androidx.room.Room.databaseBuilder(
            context,
            ConnectiasDatabase::class.java,
            "connectias_database"
        )
            .openHelperFactory(factory)
            .build()
    }
    
    @Provides
    fun provideSystemLogDao(database: ConnectiasDatabase): SystemLogDao {
        return database.systemLogDao()
    }
}
