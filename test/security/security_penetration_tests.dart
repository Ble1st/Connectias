/// Security Penetration Tests – Sicherheitstests
library;

import 'package:connectias/ffi/connectias_bindings.dart';
import 'package:connectias/services/connectias_service.dart';
import 'package:flutter/foundation.dart';

void main() {
  group('Security Tests', () {
    test('Cannot execute without initialization', () {
      final service = ConnectiasService();
      expect(
        () => service.loadPlugin('/fake/path'),
        throwsA(isA<StateError>()),
      );
    });

    test('RASP detects compromised environment', () async {
      final service = ConnectiasService();
      await service.init();
      
      final rooted = await service.isRooted();
      final debugged = await service.isDebugged();
      
      print('✅ Rooted: $rooted, Debugged: $debugged');
      
      await service.dispose();
    });
  });
}
//ich diene der aktualisierung wala
