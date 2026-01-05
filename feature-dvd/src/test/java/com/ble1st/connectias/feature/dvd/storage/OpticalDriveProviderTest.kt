package com.ble1st.connectias.feature.dvd.storage

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OpticalDriveProviderTest {

    private lateinit var context: Context
    private lateinit var usbManager: UsbManager
    private lateinit var provider: OpticalDriveProvider

    @Before
    fun setup() {
        context = mockk()
        usbManager = mockk()
        every { context.getSystemService(Context.USB_SERVICE) } returns usbManager
        provider = OpticalDriveProvider(context)
    }

    @Test
    fun `detectOpticalDrive returns null when device list is empty`() = runTest {
        every { usbManager.deviceList } returns hashMapOf()

        val result = provider.detectOpticalDrive()

        assertNull(result)
    }

    @Test
    fun `detectOpticalDrive ignores non-mass-storage devices`() = runTest {
        val device = mockk<UsbDevice>()
        // Device class 0 usually means look at interfaces, but here we set it to something else non-8
        every { device.deviceClass } returns 1 
        every { device.interfaceCount } returns 0
        
        every { usbManager.deviceList } returns hashMapOf("device1" to device)

        val result = provider.detectOpticalDrive()

        assertNull(result)
    }
    
    @Test
    fun `detectOpticalDrive checks interfaces if device class is 0`() = runTest {
        val device = mockk<UsbDevice>()
        val iface = mockk<UsbInterface>()
        
        every { device.deviceClass } returns 0
        every { device.interfaceCount } returns 1
        every { device.getInterface(0) } returns iface
        every { iface.interfaceClass } returns 3 // HID, not Mass Storage (8)
        
        every { usbManager.deviceList } returns hashMapOf("device1" to device)

        val result = provider.detectOpticalDrive()

        assertNull(result)
    }
}
