# Dart API Reference

## Connectias Flutter API

### Core Services

#### `ConnectiasService`

```dart
class ConnectiasService {
  static final ConnectiasService _instance = ConnectiasService._internal();
  static ConnectiasService get instance => _instance;
  
  late final PluginManager _pluginManager;
  late final SecurityService _securityService;
  late final NetworkSecurityService _networkSecurityService;
}
```

**Methods:**

- `Future<void> initialize()`
  - Initialisiert alle Connectias-Services
  - Lädt Konfiguration und Security-Policies

- `Future<List<PluginModel>> getPlugins()`
  - Gibt Liste aller geladenen Plugins zurück
  - Thread-sichere Abfrage

- `Future<PluginModel?> loadPlugin(File file)`
  - Lädt Plugin aus Datei (.wasm oder .zip)
  - Validiert Plugin-Manifest und Berechtigungen

- `Future<void> unloadPlugin(String pluginId)`
  - Entlädt Plugin und gibt Ressourcen frei

- `Future<PluginMetrics?> getPluginMetrics(String pluginId)`
  - Gibt Performance-Metriken für Plugin zurück

### Plugin Management

#### `PluginModel`

```dart
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
}
```

**Methods:**

- `PluginModel.fromJson(Map<String, dynamic> json)`
  - Erstellt PluginModel aus JSON

- `Map<String, dynamic> toJson()`
  - Konvertiert PluginModel zu JSON

#### `PluginStatus`

```dart
enum PluginStatus {
  active,
  inactive,
  loading,
  error,
}
```

### Security Services

#### `SecurityService`

```dart
class SecurityService {
  late final RaspMonitor _raspMonitor;
  late final ThreatDetector _threatDetector;
  late final AlertService _alertService;
}
```

**Methods:**

- `Future<bool> validateUrl(Uri url)`
  - Validiert URL gegen Security-Policy
  - Certificate Pinning und CT-Log-Verification

- `Future<void> reportThreat(ThreatLevel level, String description)`
  - Meldet Security-Threat
  - Automatische Alert-Generierung

#### `NetworkSecurityService`

```dart
class NetworkSecurityService {
  final Map<String, NetworkSecurityPolicy> _policies = {};
}
```

**Methods:**

- `Future<bool> validateUrl(Uri url)`
  - Validiert URL mit Certificate Pinning
  - X.509 Certificate-Chain-Parsing

- `Future<bool> _validateCertificatePinning(Uri url, NetworkSecurityPolicy policy)`
  - Implementiert Certificate Pinning
  - SHA-256 SPKI-Hash-Validation

- `bool _validateCertificatePin(X509Certificate cert, List<String> expectedPins)`
  - Validiert einzelnes Zertifikat
  - SPKI-Hash-Vergleich

- `String _computeSpkiHash(List<int> derData)`
  - Berechnet SHA-256 SPKI-Hash
  - Base64-Encoding für Pin-Format

#### `NetworkSecurityPolicy`

```dart
class NetworkSecurityPolicy {
  final bool enforceHttps;
  final List<String> allowedDomains;
  final List<String> certificatePins;
  final bool enableCertificateTransparency;
  final Duration timeout;
}
```

### UI Components

#### `PluginManagerScreen`

```dart
class PluginManagerScreen extends StatefulWidget {
  const PluginManagerScreen({super.key});
}
```

**Features:**

- Plugin-Liste mit Status-Anzeige
- Plugin-Installation über File-Picker
- Real-time Performance-Metriken
- Security-Dashboard-Integration

#### `PluginDetailsScreen`

```dart
class PluginDetailsScreen extends StatefulWidget {
  final PluginModel plugin;
  
  const PluginDetailsScreen({
    Key? key,
    required this.plugin,
  });
}
```

**Features:**

- Detaillierte Plugin-Informationen
- Performance-Charts (CPU, Memory, Network)
- Permission-Management
- Plugin-Konfiguration

#### `PerformanceChart`

```dart
class PerformanceChart extends StatefulWidget {
  final String pluginId;
  
  const PerformanceChart({
    Key? key,
    required this.pluginId,
  });
}
```

**Features:**

- Real-time Line-Charts mit fl_chart
- 24-Stunden-Historie
- CPU, Memory, Network-Metriken
- Auto-Update alle 5 Sekunden

#### `PluginInstallDialog`

```dart
class PluginInstallDialog extends StatefulWidget {
  final Function(File) onInstall;
  
  const PluginInstallDialog({
    Key? key,
    required this.onInstall,
  });
}
```

**Features:**

- File-Picker für .wasm und .zip-Dateien
- Progress-Indicator während Installation
- Error-Handling und Success-Feedback

### Security Features

#### Certificate Pinning

```dart
// Beispiel-Konfiguration
final policy = NetworkSecurityPolicy(
  enforceHttps: true,
  allowedDomains: ['api.connectias.com', 'cdn.connectias.com'],
  certificatePins: [
    'sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
    'sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=',
  ],
  enableCertificateTransparency: true,
  timeout: Duration(seconds: 30),
);
```

#### Threat Detection

```dart
// Threat-Level-Definitionen
enum ThreatLevel {
  low,
  medium,
  high,
  critical,
}

// Threat-Reporting
await securityService.reportThreat(
  ThreatLevel.high,
  'Suspicious plugin behavior detected',
);
```

### Error Handling

#### `ConnectiasException`

```dart
class ConnectiasException implements Exception {
  final String message;
  final String? code;
  final dynamic details;
  
  const ConnectiasException(
    this.message, {
    this.code,
    this.details,
  });
}
```

#### `PluginError`

```dart
class PluginError extends ConnectiasException {
  const PluginError(String message, {String? code, dynamic details})
      : super(message, code: code, details: details);
}
```

#### `SecurityError`

```dart
class SecurityError extends ConnectiasException {
  const SecurityError(String message, {String? code, dynamic details})
      : super(message, code: code, details: details);
}
```

### Performance Monitoring

#### `PluginMetrics`

```dart
class PluginMetrics {
  final double cpuUsage;
  final int memoryUsage;
  final int networkBytes;
  final DateTime timestamp;
  final Map<String, dynamic> customMetrics;
}
```

#### `SystemMetrics`

```dart
class SystemMetrics {
  final double totalCpuUsage;
  final int totalMemoryUsage;
  final int totalNetworkBytes;
  final int activePlugins;
  final DateTime timestamp;
}
```

### Configuration

#### `ConnectiasConfig`

```dart
class ConnectiasConfig {
  final String dataDirectory;
  final String pluginDirectory;
  final SecurityPolicy securityPolicy;
  final LogLevel logLevel;
  final bool enablePerformanceMonitoring;
  final bool enableSecurityMonitoring;
}
```

#### `SecurityPolicy`

```dart
class SecurityPolicy {
  final bool enableRasp;
  final bool enableCertificatePinning;
  final bool enableCertificateTransparency;
  final List<String> trustedCertificates;
  final Map<String, List<String>> pluginPermissions;
}
```

## Thread Safety

- **Isolate-basierte Architektur**: Flutter-Isolates für CPU-intensive Tasks
- **Async/Await**: Non-blocking Operations
- **State-Management**: Provider-Pattern für UI-State
- **Memory-Management**: Automatic Garbage Collection

## Performance Considerations

- **Lazy Loading**: Plugins werden nur bei Bedarf geladen
- **Caching**: Plugin-Metadata und Security-Policies
- **Background Processing**: CT-Log-Verification läuft asynchron
- **Memory-Pooling**: Wiederverwendung von Plugin-Instanzen
- **Real-time Updates**: 5-Sekunden-Intervall für Performance-Charts

## Security Best Practices

- **Input Validation**: Alle User-Inputs werden validiert
- **Certificate Pinning**: Verhindert Man-in-the-Middle-Attacks
- **CT-Log-Verification**: Zusätzliche Zertifikat-Validierung
- **Permission-System**: Granulare Plugin-Berechtigungen
- **Threat-Detection**: Automatische Security-Monitoring