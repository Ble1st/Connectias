/// Connectias Service – High-Level Dart API
///
/// Sichere Wrapper um FFI Bridge mit automatischem Cleanup,
/// Error Handling, und Lifecycle Management.
library;

import 'package:flutter/foundation.dart';
import '../ffi/connectias_bindings.dart' as ffi;
import '../models/plugin_model.dart';

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
        throw ConnectiasSecurityException('Gerät ist kompromittiert!', status);
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
      throw StateError(
        'ConnectiasService nicht initialisiert. Rufe init() auf!',
      );
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

  /// Hole Plugins als PluginModel Liste
  Future<List<PluginModel>> fetchPlugins() async {
    _requireInitialized();

    final pluginIds = await listPlugins();
    return pluginIds.map((id) {
      final metadata = getPluginMetadata(id);
      return PluginModel(
        id: id,
        name: metadata?['name'] ?? id,
        version: metadata?['version'] ?? '1.0.0',
        author: metadata?['author'] ?? 'Unknown',
        category: metadata?['category'] ?? 'General',
        description: metadata?['description'] ?? 'No description',
        status: PluginStatus.active,
        permissions: List<String>.from(metadata?['permissions'] ?? []),
        lastUsed: metadata?['loadedAt'] as DateTime? ?? DateTime.now(),
        memoryUsage: metadata?['memoryUsage'] as int? ?? 0,
        isEnabled: true,
        metadata: metadata,
      );
    }).toList();
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

  // =========================================================================
  // ENHANCED SERVICE INTEGRATION
  // =========================================================================

  /// Lade ein Plugin mit erweiterten Services
  ///
  /// Unterstützt:
  /// - Storage Service Integration
  /// - Network Service Integration
  /// - Permission Service Integration
  /// - Monitoring Service Integration
  Future<String> loadPluginWithServices(
    String pluginPath, {
    required List<String> permissions,
    Map<String, dynamic>? config,
  }) async {
    if (!_initialized) {
      throw ConnectiasException('Service nicht initialisiert');
    }

    debugPrint('📦 Lade Plugin mit Services: $pluginPath');
    debugPrint('🔐 Berechtigungen: $permissions');
    debugPrint('⚙️ Konfiguration: $config');

    try {
      // 1. Permission Service: Berechtigungen prüfen
      await _validatePermissions(permissions);

      // 2. Plugin laden
      final pluginId = await _loadPluginInternal(pluginPath);

      // 3. Services initialisieren
      await _initializePluginServices(pluginId, permissions, config);

      // 4. Monitoring starten
      await _startPluginMonitoring(pluginId);

      _loadedPlugins.add(pluginId);
      notifyListeners();

      debugPrint('✅ Plugin mit Services geladen: $pluginId');
      return pluginId;
    } catch (e) {
      debugPrint('❌ Plugin-Loading mit Services fehlgeschlagen: $e');
      throw ConnectiasException('Plugin-Loading fehlgeschlagen: $e');
    }
  }

  /// Führe Plugin mit Service-Integration aus
  Future<Map<String, dynamic>> executePluginWithServices(
    String pluginId,
    String command,
    Map<String, dynamic> args, {
    bool useStorage = false,
    bool useNetwork = false,
    bool trackPerformance = true,
  }) async {
    if (!_initialized) {
      throw ConnectiasException('Service nicht initialisiert');
    }

    if (!_loadedPlugins.contains(pluginId)) {
      throw ConnectiasException('Plugin nicht geladen: $pluginId');
    }

    debugPrint('⚙️ Führe Plugin mit Services aus: $pluginId');
    debugPrint('📋 Command: $command');
    debugPrint('🔧 Args: $args');

    try {
      final startTime = DateTime.now();

      // 1. Pre-Execution: Services vorbereiten
      if (useStorage) {
        await _prepareStorageService(pluginId);
      }
      if (useNetwork) {
        await _prepareNetworkService(pluginId);
      }

      // 2. Plugin ausführen
      final result = await _executePluginInternal(pluginId, command, args);

      // 3. Post-Execution: Services aufräumen
      if (useStorage) {
        await _cleanupStorageService(pluginId);
      }
      if (useNetwork) {
        await _cleanupNetworkService(pluginId);
      }

      // 4. Performance-Tracking
      if (trackPerformance) {
        final executionTime = DateTime.now().difference(startTime);
        await _recordPerformanceMetrics(pluginId, command, executionTime);
      }

      debugPrint('✅ Plugin mit Services ausgeführt: $pluginId');
      return {'result': result};
    } catch (e) {
      debugPrint('❌ Plugin-Ausführung mit Services fehlgeschlagen: $e');
      throw ConnectiasException('Plugin-Ausführung fehlgeschlagen: $e');
    }
  }

  /// Hole Threat Events für Security Dashboard
  Future<List<ThreatEvent>> getThreatEvents() async {
    if (!_initialized) {
      throw ConnectiasException('Service nicht initialisiert');
    }

    debugPrint('🔍 Hole Threat Events...');

    try {
      // Simuliere Threat Events für Demo
      final events = <ThreatEvent>[
        ThreatEvent(
          pluginId: 'demo-plugin-1',
          description: 'Suspicious network activity detected',
          severity: 'medium',
          timestamp: DateTime.now()
              .subtract(const Duration(minutes: 5))
              .toIso8601String(),
          indicators: ['network_anomaly', 'high_frequency'],
        ),
        ThreatEvent(
          pluginId: 'demo-plugin-2',
          description: 'Resource abuse detected',
          severity: 'high',
          timestamp: DateTime.now()
              .subtract(const Duration(minutes: 15))
              .toIso8601String(),
          indicators: ['cpu_abuse', 'memory_leak'],
        ),
      ];

      debugPrint('✅ Threat Events abgerufen: ${events.length}');
      return events;
    } catch (e) {
      debugPrint('❌ Threat Events-Abruf fehlgeschlagen: $e');
      throw ConnectiasException('Threat Events-Abruf fehlgeschlagen: $e');
    }
  }

  /// Hole Plugin Security Info für Security Dashboard
  Future<List<PluginSecurityInfo>> getPluginSecurityInfo() async {
    if (!_initialized) {
      throw ConnectiasException('Service nicht initialisiert');
    }

    debugPrint('🔐 Hole Plugin Security Info...');

    try {
      // Simuliere Plugin Security Info für Demo
      final securityInfo = <PluginSecurityInfo>[
        PluginSecurityInfo(
          pluginId: 'demo-plugin-1',
          isSecure: true,
          permissions: ['storage:read', 'network:https'],
          threatCount: 0,
          securityScore: 0.95,
        ),
        PluginSecurityInfo(
          pluginId: 'demo-plugin-2',
          isSecure: false,
          permissions: ['storage:write', 'network:http', 'system:admin'],
          threatCount: 3,
          securityScore: 0.45,
        ),
      ];

      debugPrint('✅ Plugin Security Info abgerufen: ${securityInfo.length}');
      return securityInfo;
    } catch (e) {
      debugPrint('❌ Plugin Security Info-Abruf fehlgeschlagen: $e');
      throw ConnectiasException(
        'Plugin Security Info-Abruf fehlgeschlagen: $e',
      );
    }
  }

  /// Hole Security Metrics für Security Dashboard
  Future<SecurityMetrics> getSecurityMetrics() async {
    if (!_initialized) {
      throw ConnectiasException('Service nicht initialisiert');
    }

    debugPrint('📊 Hole Security Metrics...');

    try {
      // Simuliere Security Metrics für Demo
      final metrics = SecurityMetrics(
        threatsBlocked: 42,
        pluginsMonitored: _loadedPlugins.length,
        rateLimitsHit: 8,
        securityScore: 0.87,
        lastUpdate: DateTime.now(),
      );

      debugPrint('✅ Security Metrics abgerufen');
      return metrics;
    } catch (e) {
      debugPrint('❌ Security Metrics-Abruf fehlgeschlagen: $e');
      throw ConnectiasException('Security Metrics-Abruf fehlgeschlagen: $e');
    }
  }

  /// Hole Plugin-Statistiken mit Service-Details
  Future<Map<String, dynamic>> getPluginStatsWithServices(
    String pluginId,
  ) async {
    if (!_initialized) {
      throw ConnectiasException('Service nicht initialisiert');
    }

    if (!_loadedPlugins.contains(pluginId)) {
      throw ConnectiasException('Plugin nicht geladen: $pluginId');
    }

    debugPrint('📊 Hole Plugin-Statistiken mit Services: $pluginId');

    try {
      final stats = <String, dynamic>{};

      // 1. Basis-Plugin-Info
      stats['pluginId'] = pluginId;
      stats['loadedAt'] = _pluginMetadata[pluginId]?['loadedAt'];

      // 2. Performance-Metriken
      final performanceMetrics = await _getPerformanceMetrics(pluginId);
      stats['performance'] = performanceMetrics;

      // 3. Storage-Statistiken
      final storageStats = await _getStorageStats(pluginId);
      stats['storage'] = storageStats;

      // 4. Network-Statistiken
      final networkStats = await _getNetworkStats(pluginId);
      stats['network'] = networkStats;

      // 5. Permission-Status
      final permissionStatus = await _getPermissionStatus(pluginId);
      stats['permissions'] = permissionStatus;

      debugPrint('✅ Plugin-Statistiken mit Services abgerufen: $pluginId');
      return stats;
    } catch (e) {
      debugPrint('❌ Plugin-Statistiken-Abruf fehlgeschlagen: $e');
      throw ConnectiasException('Plugin-Statistiken-Abruf fehlgeschlagen: $e');
    }
  }

  // =========================================================================
  // SERVICE INTEGRATION HELPERS
  // =========================================================================

  /// Validiere Plugin-Berechtigungen
  Future<void> _validatePermissions(List<String> permissions) async {
    debugPrint('🔐 Validiere Berechtigungen: $permissions');

    // Hier würde die echte Permission-Validierung stattfinden
    // Für jetzt simulieren wir die Validierung
    for (final permission in permissions) {
      if (permission.startsWith('system:')) {
        throw ConnectiasException(
          'System-Berechtigungen nicht erlaubt: $permission',
        );
      }
    }

    debugPrint('✅ Berechtigungen validiert');
  }

  /// Initialisiere Plugin-Services
  Future<void> _initializePluginServices(
    String pluginId,
    List<String> permissions,
    Map<String, dynamic>? config,
  ) async {
    debugPrint('🔧 Initialisiere Services für Plugin: $pluginId');

    // 1. Storage Service initialisieren
    if (permissions.contains('storage:read') ||
        permissions.contains('storage:write')) {
      await _initializeStorageService(pluginId, config);
    }

    // 2. Network Service initialisieren
    if (permissions.contains('network:https') ||
        permissions.contains('network:http')) {
      await _initializeNetworkService(pluginId, config);
    }

    // 3. Monitoring Service initialisieren
    await _initializeMonitoringService(pluginId);

    debugPrint('✅ Services für Plugin initialisiert: $pluginId');
  }

  /// Starte Plugin-Monitoring
  Future<void> _startPluginMonitoring(String pluginId) async {
    debugPrint('📊 Starte Monitoring für Plugin: $pluginId');

    // Hier würde das echte Monitoring gestartet werden
    // Für jetzt simulieren wir das Monitoring
    _pluginMetadata[pluginId] = {
      'loadedAt': DateTime.now().toIso8601String(),
      'monitoringActive': true,
    };

    debugPrint('✅ Monitoring für Plugin gestartet: $pluginId');
  }

  /// Bereite Storage Service vor
  Future<void> _prepareStorageService(String pluginId) async {
    debugPrint('💾 Bereite Storage Service vor: $pluginId');

    // Hier würde der Storage Service vorbereitet werden
    // Für jetzt simulieren wir die Vorbereitung
    await Future.delayed(const Duration(milliseconds: 10));

    debugPrint('✅ Storage Service vorbereitet: $pluginId');
  }

  /// Bereite Network Service vor
  Future<void> _prepareNetworkService(String pluginId) async {
    debugPrint('🌐 Bereite Network Service vor: $pluginId');

    // Hier würde der Network Service vorbereitet werden
    // Für jetzt simulieren wir die Vorbereitung
    await Future.delayed(const Duration(milliseconds: 10));

    debugPrint('✅ Network Service vorbereitet: $pluginId');
  }

  /// Räume Storage Service auf
  Future<void> _cleanupStorageService(String pluginId) async {
    debugPrint('🧹 Räume Storage Service auf: $pluginId');

    // Hier würde der Storage Service aufgeräumt werden
    // Für jetzt simulieren wir das Cleanup
    await Future.delayed(const Duration(milliseconds: 5));

    debugPrint('✅ Storage Service aufgeräumt: $pluginId');
  }

  /// Räume Network Service auf
  Future<void> _cleanupNetworkService(String pluginId) async {
    debugPrint('🧹 Räume Network Service auf: $pluginId');

    // Hier würde der Network Service aufgeräumt werden
    // Für jetzt simulieren wir das Cleanup
    await Future.delayed(const Duration(milliseconds: 5));

    debugPrint('✅ Network Service aufgeräumt: $pluginId');
  }

  /// Zeichne Performance-Metriken auf
  Future<void> _recordPerformanceMetrics(
    String pluginId,
    String command,
    Duration executionTime,
  ) async {
    debugPrint('📈 Zeichne Performance-Metriken auf: $pluginId');

    // Hier würden die echten Performance-Metriken aufgezeichnet werden
    // Für jetzt simulieren wir das Recording
    final metrics =
        _pluginMetadata[pluginId]?['metrics'] ?? <String, dynamic>{};
    metrics[command] = {
      'executionTime': executionTime.inMilliseconds,
      'timestamp': DateTime.now().toIso8601String(),
    };

    if (_pluginMetadata[pluginId] != null) {
      _pluginMetadata[pluginId]!['metrics'] = metrics;
    }

    debugPrint('✅ Performance-Metriken aufgezeichnet: $pluginId');
  }

  /// Hole Performance-Metriken
  Future<Map<String, dynamic>> _getPerformanceMetrics(String pluginId) async {
    debugPrint('📊 Hole Performance-Metriken: $pluginId');

    // Hier würden die echten Performance-Metriken abgerufen werden
    // Für jetzt simulieren wir die Metriken
    final metrics =
        _pluginMetadata[pluginId]?['metrics'] ?? <String, dynamic>{};

    return {
      'totalExecutions': metrics.length,
      'averageExecutionTime': 100, // Simulierte Durchschnittszeit
      'lastExecution': metrics.isNotEmpty ? metrics.values.last : null,
    };
  }

  /// Hole Storage-Statistiken
  Future<Map<String, dynamic>> _getStorageStats(String pluginId) async {
    debugPrint('💾 Hole Storage-Statistiken: $pluginId');

    // Hier würden die echten Storage-Statistiken abgerufen werden
    // Für jetzt simulieren wir die Statistiken
    return {
      'usedSpace': 1024 * 1024, // 1MB
      'maxSpace': 10 * 1024 * 1024, // 10MB
      'filesCount': 5,
      'lastAccess': DateTime.now().toIso8601String(),
    };
  }

  /// Hole Network-Statistiken
  Future<Map<String, dynamic>> _getNetworkStats(String pluginId) async {
    debugPrint('🌐 Hole Network-Statistiken: $pluginId');

    // Hier würden die echten Network-Statistiken abgerufen werden
    // Für jetzt simulieren wir die Statistiken
    return {
      'requestsCount': 10,
      'bytesTransferred': 50 * 1024, // 50KB
      'averageResponseTime': 200, // 200ms
      'lastRequest': DateTime.now().toIso8601String(),
    };
  }

  /// Hole Permission-Status
  Future<Map<String, dynamic>> _getPermissionStatus(String pluginId) async {
    debugPrint('🔐 Hole Permission-Status: $pluginId');

    // Hier würde der echte Permission-Status abgerufen werden
    // Für jetzt simulieren wir den Status
    return {
      'granted': ['storage:read', 'storage:write', 'network:https'],
      'denied': [],
      'requested': ['storage:read', 'storage:write', 'network:https'],
    };
  }

  /// Initialisiere Storage Service
  Future<void> _initializeStorageService(
    String pluginId,
    Map<String, dynamic>? config,
  ) async {
    debugPrint('💾 Initialisiere Storage Service: $pluginId');

    // Hier würde der echte Storage Service initialisiert werden
    // Für jetzt simulieren wir die Initialisierung
    await Future.delayed(const Duration(milliseconds: 20));

    debugPrint('✅ Storage Service initialisiert: $pluginId');
  }

  /// Initialisiere Network Service
  Future<void> _initializeNetworkService(
    String pluginId,
    Map<String, dynamic>? config,
  ) async {
    debugPrint('🌐 Initialisiere Network Service: $pluginId');

    // Hier würde der echte Network Service initialisiert werden
    // Für jetzt simulieren wir die Initialisierung
    await Future.delayed(const Duration(milliseconds: 20));

    debugPrint('✅ Network Service initialisiert: $pluginId');
  }

  /// Initialisiere Monitoring Service
  Future<void> _initializeMonitoringService(String pluginId) async {
    debugPrint('📊 Initialisiere Monitoring Service: $pluginId');

    // Hier würde der echte Monitoring Service initialisiert werden
    // Für jetzt simulieren wir die Initialisierung
    await Future.delayed(const Duration(milliseconds: 10));

    debugPrint('✅ Monitoring Service initialisiert: $pluginId');
  }

  /// Interne Plugin-Loading-Methode
  Future<String> _loadPluginInternal(String pluginPath) async {
    return await ffi.loadPlugin(pluginPath);
  }

  /// Interne Plugin-Ausführungs-Methode
  Future<String> _executePluginInternal(
    String pluginId,
    String command,
    Map<String, dynamic> args,
  ) async {
    // Konvertiere Map<String, dynamic> zu Map<String, String>
    final stringArgs = args.map(
      (key, value) => MapEntry(key, value.toString()),
    );
    return await ffi.executePlugin(pluginId, command, stringArgs);
  }
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
  String toString() =>
      'RaspCheckResult(root: $root, debugger: $debugger, emulator: $emulator, tamper: $tamper)';
}

/// Exception für Connectias Fehler
class ConnectiasException implements Exception {
  final String message;
  final Object? originalError;

  ConnectiasException(this.message, [this.originalError]);

  @override
  String toString() =>
      'ConnectiasException: $message${originalError != null ? '\nCause: $originalError' : ''}';
}

/// Exception für Security Violations
class ConnectiasSecurityException implements Exception {
  final String message;
  final ffi.RaspStatus? raspStatus;

  ConnectiasSecurityException(this.message, [this.raspStatus]);

  @override
  String toString() =>
      'ConnectiasSecurityException: $message${raspStatus != null ? '\nStatus: $raspStatus' : ''}';
}

// Data Models für erweiterte Security-Features
class ThreatEvent {
  final String pluginId;
  final String description;
  final String severity;
  final String timestamp;
  final List<String> indicators;

  ThreatEvent({
    required this.pluginId,
    required this.description,
    required this.severity,
    required this.timestamp,
    required this.indicators,
  });
}

class PluginSecurityInfo {
  final String pluginId;
  final bool isSecure;
  final List<String> permissions;
  final int threatCount;
  final double securityScore;

  PluginSecurityInfo({
    required this.pluginId,
    required this.isSecure,
    required this.permissions,
    required this.threatCount,
    required this.securityScore,
  });
}

class SecurityMetrics {
  final int threatsBlocked;
  final int pluginsMonitored;
  final int rateLimitsHit;
  final double securityScore;
  final DateTime lastUpdate;

  SecurityMetrics({
    required this.threatsBlocked,
    required this.pluginsMonitored,
    required this.rateLimitsHit,
    required this.securityScore,
    required this.lastUpdate,
  });
}

/// Globale ConnectiasService Instance
final connectiasService = ConnectiasService();

/// Convenience Function für Plugin-Laden
Future<String> loadConnectiasPlugin(String pluginPath) async {
  return connectiasService.loadPlugin(pluginPath);
}
