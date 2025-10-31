/// Permission Service Tests
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:connectias/services/permission_service.dart';

void main() {
  group('PermissionService', () {
    late PermissionService service;

    setUp(() {
      service = PermissionService();
    });

    test('Grant and check permissions', () async {
      final perms = PermissionService.standardPermissions('plugin1');
      await service.grantPermissions(perms);

      expect(
        service.hasPermission('plugin1', PluginPermission.networkAccess),
        true,
      );
    });

    test('Revoke removes permissions', () async {
      final perms = PermissionService.standardPermissions('plugin1');
      await service.grantPermissions(perms);
      await service.revokePermissions('plugin1');

      expect(
        service.hasPermission('plugin1', PluginPermission.networkAccess),
        false,
      );
    });

    test('hasAllPermissions checks all at once', () async {
      final perms = PermissionService.advancedPermissions('plugin1');
      await service.grantPermissions(perms);

      expect(
        service.hasAllPermissions('plugin1', [
          PluginPermission.networkAccess,
          PluginPermission.storageRead,
        ]),
        true,
      );
    });

    test('Audit log records events', () async {
      final perms = PermissionService.standardPermissions('plugin1');
      await service.grantPermissions(perms);

      final log = service.getAuditLog();
      expect(log.isNotEmpty, true);
      expect(log[0].action, 'GRANT');
      expect(log[0].pluginId, 'plugin1');
    });
  });
}

//ich diene der aktualisierung wala
