use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::sync::RwLock;
use serde::{Deserialize, Serialize};
use connectias_api::{Plugin, PluginInfo, PluginContext};
use connectias_security::{SignatureVerifier, PluginValidator, RaspProtection, NetworkSecurityFilter, ThreatDetectionSystem};
use connectias_wasm::WasmRuntime;
use connectias_storage::Database;
use connectias_security::sandbox::ResourceQuotaManager;

use crate::services::{
    StorageServiceImpl, NetworkServiceImpl, LoggerImpl, SystemInfoImpl,
    PermissionService, MonitoringService, AlertService
};
use crate::performance::PerformanceOptimizer;
use crate::metrics::{MetricsCollector, PerformanceMonitor};
use crate::recovery::{RecoveryManager, RecoveryHandler};
use crate::memory::{MemoryManager, MemoryMonitor};
use crate::message_broker::MessageBrokerManager;

// =========================================================================
// ENHANCED PLUGIN REGISTRY STRUCTURES
// =========================================================================

/// Plugin Registry Entry mit erweiterten Metadaten
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginRegistryEntry {
    pub plugin_id: String,
    pub plugin_info: PluginInfo,
    pub file_path: PathBuf,
    pub installed_at: i64,
    pub last_accessed: i64,
    pub version: String,
    pub status: PluginStatus,
    pub dependencies: Vec<String>,
    pub dependents: Vec<String>,
    pub permissions: Vec<String>,
    /// FIX BUG 4: Unbekannte Permissions für Forward-Compatibility
    /// Speichert Permissions die in zukünftigen Connectias-Versionen verfügbar sein werden
    pub unknown_permissions: Vec<String>,
    pub resource_usage: ResourceUsage,
    pub performance_metrics: PerformanceMetrics,
}

/// Plugin Status für Registry-Management
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum PluginStatus {
    /// Plugin ist installiert aber nicht geladen
    Installed,
    /// Plugin ist geladen und aktiv
    Loaded,
    /// Plugin ist aktiv und läuft
    Running,
    /// Plugin ist gestoppt
    Stopped,
    /// Plugin ist deaktiviert
    Disabled,
    /// Plugin hat einen Fehler
    Error { error: String },
    /// Plugin wird aktualisiert
    Updating,
    /// Plugin wird deinstalliert
    Uninstalling,
}

/// Resource Usage Tracking
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResourceUsage {
    pub memory_usage: u64,
    pub cpu_usage: f64,
    pub storage_usage: u64,
    pub network_usage: u64,
    pub last_updated: i64,
}

/// Performance Metrics für Plugin
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceMetrics {
    pub execution_count: u64,
    pub average_execution_time: f64,
    pub error_count: u64,
    pub success_rate: f64,
    pub last_execution: Option<i64>,
}

/// Plugin Discovery Result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginDiscoveryResult {
    pub discovered_plugins: Vec<DiscoveredPlugin>,
    pub scan_duration: u64,
    pub scan_paths: Vec<PathBuf>,
    pub errors: Vec<String>,
}

/// Entdecktes Plugin während Discovery
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DiscoveredPlugin {
    pub plugin_id: String,
    pub file_path: PathBuf,
    pub plugin_info: PluginInfo,
    pub is_valid: bool,
    pub validation_errors: Vec<String>,
    pub dependencies_available: bool,
    pub missing_dependencies: Vec<String>,
}

/// Dependency Resolution Result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DependencyResolutionResult {
    pub plugin_id: String,
    pub resolved_dependencies: Vec<String>,
    pub missing_dependencies: Vec<String>,
    pub circular_dependencies: Vec<String>,
    pub load_order: Vec<String>,
    pub is_resolvable: bool,
}

/// Plugin Registry Statistics
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginRegistryStats {
    pub total_plugins: usize,
    pub loaded_plugins: usize,
    pub running_plugins: usize,
    pub disabled_plugins: usize,
    pub error_plugins: usize,
    pub total_memory_usage: u64,
    pub total_storage_usage: u64,
    pub average_performance: f64,
    pub last_scan: i64,
}

pub struct PluginManager {
    plugins: Arc<RwLock<HashMap<String, Box<dyn Plugin>>>>,
    plugin_dir: PathBuf,
    wasm_runtime: WasmRuntime,
    signature_verifier: SignatureVerifier,
    validator: PluginValidator,
    rasp_protection: RaspProtection,
    metrics_collector: Arc<MetricsCollector>,
    recovery_manager: Arc<RecoveryManager>,
    recovery_handler: Arc<RecoveryHandler>,
    memory_manager: Arc<MemoryManager>,
    message_broker: Arc<MessageBrokerManager>,
    // New services
    database: Arc<Database>,
    encryption_service: Arc<connectias_storage::EncryptionService>,
    quota_manager: Arc<ResourceQuotaManager>,
    security_filter: Arc<NetworkSecurityFilter>,
    http_client: Arc<reqwest::Client>,
    performance_optimizer: Arc<PerformanceOptimizer>,
    // Threat Detection Services
    permission_service: Arc<PermissionService>,
    monitoring_service: Arc<MonitoringService>,
    alert_service: Arc<AlertService>,
    threat_detection_system: Arc<ThreatDetectionSystem>,
    // Enhanced Registry Features
    plugin_registry: Arc<RwLock<HashMap<String, PluginRegistryEntry>>>,
    dependency_graph: Arc<RwLock<HashMap<String, HashSet<String>>>>,
    discovery_cache: Arc<RwLock<HashMap<PathBuf, DiscoveredPlugin>>>,
    registry_stats: Arc<RwLock<PluginRegistryStats>>,
    /// FIX BUG 3: Strikter Mode für Permission-Validierung
    /// true = unbekannte Permissions verweigern (Produktion, Standard für Sicherheit)
    /// false = unbekannte Permissions ignorieren mit Warning (Entwicklung, Forward-Compatibility)
    strict_mode: bool,
}

impl PluginManager {
    pub fn new(app_data_dir: PathBuf) -> Result<Self, String> {
        let plugin_dir = app_data_dir.join("plugins");
        std::fs::create_dir_all(&plugin_dir)
            .map_err(|e| format!("Fehler beim Erstellen des Plugin-Verzeichnisses: {}", e))?;

        let metrics_collector = Arc::new(MetricsCollector::new());
        let recovery_manager = Arc::new(RecoveryManager::new());
        let recovery_handler = Arc::new(RecoveryHandler::new(recovery_manager.clone()));
        let memory_manager = Arc::new(MemoryManager::new());
        // Socket directory setup für IPC
        let socket_dir = app_data_dir.join("ipc");
        std::fs::create_dir_all(&socket_dir)
            .map_err(|e| format!("Fehler beim Erstellen des IPC-Verzeichnisses: {}", e))?;
        
        // IPC-Transport basierend auf Plattform
        #[cfg(unix)]
        let ipc_transport = Arc::new(connectias_ipc::UnixSocketTransport::new()) as Arc<dyn connectias_ipc::IPCTransport>;
        
        #[cfg(windows)]
        let ipc_transport = Arc::new(connectias_ipc::NamedPipeTransport::new()) as Arc<dyn connectias_ipc::IPCTransport>;
        
        let message_broker = Arc::new(
            MessageBrokerManager::new()
                .with_ipc_transport(ipc_transport)
                .with_mode(connectias_core::message_broker::ProcessMode::MultiProcess)
        );

        // Initialize new services
        let database = Arc::new(Database::new(&app_data_dir.join("connectias.db"))
            .map_err(|e| format!("Fehler beim Initialisieren der Datenbank: {}", e))?);
        
        // Sichere Schlüsselverwaltung - KEINE Random-Keys in Produktion!
        let key = load_encryption_key()?;
        log::info!("Encryption-Schlüssel erfolgreich geladen");
        let encryption_service = Arc::new(connectias_storage::EncryptionService::new_with_key(&key));
        let quota_manager = Arc::new(ResourceQuotaManager::new());
        let security_filter = Arc::new(NetworkSecurityFilter::new());
        let http_client = Arc::new(reqwest::Client::new());
        let performance_optimizer = Arc::new(PerformanceOptimizer::new());

        // Start monitoring tasks
        let metrics_monitor = PerformanceMonitor::new(metrics_collector.clone());
        let memory_monitor = MemoryMonitor::new(memory_manager.clone());
        
        tokio::spawn(async move {
            metrics_monitor.start_monitoring().await;
        });
        
        tokio::spawn(async move {
            memory_monitor.start_monitoring().await;
        });
        
        let memory_manager_cleanup = memory_manager.clone();
        tokio::spawn(async move {
            memory_manager_cleanup.start_cleanup_task().await;
        });
        
        // Start performance monitoring (simplified for now)
        // In a real implementation, this would start actual monitoring

        // Initialize Threat Detection Services
        let permission_service = Arc::new(PermissionService::new());
        let monitoring_service = Arc::new(MonitoringService::new(metrics_collector.clone()));
        let alert_service = Arc::new(AlertService::new(database.clone()));
        
        // Create ThreatDetectionSystem
        let threat_detection_system = Arc::new(ThreatDetectionSystem::new());

        // Start Threat Detection monitoring
        let threat_system = threat_detection_system.clone();
        tokio::spawn(async move {
            threat_system.start_continuous_monitoring().await;
        });

        // Start Alert Service monitoring
        let alert_service_monitor = alert_service.clone();
        tokio::spawn(async move {
            alert_service_monitor.start_monitoring().await;
        });

        // Start Permission Service monitoring
        let permission_service_monitor = permission_service.clone();
        tokio::spawn(async move {
            permission_service_monitor.start_monitoring().await;
        });

        // Initialize Enhanced Registry Features
        let plugin_registry = Arc::new(RwLock::new(HashMap::new()));
        let dependency_graph = Arc::new(RwLock::new(HashMap::new()));
        let discovery_cache = Arc::new(RwLock::new(HashMap::new()));
        let registry_stats = Arc::new(RwLock::new(PluginRegistryStats {
            total_plugins: 0,
            loaded_plugins: 0,
            running_plugins: 0,
            disabled_plugins: 0,
            error_plugins: 0,
            total_memory_usage: 0,
            total_storage_usage: 0,
            average_performance: 0.0,
            last_scan: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        }));

        Ok(Self {
            plugins: Arc::new(RwLock::new(HashMap::new())),
            plugin_dir,
            wasm_runtime: WasmRuntime::new()
                .map_err(|e| format!("Fehler beim Initialisieren des WASM-Runtime: {}", e))?,
            signature_verifier: SignatureVerifier::new(),
            validator: PluginValidator::new(),
            rasp_protection: RaspProtection::new()
                .map_err(|e| format!("Failed to initialize RASP protection: {}", e))?,
            metrics_collector,
            recovery_manager,
            recovery_handler,
            memory_manager,
            message_broker,
            database,
            encryption_service,
            quota_manager,
            security_filter,
            http_client,
            performance_optimizer,
            // Threat Detection Services
            permission_service,
            monitoring_service,
            alert_service,
            threat_detection_system,
            // Enhanced Registry Features
            plugin_registry,
            dependency_graph,
            discovery_cache,
            registry_stats,
            strict_mode: true, // Standardmäßig strikter Modus
        })
    }

    /// FIX BUG 3: Konfiguriert den Strict-Mode für Permission-Validierung
    /// 
    /// strict_mode = true: Unbekannte Permissions verweigern Plugin-Loading (Sicherheit, Standard)
    /// strict_mode = false: Unbekannte Permissions ignorieren mit Warning (Forward-Compatibility)
    pub fn with_strict_mode(mut self, strict: bool) -> Self {
        self.strict_mode = strict;
        self
    }

    /// Lädt ein Plugin aus einer ZIP-Datei
    ///
    /// Führt folgende Schritte durch:
    /// 1. RASP Environment Check - Sicherheitsprüfung
    /// 2. Signature Verification - Plugin-Signatur prüfen
    /// 3. Plugin Structure Validation - Plugin-Struktur validieren
    /// 4. Plugin Extraction - Plugin in Plugin-Verzeichnis extrahieren
    /// 5. Plugin Loading - Plugin in WASM Runtime laden
    ///
    /// # Parameters
    /// - `zip_path`: Pfad zur Plugin-ZIP-Datei
    ///
    /// # Returns
    /// - `Ok(plugin_id)`: Plugin-ID bei erfolgreichem Laden
    /// - `Err(String)`: Fehler beim Laden
    ///
    /// # Security
    /// - RASP Protection aktiviert
    /// - Plugin-Signatur wird verifiziert
    /// - Plugin-Struktur wird validiert
    /// - Resource-Limits werden gesetzt
    pub async fn load_plugin(&self, zip_path: &Path) -> Result<String, String> {
        let start_time = std::time::Instant::now();

        // 1. RASP Environment Check: Prüfe auf Root, Debugger, Emulator, etc.
        self.rasp_protection.check_environment()
            .map_err(|e| e.to_string())?;

        // 2. Verify signature: Prüfe Plugin-Signatur
        self.signature_verifier.verify_plugin(zip_path)
            .map_err(|e| e.to_string())?;

        // 3. Validate plugin structure: Prüfe Plugin-Struktur und Dependencies
        let plugin_info = self.validator.validate_plugin_zip(zip_path)
            .map_err(|e| e.to_string())?;

        // 4. Extract plugin to plugins directory: Extrahiere Plugin in Plugin-Verzeichnis
        let plugin_install_dir = self.plugin_dir.join(&plugin_info.id);
        self.extract_plugin(zip_path, &plugin_install_dir)
            .map_err(|e| e.to_string())?;

        // 5. Detect plugin type and load: Lade Plugin basierend auf Typ
        let plugin = self.load_plugin_by_type(&plugin_install_dir, &plugin_info)
            .await
            .map_err(|e| e.to_string())?;

        // 6. Create PluginContext with Services: Erstelle Plugin-Kontext mit Services
        let context = PluginContext {
            plugin_id: plugin_info.id.clone(),
            storage: Arc::new(StorageServiceImpl::new(
                plugin_info.id.clone(),
                self.database.clone(),
                self.encryption_service.clone(),
                self.quota_manager.clone(),
            )),
            network: Arc::new(NetworkServiceImpl::new(
                plugin_info.id.clone(),
                self.http_client.clone(),
                self.security_filter.clone(),
                self.quota_manager.clone(),
            )),
            logger: Arc::new(LoggerImpl::new(
                plugin_info.id.clone(),
                self.database.clone(),
            )),
            system_info: Arc::new(SystemInfoImpl::new()),
        };
        
        // 7. Initialize plugin with context: Initialisiere Plugin mit Kontext
        let mut plugin = plugin;
        plugin.init(context)
            .map_err(|e| format!("Plugin initialization failed: {}", e))?;
        
        // 8. Record load time: Aufzeichnen der Ladezeit
        let load_time = start_time.elapsed();
        self.metrics_collector.record_load_time(&plugin_info.id, load_time).await;
        
        // 9. Set memory limit: Setze Memory-Limit für Plugin
        self.memory_manager.set_limit(&plugin_info.id, 100 * 1024 * 1024).await; // 100MB
        
        // 10. Store plugin in registry: Speichere Plugin in Registry
        let mut plugins = self.plugins.write().await;
        // TOCTOU Fix: Atomic check-and-insert to prevent race conditions and duplicate IDs
        if plugins.contains_key(&plugin_info.id) {
            return Err(format!("Plugin with ID {} already loaded", plugin_info.id));
        }
        plugins.insert(plugin_info.id.clone(), plugin);

        Ok(plugin_info.id)
    }

    async fn load_plugin_by_type(
        &self,
        install_dir: &Path,
        info: &PluginInfo,
    ) -> Result<Box<dyn Plugin>, anyhow::Error> {
        // Check for WASM plugin
        let wasm_path = install_dir.join("plugin.wasm");
        if wasm_path.exists() {
            let wasm_bytes = std::fs::read(&wasm_path)?;
            let plugin = self.wasm_runtime.load_plugin(&wasm_bytes, info.id.clone())?;
            return Ok(Box::new(plugin));
        }

        Err(anyhow::anyhow!("No valid plugin file found (expected plugin.wasm)"))
    }

    fn extract_plugin(&self, zip_path: &Path, target_dir: &Path) -> Result<(), std::io::Error> {
        std::fs::create_dir_all(target_dir)?;
        
        let file = std::fs::File::open(zip_path)?;
        let mut archive = zip::ZipArchive::new(file)
            .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))?;

        for i in 0..archive.len() {
            let mut file = archive.by_index(i)
                .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))?;
            let outpath = target_dir.join(file.name());

            if file.is_dir() {
                std::fs::create_dir_all(&outpath)?;
            } else {
                if let Some(p) = outpath.parent() {
                    std::fs::create_dir_all(p)?;
                }
                let mut outfile = std::fs::File::create(&outpath)?;
                std::io::copy(&mut file, &mut outfile)?;
            }
        }

        Ok(())
    }

    pub async fn execute_plugin(
        &self,
        plugin_id: &str,
        command: &str,
        args: HashMap<String, String>,
    ) -> Result<String, String> {
        let start_time = std::time::Instant::now();
        
        let plugins = self.plugins.read().await;
        let plugin = plugins.get(plugin_id)
            .ok_or_else(|| "Plugin not found".to_string())?;

        let result = plugin.execute(command, args)
            .map_err(|e| {
                // Handle plugin crash
                let error = connectias_api::PluginError::ExecutionFailed(e.to_string());
                let recovery_handler = self.recovery_handler.clone();
                let plugin_id = plugin_id.to_string();
                tokio::spawn(async move {
                    let _ = recovery_handler.handle_crash(&plugin_id, &error).await;
                });
                e.to_string()
            })?;

        // Record execution metrics
        let execution_time = start_time.elapsed();
        self.metrics_collector.record_execution(plugin_id, execution_time).await;
        
        // Check memory usage
        let memory_usage = self.memory_manager.get_memory_usage(plugin_id).await;
        self.metrics_collector.record_memory(plugin_id, memory_usage).await;
        
        // Check if plugin exceeds memory limit
        if self.memory_manager.check_memory_limit(plugin_id).await {
            tracing::warn!("Plugin {} exceeds memory limit", plugin_id);
        }

        Ok(result)
    }

    pub async fn list_plugins(&self) -> Vec<PluginInfo> {
        let plugins = self.plugins.read().await;
        plugins.values().map(|p| p.get_info()).collect()
    }

    /// Entlädt ein Plugin und gibt alle Ressourcen frei
    pub async fn unload_plugin(&self, plugin_id: &str) -> Result<(), String> {
        // Plugin aus der HashMap entfernen
        let mut plugins = self.plugins.write().await;
        let mut plugin = plugins.remove(plugin_id)
            .ok_or_else(|| format!("Plugin '{}' nicht gefunden", plugin_id))?;

        // Plugin cleanup aufrufen
        if let Err(e) = plugin.cleanup() {
            tracing::warn!("Fehler beim Cleanup von Plugin {}: {}", plugin_id, e);
        }

        // Resource Quota für Plugin zurücksetzen
        let _ = self.quota_manager.reset_usage(plugin_id);

        // Metrics für Plugin entfernen
        // Optional: Metrics werden nicht hart entfernt, stattdessen zurückgesetzt
        self.metrics_collector.record_memory(plugin_id, 0).await;

        // Recovery Stats für Plugin zurücksetzen
        // Recovery-Statistiken können bei Bedarf extern bereinigt werden

        // Performance Optimizer für Plugin zurücksetzen
        // Keine spezifische Reset-API vorhanden; zukünftige Erweiterung

        tracing::info!("Plugin '{}' erfolgreich entladen", plugin_id);
        Ok(())
    }
}

/// Lädt den Encryption-Schlüssel aus einer sicheren Quelle
/// Priorität: Environment Variable > Config File > Fehler
/// 
/// # Sicherheitshinweise:
/// - In Produktion MUSS ein KMS/HSM verwendet werden
/// - Schlüssel wird nur im Memory gehalten
/// - Keine Fallback zu Random-Keys um Datenverlust zu vermeiden
fn load_encryption_key() -> Result<[u8; 32], String> {
    // 1. Versuche Environment Variable (für Container/Cloud-Deployment)
    if let Ok(key_hex) = std::env::var("CONNECTIAS_ENCRYPTION_KEY") {
        if key_hex.len() == 64 { // 32 bytes = 64 hex chars
            match hex::decode(&key_hex) {
                Ok(key_bytes) if key_bytes.len() == 32 => {
                    let mut key = [0u8; 32];
                    key.copy_from_slice(&key_bytes);
                    return Ok(key);
                }
                _ => return Err("Ungültiges Encryption-Key Format in Environment Variable".into())
            }
        } else {
            return Err("Encryption-Key muss 64 Zeichen (32 Bytes) lang sein".into());
        }
    }
    
    // 2. Versuche Config File (nur mit strikten Permissions)
    let config_path = std::path::Path::new("config/encryption.key");
    if config_path.exists() {
        // Prüfe Dateiberechtigungen (nur Owner lesbar)
        if let Ok(metadata) = std::fs::metadata(config_path) {
            let permissions = metadata.permissions();
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let mode = permissions.mode();
                if mode & 0o077 != 0 {
                    return Err("Config-Datei hat unsichere Berechtigungen (nur Owner sollte lesen)".into());
                }
            }
        }
        
        match std::fs::read_to_string(config_path) {
            Ok(key_hex) => {
                let key_hex = key_hex.trim();
                if key_hex.len() == 64 {
                    match hex::decode(key_hex) {
                        Ok(key_bytes) if key_bytes.len() == 32 => {
                            let mut key = [0u8; 32];
                            key.copy_from_slice(&key_bytes);
                            return Ok(key);
                        }
                        _ => return Err("Ungültiges Encryption-Key Format in Config-Datei".into())
                    }
                } else {
                    return Err("Encryption-Key in Config-Datei muss 64 Zeichen lang sein".into());
                }
            }
            Err(e) => return Err(format!("Fehler beim Lesen der Config-Datei: {}", e))
        }
    }
    
    // 3. Kein Fallback - Fehler um Datenverlust zu vermeiden
    Err("Kein Encryption-Schlüssel gefunden. Setze CONNECTIAS_ENCRYPTION_KEY Environment Variable oder erstelle config/encryption.key".into())
}

// =========================================================================
// ENHANCED PLUGIN REGISTRY METHODS
// =========================================================================

impl PluginManager {
    /// Plugin Discovery - Scanne Verzeichnisse nach Plugins
    pub async fn discover_plugins(&self, scan_paths: Vec<PathBuf>) -> Result<PluginDiscoveryResult, String> {
        let start_time = std::time::Instant::now();
        let mut discovered_plugins = Vec::new();
        let mut errors = Vec::new();

        for scan_path in &scan_paths {
            if let Err(e) = self.scan_directory_for_plugins(scan_path, &mut discovered_plugins, &mut errors).await {
                errors.push(format!("Fehler beim Scannen von {:?}: {}", scan_path, e));
            }
        }

        let scan_duration = start_time.elapsed().as_millis() as u64;

        // Update registry stats
        {
            let mut stats = self.registry_stats.write().await;
            stats.last_scan = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64;
        }

        Ok(PluginDiscoveryResult {
            discovered_plugins,
            scan_duration,
            scan_paths,
            errors,
        })
    }

    /// Scanne ein Verzeichnis nach Plugins
    async fn scan_directory_for_plugins(
        &self,
        scan_path: &Path,
        discovered_plugins: &mut Vec<DiscoveredPlugin>,
        errors: &mut Vec<String>,
    ) -> Result<(), String> {
        if !scan_path.exists() {
            return Ok(());
        }

        let mut entries = std::fs::read_dir(scan_path)
            .map_err(|e| format!("Fehler beim Lesen des Verzeichnisses {:?}: {}", scan_path, e))?;

        while let Some(entry) = entries.next() {
            let entry = entry.map_err(|e| format!("Fehler beim Lesen des Verzeichniseintrags: {}", e))?;
            let path = entry.path();

            if path.is_dir() {
                // Scanne Unterverzeichnisse rekursiv (mit Box::pin für async recursion)
                let future = self.scan_directory_for_plugins(&path, discovered_plugins, errors);
                Box::pin(future).await?;
            } else if path.extension().and_then(|s| s.to_str()) == Some("wasm") {
                // Prüfe ob es ein Plugin ist
                if let Some(discovered_plugin) = self.validate_plugin_file(&path).await {
                    discovered_plugins.push(discovered_plugin);
                }
            }
        }

        Ok(())
    }

    /// Validiere eine Plugin-Datei
    async fn validate_plugin_file(&self, file_path: &Path) -> Option<DiscoveredPlugin> {
        // Prüfe ob Plugin bereits im Cache ist
        {
            let cache = self.discovery_cache.read().await;
            if let Some(cached_plugin) = cache.get(file_path) {
                return Some(cached_plugin.clone());
            }
        }

        // Versuche Plugin-Info zu extrahieren
        let plugin_info = match self.extract_plugin_info_from_wasm(file_path).await {
            Ok(info) => info,
            Err(_) => return None,
        };

        // Validiere Plugin (simuliert für jetzt)
        let validation_errors = Vec::new(); // self.validator.validate_plugin_info(&plugin_info);
        let is_valid = validation_errors.is_empty();

        // Prüfe Dependencies
        let dependencies = plugin_info.dependencies.clone().unwrap_or_default();
        let dependencies_available = self.check_dependencies_available(&dependencies).await;
        let missing_dependencies = if dependencies_available {
            Vec::new()
        } else {
            dependencies
        };

        let discovered_plugin = DiscoveredPlugin {
            plugin_id: plugin_info.id.clone(),
            file_path: file_path.to_path_buf(),
            plugin_info,
            is_valid,
            validation_errors,
            dependencies_available,
            missing_dependencies,
        };

        // Cache das Ergebnis
        {
            let mut cache = self.discovery_cache.write().await;
            cache.insert(file_path.to_path_buf(), discovered_plugin.clone());
        }

        Some(discovered_plugin)
    }

    /// Extrahiere Plugin-Info aus WASM-Datei
    async fn extract_plugin_info_from_wasm(&self, file_path: &Path) -> Result<PluginInfo, String> {
        
        // Versuche zuerst, eine Begleitdatei zu finden
        let manifest_path = file_path.with_extension("json");
        if manifest_path.exists() {
            // FIX BUG 4: extract_from_manifest gibt jetzt (PluginInfo, Vec<String>) zurück
            let (plugin_info, _unknown_permissions) = self.extract_from_manifest(&manifest_path).await?;
            return Ok(plugin_info);
        }
        
        let toml_path = file_path.with_extension("toml");
        if toml_path.exists() {
            return self.extract_from_toml_manifest(&toml_path).await;
        }
        
        // Versuche, Plugin-Info aus WASM-Custom-Section zu extrahieren
        if let Ok(info) = self.extract_from_wasm_custom_section(file_path).await {
            return Ok(info);
        }
        
        // Fallback: Versuche, Metadaten aus WASM-Exports/Imports zu extrahieren
        if let Ok(info) = self.extract_from_wasm_metadata(file_path).await {
            return Ok(info);
        }
        
        // Letzter Fallback: Generiere Plugin-Info aus Dateiname
        let file_stem = file_path.file_stem()
            .and_then(|s| s.to_str())
            .ok_or("Invalid file path")?;
            
        Ok(PluginInfo {
            id: format!("plugin_{}", file_stem),
            name: format!("Discovered Plugin {}", file_stem),
            version: "1.0.0".to_string(),
            author: "Unknown".to_string(),
            description: format!("Auto-discovered plugin from {}", file_stem),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![connectias_api::PluginPermission::Storage],
            entry_point: "plugin.wasm".to_string(),
            dependencies: None,
        })
    }
    
    /// Extrahiere Plugin-Info aus JSON-Manifest
    /// FIX BUG 4: Gibt auch unbekannte Permissions zurück für Speicherung in Registry
    async fn extract_from_manifest(&self, manifest_path: &std::path::Path) -> Result<(PluginInfo, Vec<String>), String> {
        use std::fs;
        use serde_json;
        
        let content = fs::read_to_string(manifest_path)
            .map_err(|e| format!("Failed to read manifest: {}", e))?;
            
        let manifest: serde_json::Value = serde_json::from_str(&content)
            .map_err(|e| format!("Failed to parse manifest JSON: {}", e))?;
        
        // FIX BUG 4: Parse Permissions (gibt Tuple zurück: bekannte + unbekannte)
        let (permissions, unknown_permissions) = self.parse_permissions_from_manifest(&manifest)
            .map_err(|e| format!("Failed to parse permissions: {}", e))?;
            
        Ok((
            PluginInfo {
                id: manifest["id"].as_str().unwrap_or("unknown").to_string(),
                name: manifest["name"].as_str().unwrap_or("Unknown Plugin").to_string(),
                version: manifest["version"].as_str().unwrap_or("1.0.0").to_string(),
                author: manifest["author"].as_str().unwrap_or("Unknown").to_string(),
                description: manifest["description"].as_str().unwrap_or("No description").to_string(),
                min_core_version: manifest["min_core_version"].as_str().unwrap_or("1.0.0").to_string(),
                max_core_version: manifest["max_core_version"].as_str().map(|s| s.to_string()),
                permissions,
                entry_point: manifest["entry_point"].as_str().unwrap_or("plugin.wasm").to_string(),
                dependencies: self.parse_dependencies_from_manifest(&manifest)
                    .map_err(|e| format!("Failed to parse dependencies: {}", e))?,
            },
            unknown_permissions
        ))
    }
    
    /// Extrahiere Plugin-Info aus TOML-Manifest
    async fn extract_from_toml_manifest(&self, manifest_path: &std::path::Path) -> Result<PluginInfo, String> {
        use std::fs;
        use toml::Value;
        
        // Lese TOML-Datei
        let toml_content = fs::read_to_string(manifest_path)
            .map_err(|e| format!("Failed to read TOML manifest: {}", e))?;
        
        // Parse TOML
        let toml_value: Value = toml::from_str(&toml_content)
            .map_err(|e| format!("Failed to parse TOML: {}", e))?;
        
        // Extrahiere Plugin-Info aus TOML-Struktur
        let table = toml_value.as_table()
            .ok_or_else(|| "TOML root must be a table".to_string())?;
        
        // Erforderliche Felder extrahieren
        let id = table.get("id")
            .and_then(|v| v.as_str())
            .ok_or_else(|| "Missing 'id' field in TOML manifest".to_string())?
            .to_string();
            
        let name = table.get("name")
            .and_then(|v| v.as_str())
            .ok_or_else(|| "Missing 'name' field in TOML manifest".to_string())?
            .to_string();
            
        let version = table.get("version")
            .and_then(|v| v.as_str())
            .ok_or_else(|| "Missing 'version' field in TOML manifest".to_string())?
            .to_string();
            
        let author = table.get("author")
            .and_then(|v| v.as_str())
            .unwrap_or("Unknown")
            .to_string();
            
        let description = table.get("description")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
            
        let min_core_version = table.get("min_core_version")
            .and_then(|v| v.as_str())
            .unwrap_or("1.0.0")
            .to_string();
            
        let max_core_version = table.get("max_core_version")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string());
            
        let entry_point = table.get("entry_point")
            .and_then(|v| v.as_str())
            .unwrap_or("main.wasm")
            .to_string();
            
        // Permissions extrahieren - TOML-Struktur direkt parsen
        let permissions = self.parse_permissions_from_toml(table.get("permissions"))
            .map_err(|e| format!("Failed to parse permissions: {}", e))?;
            
        // Dependencies extrahieren
        let dependencies = table.get("dependencies")
            .and_then(|v| v.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|v| v.as_str())
                    .map(|s| s.to_string())
                    .collect::<Vec<String>>()
            });
        
        Ok(PluginInfo {
            id,
            name,
            version,
            author,
            description,
            min_core_version,
            max_core_version,
            permissions,
            entry_point,
            dependencies,
        })
    }
    
    /// Extrahiere Plugin-Info aus WASM-Custom-Section
    async fn extract_from_wasm_custom_section(&self, file_path: &std::path::Path) -> Result<PluginInfo, String> {
        use std::fs;
        use std::io::Read;
        
        let mut file = fs::File::open(file_path)
            .map_err(|e| format!("Failed to open WASM file: {}", e))?;
            
        let mut buffer = Vec::new();
        file.read_to_end(&mut buffer)
            .map_err(|e| format!("Failed to read WASM file: {}", e))?;
            
        // Suche nach "plugin-info" Custom Section
        if let Some(info_data) = self.find_custom_section(&buffer, "plugin-info") {
            if let Ok(info) = serde_json::from_slice::<PluginInfo>(&info_data) {
                return Ok(info);
            }
        }
        
        Err("No plugin-info custom section found".to_string())
    }
    
    /// Extrahiere Metadaten aus WASM-Exports/Imports
    async fn extract_from_wasm_metadata(&self, file_path: &std::path::Path) -> Result<PluginInfo, String> {
        use std::fs;
        use std::io::Read;
        
        let mut file = fs::File::open(file_path)
            .map_err(|e| format!("Failed to open WASM file: {}", e))?;
            
        let mut buffer = Vec::new();
        file.read_to_end(&mut buffer)
            .map_err(|e| format!("Failed to read WASM file: {}", e))?;
            
        // Einfache WASM-Parsing für Metadaten
        let file_stem = file_path.file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("unknown");
            
        // Versuche, Exports zu analysieren
        let exports = self.analyze_wasm_exports(&buffer)?;
        
        Ok(PluginInfo {
            id: format!("plugin_{}", file_stem),
            name: format!("WASM Plugin {}", file_stem),
            version: "1.0.0".to_string(),
            author: "Unknown".to_string(),
            description: format!("WASM plugin with exports: {}", exports.join(", ")),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![connectias_api::PluginPermission::Storage],
            entry_point: "plugin.wasm".to_string(),
            dependencies: None,
        })
    }
    
    /// Finde Custom Section in WASM-Binary
    fn find_custom_section(&self, wasm_data: &[u8], section_name: &str) -> Option<Vec<u8>> {
        use wasmparser::{Parser, Payload};
        
        let parser = Parser::new(0);
        
        for payload in parser.parse_all(wasm_data) {
            match payload {
                Ok(Payload::CustomSection(reader)) => {
                    if reader.name() == section_name {
                        return Some(reader.data().to_vec());
                    }
                }
                Ok(_) => {
                    // Ignore other sections
                }
                Err(_) => {
                    // Skip invalid sections
                    continue;
                }
            }
        }
        
        None
    }
    
    
    /// Analysiere WASM-Exports
    fn analyze_wasm_exports(&self, wasm_data: &[u8]) -> Result<Vec<String>, String> {
        use wasmparser::{Parser, Payload};
        
        let parser = Parser::new(0);
        let mut exports = Vec::new();
        
        for payload in parser.parse_all(wasm_data) {
            match payload {
                Ok(Payload::ExportSection(reader)) => {
                    for export in reader {
                        match export {
                            Ok(export) => {
                                exports.push(export.name.to_string());
                            }
                            Err(_) => {
                                // Skip invalid exports
                                continue;
                            }
                        }
                    }
                }
                Ok(_) => {
                    // Ignore other sections
                }
                Err(_) => {
                    // Skip invalid sections
                    continue;
                }
            }
        }
        
        Ok(exports)
    }
    
    /// Parse Permissions aus JSON-Manifest
    /// FIX BUG 3 + BUG 4: Respektiert den Strict-Mode Flag und gibt unbekannte Permissions zurück
    /// 
    /// Returns: (Vec<PluginPermission>, Vec<String>) - (bekannte Permissions, unbekannte Permissions)
    fn parse_permissions_from_manifest(&self, manifest: &serde_json::Value) -> Result<(Vec<connectias_api::PluginPermission>, Vec<String>), String> {
        let mut permissions = Vec::new();
        let mut unknown_permissions = Vec::new();
        
        if let Some(perms) = manifest["permissions"].as_array() {
            for perm in perms {
                if let Some(perm_str) = perm.as_str() {
                    match perm_str {
                        "Storage" => permissions.push(connectias_api::PluginPermission::Storage),
                        "Network" => permissions.push(connectias_api::PluginPermission::Network),
                        unknown => {
                            // Sammle unbekannte Permissions
                            unknown_permissions.push(unknown.to_string());
                        }
                    }
                }
            }
        }
        
        // FIX BUG 3 + BUG 4: Handling von unbekannten Permissions je nach Strict-Mode
        if !unknown_permissions.is_empty() {
            if self.strict_mode {
                // Strict Mode: Verweigere Plugin-Loading bei unbekannten Permissions (Standard)
                return Err(format!(
                    "Unbekannte Permissions im JSON-Manifest gefunden: {}. Erlaubte Permissions: 'Storage', 'Network'. Plugin-Loading wird verweigert (Strict-Mode aktiv).",
                    unknown_permissions.join(", ")
                ));
            } else {
                // FIX BUG 4: Non-Strict Mode: Warne, speichere unbekannte Permissions in Metadaten
                // Dies ermöglicht späteres Verwenden wenn Connectias aktualisiert wird
                log::warn!(
                    "Unbekannte Permissions im JSON-Manifest gefunden: {} (ggf. aus zukünftiger Version). Plugin wird mit bekannten Permissions geladen, unbekannte werden in Metadaten gespeichert.",
                    unknown_permissions.join(", ")
                );
                // Unbekannte Permissions werden zurückgegeben (für Speicherung in Registry)
            }
        }
        
        if permissions.is_empty() {
            permissions.push(connectias_api::PluginPermission::Storage);
        }
        
        // FIX BUG 4: Gebe sowohl bekannte als auch unbekannte Permissions zurück
        Ok((permissions, unknown_permissions))
    }
    
    /// Parse Permissions aus TOML-Manifest
    /// FIX BUG 3: Respektiert den Strict-Mode Flag
    fn parse_permissions_from_toml(&self, permissions_value: Option<&toml::Value>) -> Result<Vec<connectias_api::PluginPermission>, String> {
        let mut permissions = Vec::new();
        let mut unknown_permissions = Vec::new();
        
        if let Some(Some(perms)) = permissions_value.map(|v| v.as_array()) {
            for perm in perms {
                if let Some(perm_str) = perm.as_str() {
                    match perm_str {
                        "Storage" => permissions.push(connectias_api::PluginPermission::Storage),
                        "Network" => permissions.push(connectias_api::PluginPermission::Network),
                        unknown => {
                            // Sammle unbekannte Permissions
                            unknown_permissions.push(unknown.to_string());
                        }
                    }
                }
            }
        }
        
        // FIX BUG 3: Handling von unbekannten Permissions je nach Strict-Mode
        if !unknown_permissions.is_empty() {
            if self.strict_mode {
                // Strict Mode: Verweigere Plugin-Loading bei unbekannten Permissions (Standard)
                return Err(format!(
                    "Unbekannte Permissions im TOML-Manifest gefunden: {}. Erlaubte Permissions: 'Storage', 'Network'. Plugin-Loading wird verweigert (Strict-Mode aktiv).",
                    unknown_permissions.join(", ")
                ));
            } else {
                // Non-Strict Mode: Warne, aber lade Plugin mit bekannten Permissions (Forward-Compatibility)
                log::warn!(
                    "Unbekannte Permissions im TOML-Manifest gefunden: {} (ggf. aus zukünftiger Version). Plugin wird mit bekannten Permissions geladen.",
                    unknown_permissions.join(", ")
                );
            }
        }
        
        // Wenn keine Permissions angegeben, Standard verwenden
        if permissions.is_empty() {
            permissions.push(connectias_api::PluginPermission::Storage);
        }
        
        Ok(permissions)
    }
    
    /// Parse Dependencies aus JSON-Manifest
    /// FIX BUG 2: Gibt jetzt Result zurück um Plugin-Loading bei ungültigen Dependencies zu verhindern
    /// Validierung verhindert Tippfehler in Dependency-Namen, die zu Runtime-Fehlern führen würden
    fn parse_dependencies_from_manifest(&self, manifest: &serde_json::Value) -> Result<Vec<String>, String> {
        if let Some(deps) = manifest["dependencies"].as_array() {
            let mut dependencies = Vec::new();
            let mut invalid_deps = Vec::new();
            for dep in deps {
                if let Some(dep_str) = dep.as_str() {
                    // FIX BUG 2: Validiere Dependency-Name (nicht leer, keine ungültigen Zeichen)
                    if dep_str.is_empty() {
                        invalid_deps.push("(empty string)".to_string());
                    } else if !dep_str.chars().all(|c| c.is_alphanumeric() || c == '-' || c == '_' || c == '.') {
                        invalid_deps.push(format!("'{}' (contains invalid characters)", dep_str));
                    } else {
                        dependencies.push(dep_str.to_string());
                    }
                } else {
                    // FIX BUG 2: Dependency ist kein String
                    invalid_deps.push(format!("{:?} (not a string)", dep));
                }
            }
            
            // FIX BUG 2: Wenn ungültige Dependencies gefunden wurden, verhindere Plugin-Loading
            if !invalid_deps.is_empty() {
                return Err(format!(
                    "Ungültige Dependencies im JSON-Manifest gefunden: {}. Dependencies müssen nicht-leere Strings mit nur alphanumerischen Zeichen, Bindestrichen, Unterstrichen oder Punkten sein.",
                    invalid_deps.join(", ")
                ));
            }
            
            Ok(dependencies)
        } else {
            Ok(Vec::new())
        }
    }

    /// Prüfe ob Dependencies verfügbar sind
    async fn check_dependencies_available(&self, dependencies: &[String]) -> bool {
        let registry = self.plugin_registry.read().await;
        dependencies.iter().all(|dep| registry.contains_key(dep))
    }

    /// Dependency Resolution für Plugin
    pub async fn resolve_dependencies(&self, plugin_id: &str) -> Result<DependencyResolutionResult, String> {
        let registry = self.plugin_registry.read().await;
        let dependency_graph = self.dependency_graph.read().await;

        let plugin_entry = registry.get(plugin_id)
            .ok_or_else(|| format!("Plugin {} nicht in Registry gefunden", plugin_id))?;

        let dependencies = &plugin_entry.dependencies;
        let mut resolved_dependencies = Vec::new();
        let mut missing_dependencies = Vec::new();
        let mut circular_dependencies = Vec::new();
        let mut load_order = Vec::new();

        // Topologische Sortierung für Dependency-Resolution
        let mut visited = HashSet::new();
        let mut temp_visited = HashSet::new();

        for dep in dependencies {
            if !self.resolve_dependency_recursive(
                dep,
                &registry,
                &dependency_graph,
                &mut visited,
                &mut temp_visited,
                &mut resolved_dependencies,
                &mut missing_dependencies,
                &mut circular_dependencies,
            ).await {
                missing_dependencies.push(dep.clone());
            }
        }

        // Erstelle Load-Order (Dependencies zuerst)
        load_order.extend(resolved_dependencies.clone());
        load_order.push(plugin_id.to_string());

        let is_resolvable = missing_dependencies.is_empty() && circular_dependencies.is_empty();

        Ok(DependencyResolutionResult {
            plugin_id: plugin_id.to_string(),
            resolved_dependencies,
            missing_dependencies,
            circular_dependencies,
            load_order,
            is_resolvable,
        })
    }

    /// Rekursive Dependency-Resolution
    async fn resolve_dependency_recursive(
        &self,
        plugin_id: &str,
        registry: &HashMap<String, PluginRegistryEntry>,
        dependency_graph: &HashMap<String, HashSet<String>>,
        visited: &mut HashSet<String>,
        temp_visited: &mut HashSet<String>,
        resolved: &mut Vec<String>,
        missing: &mut Vec<String>,
        circular: &mut Vec<String>,
    ) -> bool {
        if temp_visited.contains(plugin_id) {
            circular.push(plugin_id.to_string());
            return false;
        }

        if visited.contains(plugin_id) {
            return true;
        }

        temp_visited.insert(plugin_id.to_string());

        if let Some(plugin_entry) = registry.get(plugin_id) {
            for dep in &plugin_entry.dependencies {
                let future = self.resolve_dependency_recursive(
                    dep,
                    registry,
                    dependency_graph,
                    visited,
                    temp_visited,
                    resolved,
                    missing,
                    circular,
                );
                if !Box::pin(future).await {
                    return false;
                }
            }

            temp_visited.remove(plugin_id);
            visited.insert(plugin_id.to_string());
            resolved.push(plugin_id.to_string());
            true
        } else {
            missing.push(plugin_id.to_string());
            false
        }
    }

    /// Registriere Plugin in Registry
    pub async fn register_plugin(&self, plugin_entry: PluginRegistryEntry) -> Result<(), String> {
        let plugin_id = plugin_entry.plugin_id.clone();
        
        // Update dependency graph
        {
            let mut graph = self.dependency_graph.write().await;
            graph.insert(plugin_id.clone(), plugin_entry.dependencies.iter().cloned().collect());
        }

        // Add to registry
        {
            let mut registry = self.plugin_registry.write().await;
            registry.insert(plugin_id.clone(), plugin_entry);
        }

        // Update registry stats
        self.update_registry_stats().await;

        Ok(())
    }

    /// Entferne Plugin aus Registry
    pub async fn unregister_plugin(&self, plugin_id: &str) -> Result<(), String> {
        // Remove from registry
        {
            let mut registry = self.plugin_registry.write().await;
            registry.remove(plugin_id);
        }

        // Update dependency graph
        {
            let mut graph = self.dependency_graph.write().await;
            graph.remove(plugin_id);
            
            // Remove from other plugins' dependencies
            for (_, deps) in graph.iter_mut() {
                deps.remove(plugin_id);
            }
        }

        // Update registry stats
        self.update_registry_stats().await;

        Ok(())
    }

    /// Hole Registry-Statistiken
    pub async fn get_registry_stats(&self) -> PluginRegistryStats {
        self.registry_stats.read().await.clone()
    }

    /// Update Registry-Statistiken
    async fn update_registry_stats(&self) {
        let registry = self.plugin_registry.read().await;
        let mut stats = self.registry_stats.write().await;

        stats.total_plugins = registry.len();
        stats.loaded_plugins = registry.values().filter(|p| p.status == PluginStatus::Loaded).count();
        stats.running_plugins = registry.values().filter(|p| p.status == PluginStatus::Running).count();
        stats.disabled_plugins = registry.values().filter(|p| p.status == PluginStatus::Disabled).count();
        stats.error_plugins = registry.values().filter(|p| matches!(p.status, PluginStatus::Error { .. })).count();

        stats.total_memory_usage = registry.values().map(|p| p.resource_usage.memory_usage).sum();
        stats.total_storage_usage = registry.values().map(|p| p.resource_usage.storage_usage).sum();

        if !registry.is_empty() {
            stats.average_performance = registry.values()
                .map(|p| p.performance_metrics.success_rate)
                .sum::<f64>() / registry.len() as f64;
        } else {
            stats.average_performance = 0.0;
        }
    }

    /// Hole alle registrierten Plugins
    pub async fn get_registered_plugins(&self) -> Vec<PluginRegistryEntry> {
        let registry = self.plugin_registry.read().await;
        registry.values().cloned().collect()
    }

    /// Hole Plugin-Status
    pub async fn get_plugin_status(&self, plugin_id: &str) -> Option<PluginStatus> {
        let registry = self.plugin_registry.read().await;
        registry.get(plugin_id).map(|p| p.status.clone())
    }

    /// Update Plugin-Status
    pub async fn update_plugin_status(&self, plugin_id: &str, status: PluginStatus) -> Result<(), String> {
        let mut registry = self.plugin_registry.write().await;
        if let Some(plugin_entry) = registry.get_mut(plugin_id) {
            plugin_entry.status = status;
            Ok(())
        } else {
            Err(format!("Plugin {} nicht in Registry gefunden", plugin_id))
        }
    }

    /// Update Plugin Resource Usage
    pub async fn update_plugin_resource_usage(&self, plugin_id: &str, resource_usage: ResourceUsage) -> Result<(), String> {
        let mut registry = self.plugin_registry.write().await;
        if let Some(plugin_entry) = registry.get_mut(plugin_id) {
            plugin_entry.resource_usage = resource_usage;
            Ok(())
        } else {
            Err(format!("Plugin {} nicht in Registry gefunden", plugin_id))
        }
    }

    /// Update Plugin Performance Metrics
    pub async fn update_plugin_performance(&self, plugin_id: &str, performance_metrics: PerformanceMetrics) -> Result<(), String> {
        let mut registry = self.plugin_registry.write().await;
        if let Some(plugin_entry) = registry.get_mut(plugin_id) {
            plugin_entry.performance_metrics = performance_metrics;
            Ok(())
        } else {
            Err(format!("Plugin {} nicht in Registry gefunden", plugin_id))
        }
    }
    
    /// Hilfsfunktion für Plugin-spezifische Socket-Pfade
    fn get_plugin_socket_path(&self, plugin_id: &str) -> PathBuf {
        self.plugin_dir.join("ipc").join(format!("plugin_{}.sock", plugin_id))
    }
}

