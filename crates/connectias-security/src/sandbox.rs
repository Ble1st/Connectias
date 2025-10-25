use std::collections::HashMap;
use std::io;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

pub struct InputSanitizer;

impl InputSanitizer {
    pub fn new() -> Self {
        Self
    }

    /// Sanitisiert User-Input gegen XSS und Injection-Angriffe
    /// Gibt HTML-escaped String zurück und warnt bei SQL-Patterns
    pub fn sanitize_input(&self, input: &str) -> String {
        // HTML-Entities escapen
        let mut escaped = input
            .replace('&', "&amp;")
            .replace('<', "&lt;")
            .replace('>', "&gt;")
            .replace('"', "&quot;")
            .replace('\'', "&#x27;");
        
        // Path-Traversal-Patterns entfernen
        escaped = escaped.replace("..", "__")
                        .replace("./", "_/")
                        .replace(".\\", "_\\")
                        .replace("C:\\", "C_")
                        .replace("c:\\", "c_");
        
        // Prüfe auf SQL-Patterns und warne, aber entferne sie nicht
        if self.contains_sql_patterns(&escaped) {
            log::warn!("Suspicious SQL patterns detected in input: {}", input);
        }
        
        escaped
    }

    /// Prüft auf SQL-Injection-Patterns ohne sie zu entfernen
    fn contains_sql_patterns(&self, input: &str) -> bool {
        let sql_patterns = [
            "--", "/*", "*/", "xp_", "sp_", 
            "UNION", "SELECT", "INSERT", "UPDATE", "DELETE", "DROP"
        ];
        
        let input_upper = input.to_uppercase();
        sql_patterns.iter().any(|pattern| input_upper.contains(pattern))
    }
}

/// Plugin Sandbox für isolierte Plugin-Ausführung
pub struct PluginSandbox {
    plugin_id: String,
    resource_quota: ResourceQuotaManager,
    input_sanitizer: InputSanitizer,
}

impl PluginSandbox {
    pub fn new(plugin_id: String) -> Self {
        Self {
            plugin_id,
            resource_quota: ResourceQuotaManager::new(),
            input_sanitizer: InputSanitizer::new(),
        }
    }

    /// Führt ein Plugin in der Sandbox aus
    pub fn execute_plugin(&self, command: &str, args: HashMap<String, String>) -> Result<String, SandboxError> {
        // Resource-Limits prüfen
        self.resource_quota.check_and_enforce(&self.plugin_id)
            .map_err(|e| SandboxError::ResourceLimitExceeded(e.to_string()))?;

        // Input sanitization
        let sanitized_command = self.input_sanitizer.sanitize_input(command);
        let sanitized_args: HashMap<String, String> = args
            .into_iter()
            .map(|(k, v)| (self.input_sanitizer.sanitize_input(&k), self.input_sanitizer.sanitize_input(&v)))
            .collect();

        // Plugin-Ausführung (vereinfacht)
        Ok(format!("Executed: {} with args: {:?}", sanitized_command, sanitized_args))
    }
}

/// Resource Quota für Plugin-Ressourcen
#[derive(Debug, Clone)]
pub struct ResourceQuota {
    pub memory_limit: usize,
    pub cpu_limit: f64,
    pub storage_limit: usize,
    pub network_limit: u32,
}

impl Default for ResourceQuota {
    fn default() -> Self {
        Self {
            memory_limit: 100 * 1024 * 1024, // 100MB
            cpu_limit: 75.0, // 75%
            storage_limit: 10 * 1024 * 1024, // 10MB
            network_limit: 60, // 60 requests per minute
        }
    }
}

/// Sandbox-spezifische Fehler
#[derive(Debug, thiserror::Error)]
pub enum SandboxError {
    #[error("Resource limit exceeded: {0}")]
    ResourceLimitExceeded(String),
    #[error("Security violation: {0}")]
    SecurityViolation(String),
    #[error("Plugin execution failed: {0}")]
    ExecutionFailed(String),
    #[error("Input validation failed: {0}")]
    InputValidationFailed(String),
}

/// Resource Quota Manager für Plugin-Ressourcen-Limits
pub struct ResourceQuotaManager {
    limits: Arc<Mutex<HashMap<String, ResourceLimits>>>,
    usage: Arc<Mutex<HashMap<String, ResourceUsage>>>,
}

/// Resource Limits für ein Plugin
#[derive(Debug, Clone)]
pub struct ResourceLimits {
    pub max_memory: usize,        // 100MB
    pub max_cpu_percent: f64,     // 75%
    pub max_storage: usize,       // 10MB
    pub max_network_req_per_min: u32, // 60
    pub max_execution_time: Duration, // 30 seconds
}

impl Default for ResourceLimits {
    fn default() -> Self {
        Self {
            max_memory: 100 * 1024 * 1024, // 100MB
            max_cpu_percent: 75.0,
            max_storage: 10 * 1024 * 1024, // 10MB
            max_network_req_per_min: 60,
            max_execution_time: Duration::from_secs(30),
        }
    }
}

/// Resource Usage für ein Plugin
#[derive(Debug, Clone)]
pub struct ResourceUsage {
    pub memory_used: usize,
    pub cpu_usage: f64,
    pub storage_used: usize,
    pub network_requests: u32,
    pub execution_time: Duration,
    pub last_network_reset: Instant,
}

impl Default for ResourceUsage {
    fn default() -> Self {
        Self {
            memory_used: 0,
            cpu_usage: 0.0,
            storage_used: 0,
            network_requests: 0,
            execution_time: Duration::default(),
            last_network_reset: Instant::now(),
        }
    }
}

impl ResourceQuotaManager {
    pub fn new() -> Self {
        Self {
            limits: Arc::new(Mutex::new(HashMap::new())),
            usage: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Setzt Resource Limits für ein Plugin
    pub fn set_limits(&self, plugin_id: &str, limits: ResourceLimits) -> Result<(), super::SecurityError> {
        let mut limits_map = self.limits.lock()
            .map_err(|e| super::SecurityError::SecurityViolation(format!("Lock acquisition failed: {}", e)))?;
        limits_map.insert(plugin_id.to_string(), limits);
        Ok(())
    }

    /// Prüft und erzwingt Resource Limits
    pub fn check_and_enforce(&self, plugin_id: &str) -> Result<(), super::SecurityError> {
        let limits = {
            let limits_map = self.limits.lock()
            .map_err(|e| super::SecurityError::SecurityViolation(format!("Lock acquisition failed: {}", e)))?;
            limits_map.get(plugin_id).cloned().unwrap_or_default()
        };

        let mut usage_map = self.usage.lock()
            .map_err(|e| super::SecurityError::SecurityViolation(format!("Lock acquisition failed: {}", e)))?;
        let usage = usage_map.entry(plugin_id.to_string()).or_default();

        // Memory-Limit prüfen
        if usage.memory_used > limits.max_memory {
            return Err(super::SecurityError::SecurityViolation(
                format!("Memory limit exceeded: {} bytes used, {} bytes limit", 
                    usage.memory_used, limits.max_memory)
            ));
        }

        // CPU-Limit prüfen
        if usage.cpu_usage > limits.max_cpu_percent {
            return Err(super::SecurityError::SecurityViolation(
                format!("CPU limit exceeded: {}% used, {}% limit", 
                    usage.cpu_usage, limits.max_cpu_percent)
            ));
        }

        // Storage-Limit prüfen
        if usage.storage_used > limits.max_storage {
            return Err(super::SecurityError::SecurityViolation(
                format!("Storage limit exceeded: {} bytes used, {} bytes limit", 
                    usage.storage_used, limits.max_storage)
            ));
        }

        // Network-Rate-Limit prüfen
        if usage.last_network_reset.elapsed() > Duration::from_secs(60) {
            usage.network_requests = 0;
            usage.last_network_reset = Instant::now();
        }

        if usage.network_requests >= limits.max_network_req_per_min {
            return Err(super::SecurityError::SecurityViolation(
                format!("Network rate limit exceeded: {} requests, {} limit", 
                    usage.network_requests, limits.max_network_req_per_min)
            ));
        }

        // Execution-Time-Limit prüfen
        if usage.execution_time > limits.max_execution_time {
            return Err(super::SecurityError::SecurityViolation(
                format!("Execution time limit exceeded: {:?} used, {:?} limit", 
                    usage.execution_time, limits.max_execution_time)
            ));
        }

        Ok(())
    }

    /// Aktualisiert Memory-Usage
    pub fn update_memory_usage(&self, plugin_id: &str, memory_bytes: usize) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut usage_map = self.usage.lock().map_err(|e| Box::new(io::Error::new(io::ErrorKind::InvalidData, e.to_string())))?;
        let usage = usage_map.entry(plugin_id.to_string()).or_default();
        usage.memory_used = memory_bytes;
        Ok(())
    }

    /// Aktualisiert CPU-Usage
    pub fn update_cpu_usage(&self, plugin_id: &str, cpu_percent: f64) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut usage_map = self.usage.lock().map_err(|e| Box::new(io::Error::new(io::ErrorKind::InvalidData, e.to_string())))?;
        let usage = usage_map.entry(plugin_id.to_string()).or_default();
        usage.cpu_usage = cpu_percent;
        Ok(())
    }

    /// Aktualisiert Storage-Usage
    pub fn update_storage_usage(&self, plugin_id: &str, storage_bytes: usize) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut usage_map = self.usage.lock().map_err(|e| Box::new(io::Error::new(io::ErrorKind::InvalidData, e.to_string())))?;
        let usage = usage_map.entry(plugin_id.to_string()).or_default();
        usage.storage_used = storage_bytes;
        Ok(())
    }

    /// Registriert einen Network-Request
    pub fn register_network_request(&self, plugin_id: &str) -> Result<(), super::SecurityError> {
        let mut usage_map = self.usage.lock()
            .map_err(|e| super::SecurityError::SecurityViolation(format!("Lock acquisition failed: {}", e)))?;
        let usage = usage_map.entry(plugin_id.to_string()).or_default();
        
        // Rate-Limit prüfen
        if usage.last_network_reset.elapsed() > Duration::from_secs(60) {
            usage.network_requests = 0;
            usage.last_network_reset = Instant::now();
        }

        let limits = {
            let limits_map = self.limits.lock()
            .map_err(|e| super::SecurityError::SecurityViolation(format!("Lock acquisition failed: {}", e)))?;
            limits_map.get(plugin_id).cloned().unwrap_or_default()
        };

        if usage.network_requests >= limits.max_network_req_per_min {
            return Err(super::SecurityError::SecurityViolation(
                "Network rate limit exceeded".to_string()
            ));
        }

        usage.network_requests += 1;
        Ok(())
    }

    /// Aktualisiert Execution-Time
    pub fn update_execution_time(&self, plugin_id: &str, execution_time: Duration) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut usage_map = self.usage.lock().map_err(|e| Box::new(io::Error::new(io::ErrorKind::InvalidData, e.to_string())))?;
        let usage = usage_map.entry(plugin_id.to_string()).or_default();
        usage.execution_time = execution_time;
        Ok(())
    }

    /// Gibt aktuelle Usage zurück
    pub fn get_usage(&self, plugin_id: &str) -> Option<ResourceUsage> {
        let usage_map = self.usage.lock().ok()?;
        usage_map.get(plugin_id).cloned()
    }

    /// Reset Usage für ein Plugin
    pub fn reset_usage(&self, plugin_id: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut usage_map = self.usage.lock().map_err(|e| Box::new(io::Error::new(io::ErrorKind::InvalidData, e.to_string())))?;
        usage_map.remove(plugin_id);
        Ok(())
    }

    /// Entfernt Plugin aus dem Quota Manager
    pub fn remove_plugin(&self, plugin_id: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut limits_map = self.limits.lock().map_err(|e| Box::new(io::Error::new(io::ErrorKind::InvalidData, e.to_string())))?;
        let mut usage_map = self.usage.lock().map_err(|e| Box::new(io::Error::new(io::ErrorKind::InvalidData, e.to_string())))?;

        limits_map.remove(plugin_id);
        usage_map.remove(plugin_id);

        Ok(())
    }
}

/// Resource Monitor für kontinuierliche Überwachung
pub struct ResourceMonitor {
    quota_manager: Arc<ResourceQuotaManager>,
    monitoring_interval: Duration,
}

impl ResourceMonitor {
    pub fn new(quota_manager: Arc<ResourceQuotaManager>) -> Self {
        Self {
            quota_manager,
            monitoring_interval: Duration::from_secs(5),
        }
    }

    /// Startet kontinuierliche Resource-Überwachung
    pub async fn start_monitoring(&self) {
        let quota_manager = self.quota_manager.clone();
        let interval = self.monitoring_interval;

        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(interval);
            
            loop {
                interval_timer.tick().await;
                
                // Überwache alle aktiven Plugins
                let usage_map = match quota_manager.usage.lock() {
                    Ok(map) => map,
                    Err(e) => {
                        tracing::error!("Failed to acquire usage lock: {}", e);
                        continue;
                    }
                };
                for (plugin_id, usage) in usage_map.iter() {
                    // Log warnings bei hoher Resource-Nutzung
                    if usage.memory_used > 50 * 1024 * 1024 { // 50MB
                        tracing::warn!("Plugin {} high memory usage: {}MB", 
                            plugin_id, usage.memory_used / 1024 / 1024);
                    }
                    
                    if usage.cpu_usage > 50.0 {
                        tracing::warn!("Plugin {} high CPU usage: {}%", 
                            plugin_id, usage.cpu_usage);
                    }
                    
                    if usage.storage_used > 5 * 1024 * 1024 { // 5MB
                        tracing::warn!("Plugin {} high storage usage: {}MB", 
                            plugin_id, usage.storage_used / 1024 / 1024);
                    }
                }
            }
        });
    }
}

impl InputSanitizer {
    /// Validiert Input auf gefährliche Patterns und gibt Fehler zurück
    /// Verwendet strikte Validierung - bei Verdacht wird Fehler zurückgegeben
    pub fn sanitize_string(input: &str) -> Result<String, super::SecurityError> {
        // SQL Injection patterns
        let sql_patterns = ["'", "\"", ";", "--", "/*", "*/", "xp_", "sp_"];
        for pattern in &sql_patterns {
            if input.contains(pattern) {
                return Err(super::SecurityError::SecurityViolation(
                    format!("Dangerous pattern detected: {}", pattern)
                ));
            }
        }

        // Path traversal patterns
        if input.contains("..") || input.contains("./") || input.contains("\\") {
            return Err(super::SecurityError::SecurityViolation(
                "Path traversal detected".to_string()
            ));
        }

        Ok(input.to_string())
    }

    pub fn sanitize_args(args: &HashMap<String, String>) -> Result<HashMap<String, String>, super::SecurityError> {
        let mut sanitized = HashMap::new();
        for (key, value) in args {
            let clean_key = Self::sanitize_string(key)?;
            let clean_value = Self::sanitize_string(value)?;
            sanitized.insert(clean_key, clean_value);
        }
        Ok(sanitized)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sql_injection_detection() {
        // SQL Injection Patterns
        assert!(InputSanitizer::sanitize_string("SELECT * FROM users WHERE id = '1' OR '1'='1'").is_err());
        assert!(InputSanitizer::sanitize_string("admin'--").is_err());
        assert!(InputSanitizer::sanitize_string("'; DROP TABLE users; --").is_err());
    }

    #[test]
    fn test_xss_detection() {
        // XSS Patterns (würde in erweiterte Version)
        assert!(InputSanitizer::sanitize_string("normal_string").is_ok());
    }

    #[test]
    fn test_path_traversal_detection() {
        assert!(InputSanitizer::sanitize_string("../etc/passwd").is_err());
        assert!(InputSanitizer::sanitize_string("./config").is_err());
        assert!(InputSanitizer::sanitize_string("C:\\Windows\\System32").is_err());
    }

    #[test]
    fn test_safe_strings() {
        assert!(InputSanitizer::sanitize_string("safe_string_123").is_ok());
        assert!(InputSanitizer::sanitize_string("user@example.com").is_ok());
    }

    #[test]
    fn test_sanitize_args() {
        let mut args = HashMap::new();
        args.insert("key1".to_string(), "value1".to_string());
        args.insert("key2".to_string(), "value2".to_string());
        
        let result = InputSanitizer::sanitize_args(&args);
        assert!(result.is_ok());
        
        let sanitized = result.unwrap(); // Test-Code, unwrap() ist hier OK
        assert_eq!(sanitized.len(), 2);
    }

    #[test]
    fn test_sanitize_args_malicious() {
        let mut args = HashMap::new();
        args.insert("key".to_string(), "'; DROP TABLE--".to_string());
        
        let result = InputSanitizer::sanitize_args(&args);
        assert!(result.is_err());
    }
}

