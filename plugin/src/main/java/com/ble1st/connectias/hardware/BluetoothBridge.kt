package com.ble1st.connectias.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bluetooth Bridge for isolated process Bluetooth access
 * 
 * Provides Bluetooth connectivity to plugins running in isolated sandbox.
 * Supports SPP (Serial Port Profile) for classic Bluetooth devices.
 * 
 * ARCHITECTURE:
 * - Uses BluetoothAdapter for device discovery and pairing
 * - SPP UUID for serial communication
 * - Returns socket as ParcelFileDescriptor
 * 
 * SECURITY:
 * - Requires BLUETOOTH_CONNECT permission (Android 12+)
 * - Only allows connecting to already paired devices
 * - Auto-cleanup on plugin unload
 * 
 * @since 2.0.0
 */
class BluetoothBridge(private val context: Context) {
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val activeConnections = ConcurrentHashMap<String, BluetoothSocket>()
    
    // Standard SPP UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    /**
     * Get paired Bluetooth devices
     * 
     * @return List of device addresses (MAC addresses)
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<String> {
        return try {
            if (bluetoothAdapter == null) {
                Timber.w("[BLUETOOTH BRIDGE] Bluetooth not available")
                return emptyList()
            }
            
            if (!bluetoothAdapter.isEnabled) {
                Timber.w("[BLUETOOTH BRIDGE] Bluetooth not enabled")
                return emptyList()
            }
            
            val devices = bluetoothAdapter.bondedDevices.map { it.address }
            Timber.d("[BLUETOOTH BRIDGE] Found ${devices.size} paired devices")
            devices
            
        } catch (e: SecurityException) {
            Timber.e(e, "[BLUETOOTH BRIDGE] Permission denied")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[BLUETOOTH BRIDGE] Failed to get devices")
            emptyList()
        }
    }
    
    /**
     * Connect to Bluetooth device
     * 
     * @param deviceAddress Device MAC address
     * @return HardwareResponseParcel with socket as ParcelFileDescriptor
     */
    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String): HardwareResponseParcel {
        return try {
            if (bluetoothAdapter == null) {
                return HardwareResponseParcel.failure("Bluetooth not available")
            }
            
            if (!bluetoothAdapter.isEnabled) {
                return HardwareResponseParcel.failure("Bluetooth not enabled")
            }
            
            Timber.d("[BLUETOOTH BRIDGE] Connecting to $deviceAddress")
            
            // Get device
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            
            // Check if paired
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                return HardwareResponseParcel.failure("Device not paired: $deviceAddress")
            }
            
            // Create socket
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            
            // Cancel discovery to improve connection speed
            bluetoothAdapter.cancelDiscovery()
            
            // Connect (blocking)
            socket.connect()
            
            // Store active connection
            activeConnections[deviceAddress] = socket
            
            // Convert to ParcelFileDescriptor
            // BluetoothSocket is not a java.net.Socket, use reflection to get FileDescriptor
            val fd = try {
                val method = socket.javaClass.getDeclaredMethod("getFileDescriptor")
                method.isAccessible = true
                val fileDescriptor = method.invoke(socket) as java.io.FileDescriptor
                ParcelFileDescriptor.dup(fileDescriptor)
            } catch (e: Exception) {
                Timber.w(e, "[BLUETOOTH BRIDGE] Failed to get FD via reflection, trying adoptFd")
                // Fallback: Create pipe and background thread to copy data
                // This is a simplified approach - real implementation would be more robust
                throw UnsupportedOperationException("BluetoothSocket to ParcelFileDescriptor not supported yet")
            }
            
            Timber.i("[BLUETOOTH BRIDGE] Connected to $deviceAddress")
            
            HardwareResponseParcel.success(
                fileDescriptor = fd,
                metadata = mapOf(
                    "device" to deviceAddress,
                    "name" to (device.name ?: "Unknown"),
                    "type" to "bluetooth_spp"
                )
            )
            
        } catch (e: SecurityException) {
            Timber.e(e, "[BLUETOOTH BRIDGE] Permission denied")
            HardwareResponseParcel.failure("Permission denied: BLUETOOTH_CONNECT")
        } catch (e: Exception) {
            Timber.e(e, "[BLUETOOTH BRIDGE] Connection failed: $deviceAddress")
            HardwareResponseParcel.failure(e)
        }
    }
    
    /**
     * Disconnect from Bluetooth device
     * 
     * @param deviceAddress Device MAC address
     */
    fun disconnect(deviceAddress: String) {
        try {
            activeConnections.remove(deviceAddress)?.let { socket ->
                socket.close()
                Timber.i("[BLUETOOTH BRIDGE] Disconnected from $deviceAddress")
            }
        } catch (e: Exception) {
            Timber.e(e, "[BLUETOOTH BRIDGE] Disconnect failed: $deviceAddress")
        }
    }
    
    /**
     * Cleanup Bluetooth resources
     */
    fun cleanup() {
        try {
            Timber.i("[BLUETOOTH BRIDGE] Cleanup started")
            
            // Close all active connections
            activeConnections.values.forEach { socket ->
                try {
                    socket.close()
                } catch (e: Exception) {
                    Timber.w(e, "[BLUETOOTH BRIDGE] Socket close error")
                }
            }
            activeConnections.clear()
            
            Timber.i("[BLUETOOTH BRIDGE] Cleanup completed")
            
        } catch (e: Exception) {
            Timber.e(e, "[BLUETOOTH BRIDGE] Cleanup error")
        }
    }
}
