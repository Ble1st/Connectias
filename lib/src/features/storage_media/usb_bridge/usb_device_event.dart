/// Event emitted when a USB device is attached or detached.
enum UsbDeviceEventType {
  attached,
  detached,
}

/// Event for USB device attach/detach.
class UsbDeviceEvent {
  const UsbDeviceEvent({
    required this.type,
    this.deviceId,
  });

  final UsbDeviceEventType type;
  final String? deviceId;

  factory UsbDeviceEvent.fromMap(Map<Object?, Object?> map) {
    final typeStr = map['type'] as String? ?? '';
    return UsbDeviceEvent(
      type: typeStr == 'attached' ? UsbDeviceEventType.attached : UsbDeviceEventType.detached,
      deviceId: map['deviceId'] as String?,
    );
  }
}
