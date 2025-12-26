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

    val history: Flow<List<NtpHistoryEntity>> = ntpDao.getAllHistory()

    suspend fun clearHistory() = ntpDao.clearAll()
    suspend fun deleteHistoryItem(item: NtpHistoryEntity) = ntpDao.delete(item)

    suspend fun queryOffset(server: String): NtpResult = withContext(Dispatchers.IO) {
        val client = NTPUDPClient()
        client.defaultTimeout = 3000
        return@withContext try {
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

            val result = NtpResult(
                server = server,
                offsetMs = offset,
                delayMs = delay,
                stratum = stratum,
                referenceId = refId,
                error = null
            )
            
            ntpDao.insert(
                NtpHistoryEntity(
                    server = server,
                    offsetMs = offset,
                    delayMs = delay,
                    stratum = stratum,
                    referenceId = refId,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            result
        } catch (t: Throwable) {
            Timber.e(t, "NTP query failed")
            NtpResult(server = server, offsetMs = 0, delayMs = 0, error = t.message ?: "NTP error")
        } finally {
            client.close()
        }
    }
}

