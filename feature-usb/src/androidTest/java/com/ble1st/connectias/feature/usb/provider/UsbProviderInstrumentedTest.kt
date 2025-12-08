package com.ble1st.connectias.feature.usb.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsbProviderInstrumentedTest {

    private lateinit var provider: UsbProvider

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        provider = UsbProvider(context)
    }

    @Test
    fun `enumerateDevices returns without exception`() = runBlocking {
        val result = provider.enumerateDevices()

        // On emulator likely empty, but should succeed
        assertTrue(result is UsbResult.Success || result is UsbResult.Failure)
        if (result is UsbResult.Success) {
            assertNotNull(result.data)
        }
    }
}
