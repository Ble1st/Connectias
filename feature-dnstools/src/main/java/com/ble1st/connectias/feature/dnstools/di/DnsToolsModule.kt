package com.ble1st.connectias.feature.dnstools.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.feature.dnstools.data.DnsHistoryDao
import com.ble1st.connectias.feature.dnstools.data.DnsToolsDatabase
import com.ble1st.connectias.feature.dnstools.data.DnsToolsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DnsToolsModule {

    @Provides
    @Singleton
    fun provideDnsToolsDatabase(@ApplicationContext context: Context): DnsToolsDatabase {
        return Room.databaseBuilder(
            context,
            DnsToolsDatabase::class.java,
            "dnstools_db"
        ).build()
    }

    @Provides
    fun provideDnsHistoryDao(database: DnsToolsDatabase): DnsHistoryDao {
        return database.dnsHistoryDao()
    }
    
    @Provides
    @Singleton
    fun provideDnsToolsRepository(
        dnsHistoryDao: DnsHistoryDao
    ): DnsToolsRepository {
        return DnsToolsRepository(dnsHistoryDao)
    }
}