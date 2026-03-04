import 'package:flutter/services.dart';

import '../../logging/services/logging_service.dart';

/// Service for NTFS volume operations on USB mass storage.
/// Uses Kotlin/Rust via MethodChannel (same channel as UsbBridge).
class UsbVolumeService {
  UsbVolumeService() : _channel = const MethodChannel('com.bleist.connectias/usb');

  final MethodChannel _channel;

  /// Opens an NTFS volume on the given USB device.
  /// Returns volume ID on success.
  Future<int> openVolume(String deviceId) async {
    LoggingService.instance.v('UsbVolumeService', 'openVolume: $deviceId');
    final result = await _channel.invokeMethod<int>(
      'openVolume',
      {'deviceId': deviceId},
    );
    if (result == null) {
      LoggingService.instance.e('UsbVolumeService', 'openVolume failed');
      throw PlatformException(
        code: 'VOLUME_ERROR',
        message: 'Failed to open volume',
      );
    }
    if (result < 0) {
      LoggingService.instance.e('UsbVolumeService', 'Volume open failed (code: $result)');
      throw PlatformException(
        code: 'VOLUME_ERROR',
        message: 'Volume open failed (code: $result)',
      );
    }
    return result;
  }

  /// Closes the volume and releases the USB session.
  Future<void> closeVolume(int volumeId) async {
    await _channel.invokeMethod('closeVolume', {'volumeId': volumeId});
  }

  /// Lists directory entries. Returns JSON array of {n, d, s} objects.
  Future<String> listDirectory(int volumeId, {String path = ''}) async {
    LoggingService.instance.v('UsbVolumeService', 'listDirectory: $volumeId path=$path');
    final result = await _channel.invokeMethod<String>(
      'listDirectory',
      {'volumeId': volumeId, 'path': path},
    );
    return result ?? '[]';
  }

  /// Reads file content.
  Future<List<int>> readFile(
    int volumeId,
    String path, {
    int offset = 0,
    int length = 65536,
  }) async {
    final result = await _channel.invokeMethod<List<Object?>>(
      'readFile',
      {
        'volumeId': volumeId,
        'path': path,
        'offset': offset,
        'length': length,
      },
    );
    if (result == null) {
      LoggingService.instance.e('UsbVolumeService', 'readFile failed');
      throw PlatformException(
        code: 'VOLUME_ERROR',
        message: 'Failed to read file',
      );
    }
    return result.map((e) => (e as num).toInt()).toList();
  }
}
