package com.ble1st.connectias.feature.ntp.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.feature.ntp.data.NtpDao
import com.ble1st.connectias.feature.ntp.data.NtpDatabase
import com.ble1st.connectias.feature.ntp.data.NtpRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NtpModule {

    @Provides
    @Singleton
    fun provideNtpDatabase(@ApplicationContext context: Context): NtpDatabase {
        return Room.databaseBuilder(
            context,
            NtpDatabase::class.java,
            "ntp_db"
        ).build()
    }

    @Provides
    fun provideNtpDao(database: NtpDatabase): NtpDao {
        return database.ntpDao()
    }
    
    @Provides
    @Singleton
    fun provideNtpRepository(ntpDao: NtpDao): NtpRepository {
        return NtpRepository(ntpDao)
    }
}
