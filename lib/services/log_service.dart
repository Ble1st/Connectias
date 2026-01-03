// Log service for interacting with Rust logging API

import '../api.dart' as rust_api;

class LogService {
  /// Get logs from database
  Future<List<String>> getLogs({
    String? levelFilter,
    int limit = 100,
    int offset = 0,
  }) async {
    try {
      return rust_api.getLogs(
        levelFilter: levelFilter,
        limit: limit,
        offset: offset,
      );
    } catch (e) {
      throw Exception('Failed to get logs: $e');
    }
  }

  /// Set log level
  Future<void> setLogLevel(String level) async {
    try {
      rust_api.setLogLevel(level: level);
    } catch (e) {
      throw Exception('Failed to set log level: $e');
    }
  }

  /// Get current log level
  Future<String> getLogLevel() async {
    try {
      return rust_api.getLogLevel();
    } catch (e) {
      throw Exception('Failed to get log level: $e');
    }
  }

  /// Log a message
  Future<void> logMessage({
    required String level,
    required String message,
    String? module,
  }) async {
    try {
      rust_api.logMessage(
        level: level,
        message: message,
        module: module,
      );
    } catch (e) {
      throw Exception('Failed to log message: $e');
    }
  }
}
