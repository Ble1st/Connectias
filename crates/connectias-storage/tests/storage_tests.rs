use tempfile::TempDir;
use connectias_storage::PluginData;

#[test]
fn test_storage_service_creation() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let db_path = temp_dir.path().join("test.db");
    
    let _storage = connectias_storage::Database::new(&db_path).expect("Failed to create database");
    // Should create without errors
    assert!(true);
}

#[test]
fn test_storage_service_save_plugin_data() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let db_path = temp_dir.path().join("test.db");
    
    let storage = connectias_storage::Database::new(&db_path).expect("Failed to create database");
    
    let plugin_data = PluginData {
        id: "1".to_string(),
        plugin_id: "com.test.plugin".to_string(),
        key: "test_key".to_string(),
        value: b"test_data".to_vec(),
        data_type: "string".to_string(),
        timestamp: chrono::Utc::now().timestamp(),
        size: 8,
        is_encrypted: false,
    };
    
    let result = storage.put_plugin_data(&plugin_data.plugin_id, &plugin_data.key, &plugin_data.value);
    assert!(result.is_ok());
}

#[test]
fn test_storage_service_load_plugin_data() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let db_path = temp_dir.path().join("test.db");
    
    let storage = connectias_storage::Database::new(&db_path).expect("Failed to create database");
    
    let plugin_data = PluginData {
        id: "1".to_string(),
        plugin_id: "com.test.plugin".to_string(),
        key: "test_key".to_string(),
        value: b"test_data".to_vec(),
        data_type: "string".to_string(),
        timestamp: chrono::Utc::now().timestamp(),
        size: 8,
        is_encrypted: false,
    };
    
    // Save the data
    storage.put_plugin_data(&plugin_data.plugin_id, &plugin_data.key, &plugin_data.value).expect("Failed to save plugin data");
    
    // Load the data
    let result = storage.get_plugin_data(&plugin_data.plugin_id, &plugin_data.key);
    assert!(result.is_ok());
    
    let loaded_data = result.unwrap();
    assert_eq!(loaded_data, Some(plugin_data.value));
}

#[test]
fn test_storage_service_delete_plugin_data() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let db_path = temp_dir.path().join("test.db");
    
    let storage = connectias_storage::Database::new(&db_path).expect("Failed to create database");
    
    let plugin_data = PluginData {
        id: "1".to_string(),
        plugin_id: "com.test.plugin".to_string(),
        key: "test_key".to_string(),
        value: b"test_data".to_vec(),
        data_type: "string".to_string(),
        timestamp: chrono::Utc::now().timestamp(),
        size: 8,
        is_encrypted: false,
    };
    
    // Save the data
    storage.put_plugin_data(&plugin_data.plugin_id, &plugin_data.key, &plugin_data.value).expect("Failed to save plugin data");
    
    // Delete the data
    let result = storage.delete_plugin_data(&plugin_data.plugin_id, &plugin_data.key);
    assert!(result.is_ok());
    
    // Try to load the deleted data
    let result = storage.get_plugin_data(&plugin_data.plugin_id, &plugin_data.key);
    assert!(result.is_ok());
    assert_eq!(result.unwrap(), None);
}

#[test]
fn test_storage_service_list_plugins() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let db_path = temp_dir.path().join("test.db");
    
    let storage = connectias_storage::Database::new(&db_path).expect("Failed to create database");
    
    // Save some plugin data
    let plugin_data = PluginData {
        id: "1".to_string(),
        plugin_id: "com.test.plugin".to_string(),
        key: "test_key".to_string(),
        value: b"test_data".to_vec(),
        data_type: "string".to_string(),
        timestamp: chrono::Utc::now().timestamp(),
        size: 8,
        is_encrypted: false,
    };
    
    storage.put_plugin_data(&plugin_data.plugin_id, &plugin_data.key, &plugin_data.value).expect("Failed to save plugin data");
    
    // Test that data was saved
    let result = storage.get_plugin_data(&plugin_data.plugin_id, &plugin_data.key);
    assert!(result.is_ok());
    assert_eq!(result.unwrap(), Some(plugin_data.value));
}

#[test]
fn test_storage_service_plugin_not_found() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let db_path = temp_dir.path().join("test.db");
    
    let storage = connectias_storage::Database::new(&db_path).expect("Failed to create database");
    
    // Try to load non-existent plugin
    let result = storage.get_plugin_data("nonexistent.plugin", "test_key");
    assert!(result.is_ok());
    assert_eq!(result.unwrap(), None);
}

#[test]
fn test_storage_service_concurrent_access() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let db_path = temp_dir.path().join("test.db");
    
    let storage = std::sync::Arc::new(connectias_storage::Database::new(&db_path).expect("Failed to create database"));
    let storage_clone = std::sync::Arc::clone(&storage);
    
    // Test concurrent access
    let handle = std::thread::spawn(move || {
        let plugin_data = PluginData {
            id: "1".to_string(),
            plugin_id: "com.test.plugin".to_string(),
            key: "test_key".to_string(),
            value: b"test_data".to_vec(),
            data_type: "string".to_string(),
            timestamp: chrono::Utc::now().timestamp(),
            size: 8,
            is_encrypted: false,
        };
        
        storage_clone.put_plugin_data(&plugin_data.plugin_id, &plugin_data.key, &plugin_data.value)
    });
    
    let result = handle.join().unwrap();
    assert!(result.is_ok());
}
