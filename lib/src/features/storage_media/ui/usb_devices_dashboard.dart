import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/app/app_router.dart';
import '../data/usb_device_info.dart';
import '../view_model/usb_devices_view_model.dart';

/// Dashboard displaying connected USB devices with live updates.
class UsbDevicesDashboard extends ConsumerWidget {
  const UsbDevicesDashboard({super.key});

  static void _openFileExplorer(BuildContext context, UsbDeviceInfo device) {
    Navigator.of(context).pushNamed(
      AppRouter.fileExplorer,
      arguments: {
        'deviceId': device.deviceId,
        'deviceName': device.productName?.isNotEmpty == true
            ? device.productName
            : 'USB Device',
      },
    );
  }

  static Future<void> _checkPermission(
    BuildContext context,
    UsbDevicesViewModel viewModel,
    String deviceId,
  ) async {
    final granted = await viewModel.hasPermission(deviceId);
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          granted
              ? 'Berechtigung erteilt'
              : 'Keine Berechtigung für dieses USB-Gerät',
        ),
      ),
    );
  }

  static Future<void> _requestPermission(
    BuildContext context,
    UsbDevicesViewModel viewModel,
    String deviceId,
  ) async {
    final granted = await viewModel.requestPermission(deviceId);
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          granted
              ? 'Berechtigung erteilt'
              : 'Berechtigung verweigert oder abgelaufen',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(usbDevicesViewModelProvider);
    final viewModel = ref.read(usbDevicesViewModelProvider.notifier);

    if (state.errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.error_outline,
                size: 48,
                color: Theme.of(context).colorScheme.error,
              ),
              const SizedBox(height: 16),
              Text(
                state.errorMessage!,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => viewModel.refresh(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (state.devices.isEmpty && !state.isLoading) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.usb,
                size: 64,
                color: Theme.of(context).colorScheme.outline,
              ),
              const SizedBox(height: 16),
              Text(
                'No USB devices connected',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(
                'Connect a USB device to see it here. The list updates automatically.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.outline,
                    ),
              ),
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: () => viewModel.refresh(),
                icon: const Icon(Icons.refresh),
                label: const Text('Refresh'),
              ),
            ],
          ),
        ),
      );
    }

    if (state.isLoading && state.devices.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }

    return RefreshIndicator(
      onRefresh: () => viewModel.refresh(),
      child: Stack(
        children: [
          ListView.builder(
            padding: const EdgeInsets.all(16.0),
            itemCount: state.devices.length,
            itemBuilder: (context, index) {
              final device = state.devices[index];
              return Card(
                margin: const EdgeInsets.only(bottom: 12),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(
                          Icons.usb,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        title: Text(
                          device.productName?.isNotEmpty == true
                              ? device.productName!
                              : 'USB Device',
                        ),
                        subtitle: Text(
                          'VID: 0x${device.vendorId.toRadixString(16).padLeft(4, '0').toUpperCase()} '
                          'PID: 0x${device.productId.toRadixString(16).padLeft(4, '0').toUpperCase()}',
                        ),
                        trailing: const Icon(Icons.folder_open),
                        onTap: () => _openFileExplorer(context, device),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          OutlinedButton.icon(
                            onPressed: () => _checkPermission(context, viewModel, device.deviceId),
                            icon: const Icon(Icons.check_circle_outline, size: 18),
                            label: const Text('Berechtigung prüfen'),
                          ),
                          const SizedBox(width: 8),
                          FilledButton.tonalIcon(
                            onPressed: () => _requestPermission(context, viewModel, device.deviceId),
                            icon: const Icon(Icons.lock_open, size: 18),
                            label: const Text('Berechtigung anfragen'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
          if (state.isLoading)
            const Positioned(
              top: 16,
              left: 0,
              right: 0,
              child: Center(child: LinearProgressIndicator()),
            ),
        ],
      ),
    );
  }
}
