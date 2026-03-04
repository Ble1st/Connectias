import 'dart:async';

import 'package:flutter/services.dart';

import '../../logging/services/logging_service.dart';
import '../data/usb_device_info.dart';
import '../data/usb_device_event.dart';

/// Bridge to Android USB APIs via MethodChannel and EventChannel.
class UsbBridge {
  static const _methodChannel = MethodChannel('com.bleist.connectias/usb');
  static const _eventChannel = EventChannel('com.bleist.connectias/usb_events');
  static const _permissionEventChannel =
      EventChannel('com.bleist.connectias/usb_permission_result');

  /// Stream of USB device attach/detach events.
  Stream<UsbDeviceEvent> get deviceEvents => _eventChannel
      .receiveBroadcastStream()
      .map((event) => UsbDeviceEvent.fromMap(Map<Object?, Object?>.from(event as Map)));

  /// Stream of USB permission results (deviceId, granted).
  Stream<Map<Object?, Object?>> get permissionResults =>
      _permissionEventChannel.receiveBroadcastStream().map(
            (event) => Map<Object?, Object?>.from(event as Map),
          );

  /// Returns the list of currently connected USB devices.
  Future<List<UsbDeviceInfo>> getDevices() async {
    LoggingService.instance.v('UsbBridge', 'getDevices');
    final result = await _methodChannel.invokeMethod<List<Object?>>('getDevices');
    if (result == null) return [];

    return result
        .map((e) => UsbDeviceInfo.fromMap(Map<Object?, Object?>.from(e as Map)))
        .toList();
  }

  /// Returns true if the app has permission to access the device.
  Future<bool> hasPermission(String deviceId) async {
    final result = await _methodChannel.invokeMethod<bool>(
      'hasPermission',
      {'deviceId': deviceId},
    );
    return result ?? false;
  }

  /// Requests permission to access the USB device. Shows system dialog.
  /// Returns true if granted, false if denied. Waits up to [timeout].
  Future<bool> requestPermission(String deviceId, {Duration timeout = const Duration(seconds: 60)}) async {
    final granted = await hasPermission(deviceId);
    if (granted) return true;

    final completer = Completer<bool>();
    StreamSubscription<Map<Object?, Object?>>? sub;
    sub = permissionResults.listen((event) {
      if (event['deviceId'] == deviceId) {
        sub?.cancel();
        if (!completer.isCompleted) {
          completer.complete(event['granted'] == true);
        }
      }
    });

    await _methodChannel.invokeMethod('requestPermission', {'deviceId': deviceId});

    try {
      return await completer.future.timeout(
        timeout,
        onTimeout: () {
          sub?.cancel();
          if (!completer.isCompleted) completer.complete(false);
          return false;
        },
      );
    } finally {
      await sub.cancel();
    }
  }

  /// Opens the USB device and returns a session ID for bulk transfers.
  Future<int> openDevice(String deviceId) async {
    LoggingService.instance.v('UsbBridge', 'openDevice: $deviceId');
    final result = await _methodChannel.invokeMethod<int>(
      'openDevice',
      {'deviceId': deviceId},
    );
    if (result == null) {
      LoggingService.instance.e('UsbBridge', 'openDevice failed');
      throw PlatformException(
        code: 'USB_ERROR',
        message: 'Failed to open device',
      );
    }
    return result;
  }

  /// Sends data to the device. Returns number of bytes written.
  Future<int> bulkTransferOut(int sessionId, List<int> data) async {
    final result = await _methodChannel.invokeMethod<int>(
      'bulkTransferOut',
      {'sessionId': sessionId, 'data': data},
    );
    if (result == null) {
      LoggingService.instance.e('UsbBridge', 'bulkTransferOut failed');
      throw PlatformException(
        code: 'USB_ERROR',
        message: 'bulkTransferOut failed',
      );
    }
    return result;
  }

  /// Receives up to [maxLength] bytes from the device.
  Future<List<int>> bulkTransferIn(int sessionId, {int maxLength = 16384}) async {
    final result = await _methodChannel.invokeMethod<List<Object?>>(
      'bulkTransferIn',
      {'sessionId': sessionId, 'maxLength': maxLength},
    );
    if (result == null) {
      LoggingService.instance.e('UsbBridge', 'bulkTransferIn failed');
      throw PlatformException(
        code: 'USB_ERROR',
        message: 'bulkTransferIn failed',
      );
    }
    return result.map((e) => (e as num).toInt()).toList();
  }

  /// Closes the USB device session.
  Future<void> closeDevice(int sessionId) async {
    await _methodChannel.invokeMethod('closeDevice', {'sessionId': sessionId});
  }
}
