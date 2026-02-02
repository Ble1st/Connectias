@file:Suppress("unused")

package com.ble1st.connectias.core.di

import android.content.Context
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    
    /**
     * Provides SettingsRepository instance.
     * The onRecoveryWillEraseData callback is set to null by default.
     * If needed in the future, this can be extended to provide a callback.
     */
    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(
            context = context,
            onRecoveryWillEraseData = null
        )
    }
}

