package com.ble1st.connectias.feature.utilities.di

import com.ble1st.connectias.feature.utilities.api.ApiTesterConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Utilities feature.
 * All providers and repository are automatically provided by Hilt via @Inject constructors.
 * 
 * Reserved for future Hilt providers/bindings if needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object UtilitiesModule {
    
    /**
     * Provides default ApiTesterConfig.
     * SSL pinning is disabled by default for security and flexibility.
     */
    @Provides
    @Singleton
    fun provideApiTesterConfig(): ApiTesterConfig {
        return ApiTesterConfig(
            enableSslPinning = false,
            pinnedDomains = emptyMap()
        )
    }
}

