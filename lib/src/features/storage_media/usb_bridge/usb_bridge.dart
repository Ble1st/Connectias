import 'dart:async';

import 'package:flutter/services.dart';

import 'usb_device_info.dart';
import 'usb_device_event.dart';

/// Bridge to Android USB APIs via MethodChannel and EventChannel.
class UsbBridge {
  static const _methodChannel = MethodChannel('com.bleist.connectias/usb');
  static const _eventChannel = EventChannel('com.bleist.connectias/usb_events');

  /// Stream of USB device attach/detach events.
  Stream<UsbDeviceEvent> get deviceEvents => _eventChannel
      .receiveBroadcastStream()
      .map((event) => UsbDeviceEvent.fromMap(Map<Object?, Object?>.from(event as Map)));

  /// Returns the list of currently connected USB devices.
  Future<List<UsbDeviceInfo>> getDevices() async {
    final result = await _methodChannel.invokeMethod<List<Object?>>('getDevices');
    if (result == null) return [];

    return result
        .map((e) => UsbDeviceInfo.fromMap(Map<Object?, Object?>.from(e as Map)))
        .toList();
  }
}
