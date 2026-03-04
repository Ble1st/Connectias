import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../logging/services/logging_service.dart';
import '../data/usb_device_info.dart';
import '../data/usb_devices_repository.dart';
import '../domain/list_usb_devices_use_case.dart';
import '../services/usb_bridge.dart';

/// UI state for the USB devices dashboard.
class UsbDevicesState {
  const UsbDevicesState({
    this.isLoading = false,
    this.devices = const [],
    this.errorMessage,
  });

  final bool isLoading;
  final List<UsbDeviceInfo> devices;
  final String? errorMessage;

  static const UsbDevicesState initial = UsbDevicesState();
}

final usbBridgeProvider = Provider<UsbBridge>((ref) => UsbBridge());

final usbDevicesRepositoryProvider = Provider<UsbDevicesRepository>((ref) {
  return UsbDevicesRepository(ref.watch(usbBridgeProvider));
});

final listUsbDevicesUseCaseProvider = Provider<ListUsbDevicesUseCase>((ref) {
  return ListUsbDevicesUseCase(ref.watch(usbDevicesRepositoryProvider));
});

final usbDevicesViewModelProvider =
    NotifierProvider<UsbDevicesViewModel, UsbDevicesState>(UsbDevicesViewModel.new);

class UsbDevicesViewModel extends Notifier<UsbDevicesState> {
  StreamSubscription<dynamic>? _eventSubscription;

  @override
  UsbDevicesState build() {
    final repository = ref.read(usbDevicesRepositoryProvider);
    _eventSubscription = repository.deviceEvents.listen((_) {
      refresh();
    });
    ref.onDispose(() {
      _eventSubscription?.cancel();
    });
    refresh();
    return UsbDevicesState.initial;
  }

  /// Reloads the device list from the repository.
  Future<void> refresh() async {
    state = UsbDevicesState(isLoading: true, devices: state.devices, errorMessage: null);

    final useCase = ref.read(listUsbDevicesUseCaseProvider);
    try {
      final devices = await useCase.call();
      state = UsbDevicesState(devices: devices, errorMessage: null);
    } on UsbDevicesRepositoryException catch (e) {
      LoggingService.instance.e('UsbDevicesViewModel', e.message);
      state = UsbDevicesState(
        devices: state.devices,
        errorMessage: e.message,
      );
    } catch (e, st) {
      LoggingService.instance.e('UsbDevicesViewModel', '${e.toString()}\n$st');
      state = UsbDevicesState(
        devices: state.devices,
        errorMessage: e.toString(),
      );
    }
  }

  /// Requests USB permission for the device (via Kotlin bridge). Returns true if granted.
  Future<bool> requestPermission(String deviceId) async {
    final bridge = ref.read(usbBridgeProvider);
    try {
      return await bridge.requestPermission(deviceId);
    } catch (e, st) {
      LoggingService.instance.e('UsbDevicesViewModel', 'requestPermission: ${e.toString()}\n$st');
      rethrow;
    }
  }

  /// Checks whether USB permission was granted for the device (via Kotlin bridge).
  Future<bool> hasPermission(String deviceId) async {
    final bridge = ref.read(usbBridgeProvider);
    return await bridge.hasPermission(deviceId);
  }
}
