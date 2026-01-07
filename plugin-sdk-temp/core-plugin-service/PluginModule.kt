package com.ble1st.connectias.core.di

import android.content.Context
import com.ble1st.connectias.core.plugin.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
    fun providePluginManager(
        @ApplicationContext context: Context,
        pluginDirectory: File
    ): PluginManager {
        return PluginManager(context, pluginDirectory)
    }
    
    @Provides
    @Singleton
    fun provideGitHubPluginDownloadManager(
        @ApplicationContext context: Context,
        pluginDirectory: File,
        okHttpClient: OkHttpClient
    ): GitHubPluginDownloadManager {
        return GitHubPluginDownloadManager(context, pluginDirectory, okHttpClient)
    }
    
    @Provides
    @Singleton
    fun providePluginSignatureValidator(
        @ApplicationContext context: Context
    ): PluginSignatureValidator {
        return PluginSignatureValidator(context, emptyList())
    }
    
    @Provides
    @Singleton
    fun providePluginPermissionManager(
        @ApplicationContext context: Context
    ): PluginPermissionManager {
        return PluginPermissionManager(context)
    }
    
    @Provides
    @Singleton
    fun providePluginValidator(
        @ApplicationContext context: Context,
        signatureValidator: PluginSignatureValidator,
        permissionManager: PluginPermissionManager
    ): PluginValidator {
        return PluginValidator(context, signatureValidator, permissionManager)
    }
    
    @Provides
    @Singleton
    fun providePluginImportService(
        @ApplicationContext context: Context,
        pluginDirectory: File,
        validator: PluginValidator,
        signatureValidator: PluginSignatureValidator
    ): PluginImportService {
        return PluginImportService(context, pluginDirectory, validator, signatureValidator)
    }
    
    @Provides
    @Singleton
    fun providePluginDependencyResolver(
        pluginManager: PluginManager
    ): PluginDependencyResolver {
        return PluginDependencyResolver(pluginManager)
    }
    
    @Provides
    @Singleton
    fun providePluginNotificationManager(
        @ApplicationContext context: Context
    ): com.ble1st.connectias.ui.PluginNotificationManager {
        return com.ble1st.connectias.ui.PluginNotificationManager(context)
    }
}
