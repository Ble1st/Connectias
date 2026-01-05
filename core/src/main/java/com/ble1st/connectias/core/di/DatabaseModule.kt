package com.ble1st.connectias.core.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.core.database.AppDatabase
import com.ble1st.connectias.core.database.dao.SecurityLogDao
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.migrations.MIGRATION_1_2
import com.ble1st.connectias.core.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    init {
        // Load SQLCipher native library early
        // This ensures the library is available before database operations
        try {
            System.loadLibrary("sqlcipher")
            Timber.d("SQLCipher native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load SQLCipher native library")
            // Don't throw here - let it fail when database is opened if library is truly missing
        }
    }
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: KeyManager
    ): AppDatabase {
        // Get secure passphrase from KeyManager (stored in EncryptedSharedPreferences)
        val passphrase = keyManager.getDatabasePassphrase()
        
        // Validate passphrase before creating factory
        require(passphrase.isNotEmpty()) {
            "Database passphrase cannot be empty"
        }
        
        val factory = SupportOpenHelperFactory(passphrase)
        
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "connectias.db"
        )
            .openHelperFactory(factory)
        
        // Add migrations here when schema changes
        builder.addMigrations(MIGRATION_1_2)
        
        // Migration strategy:
        // - All builds use explicit migrations to prevent data loss
        // - fallbackToDestructiveMigration is NOT used to ensure data safety
        // - If migration fails, app will crash rather than silently lose data
        
        return builder.build()
    }
    
    @Provides
    @Singleton
    fun provideSecurityLogDao(database: AppDatabase): SecurityLogDao {
        return database.securityLogDao()
    }
    
    @Provides
    @Singleton
    fun provideSystemLogDao(database: AppDatabase): SystemLogDao {
        return database.systemLogDao()
    }
}

