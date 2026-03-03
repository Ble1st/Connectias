import 'package:flutter/material.dart';

import '../theme/app_theme.dart';
import 'app_router.dart';
import '../../features/dashboard/dashboard_screen.dart';
import '../../features/storage_media/storage_media_screen.dart';
import '../../features/network/network_screen.dart';
import '../../features/dns/dns_screen.dart';
import '../../features/password/password_screen.dart';
import '../../features/scanner/scanner_screen.dart';
import '../../features/notes/notes_screen.dart';
import '../../features/settings/settings_screen.dart';

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
        AppRouter.network: (_) => const NetworkScreen(),
        AppRouter.dns: (_) => const DnsScreen(),
        AppRouter.password: (_) => const PasswordScreen(),
        AppRouter.scanner: (_) => const ScannerScreen(),
        AppRouter.notes: (_) => const NotesScreen(),
        AppRouter.settings: (_) => const SettingsScreen(),
      },
    );
  }
}
