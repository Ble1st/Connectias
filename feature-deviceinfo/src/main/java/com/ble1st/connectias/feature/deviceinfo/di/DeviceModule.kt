package com.ble1st.connectias.feature.deviceinfo.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.feature.deviceinfo.data.DeviceDatabase
import com.ble1st.connectias.feature.deviceinfo.data.DeviceRepository
import com.ble1st.connectias.feature.deviceinfo.data.TemperatureDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {

    @Provides
    @Singleton
    fun provideDeviceDatabase(@ApplicationContext context: Context): DeviceDatabase {
        return Room.databaseBuilder(
            context,
            DeviceDatabase::class.java,
            "device_db"
        ).build()
    }

    @Provides
    fun provideTemperatureDao(database: DeviceDatabase): TemperatureDao {
        return database.temperatureDao()
    }
    
    @Provides
    @Singleton
    fun provideDeviceRepository(
        @ApplicationContext context: Context,
        temperatureDao: TemperatureDao
    ): DeviceRepository {
        return DeviceRepository(context, temperatureDao)
    }
}
