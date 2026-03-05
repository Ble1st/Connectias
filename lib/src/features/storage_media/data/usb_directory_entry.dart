import 'dart:convert';

/// A single entry from an NTFS directory listing.
class UsbDirectoryEntry {
  const UsbDirectoryEntry({
    required this.name,
    required this.isDirectory,
    required this.size,
  });

  final String name;
  final bool isDirectory;
  final int size;

  factory UsbDirectoryEntry.fromJson(Map<String, dynamic> json) {
    return UsbDirectoryEntry(
      name: json['n'] as String? ?? '',
      isDirectory: json['d'] as bool? ?? false,
      size: (json['s'] as num?)?.toInt() ?? 0,
    );
  }

  static List<UsbDirectoryEntry> fromJsonList(String jsonStr) {
    if (jsonStr.isEmpty || jsonStr == '[]') return [];
    try {
      final decoded = jsonDecode(jsonStr) as List<dynamic>?;
      if (decoded == null) return [];
      return decoded
          .map((e) => UsbDirectoryEntry.fromJson(Map<String, dynamic>.from(e as Map)))
          .where((entry) {
            final name = entry.name;
            if (name.isEmpty) {
              return false;
            }
            // Hide NTFS metadata files and common system folders from the UI.
            final lower = name.toLowerCase();
            if (name.startsWith(r'$')) {
              return false;
            }
            if (lower == 'system volume information' ||
                lower == r'$recycle.bin' ||
                lower == 'recycler') {
              return false;
            }
            return true;
          })
          .toList();
    } catch (_) {
      return [];
    }
  }
}
