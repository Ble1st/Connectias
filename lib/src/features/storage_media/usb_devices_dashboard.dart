import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'usb_bridge/usb_bridge.dart';
import 'usb_bridge/usb_device_event.dart';
import 'usb_bridge/usb_device_info.dart';

/// Dashboard displaying connected USB devices with live updates.
class UsbDevicesDashboard extends StatefulWidget {
  const UsbDevicesDashboard({super.key});

  @override
  State<UsbDevicesDashboard> createState() => _UsbDevicesDashboardState();
}

class _UsbDevicesDashboardState extends State<UsbDevicesDashboard> {
  final UsbBridge _usbBridge = UsbBridge();
  List<UsbDeviceInfo> _devices = [];
  StreamSubscription<UsbDeviceEvent>? _eventSubscription;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadDevices();
    _listenToDeviceEvents();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }

  Future<void> _loadDevices() async {
    try {
      final devices = await _usbBridge.getDevices();
      if (mounted) {
        setState(() {
          _devices = devices;
          _error = null;
        });
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() {
          _error = e.message ?? 'Failed to load USB devices';
        });
      }
    }
  }

  void _listenToDeviceEvents() {
    _eventSubscription = _usbBridge.deviceEvents.listen((event) {
      if (mounted) {
        _loadDevices();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
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
                _error!,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _loadDevices,
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_devices.isEmpty) {
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
                onPressed: _loadDevices,
                icon: const Icon(Icons.refresh),
                label: const Text('Refresh'),
              ),
            ],
          ),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadDevices,
      child: ListView.builder(
        padding: const EdgeInsets.all(16.0),
        itemCount: _devices.length,
        itemBuilder: (context, index) {
          final device = _devices[index];
          return Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: ListTile(
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
            ),
          );
        },
      ),
    );
  }
}
