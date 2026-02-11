package com.ble1st.connectias.feature.network.ui

import com.ble1st.connectias.feature.network.model.PortPresets
import com.ble1st.connectias.feature.network.network.NetworkScanner
import com.ble1st.connectias.feature.network.network.SpeedTestManager
import com.ble1st.connectias.feature.network.port.PortScanner
import com.ble1st.connectias.feature.network.ssl.SslScanner
import com.ble1st.connectias.feature.network.wifi.WifiScanner
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkToolsViewModelTest {

    @MockK
    lateinit var wifiScanner: WifiScanner
    @MockK
    lateinit var networkScanner: NetworkScanner
    @MockK
    lateinit var portScanner: PortScanner
    @MockK
    lateinit var sslScanner: SslScanner
    @MockK
    lateinit var speedTestManager: SpeedTestManager

    private lateinit var viewModel: NetworkToolsViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = NetworkToolsViewModel(
            wifiScanner,
            networkScanner,
            portScanner,
            sslScanner,
            speedTestManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.state.value
        assertEquals(NetworkToolsTab.WIFI, state.activeTab)
        assertNotNull(state.portState.selectedPreset)
        assertEquals(PortPresets.presets.firstOrNull(), state.portState.selectedPreset)
    }

    @Test
    fun `setActiveTab updates state`() = runTest {
        viewModel.setActiveTab(NetworkToolsTab.LAN)
        assertEquals(NetworkToolsTab.LAN, viewModel.state.value.activeTab)
    }

    @Test
    fun `updatePortTarget updates target`() = runTest {
        val target = "192.168.1.1"
        viewModel.updatePortTarget(target)
        assertEquals(target, viewModel.state.value.portState.target)
    }

    @Test
    fun `onWifiPermission updates permission state`() = runTest {
        viewModel.onWifiPermission(true)
        assertEquals(true, viewModel.state.value.wifiState.permissionGranted)
        
        viewModel.onWifiPermission(false)
        assertEquals(false, viewModel.state.value.wifiState.permissionGranted)
    }
}
