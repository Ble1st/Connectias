import 'package:flutter/material.dart';

import '../theme/app_theme.dart';
import 'app_router.dart';
import '../../features/dashboard/ui/dashboard_screen.dart';
import '../../features/storage_media/ui/storage_media_screen.dart';
import '../../features/storage_media/ui/file_explorer_screen.dart';
import '../../features/logging/ui/logging_screen.dart';
import '../../features/network/ui/network_screen.dart';
import '../../features/dns/ui/dns_screen.dart';
import '../../features/password/ui/password_screen.dart';
import '../../features/scanner/ui/scanner_screen.dart';
import '../../features/notes/ui/notes_screen.dart';
import '../../features/settings/ui/settings_screen.dart';

/// Root application widget.
class ConnectiasApp extends StatelessWidget {
  const ConnectiasApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Connectias',
      theme: AppTheme.light,
      initialRoute: AppRouter.dashboard,
      routes: {
        AppRouter.dashboard: (_) => const DashboardScreen(),
        AppRouter.storageMedia: (_) => const StorageMediaScreen(),
        AppRouter.fileExplorer: (ctx) {
          final args = ModalRoute.of(ctx)?.settings.arguments as Map<String, dynamic>?;
          return FileExplorerScreen(
            deviceId: args?['deviceId'] as String? ?? '',
            deviceName: args?['deviceName'] as String?,
          );
        },
        AppRouter.network: (_) => const NetworkScreen(),
        AppRouter.dns: (_) => const DnsScreen(),
        AppRouter.password: (_) => const PasswordScreen(),
        AppRouter.scanner: (_) => const ScannerScreen(),
        AppRouter.notes: (_) => const NotesScreen(),
        AppRouter.settings: (_) => const SettingsScreen(),
        AppRouter.logging: (_) => const LoggingScreen(),
      },
    );
  }
}
