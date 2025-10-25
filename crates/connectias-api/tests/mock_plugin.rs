use connectias_api::{Plugin, PluginInfo, PluginContext, PluginError, PluginPermission, StorageService, NetworkService, Logger, SystemInfo, NetworkRequest, NetworkResponse, OsInfo, CpuInfo, MemoryInfo};
use std::collections::HashMap;

// Mock-Implementierungen für Tests
struct MockStorageService;
impl StorageService for MockStorageService {
    fn put(&self, _key: &str, _value: &[u8]) -> Result<(), PluginError> { Ok(()) }
    fn get(&self, _key: &str) -> Result<Option<Vec<u8>>, PluginError> { Ok(None) }
    fn delete(&self, _key: &str) -> Result<(), PluginError> { Ok(()) }
    fn clear(&self) -> Result<(), PluginError> { Ok(()) }
    fn size(&self) -> Result<usize, PluginError> { Ok(0) }
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

struct MockLogger;
impl Logger for MockLogger {
    fn debug(&self, _msg: &str) {}
    fn info(&self, _msg: &str) {}
    fn warn(&self, _msg: &str) {}
    fn error(&self, _msg: &str) {}
}

struct MockSystemInfo;
impl SystemInfo for MockSystemInfo {
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
            frequency: 3000,
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

pub struct MockPlugin {
    info: PluginInfo,
    initialized: bool,
}

impl MockPlugin {
    pub fn new() -> Self {
        Self {
            info: PluginInfo {
                id: "com.test.mock".to_string(),
                name: "Mock Plugin".to_string(),
                version: "1.0.0".to_string(),
                author: "Test".to_string(),
                description: "Mock for testing".to_string(),
                min_core_version: "1.0.0".to_string(),
                max_core_version: None,
                permissions: vec![PluginPermission::Storage],
                entry_point: "mock.wasm".to_string(),
                dependencies: None,
            },
            initialized: false,
        }
    }
}

impl Plugin for MockPlugin {
    fn get_info(&self) -> PluginInfo {
        self.info.clone()
    }

    fn init(&mut self, _context: PluginContext) -> Result<(), PluginError> {
        self.initialized = true;
        Ok(())
    }

    fn execute(&self, command: &str, args: HashMap<String, String>) -> Result<String, PluginError> {
        if !self.initialized {
            return Err(PluginError::InitializationFailed("Not initialized".to_string()));
        }

        match command {
            "echo" => Ok(args.get("message").cloned().unwrap_or_default()),
            "fail" => Err(PluginError::ExecutionFailed("Intentional failure".to_string())),
            _ => Err(PluginError::ExecutionFailed(format!("Unknown command: {}", command))),
        }
    }

    fn cleanup(&mut self) -> Result<(), PluginError> {
        self.initialized = false;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mock_plugin_creation() {
        let plugin = MockPlugin::new();
        let info = plugin.get_info();
        
        assert_eq!(info.id, "com.test.mock");
        assert_eq!(info.name, "Mock Plugin");
        assert_eq!(info.version, "1.0.0");
    }

    #[test]
    fn test_mock_plugin_lifecycle() {
        let mut plugin = MockPlugin::new();
        let context = PluginContext {
            plugin_id: "com.test.mock".to_string(),
            storage: std::sync::Arc::new(MockStorageService),
            network: std::sync::Arc::new(MockNetworkService),
            logger: std::sync::Arc::new(MockLogger),
            system_info: std::sync::Arc::new(MockSystemInfo),
        };

        // Test initialization
        assert!(plugin.init(context).is_ok());

        // Test execution
        let mut args = HashMap::new();
        args.insert("message".to_string(), "Hello World".to_string());
        
        let result = plugin.execute("echo", args);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), "Hello World");

        // Test cleanup
        assert!(plugin.cleanup().is_ok());
    }

    #[test]
    fn test_mock_plugin_execution_before_init() {
        let plugin = MockPlugin::new();
        let args = HashMap::new();
        
        let result = plugin.execute("echo", args);
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), PluginError::InitializationFailed(_)));
    }

    #[test]
    fn test_mock_plugin_unknown_command() {
        let mut plugin = MockPlugin::new();
        let context = PluginContext {
            plugin_id: "com.test.mock".to_string(),
            storage: std::sync::Arc::new(MockStorageService),
            network: std::sync::Arc::new(MockNetworkService),
            logger: std::sync::Arc::new(MockLogger),
            system_info: std::sync::Arc::new(MockSystemInfo),
        };

        plugin.init(context).unwrap();
        
        let args = HashMap::new();
        let result = plugin.execute("unknown", args);
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), PluginError::ExecutionFailed(_)));
    }

    #[test]
    fn test_mock_plugin_fail_command() {
        let mut plugin = MockPlugin::new();
        let context = PluginContext {
            plugin_id: "com.test.mock".to_string(),
            storage: std::sync::Arc::new(MockStorageService),
            network: std::sync::Arc::new(MockNetworkService),
            logger: std::sync::Arc::new(MockLogger),
            system_info: std::sync::Arc::new(MockSystemInfo),
        };

        plugin.init(context).unwrap();
        
        let args = HashMap::new();
        let result = plugin.execute("fail", args);
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), PluginError::ExecutionFailed(_)));
    }
}

//ich diene der aktualisierung wala
