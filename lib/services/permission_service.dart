/// Permission Service – Granulare Plugin-Berechtigungen
///
/// Verwaltet RBAC (Role-Based Access Control) und Audit-Logs
library;

import 'package:flutter/foundation.dart';

/// Plugin Permission Level
enum PermissionLevel {
  /// Keine Berechtigungen
  none,

  /// Basis-Berechtigungen (Read, Info)
  basic,

  /// Erweiterte Berechtigungen (Write, Execute)
  advanced,

  /// System-Berechtigungen (System Access)
  system,
}

/// Einzelne Permission
enum PluginPermission {
  // Netzwerk
  networkAccess,
  networkTls,
  networkPinning,

  // Storage
  storageRead,
  storageWrite,
  storageDelete,

  // System
  systemInfo,
  deviceInfo,

  // Kommunikation
  interPluginComm,
  nativeAccess,

  // Sicherheit
  cryptoAccess,
  keyAccess,
}

/// Permission Set für ein Plugin
class PluginPermissionSet {
  final String pluginId;
  final Set<PluginPermission> permissions;
  final PermissionLevel level;
  final DateTime grantedAt;
  final DateTime? expiresAt;

  PluginPermissionSet({
    required this.pluginId,
    required this.permissions,
    required this.level,
    DateTime? grantedAt,
    this.expiresAt,
  }) : grantedAt = grantedAt ?? DateTime.now();

  bool get isExpired => expiresAt != null && DateTime.now().isAfter(expiresAt!);

  Map<String, dynamic> toJson() => {
    'pluginId': pluginId,
    'permissions': permissions.map((p) => p.toString()).toList(),
    'level': level.toString(),
    'grantedAt': grantedAt.toIso8601String(),
    'expiresAt': expiresAt?.toIso8601String(),
  };
}

/// Permission Service
class PermissionService {
  static final PermissionService _instance = PermissionService._internal();

  factory PermissionService() {
    return _instance;
  }

  PermissionService._internal() : _permissions = {};

  final Map<String, PluginPermissionSet> _permissions;
  final List<PermissionAuditEvent> _auditLog = [];

  // =========================================================================
  // PERMISSION GRANTING
  // =========================================================================

  /// Gewähre Permissions für ein Plugin
  Future<void> grantPermissions(PluginPermissionSet permissionSet) async {
    debugPrint('🔑 Gewähre Permissions für: ${permissionSet.pluginId}');

    _permissions[permissionSet.pluginId] = permissionSet;

    _auditLog.add(
      PermissionAuditEvent(
        action: 'GRANT',
        pluginId: permissionSet.pluginId,
        permissions: permissionSet.permissions.toList(),
        timestamp: DateTime.now(),
      ),
    );

    debugPrint('✅ Permissions gewährt: ${permissionSet.permissions.length}');
  }

  /// Entziehe Permissions
  Future<void> revokePermissions(String pluginId) async {
    debugPrint('🚫 Entziehe Permissions für: $pluginId');

    final oldPerms = _permissions.remove(pluginId);

    if (oldPerms != null) {
      _auditLog.add(
        PermissionAuditEvent(
          action: 'REVOKE',
          pluginId: pluginId,
          permissions: oldPerms.permissions.toList(),
          timestamp: DateTime.now(),
        ),
      );
      debugPrint('✅ Permissions entzogen');
    }
  }

  /// Prüfe einzelne Permission
  bool hasPermission(String pluginId, PluginPermission permission) {
    final permSet = _permissions[pluginId];
    if (permSet == null) {
      return false;
    }

    if (permSet.isExpired) {
      debugPrint('⚠️ Permissions abgelaufen für: $pluginId');
      return false;
    }

    return permSet.permissions.contains(permission);
  }

  /// Prüfe alle Permissions
  bool hasAllPermissions(String pluginId, List<PluginPermission> permissions) {
    return permissions.every((p) => hasPermission(pluginId, p));
  }

  /// Hole Permission Set
  PluginPermissionSet? getPermissions(String pluginId) {
    return _permissions[pluginId];
  }

  // =========================================================================
  // AUDIT
  // =========================================================================

  /// Hole Audit Log
  List<PermissionAuditEvent> getAuditLog({String? pluginId, DateTime? since}) {
    var events = List<PermissionAuditEvent>.from(_auditLog);

    if (pluginId != null) {
      events = events.where((e) => e.pluginId == pluginId).toList();
    }

    if (since != null) {
      events = events.where((e) => e.timestamp.isAfter(since)).toList();
    }

    return events;
  }

  /// Lösche Audit Log
  void clearAuditLog() {
    _auditLog.clear();
    debugPrint('🗑️ Audit Log gelöscht');
  }

  // =========================================================================
  // PRESETS
  // =========================================================================

  /// Standard Permission Set (Read-Only)
  static PluginPermissionSet standardPermissions(String pluginId) {
    return PluginPermissionSet(
      pluginId: pluginId,
      permissions: {
        PluginPermission.networkAccess,
        PluginPermission.systemInfo,
      },
      level: PermissionLevel.basic,
    );
  }

  /// Erweiterte Permission Set
  static PluginPermissionSet advancedPermissions(String pluginId) {
    return PluginPermissionSet(
      pluginId: pluginId,
      permissions: {
        PluginPermission.networkAccess,
        PluginPermission.networkTls,
        PluginPermission.storageRead,
        PluginPermission.storageWrite,
        PluginPermission.systemInfo,
        PluginPermission.interPluginComm,
      },
      level: PermissionLevel.advanced,
    );
  }

  /// System Permission Set (Vorsicht!)
  static PluginPermissionSet systemPermissions(String pluginId) {
    return PluginPermissionSet(
      pluginId: pluginId,
      permissions: {
        PluginPermission.networkAccess,
        PluginPermission.networkTls,
        PluginPermission.networkPinning,
        PluginPermission.storageRead,
        PluginPermission.storageWrite,
        PluginPermission.storageDelete,
        PluginPermission.systemInfo,
        PluginPermission.deviceInfo,
        PluginPermission.interPluginComm,
        PluginPermission.cryptoAccess,
      },
      level: PermissionLevel.system,
    );
  }
}

/// Audit Event
class PermissionAuditEvent {
  final String action; // GRANT, REVOKE, DENY
  final String pluginId;
  final List<PluginPermission> permissions;
  final DateTime timestamp;

  PermissionAuditEvent({
    required this.action,
    required this.pluginId,
    required this.permissions,
    required this.timestamp,
  });

  @override
  String toString() =>
      '$action: $pluginId (${permissions.length} perms) @ $timestamp';
}

/// Globale Instanz
final permissionService = PermissionService();
//ich diene der aktualisierung wala
