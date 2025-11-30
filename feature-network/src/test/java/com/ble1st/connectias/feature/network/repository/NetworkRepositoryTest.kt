package com.ble1st.connectias.feature.network.repository

import com.ble1st.connectias.feature.network.models.NetworkAnalysis
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.NetworkResult
import com.ble1st.connectias.feature.network.models.ParcelableList
import com.ble1st.connectias.feature.network.models.WifiNetwork
import com.ble1st.connectias.feature.network.provider.LanScannerProvider
import com.ble1st.connectias.feature.network.provider.NetworkAnalysisProvider
import com.ble1st.connectias.feature.network.provider.WifiScannerProvider
import com.ble1st.connectias.core.models.ConnectionType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkRepositoryTest {

    private lateinit var wifiScannerProvider: WifiScannerProvider
    private lateinit var lanScannerProvider: LanScannerProvider
    private lateinit var networkAnalysisProvider: NetworkAnalysisProvider
    private lateinit var repository: NetworkRepository

    @Before
    fun setup() {
        wifiScannerProvider = mockk()
        lanScannerProvider = mockk()
        networkAnalysisProvider = mockk()
        repository = NetworkRepository(
            wifiScannerProvider,
            lanScannerProvider,
            networkAnalysisProvider
        )
    }

    @Test
    fun `getWifiNetworks returns cached result on second call`() = runTest {
        val networks = listOf(
            WifiNetwork("TestNetwork", "00:00:00:00:00:00", -50, 2400, com.ble1st.connectias.feature.network.models.EncryptionType.WPA2, "[WPA2]")
        )
        val parcelableList = ParcelableList(networks)
        val expectedResult = NetworkResult.Success(parcelableList)

        coEvery { wifiScannerProvider.scanWifiNetworks() } returns expectedResult

        val firstResult = repository.getWifiNetworks()
        val secondResult = repository.getWifiNetworks()

        assertTrue(firstResult is NetworkResult.Success)
        assertTrue(secondResult is NetworkResult.Success)
        assertEquals(firstResult, secondResult)
    }

    @Test
    fun `getWifiNetworks returns error when permission denied`() = runTest {
        val errorResult = NetworkResult.Error(
            message = "Location permission is required",
            errorType = com.ble1st.connectias.feature.network.models.ErrorType.PermissionDenied
        )

        coEvery { wifiScannerProvider.scanWifiNetworks() } returns errorResult

        val result = repository.getWifiNetworks()

        assertTrue(result is NetworkResult.Error)
        assertEquals(com.ble1st.connectias.feature.network.models.ErrorType.PermissionDenied, (result as NetworkResult.Error).errorType)
    }

    @Test
    fun `getLocalNetworkDevices returns error when gateway unavailable`() = runTest {
        val errorResult = NetworkResult.Error(
            message = "Network gateway is unavailable",
            errorType = com.ble1st.connectias.feature.network.models.ErrorType.ConfigurationUnavailable
        )

        coEvery { lanScannerProvider.scanLocalNetwork() } returns errorResult

        val result = repository.getLocalNetworkDevices()

        assertTrue(result is NetworkResult.Error)
        assertEquals(com.ble1st.connectias.feature.network.models.ErrorType.ConfigurationUnavailable, (result as NetworkResult.Error).errorType)
    }

    @Test
    fun `refreshWifiNetworks invalidates cache and fetches fresh data`() = runTest {
        val firstNetworks = listOf(WifiNetwork("Network1", "00:00:00:00:00:01", -50, 2400, com.ble1st.connectias.feature.network.models.EncryptionType.WPA2, "[WPA2]"))
        val secondNetworks = listOf(WifiNetwork("Network2", "00:00:00:00:00:02", -60, 5000, com.ble1st.connectias.feature.network.models.EncryptionType.WPA3, "[WPA3]"))

        coEvery { wifiScannerProvider.scanWifiNetworks() } returnsMany listOf(
            NetworkResult.Success(ParcelableList(firstNetworks)),
            NetworkResult.Success(ParcelableList(secondNetworks))
        )

        val firstResult = repository.getWifiNetworks()
        val refreshResult = repository.refreshWifiNetworks()

        assertTrue(firstResult is NetworkResult.Success)
        assertTrue(refreshResult is NetworkResult.Success)
        val firstData = (firstResult as NetworkResult.Success).data.items
        val refreshData = (refreshResult as NetworkResult.Success).data.items
        assertEquals(1, firstData.size)
        assertEquals(1, refreshData.size)
        assertEquals("Network1", firstData[0].ssid)
        assertEquals("Network2", refreshData[0].ssid)
    }

    @Test
    fun `getNetworkAnalysis returns cached result`() = runTest {
        val analysis = NetworkAnalysis(
            isConnected = true,
            dnsServers = listOf("8.8.8.8"),
            gateway = "192.168.1.1",
            networkSpeed = null,
            connectionType = ConnectionType.WIFI
        )

        coEvery { networkAnalysisProvider.getNetworkAnalysis() } returns analysis

        val firstResult = repository.getNetworkAnalysis()
        val secondResult = repository.getNetworkAnalysis()

        assertTrue(firstResult is NetworkResult.Success)
        assertTrue(secondResult is NetworkResult.Success)
        assertEquals(firstResult, secondResult)
    }
}

