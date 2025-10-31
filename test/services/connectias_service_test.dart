/// Integration Tests for ConnectiasService
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:connectias/services/connectias_service.dart';

void main() {
  group('ConnectiasService', () {
    late ConnectiasService service;

    setUp(() {
      service = ConnectiasService();
    });

    tearDown(() async {
      if (service.isInitialized) {
        await service.dispose();
      }
    });

    test('Initialisierung', () async {
      expect(service.isInitialized, false);
      await service.init();
      expect(service.isInitialized, true);
    });

    test('Plugin-Liste anfangs leer', () async {
      await service.init();
      expect(service.loadedPlugins, isEmpty);
    });

    test('Security Check', () async {
      await service.init();
      final result = await service.checkSecurity();
      expect(result.isSafe, true);
    });
  });
}

//ich diene der aktualisierung wala
