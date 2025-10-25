use std::path::Path;
use std::sync::{Arc, Mutex};
use std::collections::HashMap;

pub mod models;
pub mod encryption;

// Re-export wichtige Typen
pub use models::*;
pub use encryption::EncryptionService;

// Storage Service Trait
pub trait StorageService {
    fn put(&self, key: &str, value: &[u8]) -> Result<(), StorageError>;
    fn get(&self, key: &str) -> Result<Option<Vec<u8>>, StorageError>;
    fn delete(&self, key: &str) -> Result<(), StorageError>;
    fn clear(&self) -> Result<(), StorageError>;
    fn size(&self) -> Result<usize, StorageError>;
}

// Storage Error
#[derive(Debug, thiserror::Error)]
pub enum StorageError {
    #[error("Storage operation failed: {0}")]
    OperationFailed(String),
    #[error("Key not found: {0}")]
    KeyNotFound(String),
    #[error("Storage quota exceeded: {0}")]
    QuotaExceeded(String),
    #[error("Encryption failed: {0}")]
    EncryptionFailed(String),
    #[error("Decryption failed: {0}")]
    DecryptionFailed(String),
}

pub struct Database {
    path: String,
    // In-Memory-Speicher (verwendet für diese Implementierung)
    data: Arc<Mutex<HashMap<String, Vec<u8>>>>,
}

impl Database {
    pub fn new(db_path: &Path) -> Result<Self, String> {
        // Simplified implementation - in real system would use async database
        println!("Initializing database at: {}", db_path.display());
        Ok(Self { 
            path: db_path.to_string_lossy().to_string(),
            data: Arc::new(Mutex::new(HashMap::new())),
        })
    }

    pub fn insert_plugin(&self, plugin: &models::PluginEntity) -> Result<(), String> {
        // Simplified implementation - in real system would use async database
        println!("Inserting plugin: {} at {}", plugin.id, self.path);
        Ok(())
    }

    // Plugin data management methods
    pub fn put_plugin_data(&self, plugin_id: &str, key: &str, data: &[u8]) -> Result<(), String> {
        // Validiere plugin_id
        if plugin_id.is_empty() || plugin_id.contains(':') {
            return Err(format!("Invalid plugin_id: contains ':' or is empty"));
        }
        
        // In-Memory-Speicher
        println!("Storing data for plugin {}: key={}, size={} bytes", plugin_id, key, data.len());
        let composite_key = format!("{}:{}", plugin_id, key);
        let mut storage = self.data.lock()
            .map_err(|e| format!("Mutex poisoned: {}", e))?;
        storage.insert(composite_key, data.to_vec());
        Ok(())
    }

    pub fn get_plugin_data(&self, plugin_id: &str, key: &str) -> Result<Option<Vec<u8>>, String> {
        // Validiere plugin_id
        if plugin_id.is_empty() || plugin_id.contains(':') {
            return Err(format!("Invalid plugin_id: contains ':' or is empty"));
        }
        
        // In-Memory-Speicher
        println!("Retrieving data for plugin {}: key={}", plugin_id, key);
        let composite_key = format!("{}:{}", plugin_id, key);
        let storage = self.data.lock()
            .map_err(|e| format!("Mutex poisoned: {}", e))?;
        Ok(storage.get(&composite_key).cloned())
    }

    pub fn delete_plugin_data(&self, plugin_id: &str, key: &str) -> Result<(), String> {
        // Validiere plugin_id
        if plugin_id.is_empty() || plugin_id.contains(':') {
            return Err(format!("Invalid plugin_id: contains ':' or is empty"));
        }
        
        // In-Memory-Speicher
        println!("Deleting data for plugin {}: key={}", plugin_id, key);
        let composite_key = format!("{}:{}", plugin_id, key);
        let mut storage = self.data.lock()
            .map_err(|e| format!("Mutex poisoned: {}", e))?;
        storage.remove(&composite_key);
        Ok(())
    }

    pub fn clear_plugin_data(&self, plugin_id: &str) -> Result<(), String> {
        // Validiere plugin_id
        if plugin_id.is_empty() || plugin_id.contains(':') {
            return Err(format!("Invalid plugin_id: contains ':' or is empty"));
        }
        
        // In-Memory-Speicher
        println!("Clearing all data for plugin {}", plugin_id);
        let mut storage = self.data.lock()
            .map_err(|e| format!("Mutex poisoned: {}", e))?;
        // Sichere Präfix-Prüfung: nur löschen wenn Plugin-ID exakt übereinstimmt
        storage.retain(|k, _| {
            k.split_once(':')
                .map(|(pid, _)| pid != plugin_id)
                .unwrap_or(true)
        });
        Ok(())
    }

    pub fn get_plugin_data_size(&self, plugin_id: &str) -> Result<usize, String> {
        // Validiere plugin_id
        if plugin_id.is_empty() || plugin_id.contains(':') {
            return Err(format!("Invalid plugin_id: contains ':' or is empty"));
        }
        
        // In-Memory-Speicher
        println!("Getting data size for plugin {}", plugin_id);
        let storage = self.data.lock()
            .map_err(|e| format!("Mutex poisoned: {}", e))?;
        let total_size: usize = storage
            .iter()
            .filter(|(k, _)| {
                k.split_once(':')
                    .map(|(pid, _)| pid == plugin_id)
                    .unwrap_or(false)
            })
            .map(|(_, v)| v.len())
            .sum();
        Ok(total_size)
    }

    pub fn log_plugin_event(&self, plugin_id: &str, event: &str) -> Result<(), String> {
        // Simplified implementation
        println!("Logging event for plugin {}: {}", plugin_id, event);
        Ok(())
    }
}

/// Plugin-specific storage service that ensures plugin isolation
pub struct PluginStorageService<'a> {
    database: &'a Database,
    plugin_id: String,
}

impl<'a> PluginStorageService<'a> {
    pub fn new(database: &'a Database, plugin_id: String) -> Self {
        Self {
            database,
            plugin_id,
        }
    }
}

impl<'a> StorageService for PluginStorageService<'a> {
    fn put(&self, key: &str, value: &[u8]) -> Result<(), StorageError> {
        self.database.put_plugin_data(&self.plugin_id, key, value)
            .map_err(|e| StorageError::OperationFailed(e))
    }
    
    fn get(&self, key: &str) -> Result<Option<Vec<u8>>, StorageError> {
        self.database.get_plugin_data(&self.plugin_id, key)
            .map_err(|e| StorageError::OperationFailed(e))
    }
    
    fn delete(&self, key: &str) -> Result<(), StorageError> {
        self.database.delete_plugin_data(&self.plugin_id, key)
            .map_err(|e| StorageError::OperationFailed(e))
    }
    
    fn clear(&self) -> Result<(), StorageError> {
        self.database.clear_plugin_data(&self.plugin_id)
            .map_err(|e| StorageError::OperationFailed(e))
    }
    
    fn size(&self) -> Result<usize, StorageError> {
        self.database.get_plugin_data_size(&self.plugin_id)
            .map_err(|e| StorageError::OperationFailed(e))
    }
}

// Example usage:
// let database = Database::new();
// let plugin_storage = PluginStorageService::new(&database, "my_plugin_id".to_string());
// plugin_storage.put("key", b"value")?; // This will be isolated to "my_plugin_id"

//ich diene der aktualisierung wala
