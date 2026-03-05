import 'package:flutter/material.dart';

import '../../storage_media/data/usb_device_info.dart';
import '../../storage_media/services/usb_bridge.dart';
import '../../../core/app/app_router.dart';
import '../services/dvd_service.dart';

/// Screen to select an optical drive for DVD playback.
class DvdDeviceScreen extends StatefulWidget {
  const DvdDeviceScreen({super.key});

  @override
  State<DvdDeviceScreen> createState() => _DvdDeviceScreenState();
}

class _DvdDeviceScreenState extends State<DvdDeviceScreen> {
  final _usbBridge = UsbBridge();
  final _dvdService = DvdService();
  List<UsbDeviceInfo> _devices = [];
  List<String> _opticalIds = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadDevices();
  }

  Future<void> _loadDevices() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final devices = await _usbBridge.getDevices();
      final optical = <String>[];
      for (final d in devices) {
        try {
          final hasPermission = await _usbBridge.hasPermission(d.deviceId);
          if (!hasPermission) {
            final granted = await _usbBridge.requestPermission(d.deviceId);
            if (!granted) continue;
          }
          final type = await _dvdService.getDeviceType(d.deviceId);
          if (type == 'optical') {
            optical.add(d.deviceId);
          }
        } catch (_) {
          // Skip device on error
        }
      }
      if (mounted) {
        setState(() {
          _devices = devices;
          _opticalIds = optical;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('DVD-Player'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.error_outline, size: 48, color: Theme.of(context).colorScheme.error),
                        const SizedBox(height: 16),
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        FilledButton(
                          onPressed: _loadDevices,
                          child: const Text('Erneut versuchen'),
                        ),
                      ],
                    ),
                  ),
                )
              : _opticalIds.isEmpty
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.disc_full, size: 64, color: Theme.of(context).colorScheme.outline),
                            const SizedBox(height: 16),
                            Text(
                              'Kein optisches Laufwerk gefunden',
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Schließe ein USB-DVD-Laufwerk an.',
                              textAlign: TextAlign.center,
                            ),
                            const SizedBox(height: 24),
                            FilledButton.tonal(
                              onPressed: _loadDevices,
                              child: const Text('Aktualisieren'),
                            ),
                          ],
                        ),
                      ),
                    )
                  : ListView.builder(
                      itemCount: _opticalIds.length,
                      itemBuilder: (context, i) {
                        final deviceId = _opticalIds[i];
                        final d = _devices.firstWhere((x) => x.deviceId == deviceId, orElse: () => UsbDeviceInfo(deviceId: deviceId, vendorId: 0, productId: 0));
                        return ListTile(
                          leading: const Icon(Icons.disc_full),
                          title: Text(d.productName?.isNotEmpty == true ? d.productName! : 'DVD-Laufwerk'),
                          subtitle: Text(d.deviceId),
                          onTap: () {
                            Navigator.pushNamed(
                              context,
                              AppRouter.dvdPlayer,
                              arguments: {
                                'deviceId': deviceId,
                                'deviceName': d.productName?.isNotEmpty == true ? d.productName! : 'DVD',
                              },
                            );
                          },
                        );
                      },
                    ),
    );
  }
}
