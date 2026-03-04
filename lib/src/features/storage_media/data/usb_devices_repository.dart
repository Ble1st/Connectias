import 'package:flutter/services.dart';

import '../../logging/services/logging_service.dart';
import 'usb_device_info.dart';
import 'usb_device_event.dart';
import '../services/usb_bridge.dart';

/// Source of truth for USB devices. Wraps [UsbBridge] and handles errors.
class UsbDevicesRepository {
  UsbDevicesRepository(this._bridge);

  final UsbBridge _bridge;

  /// Returns the list of currently connected USB devices.
  /// Throws on platform errors; catch to show a user-friendly message.
  Future<List<UsbDeviceInfo>> getDevices() async {
    try {
      return await _bridge.getDevices();
    } on PlatformException catch (e) {
      LoggingService.instance.e('UsbDevicesRepository', e.message ?? 'Failed to load USB devices');
      throw UsbDevicesRepositoryException(
        e.message ?? 'Failed to load USB devices',
        cause: e,
      );
    }
  }

  /// Stream of USB device attach/detach events.
  Stream<UsbDeviceEvent> get deviceEvents => _bridge.deviceEvents;
}

/// Exception thrown by [UsbDevicesRepository] when device access fails.
class UsbDevicesRepositoryException implements Exception {
  UsbDevicesRepositoryException(this.message, {this.cause});

  final String message;
  final Object? cause;

  @override
  String toString() => 'UsbDevicesRepositoryException: $message';
}
