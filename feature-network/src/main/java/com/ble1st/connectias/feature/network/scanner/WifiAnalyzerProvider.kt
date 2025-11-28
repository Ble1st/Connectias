package com.ble1st.connectias.feature.network.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.annotation.RequiresPermission
import com.ble1st.connectias.feature.network.models.WifiNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for WiFi channel analysis.
 * Analyzes WiFi networks by channel, signal strength, and overlap.
 */
@Singleton
class WifiAnalyzerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    /**
     * WiFi frequency bands.
     */
    enum class WifiBand(val frequencies: IntRange) {
        BAND_2_4_GHZ(2400..2499),
        BAND_5_GHZ(5000..5825)
    }

    /**
     * Analyzes WiFi networks and provides channel information.
     * 
     * @param wifiNetworks List of WiFi networks to analyze
     * @return List of WiFiChannelInfo with channel analysis
     */
    suspend fun analyzeChannels(wifiNetworks: List<WifiNetwork>): List<WifiChannelInfo> = withContext(Dispatchers.Default) {
        val channelMap = mutableMapOf<Int, MutableList<WifiNetwork>>()
        
        wifiNetworks.forEach { network ->
            val channel = frequencyToChannel(network.frequency)
            if (channel != null) {
                channelMap.getOrPut(channel) { mutableListOf() }.add(network)
            }
        }
        
        channelMap.map { (channel, networks) ->
            val avgSignal = networks.map { it.signalStrength }.average().toInt()
            val maxSignal = networks.maxOfOrNull { it.signalStrength } ?: 0
            val minSignal = networks.minOfOrNull { it.signalStrength } ?: 0
            
            WifiChannelInfo(
                channel = channel,
                frequency = networks.first().frequency,
                networkCount = networks.size,
                avgSignalStrength = avgSignal,
                maxSignalStrength = maxSignal,
                minSignalStrength = minSignal,
                networks = networks
            )
        }.sortedBy { it.channel }
    }

    /**
     * Detects channel overlap issues.
     * Channels that are too close together can interfere with each other.
     */
    suspend fun detectChannelOverlap(channelInfos: List<WifiChannelInfo>): List<ChannelOverlap> = withContext(Dispatchers.Default) {
        val overlaps = mutableListOf<ChannelOverlap>()
        
        for (i in channelInfos.indices) {
            for (j in (i + 1) until channelInfos.size) {
                val channel1 = channelInfos[i]
                val channel2 = channelInfos[j]
                
                val channelDiff = kotlin.math.abs(channel1.channel - channel2.channel)
                if (channelDiff <= 5 && channel1.channel != channel2.channel) {
                    overlaps.add(
                        ChannelOverlap(
                            channel1 = channel1.channel,
                            channel2 = channel2.channel,
                            severity = when {
                                channelDiff <= 2 -> OverlapSeverity.HIGH
                                channelDiff <= 4 -> OverlapSeverity.MEDIUM
                                else -> OverlapSeverity.LOW
                            }
                        )
                    )
                }
            }
        }
        
        overlaps
    }

    /**
     * Recommends best channel based on analysis.
     * 
     * @param band The WiFi band to analyze (2.4 GHz or 5 GHz)
     * @param channelInfos Current channel usage
     * @return Recommended channel number
     */
    suspend fun recommendBestChannel(
        band: WifiBand,
        channelInfos: List<WifiChannelInfo>
    ): Int? = withContext(Dispatchers.Default) {
        val availableChannels = when (band) {
            WifiBand.BAND_2_4_GHZ -> listOf(1, 6, 11) // Non-overlapping channels for 2.4 GHz
            WifiBand.BAND_5_GHZ -> (36..165 step 4).toList() // Common 5 GHz channels
        }
        
        val usedChannels = channelInfos.map { it.channel }.toSet()
        val freeChannels = availableChannels.filter { it !in usedChannels }
        
        if (freeChannels.isNotEmpty()) {
            // Return first free non-overlapping channel
            freeChannels.firstOrNull()
        } else {
            // If all channels are used, recommend channel with lowest usage
            channelInfos.minByOrNull { it.networkCount }?.channel
        }
    }

    /**
     * Converts WiFi frequency (MHz) to channel number.
     */
    private fun frequencyToChannel(frequency: Int): Int? {
        return when {
            frequency in 2412..2484 -> {
                // 2.4 GHz band
                ((frequency - 2412) / 5) + 1
            }
            frequency in 5000..5825 -> {
                // 5 GHz band
                when {
                    frequency <= 5080 -> ((frequency - 5000) / 5)
                    frequency <= 5320 -> ((frequency - 5000) / 5) - 1
                    frequency <= 5500 -> ((frequency - 5000) / 5) - 2
                    frequency <= 5700 -> ((frequency - 5000) / 5) - 3
                    else -> ((frequency - 5000) / 5) - 4
                }
            }
            else -> null
        }
    }
}

/**
 * WiFi channel information.
 */
data class WifiChannelInfo(
    val channel: Int,
    val frequency: Int,
    val networkCount: Int,
    val avgSignalStrength: Int,
    val maxSignalStrength: Int,
    val minSignalStrength: Int,
    val networks: List<WifiNetwork>
)

/**
 * Channel overlap information.
 */
data class ChannelOverlap(
    val channel1: Int,
    val channel2: Int,
    val severity: OverlapSeverity
)

/**
 * Overlap severity levels.
 */
enum class OverlapSeverity {
    LOW,
    MEDIUM,
    HIGH
}

