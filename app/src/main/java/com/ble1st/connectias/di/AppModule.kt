package com.ble1st.connectias.di

import android.content.Context
import com.ble1st.connectias.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun providePluginManager(@ApplicationContext context: Context): PluginManager {
        return PluginManager(context)
    }
}
