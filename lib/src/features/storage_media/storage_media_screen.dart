import 'package:flutter/material.dart';

import 'usb_devices_dashboard.dart';

/// Storage & Media screen containing the USB devices dashboard.
class StorageMediaScreen extends StatelessWidget {
  const StorageMediaScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Storage & Media'),
      ),
      body: const UsbDevicesDashboard(),
    );
  }
}
