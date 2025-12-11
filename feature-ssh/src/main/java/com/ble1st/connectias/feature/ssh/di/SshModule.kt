package com.ble1st.connectias.feature.ssh.di

import android.content.Context
import androidx.room.Room
import com.ble1st.connectias.feature.ssh.data.SecureStorage
import com.ble1st.connectias.feature.ssh.data.SshDao
import com.ble1st.connectias.feature.ssh.data.SshDatabase
import com.ble1st.connectias.feature.ssh.data.SshRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SshModule {

    @Provides
    @Singleton
    fun provideSshDatabase(@ApplicationContext context: Context): SshDatabase {
        return Room.databaseBuilder(
            context,
            SshDatabase::class.java,
            "ssh_db"
        ).build()
    }

    @Provides
    fun provideSshDao(database: SshDatabase): SshDao {
        return database.sshDao()
    }
    
    @Provides
    @Singleton
    fun provideSshRepository(
        sshDao: SshDao,
        secureStorage: SecureStorage
    ): SshRepository {
        return SshRepository(sshDao, secureStorage)
    }
}
