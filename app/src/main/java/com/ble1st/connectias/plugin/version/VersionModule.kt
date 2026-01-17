package com.ble1st.connectias.plugin.version

import android.content.Context
import com.ble1st.connectias.plugin.PluginManager
import com.ble1st.connectias.plugin.StreamingPluginManager
import com.ble1st.connectias.plugin.store.GitHubPluginStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VersionModule {
    
    @Binds
    @Singleton
    abstract fun bindVersionedPluginManager(
        versionedPluginManager: VersionedPluginManager
    ): IVersionedPluginManager
    
    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }
        }
        
        @Provides
        @Singleton
        fun providePluginVersionManager(
            @ApplicationContext context: Context,
            json: Json
        ): PluginVersionManager {
            return PluginVersionManager(context, json)
        }
        
        @Provides
        @Singleton
        fun providePluginRollbackManager(
            @ApplicationContext context: Context,
            pluginManager: PluginManager,
            streamingManager: StreamingPluginManager,
            versionManager: PluginVersionManager
        ): PluginRollbackManager {
            return PluginRollbackManager(context, pluginManager, streamingManager, versionManager)
        }
        
        @Provides
        @Singleton
        fun provideVersionedPluginManager(
            @ApplicationContext context: Context,
            pluginManager: PluginManager,
            streamingManager: StreamingPluginManager,
            versionManager: PluginVersionManager,
            rollbackManager: PluginRollbackManager
        ): VersionedPluginManager {
            return VersionedPluginManager(
                context,
                pluginManager,
                streamingManager,
                versionManager,
                rollbackManager
            )
        }
    }
}

/**
 * Interface for versioned plugin management
 */
interface IVersionedPluginManager {
    suspend fun loadPluginWithVersion(
        pluginFile: java.io.File,
        version: PluginVersion
    ): Result<com.ble1st.connectias.plugin.sdk.PluginMetadata>
    
    suspend fun updatePlugin(
        pluginId: String,
        targetVersion: PluginVersion
    ): Result<kotlin.Unit>
    
    suspend fun rollbackPlugin(
        pluginId: String,
        targetVersion: String? = null,
        reason: String = "Manual rollback"
    ): Result<PluginVersion>
    
    fun getPluginVersion(pluginId: String): PluginVersion?
    fun getAvailableUpdates(): kotlinx.coroutines.flow.Flow<List<PluginVersionUpdate>>
    fun getVersionHistory(pluginId: String): List<PluginVersionHistory>
    fun canRollback(pluginId: String): Boolean
    fun getRollbackHistory(): kotlinx.coroutines.flow.Flow<List<RollbackEntry>>
    fun getRollbackStats(): RollbackStats
    suspend fun cleanup()
    suspend fun exportPluginWithVersion(pluginId: String): Result<java.io.File>
    suspend fun importPluginWithVersion(
        pluginFile: java.io.File,
        versionInfoFile: java.io.File? = null
    ): Result<com.ble1st.connectias.plugin.sdk.PluginMetadata>
}
