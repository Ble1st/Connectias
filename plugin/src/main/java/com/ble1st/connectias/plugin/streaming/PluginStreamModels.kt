package com.ble1st.connectias.plugin.streaming

import java.io.File

/**
 * Data models for plugin streaming system
 */

/**
 * Represents a chunk of plugin data
 */
data class PluginStreamChunk(
    val index: Int,
    val data: ByteArray,
    val checksum: String,
    val isLast: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PluginStreamChunk

        if (index != other.index) return false
        if (!data.contentEquals(other.data)) return false
        if (checksum != other.checksum) return false
        if (isLast != other.isLast) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + data.contentHashCode()
        result = 31 * result + checksum.hashCode()
        result = 31 * result + isLast.hashCode()
        return result
    }
}

/**
 * Metadata for a plugin stream
 */
data class PluginStreamMetadata(
    val totalChunks: Int,
    val chunkSize: Int,
    val totalSize: Long,
    val compression: CompressionType,
    val encryption: EncryptionInfo?
)

/**
 * Encryption information for chunks
 */
data class EncryptionInfo(
    val algorithm: String,
    val keySize: Int,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptionInfo

        if (algorithm != other.algorithm) return false
        if (keySize != other.keySize) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + keySize
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

enum class CompressionType {
    NONE,
    GZIP,
    ZSTD,
    LZ4
}

data class PluginStream(
    val metadata: PluginStreamMetadata,
    val chunks: List<PluginStreamChunk>,
    val pluginId: String,
    val version: String
)

data class PluginPackage(
    val pluginFile: File,
    val metadata: com.ble1st.connectias.plugin.sdk.PluginMetadata,
    val stream: PluginStream?
)

data class CachedPlugin(
    val pluginId: String,
    val version: String,
    val chunks: Map<Int, ByteArray>,
    val metadata: PluginStreamMetadata,
    val cachedAt: Long,
    val lastAccessed: Long
)

data class CacheOptimizationResult(
    val freedSpace: Long,
    val removedPlugins: List<String>,
    val optimizationTime: Long
)

enum class CachePriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
