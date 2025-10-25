/// Plugin Model – Data Model für Plugin-Informationen
library plugin_model;

enum PluginStatus {
  loaded,
  loading,
  error,
}

class PluginModel {
  final String id;
  final PluginStatus status;
  final Map<String, dynamic>? metadata;

  PluginModel({
    required this.id,
    required this.status,
    this.metadata,
  });
}
