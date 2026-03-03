import 'package:flutter/material.dart';

import '../../core/app/app_router.dart';

/// Main dashboard screen with module tiles.
class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Connectias'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: GridView.count(
          crossAxisCount: 2,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.2,
          children: [
            _DashboardTile(
              title: 'Storage & Media',
              icon: Icons.storage,
              route: AppRouter.storageMedia,
            ),
            _DashboardTile(
              title: 'Network',
              icon: Icons.network_wifi,
              route: AppRouter.network,
            ),
            _DashboardTile(
              title: 'DNS',
              icon: Icons.dns,
              route: AppRouter.dns,
            ),
            _DashboardTile(
              title: 'Password',
              icon: Icons.lock,
              route: AppRouter.password,
            ),
            _DashboardTile(
              title: 'Scanner',
              icon: Icons.scanner,
              route: AppRouter.scanner,
            ),
            _DashboardTile(
              title: 'Notes',
              icon: Icons.note,
              route: AppRouter.notes,
            ),
            _DashboardTile(
              title: 'Settings',
              icon: Icons.settings,
              route: AppRouter.settings,
            ),
          ],
        ),
      ),
    );
  }
}

class _DashboardTile extends StatelessWidget {
  const _DashboardTile({
    required this.title,
    required this.icon,
    required this.route,
  });

  final String title;
  final IconData icon;
  final String route;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: () => Navigator.pushNamed(context, route),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 40, color: Theme.of(context).colorScheme.primary),
              const SizedBox(height: 8),
              Text(
                title,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
