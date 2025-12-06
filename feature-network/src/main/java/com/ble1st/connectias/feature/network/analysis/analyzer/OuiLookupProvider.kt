package com.ble1st.connectias.feature.network.analysis.analyzer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import com.ble1st.connectias.feature.network.analysis.models.MacAddressInfo
import com.ble1st.connectias.feature.network.analysis.models.OuiEntry
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for OUI (Organizationally Unique Identifier) lookups.
 * Provides MAC address to manufacturer mapping using embedded OUI database.
 */
@Singleton
class OuiLookupProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    @Volatile
    private var ouiDatabase: List<OuiEntry>? = null
    
    private val databaseMutex = Mutex()

    /**
     * Loads the OUI database from resources.
     * This is a lazy operation that loads the database on first access.
     */
    private suspend fun loadOuiDatabase(): List<OuiEntry> = withContext(Dispatchers.IO) {
        databaseMutex.withLock {
            if (ouiDatabase != null) {
                return@withContext ouiDatabase!!
            }

            try {
                val resourceId = context.resources.getIdentifier("oui_database", "raw", context.packageName)
                if (resourceId == 0) {
                    Timber.w("OUI database resource not found")
                    return@withContext emptyList()
                }
                val inputStream: InputStream = context.resources.openRawResource(resourceId)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val entries = json.decodeFromString<List<OuiEntry>>(jsonString)
                ouiDatabase = entries
                Timber.d("Loaded ${entries.size} OUI entries from database")
                entries
            } catch (e: Exception) {
                Timber.e(e, "Failed to load OUI database")
                emptyList()
            }
        }
    }

    /**
     * Normalizes a MAC address to a standard format (uppercase, colons).
     */
    private fun normalizeMacAddress(macAddress: String): String {
        // Remove common separators and convert to uppercase
        val cleaned = macAddress.replace(Regex("[:-]"), "").uppercase()
        
        // Validate format (should be 12 hex characters)
        if (!cleaned.matches(Regex("[0-9A-F]{12}"))) {
            return macAddress
        }
        
        // Format with colons: XX:XX:XX:XX:XX:XX
        return cleaned.chunked(2).joinToString(":")
    }

    /**
     * Formats a MAC address for display.
     */
    private fun formatMacAddress(macAddress: String): String {
        val normalized = normalizeMacAddress(macAddress)
        return if (normalized.length == 17) normalized else macAddress
    }

    /**
     * Extracts the OUI prefix (first 3 octets) from a MAC address.
     */
    private fun extractOuiPrefix(macAddress: String): String? {
        val normalized = normalizeMacAddress(macAddress)
        if (normalized.length != 17) return null
        
        // Extract first 3 octets (8 characters)
        return normalized.substring(0, 8).replace(":", "")
    }

    /**
     * Looks up manufacturer information for a MAC address.
     * 
     * @param macAddress The MAC address to look up
     * @return MacAddressInfo with manufacturer information, or null if not found
     */
    suspend fun lookupMacAddress(macAddress: String): MacAddressInfo {
        val normalized = normalizeMacAddress(macAddress)
        val isValid = normalized.length == 17 && normalized.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))
        
        if (!isValid) {
            return MacAddressInfo(
                macAddress = macAddress,
                manufacturer = null,
                isValid = false,
                formattedAddress = formatMacAddress(macAddress)
            )
        }

        val ouiPrefix = extractOuiPrefix(normalized)
        if (ouiPrefix == null) {
            return MacAddressInfo(
                macAddress = macAddress,
                manufacturer = null,
                isValid = true,
                formattedAddress = formatMacAddress(macAddress)
            )
        }

        val database = loadOuiDatabase()
        val entry = database.find { it.prefix.equals(ouiPrefix, ignoreCase = true) }
        
        return MacAddressInfo(
            macAddress = macAddress,
            manufacturer = entry?.manufacturer,
            isValid = true,
            formattedAddress = formatMacAddress(macAddress)
        )
    }

    /**
     * Validates if a MAC address has a valid format.
     */
    fun isValidMacAddress(macAddress: String): Boolean {
        val normalized = normalizeMacAddress(macAddress)
        return normalized.length == 17 && normalized.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))
    }
}
