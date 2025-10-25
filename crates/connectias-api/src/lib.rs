//! # Connectias Plugin API
//!
//! Diese Bibliothek definiert die Core API für Connectias Plugins.
//!
//! ## Übersicht
//!
//! Das Plugin-System basiert auf dem `Plugin` Trait, der von allen Plugins
//! implementiert werden muss. Plugins werden über ein Manifest (`PluginInfo`)
//! beschrieben und erhalten einen `PluginContext` bei der Initialisierung.
//!
//! ## Beispiel
//!
//! ```rust
//! use connectias_api::{Plugin, PluginInfo, PluginContext, PluginError, PluginPermission};
//! use std::collections::HashMap;
//!
//! struct MyPlugin {
//!     info: PluginInfo,
//! }
//!
//! impl Plugin for MyPlugin {
//!     fn get_info(&self) -> PluginInfo {
//!         self.info.clone()
//!     }
//!
//!     fn init(&mut self, context: PluginContext) -> Result<(), PluginError> {
//!         println!("Plugin initialized: {}", context.plugin_id);
//!         Ok(())
//!     }
//!
//!     fn execute(&self, command: &str, args: HashMap<String, String>) -> Result<String, PluginError> {
//!         match command {
//!             "hello" => Ok("Hello World!".to_string()),
//!             _ => Err(PluginError::ExecutionFailed("Unknown command".to_string())),
//!         }
//!     }
//!
//!     fn cleanup(&mut self) -> Result<(), PluginError> {
//!         Ok(())
//!     }
//! }
//! ```

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

pub mod error;

/// Plugin-Metadaten aus dem plugin.json Manifest
///
/// Diese Struktur enthält alle Informationen über ein Plugin, die für
/// das Loading, Dependency Resolution und Permission Management benötigt werden.
///
/// # Felder
///
/// * `id` - Eindeutige Plugin-ID im Reverse-Domain Format (z.B. "com.example.plugin")
/// * `name` - Human-readable Plugin-Name
/// * `version` - Semantic Version (z.B. "1.0.0")
/// * `author` - Plugin-Autor
/// * `description` - Plugin-Beschreibung
/// * `min_core_version` - Minimale Connectias Core Version
/// * `max_core_version` - Maximale Connectias Core Version (optional)
/// * `permissions` - Liste der benötigten Berechtigungen
/// * `entry_point` - Dateiname des Plugin-Entry-Points (z.B. "plugin.wasm")
/// * `dependencies` - Liste von Plugin-IDs, die geladen sein müssen
///
/// # Beispiel
///
/// ```rust
/// use connectias_api::{PluginInfo, PluginPermission};
///
/// let info = PluginInfo {
///     id: "com.example.calculator".to_string(),
///     name: "Calculator".to_string(),
///     version: "1.0.0".to_string(),
///     author: "Developer".to_string(),
///     description: "A simple calculator".to_string(),
///     min_core_version: "1.0.0".to_string(),
///     max_core_version: None,
///     permissions: vec![PluginPermission::Storage],
///     entry_point: "plugin.wasm".to_string(),
///     dependencies: None,
/// };
/// ```
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginInfo {
    /// Eindeutige Plugin-ID im Reverse-Domain Format
    pub id: String,
    /// Human-readable Plugin-Name
    pub name: String,
    /// Semantic Version des Plugins
    pub version: String,
    /// Plugin-Autor
    pub author: String,
    /// Plugin-Beschreibung
    pub description: String,
    /// Minimale Connectias Core Version
    pub min_core_version: String,
    /// Maximale Connectias Core Version (optional)
    pub max_core_version: Option<String>,
    /// Liste der benötigten Berechtigungen
    pub permissions: Vec<PluginPermission>,
    /// Dateiname des Plugin-Entry-Points
    pub entry_point: String,
    /// Liste von Plugin-IDs, die geladen sein müssen
    pub dependencies: Option<Vec<String>>,
}

/// Plugin-Berechtigungen die zur Laufzeit angefordert werden können
///
/// Das Permission-System folgt dem Principle of Least Privilege:
/// Plugins erhalten nur die Berechtigungen, die sie explizit anfordern
/// und die vom User genehmigt wurden.
///
/// # Varianten
///
/// * `Network` - Erlaubt HTTP/HTTPS Requests zu externen Servern
/// * `Storage` - Erlaubt Lesen/Schreiben in der Plugin-Storage
/// * `SystemInfo` - Erlaubt Zugriff auf System-Informationen
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum PluginPermission {
    /// Erlaubt HTTP/HTTPS Requests
    ///
    /// Mit dieser Berechtigung kann das Plugin:
    /// - HTTP GET/POST/PUT/DELETE Requests senden
    /// - HTTPS-Verbindungen aufbauen
    /// - Daten von externen APIs abrufen
    ///
    /// Einschränkungen:
    /// - Keine localhost/127.0.0.1 Zugriffe (SSRF Protection)
    /// - Keine private IP-Ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
    /// - SSL Certificate Pinning aktiv
    Network,

    /// Erlaubt persistente Storage-Operationen
    ///
    /// Mit dieser Berechtigung kann das Plugin:
    /// - Key-Value Daten speichern
    /// - Gespeicherte Daten abrufen
    /// - Daten löschen
    ///
    /// Einschränkungen:
    /// - Maximale Storage-Größe: 10MB pro Plugin
    /// - Alle Daten sind AES-256-GCM verschlüsselt
    /// - Kein Zugriff auf andere Plugin-Daten
    Storage,

    /// Erlaubt Zugriff auf System-Informationen
    ///
    /// Mit dieser Berechtigung kann das Plugin:
    /// - OS-Name und Version abrufen
    /// - CPU-Architektur ermitteln
    /// - Verfügbaren Memory abrufen
    ///
    /// Einschränkungen:
    /// - Keine sensiblen Informationen (Hostname, User, etc.)
    SystemInfo,
}

/// Execution-Context der einem Plugin bei Initialisierung übergeben wird
///
/// Der Context enthält Informationen über die Runtime-Umgebung
/// und ermöglicht Zugriff auf Host-Services.
///
/// # Felder
///
/// * `plugin_id` - Die ID des Plugins (für Logging/Storage)
/// * `storage` - Storage-Service für persistente Daten
/// * `network` - Network-Service für HTTP-Requests
/// * `logger` - Logger-Service für Logging
/// * `system_info` - System-Info-Service für System-Informationen
pub struct PluginContext {
    /// Die ID des Plugins (für Logging/Storage)
    pub plugin_id: String,
    /// Storage-Service für persistente Daten
    pub storage: std::sync::Arc<dyn StorageService>,
    /// Network-Service für HTTP-Requests
    pub network: std::sync::Arc<dyn NetworkService>,
    /// Logger-Service für Logging
    pub logger: std::sync::Arc<dyn Logger>,
    /// System-Info-Service für System-Informationen
    pub system_info: std::sync::Arc<dyn SystemInfo>,
}

impl Clone for PluginContext {
    fn clone(&self) -> Self {
        Self {
            plugin_id: self.plugin_id.clone(),
            storage: self.storage.clone(),
            network: self.network.clone(),
            logger: self.logger.clone(),
            system_info: self.system_info.clone(),
        }
    }
}

impl std::fmt::Debug for PluginContext {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("PluginContext")
            .field("plugin_id", &self.plugin_id)
            .field("storage", &"<StorageService>")
            .field("network", &"<NetworkService>")
            .field("logger", &"<Logger>")
            .field("system_info", &"<SystemInfo>")
            .finish()
    }
}

/// Core Plugin Trait der von allen Plugins implementiert werden muss
///
/// Dieser Trait definiert den Plugin-Lifecycle und die Execution-API.
///
/// # Lifecycle
///
/// 1. **Load** - Plugin wird aus ZIP extrahiert und geladen
/// 2. **Init** - `init()` wird mit PluginContext aufgerufen
/// 3. **Execute** - `execute()` kann mehrfach aufgerufen werden
/// 4. **Cleanup** - `cleanup()` wird vor dem Unload aufgerufen
///
/// # Safety
///
/// Implementierungen müssen `Send + Sync` sein, da Plugins in
/// separaten Threads ausgeführt werden können.
pub trait Plugin: Send + Sync {
    /// Gibt die Plugin-Metadaten zurück
    ///
    /// Diese Methode wird direkt nach dem Laden aufgerufen um
    /// Plugin-Informationen zu extrahieren.
    fn get_info(&self) -> PluginInfo;

    /// Initialisiert das Plugin mit dem gegebenen Context
    ///
    /// # Arguments
    ///
    /// * `context` - Runtime-Context mit Plugin-ID
    ///
    /// # Errors
    ///
    /// Gibt `PluginError::InitializationFailed` zurück wenn:
    /// - Benötigte Ressourcen nicht verfügbar sind
    /// - Permissions fehlen
    /// - Dependencies nicht geladen sind
    fn init(&mut self, context: PluginContext) -> Result<(), PluginError>;

    /// Führt einen Plugin-Command aus
    ///
    /// # Arguments
    ///
    /// * `command` - Command-Name (z.B. "calculate", "process")
    /// * `args` - Command-Argumente als Key-Value Map
    ///
    /// # Returns
    ///
    /// Gibt einen String mit dem Execution-Result zurück.
    ///
    /// # Errors
    ///
    /// Gibt `PluginError::ExecutionFailed` zurück wenn:
    /// - Command unbekannt ist
    /// - Argumente invalid sind
    /// - Execution fehlschlägt
    fn execute(&self, command: &str, args: HashMap<String, String>) -> Result<String, PluginError>;

    /// Cleanup beim Plugin-Unload
    ///
    /// Hier sollten Ressourcen freigegeben werden:
    /// - File Handles schließen
    /// - Network Connections beenden
    /// - Memory freigeben
    fn cleanup(&mut self) -> Result<(), PluginError>;
}

/// Storage-Service für persistente Plugin-Daten
///
/// Ermöglicht Plugins das Speichern und Abrufen von Daten
/// mit automatischer Verschlüsselung und Quota-Management.
pub trait StorageService: Send + Sync {
    /// Speichert einen Wert unter dem gegebenen Schlüssel
    fn put(&self, key: &str, value: &[u8]) -> Result<(), PluginError>;
    
    /// Ruft einen Wert unter dem gegebenen Schlüssel ab
    fn get(&self, key: &str) -> Result<Option<Vec<u8>>, PluginError>;
    
    /// Löscht einen Wert unter dem gegebenen Schlüssel
    fn delete(&self, key: &str) -> Result<(), PluginError>;
    
    /// Löscht alle Plugin-Daten
    fn clear(&self) -> Result<(), PluginError>;
    
    /// Gibt die aktuelle Storage-Größe in Bytes zurück
    fn size(&self) -> Result<usize, PluginError>;
}

/// Network-Service für HTTP-Requests
///
/// Ermöglicht Plugins sichere HTTP-Requests mit
/// SSRF-Protection und Rate-Limiting.
pub trait NetworkService: Send + Sync {
    /// Führt einen HTTP-Request aus
    fn request(&self, req: NetworkRequest) -> Result<NetworkResponse, PluginError>;
}

/// Logger-Service für Plugin-Logging
///
/// Ermöglicht Plugins strukturiertes Logging mit
/// automatischer Plugin-ID-Anreicherung.
pub trait Logger: Send + Sync {
    /// Debug-Level Logging
    fn debug(&self, msg: &str);
    
    /// Info-Level Logging
    fn info(&self, msg: &str);
    
    /// Warning-Level Logging
    fn warn(&self, msg: &str);
    
    /// Error-Level Logging
    fn error(&self, msg: &str);
}

/// System-Info-Service für System-Informationen
///
/// Ermöglicht Plugins den Zugriff auf sichere
/// System-Informationen ohne sensible Daten.
pub trait SystemInfo: Send + Sync {
    /// Gibt OS-Informationen zurück
    fn get_os_info(&self) -> Result<OsInfo, PluginError>;
    
    /// Gibt CPU-Informationen zurück
    fn get_cpu_info(&self) -> Result<CpuInfo, PluginError>;
    
    /// Gibt Memory-Informationen zurück
    fn get_memory_info(&self) -> Result<MemoryInfo, PluginError>;
}

/// Network-Request-Struktur
#[derive(Debug, Clone)]
pub struct NetworkRequest {
    pub method: String,
    pub url: String,
    pub headers: std::collections::HashMap<String, String>,
    pub body: Option<Vec<u8>>,
}

/// Network-Response-Struktur
#[derive(Debug, Clone)]
pub struct NetworkResponse {
    pub status_code: u16,
    pub headers: std::collections::HashMap<String, String>,
    pub body: Vec<u8>,
}

/// OS-Informationen
#[derive(Debug, Clone)]
pub struct OsInfo {
    pub name: String,
    pub version: String,
    pub arch: String,
}

/// CPU-Informationen
#[derive(Debug, Clone)]
pub struct CpuInfo {
    pub cores: u32,
    pub model: String,
    pub frequency: u64,
}

/// Memory-Informationen
#[derive(Debug, Clone)]
pub struct MemoryInfo {
    pub total: u64,
    pub available: u64,
    pub used: u64,
}

// PluginError ist bereits in error.rs definiert und wird hier re-exportiert
pub use error::PluginError;

// =========================================================================
// ENHANCED PLUGIN API SERVICES (Simplified for dyn compatibility)
// =========================================================================

/// Plugin Connection Information
#[derive(Debug, Clone)]
pub struct PluginConnectionInfo {
    pub plugin_id: String,
    pub is_active: bool,
    pub connected_at: std::time::SystemTime,
    pub last_heartbeat: std::time::SystemTime,
    pub message_count: u64,
}

/// Plugin Performance Metrics
#[derive(Debug, Clone)]
pub struct PluginMetrics {
    pub total_executions: u64,
    pub average_execution_time: std::time::Duration,
    pub memory_usage: u64,
    pub error_count: u64,
    pub last_execution: Option<std::time::SystemTime>,
}

/// Plugin Events für Event-driven Architecture
#[derive(Debug, Clone)]
pub enum PluginEvent {
    /// Plugin wurde gestartet
    Started,
    /// Plugin wurde gestoppt
    Stopped,
    /// Plugin Error aufgetreten
    Error { error: String },
    /// Plugin Performance-Warning
    PerformanceWarning { metric: String, value: f64 },
    /// Plugin Permission geändert
    PermissionChanged { permission: String, granted: bool },
}

/// Plugin Health Status
#[derive(Debug, Clone)]
pub enum PluginHealth {
    /// Plugin ist gesund
    Healthy,
    /// Plugin hat Warnungen
    Warning { message: String },
    /// Plugin ist ungesund
    Unhealthy { error: String },
}
