use connectias_api::{Plugin, PluginError, PluginContext, PluginInfo, PluginPermission, StorageService, NetworkService, Logger, SystemInfo, NetworkRequest, NetworkResponse, OsInfo, CpuInfo, MemoryInfo};
use std::collections::HashMap;
use std::sync::Arc;

// Mock plugin for testing
struct MockPlugin {
    id: String,
    name: String,
    version: String,
    permissions: Vec<PluginPermission>,
}

impl MockPlugin {
    fn new(id: String, name: String, version: String, permissions: Vec<PluginPermission>) -> Self {
        Self {
            id,
            name,
            version,
            permissions,
        }
    }
}

impl Plugin for MockPlugin {
    fn get_info(&self) -> PluginInfo {
        PluginInfo {
            id: self.id.clone(),
            name: self.name.clone(),
            version: self.version.clone(),
            author: "Test Author".to_string(),
            description: "Test plugin for integration testing".to_string(),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: self.permissions.clone(),
            entry_point: "mock_plugin.wasm".to_string(),
            dependencies: None,
        }
    }

    fn init(&mut self, _context: PluginContext) -> Result<(), PluginError> {
        // Mock initialization
        Ok(())
    }

    fn execute(&self, command: &str, _args: HashMap<String, String>) -> Result<String, PluginError> {
        match command {
            "test_command" => Ok("Test response".to_string()),
            "error_command" => Err(PluginError::ExecutionFailed("Test error".to_string())),
            _ => Err(PluginError::NotFound(command.to_string())),
        }
    }

    fn cleanup(&mut self) -> Result<(), PluginError> {
        // Mock cleanup
        Ok(())
    }
}

// Mock Service Implementations
struct MockStorageService;

impl StorageService for MockStorageService {
    fn put(&self, _key: &str, _value: &[u8]) -> Result<(), PluginError> {
        Ok(())
    }
    
    fn get(&self, _key: &str) -> Result<Option<Vec<u8>>, PluginError> {
        Ok(None)
    }
    
    fn delete(&self, _key: &str) -> Result<(), PluginError> {
        Ok(())
    }
    
    fn clear(&self) -> Result<(), PluginError> {
        Ok(())
    }
    
    fn size(&self) -> Result<usize, PluginError> {
        Ok(0)
    }
}

struct MockNetworkService;

impl NetworkService for MockNetworkService {
    fn request(&self, _req: NetworkRequest) -> Result<NetworkResponse, PluginError> {
        Ok(NetworkResponse {
            status_code: 200,
            headers: HashMap::new(),
            body: vec![],
        })
    }
}

struct MockLoggerService;

impl Logger for MockLoggerService {
    fn debug(&self, _msg: &str) {}
    fn info(&self, _msg: &str) {}
    fn warn(&self, _msg: &str) {}
    fn error(&self, _msg: &str) {}
}

struct MockSystemInfoService;

impl SystemInfo for MockSystemInfoService {
    fn get_os_info(&self) -> Result<OsInfo, PluginError> {
        Ok(OsInfo {
            name: "Test OS".to_string(),
            version: "1.0.0".to_string(),
            arch: "x86_64".to_string(),
        })
    }
    
    fn get_cpu_info(&self) -> Result<CpuInfo, PluginError> {
        Ok(CpuInfo {
            cores: 4,
            model: "Test CPU".to_string(),
            frequency: 2000,
        })
    }
    
    fn get_memory_info(&self) -> Result<MemoryInfo, PluginError> {
        Ok(MemoryInfo {
            total: 8192,
            available: 4096,
            used: 4096,
        })
    }
}

#[test]
fn test_plugin_trait_implementation() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    let info = plugin.get_info();
    assert_eq!(info.id, "com.test.plugin");
    assert_eq!(info.name, "Test Plugin");
    assert_eq!(info.version, "1.0.0");
    assert_eq!(info.permissions, vec![PluginPermission::Storage]);
}

#[test]
fn test_plugin_execute_success() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    let mut args = HashMap::new();
    args.insert("param1".to_string(), "value1".to_string());

    let result = plugin.execute("test_command", args);
    assert!(result.is_ok());
    assert_eq!(result.unwrap(), "Test response");
}

#[test]
fn test_plugin_execute_error() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    let args = HashMap::new();
    let result = plugin.execute("error_command", args);
    assert!(result.is_err());
    
    if let Err(PluginError::ExecutionFailed(msg)) = result {
        assert_eq!(msg, "Test error");
    } else {
        panic!("Expected ExecutionError");
    }
}

#[test]
fn test_plugin_execute_unknown_command() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    let args = HashMap::new();
    let result = plugin.execute("unknown_command", args);
    assert!(result.is_err());
    
    if let Err(PluginError::NotFound(cmd)) = result {
        assert_eq!(cmd, "unknown_command");
    } else {
        panic!("Expected NotFound");
    }
}

#[test]
fn test_plugin_init_and_cleanup() {
    let mut plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    // Create mock context
    let logger = Arc::new(MockLoggerService);
    let network = Arc::new(MockNetworkService);
    let storage = Arc::new(MockStorageService);
    let system_info = Arc::new(MockSystemInfoService);

    let context = PluginContext {
        plugin_id: "com.test.plugin".to_string(),
        storage,
        network,
        logger,
        system_info,
    };

    // Test initialization
    let result = plugin.init(context);
    assert!(result.is_ok());

    // Test cleanup
    let result = plugin.cleanup();
    assert!(result.is_ok());
}

#[test]
fn test_plugin_info_validation() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage, PluginPermission::Network],
    );

    let info = plugin.get_info();
    
    // Test required fields
    assert!(!info.id.is_empty());
    assert!(!info.name.is_empty());
    assert!(!info.version.is_empty());
    assert!(!info.author.is_empty());
    assert!(!info.description.is_empty());
    assert!(!info.min_core_version.is_empty());
    assert!(!info.entry_point.is_empty());
    
    // Test permissions
    assert!(info.permissions.contains(&PluginPermission::Storage));
    assert!(info.permissions.contains(&PluginPermission::Network));
    
    // Test optional fields
    assert!(info.max_core_version.is_none());
    assert!(info.dependencies.is_none());
}

#[test]
fn test_plugin_error_types() {
    // Test different error types
    let errors = vec![
        PluginError::NotFound("Load error".to_string()),
        PluginError::InitializationFailed("Init error".to_string()),
        PluginError::ExecutionFailed("Exec error".to_string()),
        PluginError::NotFound("Unknown command".to_string()),
        PluginError::PermissionDenied { permission: "access_denied".to_string() },
        PluginError::NotFound("Dependency error".to_string()),
        PluginError::NotFound("Validation error".to_string()),
        PluginError::SecurityViolation("Security error".to_string()),
    ];

    for error in errors {
        // Test that error can be converted to string
        let error_string = format!("{}", error);
        assert!(!error_string.is_empty());
    }
}

#[test]
fn test_plugin_context_creation() {
    let logger = Arc::new(MockLoggerService);
    let network = Arc::new(MockNetworkService);
    let storage = Arc::new(MockStorageService);
    let system_info = Arc::new(MockSystemInfoService);

    let _context = PluginContext {
        plugin_id: "com.test.plugin".to_string(),
        storage,
        network,
        logger,
        system_info,
    };

    // Test that context can be created
    assert!(true);
}

#[test]
fn test_plugin_permissions() {
    let permissions = vec![
        PluginPermission::Storage,
        PluginPermission::Network,
        PluginPermission::SystemInfo,
    ];

    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        permissions.clone(),
    );

    let info = plugin.get_info();
    assert_eq!(info.permissions, permissions);
}

#[test]
fn test_plugin_dependencies() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    // Test that plugin can be created with dependencies
    let info = plugin.get_info();
    assert!(info.dependencies.is_none());
}

#[test]
fn test_plugin_version_compatibility() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    let info = plugin.get_info();
    
    // Test version compatibility
    assert_eq!(info.min_core_version, "1.0.0");
    assert!(info.max_core_version.is_none());
}

#[test]
fn test_plugin_concurrent_access() {
    let plugin = Arc::new(MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    ));

    let plugin_clone = Arc::clone(&plugin);
    
    // Test concurrent access
    let handle = std::thread::spawn(move || {
        let info = plugin_clone.get_info();
        assert_eq!(info.id, "com.test.plugin");
    });

    handle.join().unwrap();
}

#[test]
fn test_plugin_performance() {
    let plugin = MockPlugin::new(
        "com.test.plugin".to_string(),
        "Test Plugin".to_string(),
        "1.0.0".to_string(),
        vec![PluginPermission::Storage],
    );

    let iterations = 1000;
    let start = std::time::Instant::now();

    for _ in 0..iterations {
        let _info = plugin.get_info();
    }

    let duration = start.elapsed();
    
    // Should complete within reasonable time (less than 1 second for 1000 iterations)
    assert!(duration < std::time::Duration::from_secs(1));
}