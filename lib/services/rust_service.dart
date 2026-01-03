// Service for Rust API calls

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;
import '../frb_generated.dart';
import '../api.dart';

class RustService {
  static bool _initialized = false;

  /// Initialize Rust library and database
  static Future<void> initialize() async {
    if (_initialized) return;

    try {
      // Initialize flutter_rust_bridge
      await RustLib.init();

      // Get database path
      final appDir = await getApplicationDocumentsDirectory();
      final dbPath = path.join(appDir.path, 'connectias.db');

      // Initialize database
      final result = initDatabase(databasePath: dbPath);
      if (result.contains('Error') || result.contains('error')) {
        throw Exception('Database initialization failed: $result');
      }

      _initialized = true;
    } catch (e, stackTrace) {
      debugPrint('Failed to initialize Rust service: $e');
      debugPrint('Stack trace: $stackTrace');
      rethrow;
    }
  }

  /// Check if service is initialized
  static bool get isInitialized => _initialized;
}

