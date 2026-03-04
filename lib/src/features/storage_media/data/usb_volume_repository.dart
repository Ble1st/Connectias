import 'package:flutter/services.dart';

import '../../logging/services/logging_service.dart';
import 'usb_directory_entry.dart';
import '../services/usb_bridge.dart';
import '../services/usb_volume_service.dart';

/// Repository for NTFS volume operations on USB devices.
/// Handles permission, open/close, directory listing, file reading.
class UsbVolumeRepository {
  UsbVolumeRepository(this._bridge, this._volumeService);

  final UsbBridge _bridge;
  final UsbVolumeService _volumeService;

  /// Ensures permission and opens the NTFS volume on the device.
  Future<int> openVolume(String deviceId) async {
    LoggingService.instance.v('UsbVolumeRepository', 'openVolume: $deviceId');
    final hasPermission = await _bridge.hasPermission(deviceId);
    if (!hasPermission) {
      final granted = await _bridge.requestPermission(deviceId);
      if (!granted) {
        throw UsbVolumeRepositoryException('USB permission denied');
      }
    }
    try {
      return await _volumeService.openVolume(deviceId);
    } on PlatformException catch (e) {
      final msg = e.message ?? 'Failed to open volume';
      LoggingService.instance.e('UsbVolumeRepository', 'openVolume: $msg');
      if (msg.contains('No NTFS') || msg.contains('FAT32') || msg.contains('ext4')) {
        throw UsbVolumeRepositoryException(
          'Only NTFS volumes are supported. This device has a different filesystem.',
        );
      }
      throw UsbVolumeRepositoryException(msg, cause: e);
    }
  }

  /// Closes the volume.
  Future<void> closeVolume(int volumeId) async {
    await _volumeService.closeVolume(volumeId);
  }

  /// Lists directory entries at the given path.
  Future<List<UsbDirectoryEntry>> listDirectory(int volumeId, {String path = ''}) async {
    try {
      final json = await _volumeService.listDirectory(volumeId, path: path);
      return UsbDirectoryEntry.fromJsonList(json);
    } on PlatformException catch (e) {
      LoggingService.instance.e('UsbVolumeRepository', 'listDirectory: ${e.message}');
      throw UsbVolumeRepositoryException(
        e.message ?? 'Failed to list directory',
        cause: e,
      );
    }
  }

  /// Reads file content.
  Future<List<int>> readFile(
    int volumeId,
    String path, {
    int offset = 0,
    int length = 65536,
  }) async {
    try {
      return await _volumeService.readFile(
        volumeId,
        path,
        offset: offset,
        length: length,
      );
    } on PlatformException catch (e) {
      LoggingService.instance.e('UsbVolumeRepository', 'readFile: ${e.message}');
      throw UsbVolumeRepositoryException(
        e.message ?? 'Failed to read file',
        cause: e,
      );
    }
  }
}

class UsbVolumeRepositoryException implements Exception {
  UsbVolumeRepositoryException(this.message, {this.cause});

  final String message;
  final Object? cause;

  @override
  String toString() => 'UsbVolumeRepositoryException: $message';
}
