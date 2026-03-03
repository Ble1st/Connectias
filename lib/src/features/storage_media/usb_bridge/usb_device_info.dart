/// Information about a connected USB device.
class UsbDeviceInfo {
  const UsbDeviceInfo({
    required this.deviceId,
    required this.vendorId,
    required this.productId,
    this.productName,
    this.deviceClass,
    this.deviceSubclass,
    this.deviceProtocol,
  });

  final String deviceId;
  final int vendorId;
  final int productId;
  final String? productName;
  final int? deviceClass;
  final int? deviceSubclass;
  final int? deviceProtocol;

  factory UsbDeviceInfo.fromMap(Map<Object?, Object?> map) {
    return UsbDeviceInfo(
      deviceId: map['deviceId'] as String? ?? '',
      vendorId: _parseInt(map['vendorId']),
      productId: _parseInt(map['productId']),
      productName: map['productName'] as String?,
      deviceClass: _parseIntNullable(map['deviceClass']),
      deviceSubclass: _parseIntNullable(map['deviceSubclass']),
      deviceProtocol: _parseIntNullable(map['deviceProtocol']),
    );
  }

  static int _parseInt(Object? value) {
    if (value == null) return 0;
    if (value is int) return value;
    if (value is double) return value.toInt();
    return int.tryParse(value.toString()) ?? 0;
  }

  static int? _parseIntNullable(Object? value) {
    if (value == null) return null;
    if (value is int) return value;
    if (value is double) return value.toInt();
    final parsed = int.tryParse(value.toString());
    return parsed;
  }
}
