/// Connectias Service – High-Level Dart API
/// 
/// Sichere Wrapper um FFI Bridge mit automatischem Cleanup,
/// Error Handling, und Lifecycle Management.
library connectias_service;

import 'package:flutter/foundation.dart';
import '../ffi/connectias_bindings.dart' as ffi;

/// Connectias Plugin Manager Service
/// 
/// Beispiel:
/// ```dart
/// final service = ConnectiasService();
/// await service.init();
/// 
/// try {
///   final pluginId = await service.loadPlugin('/path/to/plugin.wasm');
///   final result = await service.executePlugin(pluginId, 'myCommand', {});
///   print('Result: $result');
/// } finally {
///   await service.dispose();
/// }
/// ```
class ConnectiasService extends ChangeNotifier {
  static final ConnectiasService _instance = ConnectiasService._internal();

  factory ConnectiasService() {
    return _instance;
  }

  ConnectiasService._internal();

  bool _initialized = false;
  final Set<String> _loadedPlugins = {};
  final Map<String, dynamic> _pluginMetadata = {};

  // =========================================================================
  // LIFECYCLE
  // =========================================================================

  /// Initialisiere den Service
  Future<void> init() async {
    if (_initialized) {
      debugPrint('⚠️ ConnectiasService bereits initialisiert');
      return;
    }

    try {
      debugPrint('🚀 Initialisiere ConnectiasService...');
      
      // Initialisiere FFI Bridge
      await ffi.init();
      
      // Hole Version
      final version = ffi.getVersion();
      debugPrint('✅ FFI Version: $version');
      
      // Hole System Info
      final systemInfo = ffi.getSystemInfo();
      debugPrint('✅ System Info: $systemInfo');
      
      // Prüfe RASP auf Sicherheitsbedrohungen
      await _checkSecurity();
      
      _initialized = true;
      debugPrint('✅ ConnectiasService initialisiert');
      notifyListeners();
    } catch (e) {
      debugPrint('❌ Initialisierung fehlgeschlagen: $e');
      rethrow;
    }
  }

  /// Prüfe Sicherheitsbedrohungen
  Future<void> _checkSecurity() async {
    try {
      final status = await ffi.getRaspStatus();
      debugPrint('🛡️ RASP Status: $status');
      
      if (status.isCompromised) {
        throw ConnectiasSecurityException(
          'Gerät ist kompromittiert!',
          status,
        );
      }
      
      if (status.isSuspicious) {
        debugPrint('⚠️ WARNUNG: Verdächtige Aktivitäten erkannt!');
      }
    } catch (e) {
      debugPrint('❌ Security Check fehlgeschlagen: $e');
      rethrow;
    }
  }

  /// Prüfe ob initialisiert
  void _requireInitialized() {
    if (!_initialized) {
      throw StateError('ConnectiasService nicht initialisiert. Rufe init() auf!');
    }
  }

  /// Cleanup
  @override
  Future<void> dispose() async {
    debugPrint('🧹 Cleanup ConnectiasService...');
    
    // Entlade alle Plugins
    for (final pluginId in List.from(_loadedPlugins)) {
      try {
        await unloadPlugin(pluginId);
      } catch (e) {
        debugPrint('⚠️ Fehler beim Unload von $pluginId: $e');
      }
    }
    
    _initialized = false;
    _loadedPlugins.clear();
    _pluginMetadata.clear();
    
    debugPrint('✅ Cleanup fertig');
    notifyListeners();
    super.dispose();
  }

  // =========================================================================
  // PLUGIN MANAGEMENT
  // =========================================================================

  /// Lade ein Plugin
  Future<String> loadPlugin(String pluginPath) async {
    _requireInitialized();
    
    debugPrint('📦 Lade Plugin: $pluginPath');
    
    try {
      final pluginId = await ffi.loadPlugin(pluginPath);
      _loadedPlugins.add(pluginId);
      _pluginMetadata[pluginId] = {
        'path': pluginPath,
        'loadedAt': DateTime.now(),
      };
      
      debugPrint('✅ Plugin geladen: $pluginId');
      notifyListeners();
      return pluginId;
    } catch (e) {
      debugPrint('❌ Plugin-Loading fehlgeschlagen: $e');
      rethrow;
    }
  }

  /// Entlade ein Plugin
  Future<void> unloadPlugin(String pluginId) async {
    _requireInitialized();
    
    debugPrint('🔌 Entlade Plugin: $pluginId');
    
    try {
      await ffi.unloadPlugin(pluginId);
      _loadedPlugins.remove(pluginId);
      _pluginMetadata.remove(pluginId);
      
      debugPrint('✅ Plugin entladen: $pluginId');
      notifyListeners();
    } catch (e) {
      debugPrint('❌ Plugin-Entladung fehlgeschlagen: $e');
      rethrow;
    }
  }

  /// Führe ein Plugin aus
  Future<T> executePlugin<T>(
    String pluginId,
    String command,
    Map<String, String> args, {
    T Function(String)? parser,
  }) async {
    _requireInitialized();
    
    if (!_loadedPlugins.contains(pluginId)) {
      throw StateError('Plugin $pluginId nicht geladen');
    }
    
    debugPrint('⚙️ Führe aus: $pluginId::$command');
    
    try {
      final result = await ffi.executePlugin(pluginId, command, args);
      
      debugPrint('✅ Ausführung erfolgreich: $pluginId::$command');
      
      if (parser != null) {
        return parser(result);
      }
      
      return result as T;
    } catch (e) {
      debugPrint('❌ Ausführung fehlgeschlagen: $e');
      rethrow;
    }
  }

  /// Liste alle Plugins auf
  Future<List<String>> listPlugins() async {
    _requireInitialized();
    
    return List.from(_loadedPlugins);
  }

  /// Erhalte Plugin Metadata
  Map<String, dynamic>? getPluginMetadata(String pluginId) {
    return _pluginMetadata[pluginId];
  }

  // =========================================================================
  // SECURITY
  // =========================================================================

  /// Prüfe Sicherheitsstatus
  Future<RaspCheckResult> checkSecurity() async {
    _requireInitialized();
    
    debugPrint('🛡️ Prüfe Security...');
    
    try {
      final root = await ffi.raspCheckRoot();
      final debugger = await ffi.raspCheckDebugger();
      final emulator = await ffi.raspCheckEmulator();
      final tamper = await ffi.raspCheckTamper();
      
      final result = RaspCheckResult(
        root: root,
        debugger: debugger,
        emulator: emulator,
        tamper: tamper,
      );
      
      debugPrint('✅ Security Check: $result');
      return result;
    } catch (e) {
      debugPrint('❌ Security Check fehlgeschlagen: $e');
      rethrow;
    }
  }

  /// Ist das Gerät root/jailbroken?
  Future<bool> isRooted() async {
    final result = await ffi.raspCheckRoot();
    return result == 2; // 2 = compromised
  }

  /// Wird die App debugged?
  Future<bool> isDebugged() async {
    final result = await ffi.raspCheckDebugger();
    return result == 2; // 2 = compromised
  }

  /// Läuft die App in einem Emulator?
  Future<bool> isEmulated() async {
    final result = await ffi.raspCheckEmulator();
    return result >= 1; // 1 = suspicious, 2 = compromised
  }

  // =========================================================================
  // PROPERTIES
  // =========================================================================

  bool get isInitialized => _initialized;

  List<String> get loadedPlugins => List.from(_loadedPlugins);

  int get pluginCount => _loadedPlugins.length;
}

// ============================================================================
// MODELS & EXCEPTIONS
// ============================================================================

/// Ergebnis eines RASP-Checks
class RaspCheckResult {
  final int root;
  final int debugger;
  final int emulator;
  final int tamper;

  RaspCheckResult({
    required this.root,
    required this.debugger,
    required this.emulator,
    required this.tamper,
  });

  bool get isSafe => root == 0 && debugger == 0 && emulator == 0 && tamper == 0;

  bool get isCompromised => root == 2 || debugger == 2;

  bool get isSuspicious => !isSafe && !isCompromised;

  @override
  String toString() => 'RaspCheckResult(root: $root, debugger: $debugger, emulator: $emulator, tamper: $tamper)';
}

/// Exception für Connectias Fehler
class ConnectiasException implements Exception {
  final String message;
  final Object? originalError;

  ConnectiasException(this.message, [this.originalError]);

  @override
  String toString() => 'ConnectiasException: $message${originalError != null ? '\nCause: $originalError' : ''}';
}

/// Exception für Security Violations
class ConnectiasSecurityException implements Exception {
  final String message;
  final ffi.RaspStatus? raspStatus;

  ConnectiasSecurityException(this.message, [this.raspStatus]);

  @override
  String toString() => 'ConnectiasSecurityException: $message${raspStatus != null ? '\nStatus: $raspStatus' : ''}';
}

// ============================================================================
// SINGLETON ACCESS
// ============================================================================

/// Globale ConnectiasService Instance
final connectiasService = ConnectiasService();

/// Convenience Function für Plugin-Ausführung
Future<String> executeConnectiasPlugin(
  String pluginId,
  String command,
  Map<String, String> args,
) async {
  return connectiasService.executePlugin(pluginId, command, args);
}

/// Convenience Function für Plugin-Laden
Future<String> loadConnectiasPlugin(String pluginPath) async {
  return connectiasService.loadPlugin(pluginPath);
}
