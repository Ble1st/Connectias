use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use connectias_api::PluginError;
use thiserror::Error;
use async_trait::async_trait;
use connectias_security::threat_detection::PermissionServiceTrait;

/// Permission Service für Plugin-Berechtigungen
#[derive(Clone)]
pub struct PermissionService {
    plugin_permissions: Arc<RwLock<HashMap<String, Vec<String>>>>,
    default_permissions: Vec<String>,
}

/// Permission Service Errors
#[derive(Debug, Error)]
pub enum PermissionError {
    #[error("Permission denied: {permission} not granted for plugin {plugin_id}")]
    PermissionDenied { plugin_id: String, permission: String },
    #[error("Invalid permission: {permission}")]
    InvalidPermission { permission: String },
    #[error("Plugin not found: {plugin_id}")]
    PluginNotFound { plugin_id: String },
    #[error("Lock poisoned for plugin {plugin_id}: {context}")]
    LockPoisoned { plugin_id: String, context: String },
}

/// Gültige Permissions (Single Source of Truth)
const VALID_PERMISSIONS: &[&str] = &[
    "Storage",
    "Network",
    "Logger",
    "SystemInfo",
    "FileSystem",
    "Database",
    "Crypto",
    "UI",
];

impl From<PermissionError> for PluginError {
    fn from(err: PermissionError) -> Self {
        match err {
            PermissionError::PermissionDenied { plugin_id, permission } => {
                PluginError::PermissionDenied { permission: format!("{} for plugin {}", permission, plugin_id) }
            },
            PermissionError::InvalidPermission { permission } => {
                PluginError::PermissionRequestFailed(format!("Invalid permission: {}", permission))
            },
            PermissionError::PluginNotFound { plugin_id } => {
                PluginError::NotFound(format!("Plugin not found: {}", plugin_id))
            },
            PermissionError::LockPoisoned { .. } => {
                PluginError::ExecutionFailed("Permission service lock poisoned".to_string())
            },
        }
    }
}

impl PermissionService {
    /// Erstellt einen neuen PermissionService
    pub fn new() -> Self {
        Self {
            plugin_permissions: Arc::new(RwLock::new(HashMap::new())),
            default_permissions: vec![
                "Storage".to_string(),
                "Logger".to_string(),
                "SystemInfo".to_string(),
            ],
        }
    }

    /// Setzt Berechtigungen für ein Plugin
    pub async fn set_plugin_permissions(&self, plugin_id: &str, permissions: Vec<String>) -> Result<(), PermissionError> {
        // Validiere Permissions
        for permission in &permissions {
            if !self.is_valid_permission(permission) {
                return Err(PermissionError::InvalidPermission { 
                    permission: permission.clone() 
                });
            }
        }

        let mut perms_map = self.plugin_permissions.write()
            .map_err(|e| PermissionError::LockPoisoned { 
                plugin_id: plugin_id.to_string(), 
                context: format!("write lock: {}", e) 
            })?;
        
        perms_map.insert(plugin_id.to_string(), permissions);
        Ok(())
    }

    /// Prüft ob ein Plugin eine Berechtigung hat
    pub async fn check_permission(&self, plugin_id: &str, permission: &str) -> Result<bool, PermissionError> {
        let perms_map = self.plugin_permissions.read()
            .map_err(|e| PermissionError::LockPoisoned { 
                plugin_id: plugin_id.to_string(), 
                context: format!("read lock: {}", e) 
            })?;
        
        if let Some(permissions) = perms_map.get(plugin_id) {
            Ok(permissions.contains(&permission.to_string()))
        } else {
            // Fallback auf Default-Permissions
            Ok(self.default_permissions.contains(&permission.to_string()))
        }
    }

    /// Beschränkt Berechtigungen für ein Plugin
    pub async fn restrict_plugin_permissions(&self, plugin_id: &str, allowed_permissions: Vec<String>) -> Result<(), PermissionError> {
        // Validiere erlaubte Permissions
        for permission in &allowed_permissions {
            if !self.is_valid_permission(permission) {
                return Err(PermissionError::InvalidPermission { 
                    permission: permission.clone() 
                });
            }
        }

        let mut perms_map = self.plugin_permissions.write()
            .map_err(|e| PermissionError::LockPoisoned { 
                plugin_id: plugin_id.to_string(), 
                context: format!("write lock: {}", e) 
            })?;
        
        perms_map.insert(plugin_id.to_string(), allowed_permissions);
        Ok(())
    }

    /// Entzieht eine Berechtigung von einem Plugin
    pub async fn revoke_permission(&self, plugin_id: &str, permission: &str) -> Result<(), PermissionError> {
        let mut perms_map = self.plugin_permissions.write()
            .map_err(|e| PermissionError::LockPoisoned { 
                plugin_id: plugin_id.to_string(), 
                context: format!("write lock: {}", e) 
            })?;
        
        if let Some(permissions) = perms_map.get_mut(plugin_id) {
            permissions.retain(|p| p != permission);
        }
        
        Ok(())
    }

    /// Gibt alle Berechtigungen eines Plugins zurück
    pub async fn get_plugin_permissions(&self, plugin_id: &str) -> Result<Vec<String>, PermissionError> {
        let perms_map = self.plugin_permissions.read()
            .map_err(|e| PermissionError::LockPoisoned { 
                plugin_id: plugin_id.to_string(), 
                context: format!("read lock: {}", e) 
            })?;
        
        if let Some(permissions) = perms_map.get(plugin_id) {
            Ok(permissions.clone())
        } else {
            Ok(self.default_permissions.clone())
        }
    }

    /// Entfernt alle Berechtigungen eines Plugins
    pub async fn remove_plugin_permissions(&self, plugin_id: &str) -> Result<(), PermissionError> {
        let mut perms_map = self.plugin_permissions.write()
            .map_err(|e| PermissionError::LockPoisoned { 
                plugin_id: plugin_id.to_string(), 
                context: format!("write lock: {}", e) 
            })?;
        
        perms_map.remove(plugin_id);
        Ok(())
    }

    /// Validiert ob eine Permission gültig ist
    fn is_valid_permission(&self, permission: &str) -> bool {
        VALID_PERMISSIONS.contains(&permission)
    }

    /// Gibt alle verfügbaren Permissions zurück
    pub fn get_available_permissions(&self) -> Vec<String> {
        VALID_PERMISSIONS.iter().map(|s| s.to_string()).collect()
    }

    /// Prüft ob ein Plugin alle erforderlichen Permissions hat
    pub async fn check_required_permissions(&self, plugin_id: &str, required: &[String]) -> Result<bool, PermissionError> {
        for permission in required {
            if !self.check_permission(plugin_id, permission).await? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    /// Erstellt eine Permission-Audit-Log
    pub async fn audit_permissions(&self) -> HashMap<String, Vec<String>> {
        let perms_map = self.plugin_permissions.read().unwrap_or_else(|poisoned| {
            log::warn!("Permission map lock poisoned during audit, recovering data");
            poisoned.into_inner()
        });
        
        perms_map.clone()
    }

    /// Startet kontinuierliche Permission-Überwachung
    pub async fn start_monitoring(&self) {
        let permission_service = self.clone();
        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(std::time::Duration::from_secs(300)); // 5 Minuten
            
            loop {
                interval_timer.tick().await;
                
                // Überwache Permission-Violations
                let audit = permission_service.audit_permissions().await;
                if !audit.is_empty() {
                    log::info!("Permission audit: {} Plugins mit aktiven Permissions", audit.len());
                }
            }
        });
    }
}

#[async_trait]
impl PermissionServiceTrait for PermissionService {
    async fn restrict_plugin_permissions(&self, plugin_id: &str, allowed: Vec<String>) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        self.restrict_plugin_permissions(plugin_id, allowed).await
            .map_err(|e| Box::new(e) as Box<dyn std::error::Error + Send + Sync>)
    }

    async fn revoke_permission(&self, plugin_id: &str, permission: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        self.revoke_permission(plugin_id, permission).await
            .map_err(|e| Box::new(e) as Box<dyn std::error::Error + Send + Sync>)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_permission_service_creation() {
        let service = PermissionService::new();
        assert!(service.get_available_permissions().len() > 0);
    }

    #[tokio::test]
    async fn test_set_and_check_permissions() {
        let service = PermissionService::new();
        let plugin_id = "test-plugin";
        let permissions = vec!["Storage".to_string(), "Network".to_string()];

        // Set permissions
        service.set_plugin_permissions(plugin_id, permissions.clone()).await.unwrap();

        // Check permissions
        assert!(service.check_permission(plugin_id, "Storage").await.unwrap());
        assert!(service.check_permission(plugin_id, "Network").await.unwrap());
        assert!(!service.check_permission(plugin_id, "FileSystem").await.unwrap());
    }

    #[tokio::test]
    async fn test_restrict_permissions() {
        let service = PermissionService::new();
        let plugin_id = "test-plugin";
        
        // Set initial permissions
        service.set_plugin_permissions(plugin_id, vec!["Storage".to_string(), "Network".to_string()]).await.unwrap();
        
        // Restrict to only Storage
        service.restrict_plugin_permissions(plugin_id, vec!["Storage".to_string()]).await.unwrap();
        
        // Check restricted permissions
        assert!(service.check_permission(plugin_id, "Storage").await.unwrap());
        assert!(!service.check_permission(plugin_id, "Network").await.unwrap());
    }

    #[tokio::test]
    async fn test_revoke_permission() {
        let service = PermissionService::new();
        let plugin_id = "test-plugin";
        let permissions = vec!["Storage".to_string(), "Network".to_string()];

        service.set_plugin_permissions(plugin_id, permissions).await.unwrap();
        service.revoke_permission(plugin_id, "Network").await.unwrap();

        assert!(service.check_permission(plugin_id, "Storage").await.unwrap());
        assert!(!service.check_permission(plugin_id, "Network").await.unwrap());
    }

    #[tokio::test]
    async fn test_invalid_permission() {
        let service = PermissionService::new();
        let plugin_id = "test-plugin";
        let invalid_permissions = vec!["InvalidPermission".to_string()];

        let result = service.set_plugin_permissions(plugin_id, invalid_permissions).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_required_permissions_check() {
        let service = PermissionService::new();
        let plugin_id = "test-plugin";
        let permissions = vec!["Storage".to_string(), "Network".to_string()];

        service.set_plugin_permissions(plugin_id, permissions).await.unwrap();

        // Check required permissions
        let required = vec!["Storage".to_string()];
        assert!(service.check_required_permissions(plugin_id, &required).await.unwrap());

        let required_all = vec!["Storage".to_string(), "Network".to_string()];
        assert!(service.check_required_permissions(plugin_id, &required_all).await.unwrap());

        let required_missing = vec!["Storage".to_string(), "FileSystem".to_string()];
        assert!(!service.check_required_permissions(plugin_id, &required_missing).await.unwrap());
    }
}
