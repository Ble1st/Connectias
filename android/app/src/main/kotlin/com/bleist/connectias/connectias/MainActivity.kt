package com.bleist.connectias.connectias

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private lateinit var usbPlugin: UsbPlugin

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        usbPlugin = UsbPlugin(this)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.bleist.connectias/usb",
        ).setMethodCallHandler(usbPlugin)

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.bleist.connectias/usb_events",
        ).setStreamHandler(usbPlugin)
    }
}
