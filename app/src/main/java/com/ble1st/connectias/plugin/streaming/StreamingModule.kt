@file:Suppress("unused")

package com.ble1st.connectias.plugin.streaming

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module for plugin streaming dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingModule {
    
    @Binds
    abstract fun bindStreamCache(
        streamCache: StreamCache
    ): IStreamCache
    
    companion object {
        
        @Provides
        @Singleton
        @StreamingHttpClient
        fun provideStreamingHttpClient(): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS) // Longer timeout for streaming
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
        
        @Provides
        @Singleton
        fun providePluginStreamingManager(
            @ApplicationContext context: Context,
            streamCache: StreamCache,
            @StreamingHttpClient okHttpClient: OkHttpClient
        ): PluginStreamingManager {
            return PluginStreamingManager(context, streamCache, okHttpClient)
        }
        
        @Provides
        @Singleton
        fun provideStreamCache(
            @ApplicationContext context: Context
        ): StreamCache {
            return StreamCache(context)
        }
        
        @Provides
        @Singleton
        fun provideLazyPluginLoader(
            @ApplicationContext context: Context
        ): LazyPluginLoader {
            return LazyPluginLoader(context)
        }
        
        @Provides
        @Singleton
        fun provideMappedPluginLoader(
            @ApplicationContext context: Context
        ): MappedPluginLoader {
            return MappedPluginLoader(context)
        }
    }
}

/**
 * Interface for StreamCache to support dependency injection
 */
interface IStreamCache {
    suspend fun getCachedPlugin(pluginId: String): CachedPlugin?
    suspend fun cacheChunk(pluginId: String, chunkIndex: Int, data: ByteArray)
    suspend fun getCachedChunk(pluginId: String, chunkIndex: Int): ByteArray?
    suspend fun optimizeCache(): CacheOptimizationResult
    suspend fun clearCache()
    suspend fun getCacheStats(): CacheStats
}

/**
 * Qualifier for streaming HTTP client
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingHttpClient

/**
 * Additional streaming-related modules
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamingUtilsModule {
    
    @Provides
    @Singleton
    fun provideStreamingConfig(): StreamingConfig {
        return StreamingConfig(
            defaultChunkSize = 1024 * 1024, // 1MB
            maxConcurrentDownloads = 3,
            maxCacheSize = 500 * 1024 * 1024, // 500MB
            connectionTimeoutMs = 10_000,
            readTimeoutMs = 60_000
        )
    }
}

/**
 * Configuration for streaming system
 */
data class StreamingConfig(
    val defaultChunkSize: Int,
    val maxConcurrentDownloads: Int,
    val maxCacheSize: Long,
    val connectionTimeoutMs: Long,
    val readTimeoutMs: Long
)
