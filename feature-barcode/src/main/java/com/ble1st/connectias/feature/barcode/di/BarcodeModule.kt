package com.ble1st.connectias.feature.barcode.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.feature.barcode.data.BarcodeDao
import com.ble1st.connectias.feature.barcode.data.BarcodeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BarcodeModule {

    @Provides
    @Singleton
    fun provideBarcodeDatabase(@ApplicationContext context: Context): BarcodeDatabase {
        return Room.databaseBuilder(
            context,
            BarcodeDatabase::class.java,
            "barcode_db"
        ).build()
    }

    @Provides
    fun provideBarcodeDao(database: BarcodeDatabase): BarcodeDao {
        return database.barcodeDao()
    }
}
