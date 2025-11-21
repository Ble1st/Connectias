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
        val factory = SupportOpenHelperFactory(passphrase)
        
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "connectias.db"
        )
            .openHelperFactory(factory)
        
        // Add migrations here when schema changes
        // Example: .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        // For now, we use fallbackToDestructiveMigration only in debug builds
        // In production, you should always provide migrations
        if (com.ble1st.connectias.core.BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }
        // TODO: Add explicit migrations when database schema changes
        // builder.addMigrations(Migrations.MIGRATION_1_2)
        
        return builder.build()
    }
}

