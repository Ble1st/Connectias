use connectias_security::validator::PluginValidator;
use std::path::Path;
use tempfile::TempDir;
use std::fs::File;
use std::io::Write;
use zip::ZipWriter;
use zip::write::FileOptions;
use serde_json::json;
use connectias_api::PluginInfo;

#[test]
fn test_plugin_validator_creation() {
    let _validator = PluginValidator::new();
    // Should create without errors
    assert!(true);
}

#[test]
fn test_validate_plugin_zip_with_valid_manifest() {
    let validator = PluginValidator::new();
    
    // Create a test plugin ZIP with valid manifest
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("valid_plugin.zip");
    
    create_valid_plugin_zip(&plugin_path).expect("Failed to create valid plugin");
    
    let result = validator.validate_plugin_zip(&plugin_path);
    assert!(result.is_ok());
    
    let plugin_info = result.unwrap();
    assert_eq!(plugin_info.id, "test-plugin");
    assert_eq!(plugin_info.entry_point, "main.py");
}

#[test]
fn test_validate_plugin_zip_with_invalid_manifest() {
    let validator = PluginValidator::new();
    
    // Create a test plugin ZIP with invalid manifest
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("invalid_plugin.zip");
    
    create_invalid_plugin_zip(&plugin_path).expect("Failed to create invalid plugin");
    
    let result = validator.validate_plugin_zip(&plugin_path);
    assert!(result.is_err());
}

#[test]
fn test_validate_plugin_zip_missing_manifest() {
    let validator = PluginValidator::new();
    
    // Create a test plugin ZIP without manifest
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("no_manifest_plugin.zip");
    
    create_plugin_zip_without_manifest(&plugin_path).expect("Failed to create plugin without manifest");
    
    let result = validator.validate_plugin_zip(&plugin_path);
    assert!(result.is_err());
}

#[test]
fn test_validate_plugin_zip_invalid_zip() {
    let validator = PluginValidator::new();
    
    // Create a file that's not a valid ZIP
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("invalid.zip");
    std::fs::write(&plugin_path, "This is not a ZIP file").expect("Failed to write test file");
    
    let result = validator.validate_plugin_zip(&plugin_path);
    assert!(result.is_err());
}

#[test]
fn test_validate_plugin_zip_nonexistent_file() {
    let validator = PluginValidator::new();
    let result = validator.validate_plugin_zip(Path::new("/nonexistent/plugin.zip"));
    assert!(result.is_err());
}

#[test]
fn test_validate_plugin_info_directly() {
    let validator = PluginValidator::new();
    
    // Test direct validation of PluginInfo
    let plugin_info = PluginInfo {
        id: "test-plugin".to_string(),
        name: "Test Plugin".to_string(),
        version: "1.0.0".to_string(),
        author: "Test Author".to_string(),
        description: "A test plugin".to_string(),
        min_core_version: "1.0.0".to_string(),
        max_core_version: None,
        permissions: vec![],
        entry_point: "main.py".to_string(),
        dependencies: None,
    };
    
    let result = validator.validate_plugin(&plugin_info);
    assert!(result.is_ok());
}

#[test]
fn test_validate_plugin_info_empty_id() {
    let validator = PluginValidator::new();
    
    let plugin_info = PluginInfo {
        id: "".to_string(), // Empty ID should fail
        name: "Test Plugin".to_string(),
        version: "1.0.0".to_string(),
        author: "Test Author".to_string(),
        description: "A test plugin".to_string(),
        min_core_version: "1.0.0".to_string(),
        max_core_version: None,
        permissions: vec![],
        entry_point: "main.py".to_string(),
        dependencies: None,
    };
    
    let result = validator.validate_plugin(&plugin_info);
    assert!(result.is_err());
}

#[test]
fn test_validate_plugin_info_empty_entry_point() {
    let validator = PluginValidator::new();
    
    let plugin_info = PluginInfo {
        id: "test-plugin".to_string(),
        name: "Test Plugin".to_string(),
        version: "1.0.0".to_string(),
        author: "Test Author".to_string(),
        description: "A test plugin".to_string(),
        min_core_version: "1.0.0".to_string(),
        max_core_version: None,
        permissions: vec![],
        entry_point: "".to_string(), // Empty entry point should fail
        dependencies: None,
    };
    
    let result = validator.validate_plugin(&plugin_info);
    assert!(result.is_err());
}

// Helper functions for creating test plugin ZIPs
fn create_valid_plugin_zip(plugin_path: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let file = File::create(plugin_path)?;
    let mut zip = ZipWriter::new(file);
    
    // Add plugin.json
    zip.start_file("plugin.json", FileOptions::default())?;
    let manifest = json!({
        "id": "test-plugin",
        "name": "Test Plugin",
        "version": "1.0.0",
        "author": "Test Author",
        "description": "A test plugin",
        "min_core_version": "1.0.0",
        "max_core_version": null,
        "permissions": [],
        "entry_point": "main.py",
        "dependencies": null
    });
    zip.write_all(manifest.to_string().as_bytes())?;
    
    // Add main.py
    zip.start_file("main.py", FileOptions::default())?;
    zip.write_all(b"def main():\n    return 'Hello from plugin!'")?;
    
    zip.finish()?;
    Ok(())
}

fn create_invalid_plugin_zip(plugin_path: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let file = File::create(plugin_path)?;
    let mut zip = ZipWriter::new(file);
    
    // Add invalid plugin.json (missing required fields)
    zip.start_file("plugin.json", FileOptions::default())?;
    let invalid_manifest = json!({
        "name": "Test Plugin"
        // Missing id, entry_point, etc.
    });
    zip.write_all(invalid_manifest.to_string().as_bytes())?;
    
    zip.finish()?;
    Ok(())
}

fn create_plugin_zip_without_manifest(plugin_path: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let file = File::create(plugin_path)?;
    let mut zip = ZipWriter::new(file);
    
    // Add only main.py, no plugin.json
    zip.start_file("main.py", FileOptions::default())?;
    zip.write_all(b"def main():\n    return 'Hello from plugin!'")?;
    
    zip.finish()?;
    Ok(())
}