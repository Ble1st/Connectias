package com.ble1st.connectias.feature.ntp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NtpRepository @Inject constructor(
    private val ntpDao: NtpDao
) {
    private val rustClient = try {
        RustNtpClient()
    } catch (e: Exception) {
        null // Fallback to Kotlin if Rust not available
    }

    val history: Flow<List<NtpHistoryEntity>> = ntpDao.getAllHistory()

    suspend fun clearHistory() = ntpDao.clearAll()
    suspend fun deleteHistoryItem(item: NtpHistoryEntity) = ntpDao.delete(item)

    suspend fun queryOffset(server: String): NtpResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster)
        if (rustClient != null) {
            try {
                Timber.i("🔴 [NtpRepository] Using RUST implementation for NTP query")
                val rustStartTime = System.currentTimeMillis()
                
                val result = rustClient.queryOffset(server)
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [NtpRepository] RUST NTP query completed in ${rustDuration}ms")
                Timber.d("📊 [NtpRepository] Total time (including overhead): ${totalDuration}ms")
                
                // Save to history
                if (result.error == null) {
                    ntpDao.insert(
                        NtpHistoryEntity(
                            server = result.server,
                            offsetMs = result.offsetMs,
                            delayMs = result.delayMs,
                            stratum = result.stratum,
                            referenceId = result.referenceId,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
                return@withContext result
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [NtpRepository] RUST NTP query failed after ${rustDuration}ms, falling back to Kotlin")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [NtpRepository] Rust client not available, using Kotlin")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [NtpRepository] Using KOTLIN implementation for NTP query")
        val kotlinStartTime = System.currentTimeMillis()
        
        val client = NTPUDPClient()
        client.defaultTimeout = 3000
        val result = try {
            val address = InetAddress.getByName(server)
            val info: TimeInfo = client.getTime(address)
            info.computeDetails()
            
            val offset = info.offset ?: 0L
            val delay = info.delay ?: 0L
            val message = info.message
            val stratum = message.stratum
            // Reference ID is often an int or bytes, let's convert to readable string if possible
            val refId = if (stratum <= 1) {
                // For Stratum 1, it's a 4-char string (e.g. "GPS", "PPS")
                val refIdInt = message.referenceId
                val chars = ByteArray(4)
                chars[0] = ((refIdInt shr 24) and 0xFF).toByte()
                chars[1] = ((refIdInt shr 16) and 0xFF).toByte()
                chars[2] = ((refIdInt shr 8) and 0xFF).toByte()
                chars[3] = (refIdInt and 0xFF).toByte()
                String(chars).trim { it <= ' ' } // Remove nulls
            } else {
                // For secondary servers, it's the IP address of the reference
                // Commons-Net might return it as int. Let's just use message.referenceIdentifier (from newer libs) or manually format
                // message.referenceId is an int.
                val refIdInt = message.referenceId
                "${(refIdInt shr 24) and 0xFF}.${(refIdInt shr 16) and 0xFF}.${(refIdInt shr 8) and 0xFF}.${refIdInt and 0xFF}"
            }

            NtpResult(
                server = server,
                offsetMs = offset,
                delayMs = delay,
                stratum = stratum,
                referenceId = refId,
                error = null
            )
        } catch (t: Throwable) {
            Timber.e(t, "NTP query failed")
            NtpResult(server = server, offsetMs = 0, delayMs = 0, error = t.message ?: "NTP error")
        } finally {
            client.close()
        }
        
        val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
        val totalDuration = System.currentTimeMillis() - startTime
        
        Timber.i("✅ [NtpRepository] KOTLIN NTP query completed in ${kotlinDuration}ms")
        Timber.d("📊 [NtpRepository] Total time (including overhead): ${totalDuration}ms")
        
        // Save to history
        if (result.error == null) {
            ntpDao.insert(
                NtpHistoryEntity(
                    server = result.server,
                    offsetMs = result.offsetMs,
                    delayMs = result.delayMs,
                    stratum = result.stratum,
                    referenceId = result.referenceId,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        
        return@withContext result
    }
}

