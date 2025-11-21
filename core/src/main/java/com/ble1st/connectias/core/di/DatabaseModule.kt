package com.ble1st.connectias.core.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SupportSQLCipher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        val passphrase = SupportSQLCipher.getBytes("connectias-secret-key".toCharArray())
        val factory = SupportOpenHelperFactory(passphrase)
        
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "connectias.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }
}

