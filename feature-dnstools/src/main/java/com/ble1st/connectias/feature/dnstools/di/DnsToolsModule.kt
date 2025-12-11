package com.ble1st.connectias.feature.dnstools.di

import com.ble1st.connectias.feature.dnstools.data.DnsToolsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DnsToolsModule {

    @Provides
    @Singleton
    fun provideDnsToolsRepository(
        okHttpClient: OkHttpClient
    ): DnsToolsRepository = DnsToolsRepository(okHttpClient)
}
