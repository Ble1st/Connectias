# Rust API Reference

## Connectias Core API

### Plugin Management

#### `PluginManager`

```rust
pub struct PluginManager {
    plugins: Arc<RwLock<HashMap<String, PluginInfo>>>,
    wasm_runtime: Arc<WasmRuntime>,
    security_policy: Arc<SecurityPolicy>,
}
```

**Methods:**

- `load_plugin(path: &str) -> Result<PluginInfo, PluginError>`
  - Lädt ein Plugin aus einer Datei (.wasm oder .zip)
  - Validiert Plugin-Manifest und Berechtigungen
  - Initialisiert WASM-Runtime für das Plugin

- `unload_plugin(plugin_id: &str) -> Result<(), PluginError>`
  - Entlädt ein Plugin und gibt Ressourcen frei
  - Stoppt alle laufenden Plugin-Prozesse

- `get_plugin_info(plugin_id: &str) -> Option<PluginInfo>`
  - Gibt Plugin-Informationen zurück
  - Thread-sichere Abfrage ohne Locks

- `list_plugins() -> Vec<PluginInfo>`
  - Gibt Liste aller geladenen Plugins zurück

#### `PluginInfo`

```rust
pub struct PluginInfo {
    pub id: String,
    pub name: String,
    pub version: String,
    pub author: String,
    pub description: String,
    pub min_core_version: String,
    pub max_core_version: String,
    pub permissions: Vec<String>,
    pub entry_point: String,
    pub dependencies: Vec<String>,
    pub manifest_path: Option<String>,
}
```

### WASM Runtime

#### `WasmRuntime`

```rust
pub struct WasmRuntime {
    engine: Engine,
    resource_limits: ResourceLimits,
    #[cfg(feature = "advanced_fuel_metering")]
    fuel_meter: Option<AdvancedFuelMeter>,
}
```

**Methods:**

- `load_plugin(wasm_bytes: &[u8]) -> Result<WasmPlugin, PluginError>`
  - Lädt WASM-Modul und erstellt Plugin-Instance
  - Konfiguriert Resource-Limits und Fuel-Metering

- `create_store() -> Store<()>`
  - Erstellt neuen WASM-Store für Plugin-Execution

#### `WasmPlugin`

```rust
pub struct WasmPlugin {
    module: Module,
    engine: Engine,
    resource_limits: ResourceLimits,
    store: Option<Store<()>>,
    allocations: HashMap<u32, AllocationInfo>,
    next_offset: u32,
}
```

**Methods:**

- `execute(input: &[u8]) -> Result<Vec<u8>, PluginError>`
  - Führt Plugin mit Input-Daten aus
  - Gibt Output-Daten zurück

- `write_to_wasm_memory_safe(store: &mut Store<()>, data: &[u8]) -> Result<u32, PluginError>`
  - Schreibt Daten in WASM-Memory (sichere Version)
  - Gibt Memory-Pointer zurück

- `read_from_wasm_memory(store: &mut Store<()>, ptr: u32, len: u32) -> Result<Vec<u8>, PluginError>`
  - Liest Daten aus WASM-Memory
  - Bounds-Checking für Memory-Safety

### Security Services

#### `RaspMonitor`

```rust
pub struct RaspMonitor {
    security_policy: Arc<SecurityPolicy>,
    threat_detector: Arc<ThreatDetector>,
    alert_service: Arc<AlertService>,
}
```

**Methods:**

- `verify_certificate_transparency(cert: &[u8]) -> Result<bool, SecurityError>`
  - Verifiziert Zertifikat gegen CT-Logs
  - Asynchrone HTTP-Requests zu CT-APIs

- `validate_certificate_pinning(cert: &[u8], expected_pins: &[String]) -> bool`
  - Validiert Certificate Pinning
  - SHA-256 SPKI-Hash-Vergleich

#### `PermissionService`

```rust
pub struct PermissionService {
    permissions: Arc<RwLock<HashMap<String, Vec<String>>>>,
    security_policy: Arc<SecurityPolicy>,
}
```

**Methods:**

- `grant_permission(plugin_id: &str, permission: &str) -> Result<(), PermissionError>`
  - Gewährt Plugin-Berechtigung

- `revoke_permission(plugin_id: &str, permission: &str) -> Result<(), PermissionError>`
  - Entzieht Plugin-Berechtigung

- `has_permission(plugin_id: &str, permission: &str) -> bool`
  - Prüft Plugin-Berechtigung

### Message Broker

#### `MessageBroker`

```rust
pub struct MessageBroker {
    subscribers: Arc<RwLock<HashMap<String, Vec<MessageHandler>>>>,
    message_queue: Arc<Mutex<VecDeque<Message>>>,
    message_history: Arc<RwLock<HashMap<String, Vec<Message>>>>,
}
```

**Methods:**

- `publish(topic: &str, message: Message) -> Result<(), MessageError>`
  - Veröffentlicht Nachricht an Topic

- `subscribe<F>(topic: &str, plugin_id: &str, handler: F)`
  - Abonniert Topic mit Handler-Funktion
  - Arc-basiertes Cloning für Thread-Safety

- `unsubscribe(topic: &str, plugin_id: &str) -> Result<(), MessageError>`
  - Beendet Topic-Abonnement

#### `Message`

```rust
pub struct Message {
    pub topic: String,
    pub payload: Vec<u8>,
    pub sender_id: String,
    pub timestamp: i64,
    pub message_id: String,
}
```

### Error Types

#### `PluginError`

```rust
pub enum PluginError {
    LoadError(String),
    ExecutionError(String),
    MemoryError(String),
    SecurityError(String),
    ValidationError(String),
}
```

#### `SecurityError`

```rust
pub enum SecurityError {
    CertificateError(String),
    PermissionDenied(String),
    ThreatDetected(String),
    ValidationFailed(String),
}
```

## Security Features

### Certificate Transparency

- **Google CT Log**: `https://ct.googleapis.com/logs/argon2024/ct/v1/get-entries`
- **Cloudflare CT Log**: `https://ct.cloudflare.com/logs/nimbus2024/ct/v1/get-entries`
- **Fallback-Mechanismus**: Bei API-Ausfall wird Warnung geloggt
- **Caching**: 60 Minuten TTL für CT-Log-Responses

### Certificate Pinning

- **Algorithmus**: SHA-256 SPKI-Hashing
- **Format**: Base64-encoded SPKI-Hash
- **Validation**: X.509 Certificate-Chain-Parsing
- **Error-Handling**: SecurityException bei Pinning-Fehler

### WASM Security

- **Memory-Isolation**: Jedes Plugin hat isolierten Memory-Space
- **Resource-Limits**: CPU und Memory-Limits pro Plugin
- **Fuel-Metering**: Verhindert Endlosschleifen
- **Bounds-Checking**: Buffer-Overflow-Schutz

## Thread Safety

Alle öffentlichen APIs sind thread-safe:

- **Arc-basierte Sharing**: `Arc<RwLock<T>>` für Shared-State
- **Mutex-basierte Synchronisation**: Für mutable Operations
- **Send + Sync**: Alle Handler-Funktionen sind thread-safe
- **Clone-Support**: Arc-basierte Handler können geklont werden

## Performance Considerations

- **Memory-Pooling**: Wiederverwendung von WASM-Memory-Allocations
- **Async-Operations**: Non-blocking I/O für CT-Log-Requests
- **Caching**: CT-Log-Responses und Plugin-Metadata
- **Resource-Monitoring**: Real-time Performance-Metriken
