package com.ble1st.connectias.plugin

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {
    
    @Provides
    @Singleton
    fun providePluginDirectory(
        @ApplicationContext context: Context
    ): File {
        val pluginDir = File(context.filesDir, "plugins")
        pluginDir.mkdirs()
        return pluginDir
    }
    
    @Provides
    @Singleton
    fun provideNativeLibraryManager(
        @ApplicationContext context: Context,
        pluginDirectory: File
    ): NativeLibraryManager {
        return NativeLibraryManager(pluginDirectory)
    }
    
    @Provides
    @Singleton
    fun providePluginNotificationManager(
        @ApplicationContext context: Context
    ): PluginNotificationManager {
        return PluginNotificationManager(context)
    }
    
    @Provides
    @Singleton
    fun providePluginManager(
        @ApplicationContext context: Context,
        pluginDirectory: File
    ): PluginManager {
        return PluginManager(context, pluginDirectory)
    }
}
