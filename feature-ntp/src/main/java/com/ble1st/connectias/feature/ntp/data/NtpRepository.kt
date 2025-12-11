package com.ble1st.connectias.feature.ntp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NtpRepository @Inject constructor() {

    suspend fun queryOffset(server: String): NtpResult = withContext(Dispatchers.IO) {
        val client = NTPUDPClient()
        client.defaultTimeout = 3000
        return@withContext try {
            val address = InetAddress.getByName(server)
            val info: TimeInfo = client.getTime(address)
            info.computeDetails()
            val offset = info.offset
            val delay = info.delay
            NtpResult(
                server = server,
                offsetMs = offset ?: 0L,
                delayMs = delay ?: 0L,
                error = null
            )
        } catch (t: Throwable) {
            Timber.e(t, "NTP query failed")
            NtpResult(server = server, offsetMs = 0, delayMs = 0, error = t.message ?: "NTP error")
        } finally {
            client.close()
        }
    }
}
