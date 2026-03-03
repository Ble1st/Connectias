package com.bleist.connectias.connectias

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMethodCodec
import java.util.HashMap

/**
 * USB plugin providing device enumeration and attach/detach events.
 */
class UsbPlugin(private val context: Context) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var eventSink: EventChannel.EventSink? = null
    private var broadcastReceiver: BroadcastReceiver? = null

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
            else -> result.notImplemented()
        }
    }

    private fun getDevicesList(): List<Map<String, Any?>> {
        val deviceList = usbManager.deviceList ?: return emptyList()
        return deviceList.values.map { device ->
            mapOf(
                "deviceId" to device.deviceId.toString(),
                "vendorId" to device.vendorId,
                "productId" to device.productId,
                "productName" to (device.productName ?: ""),
                "deviceClass" to device.deviceClass,
                "deviceSubclass" to device.deviceSubclass,
                "deviceProtocol" to device.deviceProtocol,
            )
        }
    }

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

    private fun sendEvent(type: String, deviceId: String?) {
        val map = HashMap<String, Any?>().apply {
            put("type", type)
            put("deviceId", deviceId)
        }
        eventSink?.success(map)
    }
}
