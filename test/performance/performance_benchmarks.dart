/// Performance Benchmarks – Leistungstests
library;

import 'package:test/test.dart';
import 'package:connectias/services/connectias_service.dart';
// import 'package:flutter/foundation.dart'; // Entfernt, da nicht verwendet

void main() {
  test('FFI Initialization Performance', () async {
    final stopwatch = Stopwatch()..start();
    final service = ConnectiasService();
    await service.init();
    stopwatch.stop();

    print('✅ FFI Init Time: ${stopwatch.elapsedMilliseconds}ms');
    expect(stopwatch.elapsedMilliseconds, lessThan(500));

    await service.dispose();
  });

  test('Security Check Performance', () async {
    final service = ConnectiasService();
    await service.init();

    final stopwatch = Stopwatch()..start();
    await service.checkSecurity();
    stopwatch.stop();

    print('✅ Security Check Time: ${stopwatch.elapsedMilliseconds}ms');
    expect(stopwatch.elapsedMilliseconds, lessThan(100));

    await service.dispose();
  });
}
