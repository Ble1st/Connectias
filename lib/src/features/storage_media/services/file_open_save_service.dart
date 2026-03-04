import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../../logging/services/logging_service.dart';
import '../data/usb_volume_repository.dart';

/// Max size to read for "open in other app" / "save to device" (100 MB).
const int _kMaxFileSize = 100 * 1024 * 1024;

const int _kChunkSize = 256 * 1024;

/// MIME types by file extension (common types).
const Map<String, String> _mimeTypes = {
  'txt': 'text/plain',
  'html': 'text/html',
  'htm': 'text/html',
  'css': 'text/css',
  'json': 'application/json',
  'xml': 'application/xml',
  'pdf': 'application/pdf',
  'zip': 'application/zip',
  'jpg': 'image/jpeg',
  'jpeg': 'image/jpeg',
  'png': 'image/png',
  'gif': 'image/gif',
  'webp': 'image/webp',
  'mp3': 'audio/mpeg',
  'mp4': 'video/mp4',
  'webm': 'video/webm',
};

/// Service to open a USB file in another app or save it to device storage (SAF).
class FileOpenSaveService {
  FileOpenSaveService(this._repository)
      : _channel = const MethodChannel('com.bleist.connectias/log');

  final UsbVolumeRepository _repository;
  final MethodChannel _channel;

  /// Reads file from USB into a temp file and opens it with an external app (chooser).
  /// Returns true if the intent was started, false on error.
  Future<bool> openInOtherApp({
    required int volumeId,
    required String path,
    required String fileName,
  }) async {
    final tempPath = await _readToTempFile(volumeId, path, fileName);
    if (tempPath == null) return false;
    try {
      final mime = _mimeTypeForFileName(fileName);
      final result = await _channel.invokeMethod<bool>(
        'openFileInOtherApp',
        {'tempPath': tempPath, 'mimeType': mime},
      );
      return result ?? false;
    } catch (e) {
      LoggingService.instance.e('FileOpenSaveService', 'openInOtherApp: $e');
      return false;
    }
    // Do not delete temp file here; the other app may still be reading it. Cache will be cleared by system.
  }

  static Future<FileSystemEntity> _deleteSafe(File file) {
    return file.delete().catchError((_) => file);
  }

  /// Reads file from USB into a temp file, then opens SAF "save as" and copies there.
  /// Returns true if user picked a location and copy succeeded.
  Future<bool> saveToDevice({
    required int volumeId,
    required String path,
    required String fileName,
  }) async {
    final tempPath = await _readToTempFile(volumeId, path, fileName);
    if (tempPath == null) return false;
    try {
      final result = await _channel.invokeMethod<bool>(
        'saveFileToDevice',
        {'tempPath': tempPath, 'suggestedName': fileName},
      );
      return result ?? false;
    } catch (e) {
      LoggingService.instance.e('FileOpenSaveService', 'saveToDevice: $e');
      return false;
    }
    // Kotlin deletes temp file after successful copy; if user cancelled, we delete here
    finally {
      await _deleteSafe(File(tempPath));
    }
  }

  /// Returns temp file path or null on error. Caller should delete the file when done.
  Future<String?> _readToTempFile(int volumeId, String path, String fileName) async {
    final dir = await getTemporaryDirectory();
    final safeName = p.basename(fileName).replaceAll(RegExp(r'[^\w\.\-]'), '_');
    if (safeName.isEmpty) return null;
    final file = File(p.join(dir.path, 'usb_$safeName'));
    try {
      int offset = 0;
      int totalRead = 0;
      final sink = file.openWrite();
      try {
        while (totalRead < _kMaxFileSize) {
          final chunk = await _repository.readFile(
            volumeId,
            path,
            offset: offset,
            length: _kChunkSize,
          );
          if (chunk.isEmpty) break;
          sink.add(chunk);
          totalRead += chunk.length;
          offset += chunk.length;
          if (chunk.length < _kChunkSize) break;
        }
      } finally {
        await sink.close();
      }
      return file.path;
    } catch (e) {
      LoggingService.instance.e('FileOpenSaveService', '_readToTempFile: $e');
      await _deleteSafe(file);
      return null;
    }
  }

  static String _mimeTypeForFileName(String fileName) {
    final ext = p.extension(fileName).toLowerCase();
    if (ext.startsWith('.')) {
      final key = ext.substring(1);
      if (_mimeTypes.containsKey(key)) return _mimeTypes[key]!;
    }
    return '*/*';
  }
}
