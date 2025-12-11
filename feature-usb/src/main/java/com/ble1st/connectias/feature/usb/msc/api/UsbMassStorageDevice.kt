package com.ble1st.connectias.feature.usb.msc.api

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.usb.msc.fs.FatFileSystemFactory
import com.ble1st.connectias.feature.usb.msc.storage.BlockDevice
import com.ble1st.connectias.feature.usb.msc.storage.PartitionBlockDevice
import com.ble1st.connectias.feature.usb.msc.storage.PartitionTable
import com.ble1st.connectias.feature.usb.msc.transport.UsbTransport

/**
 * Drop-in replacement for libaums' UsbMassStorageDevice (subset).
 */
class UsbMassStorageDevice(
    val usbDevice: UsbDevice,
    private val usbInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint,
    private val usbManager: UsbManager
) {
    private var connection: UsbDeviceConnection? = null
    var partitions: List<Partition> = emptyList()
        private set

    fun init() {
        val conn = usbManager.openDevice(usbDevice)
            ?: throw IllegalStateException("Cannot open USB device")
        connection = conn
        require(conn.claimInterface(usbInterface, true)) { "Could not claim interface" }
        val transport = UsbTransport(conn, inEndpoint, outEndpoint)
        val capacity = transport.readCapacity()
        val blockDevice = BlockDevice(
            transport = transport,
            blockSize = capacity.blockSize,
            blockCount = capacity.blockCount
        )
        val entries = PartitionTable.parse(blockDevice)
        partitions = entries.map { entry ->
            val pbd = PartitionBlockDevice(blockDevice, entry.startLba, entry.totalLba)
            val fs = FatFileSystemFactory.create(pbd)
            Partition(
                fileSystem = FileSystem(
                    rootDirectory = FatUsbDirectory(fs, fs.rootDirectory),
                    volumeLabel = null,
                    capacity = entry.totalLba * blockDevice.blockSize,
                    freeSpace = 0 // not computed
                )
            )
        }
    }

    fun close() {
        try {
            connection?.releaseInterface(usbInterface)
        } catch (_: Exception) {
        }
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
    }

    companion object {
        fun getMassStorageDevices(context: Context): List<UsbMassStorageDevice> {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val result = mutableListOf<UsbMassStorageDevice>()
            for (device in manager.deviceList.values) {
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        val endpoints = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
                        val inEp = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
                        val outEp = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
                        if (inEp != null && outEp != null) {
                            result.add(
                                UsbMassStorageDevice(
                                    usbDevice = device,
                                    usbInterface = intf,
                                    inEndpoint = inEp,
                                    outEndpoint = outEp,
                                    usbManager = manager
                                )
                            )
                            break
                        }
                    }
                }
            }
            return result
        }
    }
}
