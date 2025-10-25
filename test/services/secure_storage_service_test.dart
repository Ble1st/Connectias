/// Secure Storage Service Tests
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:connectias/services/secure_storage_service.dart';

void main() {
  group('SecureStorageService', () {
    late SecureStorageService service;

    setUp(() {
      service = SecureStorageService();
    });

    tearDown(() async {
      await service.clear();
    });

    test('Save and retrieve secure value', () async {
      await service.saveSecure('key', 'value');
      final value = await service.getSecure('key');
      expect(value, 'value');
    });

    test('hasKey returns correct result', () async {
      await service.saveSecure('key', 'value');
      expect(await service.hasKey('key'), true);
      expect(await service.hasKey('nonexistent'), false);
    });

    test('Delete removes value', () async {
      await service.saveSecure('key', 'value');
      await service.deleteSecure('key');
      expect(await service.hasKey('key'), false);
    });

    test('Save and retrieve JSON', () async {
      final data = {'test': 'value', 'number': 42};
      await service.saveJson('json_key', data);
      final retrieved = await service.getJson(
        'json_key',
        (json) => json as Map<String, dynamic>,
      );
      expect(retrieved?['test'], 'value');
    });

    test('Clear removes all values', () async {
      await service.saveSecure('key1', 'value1');
      await service.saveSecure('key2', 'value2');
      await service.clear();
      expect(await service.hasKey('key1'), false);
      expect(await service.hasKey('key2'), false);
    });
  });
}
//ich diene der aktualisierung wala
