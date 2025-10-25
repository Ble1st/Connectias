use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use tokio::sync::RwLock;
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
        let message_broker = Arc::new(MessageBrokerManager::new());

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

        Ok(Self {
            plugins: Arc::new(RwLock::new(HashMap::new())),
            plugin_dir,
            wasm_runtime: WasmRuntime::new()
                .map_err(|e| format!("Fehler beim Initialisieren des WASM-Runtime: {}", e))?,
            signature_verifier: SignatureVerifier::new(),
            validator: PluginValidator::new(),
            rasp_protection: RaspProtection::new(),
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
        })
    }

    pub async fn load_plugin(&self, zip_path: &Path) -> Result<String, String> {
        let start_time = std::time::Instant::now();

        // 1. RASP Environment Check
        self.rasp_protection.check_environment()
            .map_err(|e| e.to_string())?;

        // 2. Verify signature
        self.signature_verifier.verify_plugin(zip_path)
            .map_err(|e| e.to_string())?;

        // 3. Validate plugin structure
        let plugin_info = self.validator.validate_plugin_zip(zip_path)
            .map_err(|e| e.to_string())?;

        // 3. Extract plugin to plugins directory
        let plugin_install_dir = self.plugin_dir.join(&plugin_info.id);
        self.extract_plugin(zip_path, &plugin_install_dir)
            .map_err(|e| e.to_string())?;

        // 4. Detect plugin type and load
        let plugin = self.load_plugin_by_type(&plugin_install_dir, &plugin_info)
            .await
            .map_err(|e| e.to_string())?;

        // 5. Create PluginContext with Services
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
        
        // 6. Initialize plugin with context
        let mut plugin = plugin;
        plugin.init(context)
            .map_err(|e| format!("Plugin initialization failed: {}", e))?;
        
        // 7. Record load time
        let load_time = start_time.elapsed();
        self.metrics_collector.record_load_time(&plugin_info.id, load_time).await;
        
        // 8. Set memory limit
        self.memory_manager.set_limit(&plugin_info.id, 100 * 1024 * 1024).await; // 100MB
        
        let mut plugins = self.plugins.write().await;
        plugins.insert(plugin_info.id.clone(), plugin);

        Ok(plugin_info.id)
    }

    async fn load_plugin_by_type(
        &self,
        install_dir: &Path,
        _info: &PluginInfo,
    ) -> Result<Box<dyn Plugin>, anyhow::Error> {
        // Check for WASM plugin
        let wasm_path = install_dir.join("plugin.wasm");
        if wasm_path.exists() {
            let wasm_bytes = std::fs::read(&wasm_path)?;
            let plugin = self.wasm_runtime.load_plugin(&wasm_bytes)?;
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

