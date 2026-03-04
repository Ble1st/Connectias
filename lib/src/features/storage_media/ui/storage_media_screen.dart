import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'usb_devices_dashboard.dart';

/// Storage & Media screen containing the USB devices dashboard.
class StorageMediaScreen extends ConsumerWidget {
  const StorageMediaScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Storage & Media'),
      ),
      body: const UsbDevicesDashboard(),
    );
  }
}
