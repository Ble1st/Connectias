package com.ble1st.connectias.feature.bluetooth.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ble1st.connectias.feature.bluetooth.model.DiscoveredDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface BluetoothScanner {
    fun scan(): Flow<List<DiscoveredDevice>>
}

@Singleton
class BluetoothScannerImpl @Inject constructor(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) : BluetoothScanner {

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<DiscoveredDevice>> = callbackFlow {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Timber.w("Bluetooth adapter is null; cannot start scan")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        if (!adapter.isEnabled) {
            Timber.w("Bluetooth adapter disabled; aborting scan")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        if (!hasScanPermission()) {
            Timber.w("Bluetooth scan permission missing; aborting scan")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val discovered = linkedMapOf<String, DiscoveredDevice>()
        val leScanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("Bluetooth scan failed with code: $errorCode")
                trySend(emptyList())
            }

            private fun handleResult(result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                val model = DiscoveredDevice(
                    name = device.name ?: result.scanRecord?.deviceName,
                    address = address,
                    rssi = result.rssi,
                    lastSeen = System.currentTimeMillis()
                )
                discovered[address] = model
                trySend(discovered.values.sortedByDescending { it.rssi })
            }
        }

        Timber.d("Starting Bluetooth LE scan")
        try {
            leScanner?.startScan(callback)
        } catch (sec: SecurityException) {
            Timber.e(sec, "SecurityException while starting Bluetooth scan")
            trySend(emptyList())
            close(sec)
            return@callbackFlow
        } catch (ex: Exception) {
            Timber.e(ex, "Unexpected error while starting Bluetooth scan")
            trySend(emptyList())
            close(ex)
            return@callbackFlow
        }

        awaitClose {
            Timber.d("Stopping Bluetooth scan")
            runCatching { leScanner?.stopScan(callback) }
                .onFailure { Timber.w(it, "Failed to stop Bluetooth scan") }
        }
    }

    private fun hasScanPermission(): Boolean {
        return hasPermission(android.Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
