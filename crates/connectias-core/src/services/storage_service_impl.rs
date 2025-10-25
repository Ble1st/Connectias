use std::sync::Arc;
use connectias_api::{StorageService, PluginError};
use connectias_storage::Database;
use connectias_security::sandbox::ResourceQuotaManager;

pub struct StorageServiceImpl {
    plugin_id: String,
    db: Arc<Database>,
    encryption: Arc<connectias_storage::EncryptionService>,
    quota_manager: Arc<ResourceQuotaManager>,
}

impl StorageServiceImpl {
    pub fn new(
        plugin_id: String,
        db: Arc<Database>,
        encryption: Arc<connectias_storage::EncryptionService>,
        quota_manager: Arc<ResourceQuotaManager>,
    ) -> Self {
        Self {
            plugin_id,
            db,
            encryption,
            quota_manager,
        }
    }
}

impl StorageService for StorageServiceImpl {
    fn put(&self, key: &str, value: &[u8]) -> Result<(), PluginError> {
        // Quota check
        self.quota_manager.check_and_enforce(&self.plugin_id)
            .map_err(|e| PluginError::ExecutionFailed(format!("Storage quota exceeded: {}", e)))?;

        // Encrypt data
        let encrypted = self.encryption.encrypt(value)
            .map_err(|e| PluginError::ExecutionFailed(format!("Encryption failed: {}", e)))?;

        // Store in database
        self.db.put_plugin_data(&self.plugin_id, key, &encrypted)
            .map_err(|e| PluginError::ExecutionFailed(format!("Database error: {}", e)))?;
        
        Ok(())
    }
    
    fn get(&self, key: &str) -> Result<Option<Vec<u8>>, PluginError> {
        // Retrieve from database
        let encrypted = self.db.get_plugin_data(&self.plugin_id, key)
            .map_err(|e| PluginError::ExecutionFailed(format!("Database error: {}", e)))?;

        // Decrypt if found
        if let Some(data) = encrypted {
            let decrypted = self.encryption.decrypt(&data)
                .map_err(|e| PluginError::ExecutionFailed(format!("Decryption failed: {}", e)))?;
            Ok(Some(decrypted))
        } else {
            Ok(None)
        }
    }
    
    fn delete(&self, key: &str) -> Result<(), PluginError> {
        self.db.delete_plugin_data(&self.plugin_id, key)
            .map_err(|e| PluginError::ExecutionFailed(format!("Database error: {}", e)))?;
        Ok(())
    }

    fn clear(&self) -> Result<(), PluginError> {
        self.db.clear_plugin_data(&self.plugin_id)
            .map_err(|e| PluginError::ExecutionFailed(format!("Database error: {}", e)))?;
        Ok(())
    }

    fn size(&self) -> Result<usize, PluginError> {
        self.db.get_plugin_data_size(&self.plugin_id)
            .map_err(|e| PluginError::ExecutionFailed(format!("Database error: {}", e)))
    }
}

//ich diene der aktualisierung wala
