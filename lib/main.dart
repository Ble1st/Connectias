import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'src/core/app/connectias_app.dart';
import 'src/features/logging/services/logging_service.dart';

export 'src/core/app/connectias_app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  LoggingService.instance.init();
  LoggingService.instance.i('App', 'App gestartet – Testlog');
  runApp(const ProviderScope(child: ConnectiasApp()));
}
