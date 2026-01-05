package com.ble1st.connectias.feature.bluetooth

import android.bluetooth.BluetoothAdapter
import com.ble1st.connectias.feature.bluetooth.data.BluetoothScanner
import com.ble1st.connectias.feature.bluetooth.model.DiscoveredDevice
import com.ble1st.connectias.feature.bluetooth.ui.BluetoothScannerViewModel
import com.ble1st.connectias.feature.bluetooth.ui.model.PermissionStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothScannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starting scan with permission updates devices`() = runTest {
        val devices = listOf(
            DiscoveredDevice(name = "A", address = "00:11:22", rssi = -50),
            DiscoveredDevice(name = "B", address = "11:22:33", rssi = -70)
        )
        val scanner = fakeScanner(flow { emit(devices) })
        val adapter = mockk<BluetoothAdapter>(relaxed = true).also {
            every { it.isEnabled } returns true
        }
        val viewModel = BluetoothScannerViewModel(scanner, adapter, dispatcher)

        viewModel.onPermissionStatus(PermissionStatus.Granted)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isScanning)
        assertEquals(2, state.devices.size)
        assertEquals("A", state.devices.first().title)
    }

    @Test
    fun `start scan without permission requests rationale`() = runTest {
        val scanner = fakeScanner(flow { emit(emptyList()) })
        val adapter = mockk<BluetoothAdapter>(relaxed = true).also {
            every { it.isEnabled } returns true
        }
        val viewModel = BluetoothScannerViewModel(scanner, adapter, dispatcher)

        viewModel.startScanning()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PermissionStatus.Rationale, state.permissionStatus)
        assertTrue(state.devices.isEmpty())
    }

    private fun fakeScanner(flow: Flow<List<DiscoveredDevice>>): BluetoothScanner {
        return object : BluetoothScanner {
            override fun scan(): Flow<List<DiscoveredDevice>> = flow
        }
    }
}
