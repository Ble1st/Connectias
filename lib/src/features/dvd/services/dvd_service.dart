import 'package:flutter/services.dart';

import '../../logging/services/logging_service.dart';

/// Service for DVD operations via MethodChannel.
/// Uses /usb for device/open/list and /dvd for playback control.
class DvdService {
  DvdService()
      : _usbChannel = const MethodChannel('com.bleist.connectias/usb'),
        _dvdChannel = const MethodChannel('com.bleist.connectias/dvd');

  final MethodChannel _usbChannel;
  final MethodChannel _dvdChannel;

  /// Returns device type: "block" or "optical".
  Future<String> getDeviceType(String deviceId) async {
    LoggingService.instance.v('DvdService', 'getDeviceType: $deviceId');
    final result = await _usbChannel.invokeMethod<String>(
      'getDeviceType',
      {'deviceId': deviceId},
    );
    return result ?? 'block';
  }

  /// Opens DVD on the given USB device. Returns dvdHandle.
  /// Throws PlatformException on CSS/decryption errors or device issues.
  Future<int> openDvd(String deviceId) async {
    LoggingService.instance.v('DvdService', 'openDvd: $deviceId');
    final result = await _usbChannel.invokeMethod<int>(
      'openDvd',
      {'deviceId': deviceId},
    );
    if (result == null || result < 0) {
      throw PlatformException(
        code: 'DVD_ERROR',
        message: 'Failed to open DVD',
      );
    }
    return result;
  }

  /// Closes the DVD and releases the USB session.
  Future<void> closeDvd(int dvdHandle) async {
    LoggingService.instance.v('DvdService', 'closeDvd: $dvdHandle');
    await _usbChannel.invokeMethod('closeDvd', {'dvdHandle': dvdHandle});
  }

  /// Lists DVD titles as JSON array.
  Future<String> listTitles(int dvdHandle) async {
    LoggingService.instance.v('DvdService', 'listTitles: $dvdHandle');
    final result = await _usbChannel.invokeMethod<String>(
      'dvdListTitles',
      {'dvdHandle': dvdHandle},
    );
    return result ?? '[]';
  }

  /// Lists chapters for a title as JSON array.
  Future<String> listChapters(int dvdHandle, int titleId) async {
    final result = await _usbChannel.invokeMethod<String>(
      'dvdListChapters',
      {'dvdHandle': dvdHandle, 'titleId': titleId},
    );
    return result ?? '[]';
  }

  /// Loads DVD for playback (passes dvdHandle to DvdPlayerPlugin).
  Future<void> loadDvd(int dvdHandle) async {
    await _dvdChannel.invokeMethod('loadDvd', dvdHandle);
  }

  /// Starts playing the given title. Call loadDvd(dvdHandle) first.
  Future<void> playTitle(int titleId) async {
    await _dvdChannel.invokeMethod('playTitle', {'titleId': titleId});
  }

  /// Pauses playback.
  Future<void> pause() async {
    await _dvdChannel.invokeMethod('pause');
  }

  /// Resumes playback.
  Future<void> resume() async {
    await _dvdChannel.invokeMethod('resume');
  }

  /// Seeks to position in milliseconds.
  Future<void> seek(int positionMs) async {
    await _dvdChannel.invokeMethod('seek', {'positionMs': positionMs});
  }

  /// Stops playback.
  Future<void> stop() async {
    await _dvdChannel.invokeMethod('stop');
  }
}
