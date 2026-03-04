package com.bleist.connectias.connectias

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private lateinit var usbPlugin: UsbPlugin
    private lateinit var loggingPlugin: LoggingPlugin

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val logChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.bleist.connectias/log",
        )
        usbPlugin = UsbPlugin(this, logChannel)
        loggingPlugin = LoggingPlugin(this)
        logChannel.setMethodCallHandler(loggingPlugin)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.bleist.connectias/usb",
        ).setMethodCallHandler(usbPlugin)

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.bleist.connectias/usb_events",
        ).setStreamHandler(usbPlugin.deviceEventsStreamHandler)

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.bleist.connectias/usb_permission_result",
        ).setStreamHandler(usbPlugin.permissionResultStreamHandler)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        loggingPlugin.onActivityResult(requestCode, resultCode, data)
    }
}
