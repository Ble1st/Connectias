import 'dart:async';

import 'package:flutter/services.dart';

import '../data/log_repository.dart';

/// Central logging service. Writes to SQLite. Receives logs from Dart, Kotlin, Rust.
class LoggingService {
  LoggingService._();
  static final LoggingService _instance = LoggingService._();
  static LoggingService get instance => _instance;

  static const _logChannel = MethodChannel('com.bleist.connectias/log');

  bool _initialized = false;

  /// Initialize: set up Kotlin log receiver.
  void init() {
    if (_initialized) return;
    _initialized = true;
    _logChannel.setMethodCallHandler(_handleLogFromKotlin);
  }

  Future<dynamic> _handleLogFromKotlin(MethodCall call) async {
    if (call.method == 'log') {
      final args = call.arguments as Map<Object?, Object?>?;
      if (args != null) {
        await v(
          args['tag'] as String? ?? 'kotlin',
          args['message'] as String? ?? '',
          source: 'kotlin',
        );
      }
    }
    return null;
  }

  /// Log verbose.
  Future<void> v(String tag, String message, {String source = 'dart'}) async {
    await _log('verbose', tag, message, source);
  }

  /// Log debug.
  Future<void> d(String tag, String message, {String source = 'dart'}) async {
    await _log('debug', tag, message, source);
  }

  /// Log info.
  Future<void> i(String tag, String message, {String source = 'dart'}) async {
    await _log('info', tag, message, source);
  }

  /// Log warning.
  Future<void> w(String tag, String message, {String source = 'dart'}) async {
    await _log('warning', tag, message, source);
  }

  /// Log error.
  Future<void> e(String tag, String message, {String source = 'dart'}) async {
    await _log('error', tag, message, source);
  }

  Future<void> _log(String level, String tag, String message, String source) async {
    try {
      await LogRepository.instance.insert(
        timestamp: DateTime.now(),
        level: level,
        tag: tag,
        message: message,
        source: source,
      );
    } catch (_) {}
  }

  /// Get all logs for display.
  Future<List<dynamic>> getLogs({int? limit}) async {
    return LogRepository.instance.getAll(limit: limit);
  }

  /// Export logs as TXT string.
  Future<String> exportAsText() async {
    return LogRepository.instance.exportAsText();
  }

  /// Clear all logs.
  Future<void> clear() async {
    await LogRepository.instance.clear();
  }

  /// Request SAF export: Kotlin opens document picker, writes content, returns success.
  Future<bool> exportToFile() async {
    try {
      final content = await exportAsText();
      final result = await _logChannel.invokeMethod<bool>(
        'exportLogsToFile',
        {'content': content},
      );
      return result ?? false;
    } catch (_) {
      return false;
    }
  }
}
