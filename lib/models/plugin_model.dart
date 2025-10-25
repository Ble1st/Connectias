/// Plugin Model – Data Model für Plugin-Informationen
library;

enum PluginStatus {
  active,
  inactive,
  loading,
  error,
}

class PluginModel {
  final String id;
  final String name;
  final String version;
  final String author;
  final String category;
  final String description;
  final PluginStatus status;
  final List<String> permissions;
  final DateTime lastUsed;
  final int memoryUsage;
  bool isEnabled;
  final Map<String, dynamic>? metadata;

  PluginModel({
    required this.id,
    required this.name,
    required this.version,
    required this.author,
    required this.category,
    required this.description,
    required this.status,
    required this.permissions,
    required this.lastUsed,
    required this.memoryUsage,
    required this.isEnabled,
    this.metadata,
  });
}
