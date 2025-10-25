use serde::{Deserialize, Serialize};

// Plugin Data Model - In-Memory Representation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginData {
    pub id: String,
    pub plugin_id: String,
    pub key: String,
    pub value: Vec<u8>,
    pub data_type: String,
    pub timestamp: i64,
    pub size: i64,
    pub is_encrypted: bool,
}

impl PluginData {
    /// Erstellt eine neue PluginData-Instanz mit automatischer Größenberechnung
    pub fn new(
        id: String,
        plugin_id: String,
        key: String,
        value: Vec<u8>,
        data_type: String,
        timestamp: i64,
        is_encrypted: bool,
    ) -> Self {
        let size = value.len() as i64;
        Self {
            id,
            plugin_id,
            key,
            value,
            data_type,
            timestamp,
            size,
            is_encrypted,
        }
    }

    /// Gibt die Größe der Daten zurück
    pub fn get_size(&self) -> i64 {
        self.size
    }

    /// Validiert, dass die Größe mit der tatsächlichen Datenlänge übereinstimmt
    pub fn validate_size(&self) -> bool {
        self.size == self.value.len() as i64
    }
}

// Plugin Metadata Model - Alias für PluginEntity
pub type PluginMetadata = PluginEntity;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginEntity {
    pub id: String,
    pub name: String,
    pub version: String,
    pub author: String,
    pub description: String,
    pub install_path: String,
    pub state: String,
    pub install_date: i64,
    pub last_update: i64,
    pub min_core_version: String,
    pub max_core_version: Option<String>,
    pub entry_point: String,
    pub dependencies: Option<String>,
    pub is_enabled: bool,
    pub signature: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginDataEntity {
    pub id: Option<i64>,
    pub plugin_id: String,
    pub key: String,
    pub value: Vec<u8>,
    pub data_type: String,
    pub timestamp: i64,
    pub size: i64,
    pub is_encrypted: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginLogEntity {
    pub id: Option<i64>,
    pub plugin_id: String,
    pub level: String,
    pub message: String,
    pub timestamp: i64,
    pub stack_trace: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginPermissionEntity {
    pub id: Option<i64>,
    pub plugin_id: String,
    pub permission: String,
    pub granted: bool,
    pub request_date: i64,
    pub grant_date: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginCrashEntity {
    pub id: Option<i64>,
    pub plugin_id: String,
    pub exception: String,
    pub message: String,
    pub stack_trace: String,
    pub timestamp: i64,
    pub severity: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginAlertEntity {
    pub id: Option<i64>,
    pub plugin_id: String,
    pub alert_type: String,
    pub title: String,
    pub message: String,
    pub severity: String,
    pub timestamp: i64,
    pub is_read: bool,
}

