import '../data/usb_device_info.dart';
import '../data/usb_devices_repository.dart';

/// Use case: list currently connected USB devices.
class ListUsbDevicesUseCase {
  ListUsbDevicesUseCase(this._repository);

  final UsbDevicesRepository _repository;

  /// Returns the list of currently connected USB devices.
  Future<List<UsbDeviceInfo>> call() => _repository.getDevices();
}
