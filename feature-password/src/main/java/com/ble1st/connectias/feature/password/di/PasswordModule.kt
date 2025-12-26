package com.ble1st.connectias.feature.password.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.feature.password.data.PasswordDao
import com.ble1st.connectias.feature.password.data.PasswordDatabase
import com.ble1st.connectias.feature.password.data.PasswordRepository
import com.ble1st.connectias.feature.password.domain.PasswordAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PasswordModule {

    @Provides
    @Singleton
    fun providePasswordDatabase(@ApplicationContext context: Context): PasswordDatabase {
        return Room.databaseBuilder(
            context,
            PasswordDatabase::class.java,
            "password_db"
        ).build()
    }

    @Provides
    fun providePasswordDao(database: PasswordDatabase): PasswordDao {
        return database.passwordDao()
    }
    
    @Provides
    @Singleton
    fun providePasswordRepository(
        passwordDao: PasswordDao,
        passwordAnalyzer: PasswordAnalyzer
    ): PasswordRepository {
        return PasswordRepository(passwordDao, passwordAnalyzer)
    }
}
