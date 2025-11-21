package com.ble1st.connectias.core.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.core.database.AppDatabase
import com.ble1st.connectias.core.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
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
        // Example: .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
        
        // Migration strategy:
        // - Debug builds: fallbackToDestructiveMigration for development convenience
        // - Production builds: Temporary fallback until explicit migrations are implemented
        // TODO: Remove production fallback and add explicit migrations before first schema change
        if (com.ble1st.connectias.core.BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        } else {
            // Temporary: fallback in production until migrations are added
            // TODO: Remove this and add explicit migrations before first schema change
            builder.fallbackToDestructiveMigration()
        }
        
        return builder.build()
    }
}

