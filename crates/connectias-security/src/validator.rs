use std::path::Path;
use zip::ZipArchive;
use std::fs::File;
use connectias_api::PluginInfo;

pub struct PluginValidator;

impl PluginValidator {
    pub fn new() -> Self {
        Self
    }

    pub fn validate_plugin_zip(&self, plugin_path: &Path) -> Result<PluginInfo, super::SecurityError> {
        let file = File::open(plugin_path)
            .map_err(|e| super::SecurityError::InvalidPluginStructure(e.to_string()))?;
        
        let mut archive = ZipArchive::new(file)
            .map_err(|e| super::SecurityError::InvalidPluginStructure(e.to_string()))?;

        // Find and parse plugin.json
        let mut manifest_file = archive.by_name("plugin.json")
            .map_err(|_| super::SecurityError::InvalidPluginStructure("plugin.json not found".to_string()))?;

        let mut manifest_content = String::new();
        std::io::Read::read_to_string(&mut manifest_file, &mut manifest_content)
            .map_err(|e| super::SecurityError::InvalidPluginStructure(e.to_string()))?;

        let plugin_info: PluginInfo = serde_json::from_str(&manifest_content)
            .map_err(|e| super::SecurityError::InvalidPluginStructure(e.to_string()))?;

        // Validate plugin structure
        self.validate_plugin_info(&plugin_info)?;

        Ok(plugin_info)
    }

    /// Validiert ein Plugin-Objekt direkt (für Tests)
    pub fn validate_plugin(&self, plugin_info: &PluginInfo) -> Result<(), super::SecurityError> {
        self.validate_plugin_info(plugin_info)
    }

    fn validate_plugin_info(&self, info: &PluginInfo) -> Result<(), super::SecurityError> {
        if info.id.is_empty() {
            return Err(super::SecurityError::InvalidPluginStructure("Plugin ID is empty".to_string()));
        }
        
        if info.entry_point.is_empty() {
            return Err(super::SecurityError::InvalidPluginStructure("Entry point is empty".to_string()));
        }

        Ok(())
    }
}

