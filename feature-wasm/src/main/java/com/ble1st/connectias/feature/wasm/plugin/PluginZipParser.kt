package com.ble1st.connectias.feature.wasm.plugin

import com.ble1st.connectias.feature.wasm.plugin.models.PluginMetadata
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for plugin ZIP files.
 */
@Singleton
class PluginZipParser @Inject constructor() {
    
    private val tag = "PluginZipParser"
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    /**
     * Extract and parse plugin ZIP file.
     * 
     * @param zipFile Path to plugin ZIP file
     * @return Parsed plugin data
     * @throws PluginParseException if parsing fails
     */
    fun parsePluginZip(zipFile: File): PluginZipData {
        Timber.d(tag, "Parsing plugin ZIP: ${zipFile.absolutePath}")
        
        if (!zipFile.exists()) {
            throw PluginParseException("ZIP file does not exist: ${zipFile.absolutePath}")
        }
        
        return try {
            FileInputStream(zipFile).use { stream ->
                parseFromStream(stream)
            }
        } catch (e: Exception) {
             // If it was already a PluginParseException, rethrow it
             if (e is PluginParseException) throw e
             Timber.e(e, "Failed to parse plugin ZIP file")
             throw PluginParseException("Failed to parse plugin ZIP file: ${e.message}", e)
        }
    }
    
    /**
     * Parse plugin ZIP from byte array.
     */
    fun parsePluginZip(zipBytes: ByteArray): PluginZipData {
        return try {
            java.io.ByteArrayInputStream(zipBytes).use { stream ->
                parseFromStream(stream)
            }
        } catch (e: Exception) {
             if (e is PluginParseException) throw e
             Timber.e(e, "Failed to parse plugin ZIP bytes")
             throw PluginParseException("Failed to parse plugin ZIP bytes: ${e.message}", e)
        }
    }

    private fun parseFromStream(inputStream: java.io.InputStream): PluginZipData {
        val metadata: PluginMetadata
        val wasmBytes: ByteArray
        val signature: String?
        
        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            val entries = mutableMapOf<String, ByteArray>()
            
            while (entry != null) {
                val name = entry.name
                val content = zip.readBytes()
                entries[name] = content
                entry = zip.nextEntry
            }
            
            // Parse plugin.json
            val pluginJsonBytes = entries["plugin.json"]
                ?: throw PluginParseException("plugin.json not found in ZIP")
            
            metadata = try {
                json.decodeFromString(PluginMetadata.serializer(), pluginJsonBytes.decodeToString())
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse plugin.json")
                throw PluginParseException("Invalid plugin.json: ${e.message}", e)
            }
            
            // Load WASM module
            val wasmFileName = metadata.entryPoint
            wasmBytes = entries[wasmFileName]
                ?: throw PluginParseException("WASM file '$wasmFileName' not found in ZIP")
            
            // Load signature (optional)
            signature = entries["META-INF/SIGNATURE.RSA"]?.decodeToString()
            
            Timber.d(tag, "Parsed plugin: ${metadata.id} v${metadata.version}")
        }
        
        return PluginZipData(
            metadata = metadata,
            wasmBytes = wasmBytes,
            signature = signature
        )
    }
}

/**
 * Data class for parsed plugin ZIP contents.
 */
data class PluginZipData(
    val metadata: PluginMetadata,
    val wasmBytes: ByteArray,
    val signature: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as PluginZipData
        
        if (metadata != other.metadata) return false
        if (!wasmBytes.contentEquals(other.wasmBytes)) return false
        if (signature != other.signature) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + wasmBytes.contentHashCode()
        result = 31 * result + (signature?.hashCode() ?: 0)
        return result
    }
}

/**
 * Exception thrown when plugin parsing fails.
 */
class PluginParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

