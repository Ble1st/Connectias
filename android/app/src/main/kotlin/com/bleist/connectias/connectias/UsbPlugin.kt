package com.bleist.connectias.connectias

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.HashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * USB plugin providing device enumeration, permission, open/close, bulk transfers,
 * and attach/detach events.
 */
class UsbPlugin(
    private val context: Context,
    private val logChannel: MethodChannel? = null,
) : MethodChannel.MethodCallHandler {

    private fun log(tag: String, message: String) {
        logChannel?.invokeMethod("log", mapOf(
            "tag" to tag,
            "message" to message,
        ))
    }

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var eventSink: EventChannel.EventSink? = null
    private var permissionEventSink: EventChannel.EventSink? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var permissionReceiver: BroadcastReceiver? = null

    private val sessions = HashMap<Long, UsbSession>()
    private val sessionIdGenerator = AtomicLong(1)
    private val volumeToSession = HashMap<Long, Long>()
    private val dvdToSession = HashMap<Long, Long>()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    sendEvent("attached", device?.deviceId?.toString())
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    sendEvent("detached", device?.deviceId?.toString())
                }
            }
        }
    }

    private val permissionReceiverImpl = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.hardware.usb.action.USB_PERMISSION") {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                permissionReceiver?.let { context?.unregisterReceiver(it) }
                permissionReceiver = null
                device?.let {
                    val deviceId = usbManager.deviceList?.entries?.find { it.value == device }?.key ?: device.deviceId.toString()
                    sendPermissionResult(deviceId, granted)
                }
            }
        }
    }

    override fun onMethodCall(call: io.flutter.plugin.common.MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getDevices" -> {
                try {
                    val devices = getDevicesList()
                    result.success(devices)
                } catch (e: Exception) {
                    result.error("USB_ERROR", e.message, null)
                }
            }
            "hasPermission" -> {
                val deviceId = call.argument<String>("deviceId")
                if (deviceId == null) {
                    result.error("USB_ERROR", "deviceId required", null)
                    return
                }
                val device = usbManager.deviceList[deviceId] ?: run {
                    result.success(false)
                    return@onMethodCall
                }
                result.success(usbManager.hasPermission(device))
            }
            "requestPermission" -> {
                val deviceId = call.argument<String>("deviceId")
                if (deviceId == null) {
                    result.error("USB_ERROR", "deviceId required", null)
                    return
                }
                val device = usbManager.deviceList[deviceId]
                if (device == null) {
                    result.error("USB_ERROR", "Device not found: $deviceId", null)
                    return
                }
                if (usbManager.hasPermission(device)) {
                    result.success(true)
                    return@onMethodCall
                }
                val filter = IntentFilter("android.hardware.usb.action.USB_PERMISSION")
                if (permissionReceiver != null) {
                    try { context.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
                }
                permissionReceiver = permissionReceiverImpl
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                val flags = when {
                    android.os.Build.VERSION.SDK_INT >= 34 -> PendingIntent.FLAG_IMMUTABLE
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE
                    else -> 0
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0,
                    Intent("android.hardware.usb.action.USB_PERMISSION"),
                    flags
                )
                usbManager.requestPermission(device, pendingIntent)
                result.success(null)
            }
            "openDevice" -> {
                val deviceId = call.argument<String>("deviceId")
                log("UsbPlugin", "openDevice: $deviceId")
                if (deviceId == null) {
                    result.error("USB_ERROR", "deviceId required", null)
                    return
                }
                try {
                    val sessionId = openDevice(deviceId)
                    if (sessionId != null) {
                        result.success(sessionId)
                    } else {
                        result.error("USB_ERROR", "Failed to open device: no Mass Storage interface or claim failed", null)
                    }
                } catch (e: Exception) {
                    result.error("USB_ERROR", e.message, null)
                }
            }
            "bulkTransferOut" -> {
                val sessionId = call.argument<Number>("sessionId")?.toLong()
                val dataRaw = call.argument<Any>("data")
                val data = when (dataRaw) {
                    is ByteArray -> dataRaw
                    is List<*> -> dataRaw.map { (it as Number).toInt().toByte() }.toByteArray()
                    else -> null
                }
                if (sessionId == null || data == null) {
                    result.error("USB_ERROR", "sessionId and data required", null)
                    return
                }
                try {
                    val written = bulkTransferOut(sessionId, data)
                    result.success(written)
                } catch (e: Exception) {
                    result.error("USB_ERROR", e.message, null)
                }
            }
            "bulkTransferIn" -> {
                val sessionId = call.argument<Number>("sessionId")?.toLong()
                val maxLength = call.argument<Int>("maxLength") ?: 16384
                if (sessionId == null) {
                    result.error("USB_ERROR", "sessionId required", null)
                    return
                }
                try {
                    val data = bulkTransferIn(sessionId, maxLength)
                    result.success(data)
                } catch (e: Exception) {
                    result.error("USB_ERROR", e.message, null)
                }
            }
            "closeDevice" -> {
                val sessionId = call.argument<Number>("sessionId")?.toLong()
                if (sessionId == null) {
                    result.error("USB_ERROR", "sessionId required", null)
                    return
                }
                closeDevice(sessionId)
                result.success(null)
            }
            "openVolume" -> {
                val deviceId = call.argument<String>("deviceId")
                log("UsbPlugin", "openVolume: $deviceId")
                if (deviceId == null) {
                    result.error("USB_ERROR", "deviceId required", null)
                    return
                }
                try {
                    val sessionId = openDevice(deviceId)
                    if (sessionId == null) {
                        result.error("USB_ERROR", "Failed to open device", null)
                        return
                    }
                    val handler = object : BulkTransferHandler {
                        override fun bulkOut(data: ByteArray): Int = bulkTransferOut(sessionId, data)
                        override fun bulkIn(maxLength: Int): ByteArray = bulkTransferIn(sessionId, maxLength)
                    }
                    val volumeId = NativeBridge.openVolume(sessionId, handler)
                    if (volumeId < 0) {
                        closeDevice(sessionId)
                        result.error("VOLUME_ERROR", NativeBridge.lastError() ?: "Open failed", volumeId)
                        return
                    }
                    volumeToSession[volumeId] = sessionId
                    result.success(volumeId)
                } catch (e: Exception) {
                    result.error("USB_ERROR", e.message, null)
                }
            }
            "getDeviceType" -> {
                val deviceId = call.argument<String>("deviceId")
                if (deviceId == null) {
                    result.error("USB_ERROR", "deviceId required", null)
                    return
                }
                try {
                    val sessionId = openDevice(deviceId)
                    if (sessionId == null) {
                        result.error("USB_ERROR", "Failed to open device", null)
                        return
                    }
                    val handler = object : BulkTransferHandler {
                        override fun bulkOut(data: ByteArray): Int = bulkTransferOut(sessionId, data)
                        override fun bulkIn(maxLength: Int): ByteArray = bulkTransferIn(sessionId, maxLength)
                    }
                    val type = NativeBridge.getDeviceType(sessionId, handler)
                    closeDevice(sessionId)
                    result.success(type ?: "block")
                } catch (e: Exception) {
                    result.error("USB_ERROR", e.message, null)
                }
            }
            "openDvd" -> {
                val deviceId = call.argument<String>("deviceId")
                if (deviceId == null) {
                    result.error("USB_ERROR", "deviceId required", null)
                    return
                }
                try {
                    val sessionId = openDevice(deviceId)
                    if (sessionId == null) {
                        result.error("USB_ERROR", "Failed to open device", null)
                        return
                    }
                    val handler = object : BulkTransferHandler {
                        override fun bulkOut(data: ByteArray): Int = bulkTransferOut(sessionId, data)
                        override fun bulkIn(maxLength: Int): ByteArray = bulkTransferIn(sessionId, maxLength)
                    }
                    val dvdHandle = NativeBridge.openDvd(sessionId, handler)
                    if (dvdHandle < 0) {
                        closeDevice(sessionId)
                        result.error("DVD_ERROR", NativeBridge.lastError() ?: "Open failed", null)
                        return
                    }
                    dvdToSession[dvdHandle] = sessionId
                    result.success(dvdHandle)
                } catch (e: Exception) {
                    result.error("USB_ERROR", e.message, null)
                }
            }
            "closeDvd" -> {
                val dvdHandle = call.argument<Number>("dvdHandle")?.toLong()
                if (dvdHandle == null) {
                    result.error("USB_ERROR", "dvdHandle required", null)
                    return
                }
                NativeBridge.closeDvd(dvdHandle)
                dvdToSession.remove(dvdHandle)?.let { closeDevice(it) }
                result.success(null)
            }
            "dvdListTitles" -> {
                val dvdHandle = call.argument<Number>("dvdHandle")?.toLong()
                if (dvdHandle == null) {
                    result.error("USB_ERROR", "dvdHandle required", null)
                    return
                }
                try {
                    result.success(NativeBridge.dvdListTitles(dvdHandle) ?: "[]")
                } catch (e: Exception) {
                    result.error("DVD_ERROR", e.message, null)
                }
            }
            "dvdListChapters" -> {
                val dvdHandle = call.argument<Number>("dvdHandle")?.toLong()
                val titleId = call.argument<Int>("titleId") ?: 1
                if (dvdHandle == null) {
                    result.error("USB_ERROR", "dvdHandle required", null)
                    return
                }
                try {
                    result.success(NativeBridge.dvdListChapters(dvdHandle, titleId) ?: "[]")
                } catch (e: Exception) {
                    result.error("DVD_ERROR", e.message, null)
                }
            }
            "dvdOpenTitleStream" -> {
                val dvdHandle = call.argument<Number>("dvdHandle")?.toLong()
                val titleId = call.argument<Int>("titleId") ?: 1
                if (dvdHandle == null) {
                    result.error("USB_ERROR", "dvdHandle required", null)
                    return
                }
                try {
                    val streamId = NativeBridge.dvdOpenTitleStream(dvdHandle, titleId)
                    if (streamId < 0) {
                        result.error("DVD_ERROR", NativeBridge.lastError() ?: "Open stream failed", null)
                        return
                    }
                    result.success(streamId)
                } catch (e: Exception) {
                    result.error("DVD_ERROR", e.message, null)
                }
            }
            "dvdReadStream" -> {
                val streamId = call.argument<Number>("streamId")?.toLong()
                val length = call.argument<Int>("length") ?: 65536
                if (streamId == null) {
                    result.error("USB_ERROR", "streamId required", null)
                    return
                }
                try {
                    val buf = ByteArray(length)
                    val n = NativeBridge.dvdReadStream(streamId, buf)
                    if (n < 0) {
                        result.error("DVD_ERROR", NativeBridge.lastError() ?: "Read failed", null)
                        return
                    }
                    result.success(buf.take(n).map { it.toInt() }.toList())
                } catch (e: Exception) {
                    result.error("DVD_ERROR", e.message, null)
                }
            }
            "dvdSeekStream" -> {
                val streamId = call.argument<Number>("streamId")?.toLong()
                val offset = call.argument<Number>("offset")?.toLong() ?: 0L
                if (streamId == null) {
                    result.error("USB_ERROR", "streamId required", null)
                    return
                }
                try {
                    result.success(NativeBridge.dvdSeekStream(streamId, offset))
                } catch (e: Exception) {
                    result.error("DVD_ERROR", e.message, null)
                }
            }
            "dvdCloseStream" -> {
                val streamId = call.argument<Number>("streamId")?.toLong()
                if (streamId == null) {
                    result.error("USB_ERROR", "streamId required", null)
                    return
                }
                NativeBridge.dvdCloseStream(streamId)
                result.success(null)
            }
            "closeVolume" -> {
                val volumeId = call.argument<Number>("volumeId")?.toLong()
                if (volumeId == null) {
                    result.error("USB_ERROR", "volumeId required", null)
                    return
                }
                val rc = NativeBridge.closeVolume(volumeId)
                volumeToSession.remove(volumeId)?.let { closeDevice(it) }
                result.success(rc)
            }
            "listDirectory" -> {
                val volumeId = call.argument<Number>("volumeId")?.toLong()
                val path = call.argument<String>("path") ?: ""
                if (volumeId == null) {
                    result.error("USB_ERROR", "volumeId required", null)
                    return
                }
                try {
                    val json = NativeBridge.listDirectory(volumeId, path)
                    result.success(json ?: "[]")
                } catch (e: Exception) {
                    result.error("VOLUME_ERROR", e.message, null)
                }
            }
            "readFile" -> {
                val volumeId = call.argument<Number>("volumeId")?.toLong()
                val path = call.argument<String>("path")
                val offset = call.argument<Number>("offset")?.toLong() ?: 0L
                val length = call.argument<Int>("length") ?: 65536
                if (volumeId == null || path == null) {
                    result.error("USB_ERROR", "volumeId and path required", null)
                    return
                }
                try {
                    val data = NativeBridge.readFile(volumeId, path, offset, length)
                    result.success(data?.toList() ?: emptyList<Int>())
                } catch (e: Exception) {
                    result.error("VOLUME_ERROR", e.message, null)
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun getDevicesList(): List<Map<String, Any?>> {
        val deviceList = usbManager.deviceList ?: return emptyList()
        return deviceList.map { (deviceName, device) ->
            mapOf(
                "deviceId" to deviceName,
                "vendorId" to device.vendorId,
                "productId" to device.productId,
                "productName" to (device.productName ?: ""),
                "deviceClass" to device.deviceClass,
                "deviceSubclass" to device.deviceSubclass,
                "deviceProtocol" to device.deviceProtocol,
            )
        }.toList()
    }

    private fun findMassStorageInterface(device: UsbDevice): android.hardware.usb.UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                var endpointIn: android.hardware.usb.UsbEndpoint? = null
                var endpointOut: android.hardware.usb.UsbEndpoint? = null
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) endpointIn = ep
                        else endpointOut = ep
                    }
                }
                if (endpointIn != null && endpointOut != null) {
                    return iface
                }
            }
        }
        return null
    }

    private fun openDevice(deviceId: String): Long? {
        val device = usbManager.deviceList[deviceId] ?: return null
        if (!usbManager.hasPermission(device)) return null

        val connection = usbManager.openDevice(device) ?: return null
        val iface = findMassStorageInterface(device) ?: run {
            connection.close()
            return null
        }
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            return null
        }
        var endpointIn: android.hardware.usb.UsbEndpoint? = null
        var endpointOut: android.hardware.usb.UsbEndpoint? = null
        for (j in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(j)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) endpointIn = ep
                else endpointOut = ep
            }
        }
        if (endpointIn == null || endpointOut == null) {
            connection.releaseInterface(iface)
            connection.close()
            return null
        }
        val session = UsbSession(connection, iface, endpointIn, endpointOut)
        val sessionId = sessionIdGenerator.getAndIncrement()
        sessions[sessionId] = session
        return sessionId
    }

    private fun bulkTransferOut(sessionId: Long, data: ByteArray): Int {
        val session = sessions[sessionId] ?: throw IllegalStateException("Session not found: $sessionId")
        val result = session.connection.bulkTransfer(session.endpointOut, data, data.size, 30000)
        if (result < 0) throw IllegalStateException("bulkTransferOut failed: $result")
        return result
    }

    private fun bulkTransferIn(sessionId: Long, maxLength: Int): ByteArray {
        val session = sessions[sessionId] ?: throw IllegalStateException("Session not found: $sessionId")
        val buffer = ByteArray(maxLength)
        val result = session.connection.bulkTransfer(session.endpointIn, buffer, maxLength, 30000)
        if (result < 0) throw IllegalStateException("bulkTransferIn failed: $result")
        return if (result < maxLength) buffer.copyOf(result) else buffer
    }

    private fun closeDevice(sessionId: Long) {
        sessions.remove(sessionId)?.close()
    }

    private fun sendEvent(type: String, deviceId: String?) {
        val map = HashMap<String, Any?>().apply {
            put("type", type)
            put("deviceId", deviceId)
        }
        eventSink?.success(map)
    }

    private fun sendPermissionResult(deviceId: String, granted: Boolean) {
        val map = HashMap<String, Any?>().apply {
            put("deviceId", deviceId)
            put("granted", granted)
        }
        permissionEventSink?.success(map)
    }

    fun setPermissionEventSink(sink: EventChannel.EventSink?) {
        permissionEventSink = sink
    }

    val deviceEventsStreamHandler = object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            eventSink = events
            broadcastReceiver = usbReceiver
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }

        override fun onCancel(arguments: Any?) {
            broadcastReceiver?.let { context.unregisterReceiver(it) }
            broadcastReceiver = null
            eventSink = null
        }
    }

    val permissionResultStreamHandler = object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            permissionEventSink = events
        }

        override fun onCancel(arguments: Any?) {
            permissionEventSink = null
        }
    }
}
