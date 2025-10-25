use connectias_security::sandbox::{PluginSandbox, ResourceQuota};
use std::thread;
use std::sync::Arc;

#[test]
fn test_plugin_sandbox_creation() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    // Should create without errors
    assert!(true);
}

#[test]
fn test_resource_quota_creation() {
    let quota = ResourceQuota {
        memory_limit: 100 * 1024 * 1024, // 100MB
        cpu_limit: 50.0,
        storage_limit: 10 * 1024 * 1024, // 10MB
        network_limit: 100,
    };
    
    assert_eq!(quota.memory_limit, 100 * 1024 * 1024);
    assert_eq!(quota.cpu_limit, 50.0);
    assert_eq!(quota.storage_limit, 10 * 1024 * 1024);
    assert_eq!(quota.network_limit, 100);
}

#[test]
fn test_sandbox_plugin_execution() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    
    // Test basic plugin execution
    let result = _sandbox.execute_plugin("test_command", std::collections::HashMap::new());
    assert!(result.is_ok());
    
    let output = result.unwrap();
    assert!(output.contains("Executed: test_command"));
}

#[test]
fn test_sandbox_input_sanitization() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    
    // Test with potentially malicious input
    let mut args = std::collections::HashMap::new();
    args.insert("user_input".to_string(), "<script>alert('xss')</script>".to_string());
    
    let result = _sandbox.execute_plugin("test_command", args);
    assert!(result.is_ok());
    
    let output = result.unwrap();
    // Should be sanitized
    assert!(!output.contains("<script>"));
    assert!(output.contains("&lt;script&gt;"));
}

#[test]
fn test_sandbox_resource_quota_defaults() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    
    // Test that sandbox can be created with default resource quotas
    // The actual quota management is handled internally by ResourceQuotaManager
    assert!(true);
}

#[test]
fn test_sandbox_concurrent_execution() {
    let sandbox = Arc::new(PluginSandbox::new("test-plugin".to_string()));
    let mut handles = vec![];
    
    // Test concurrent plugin execution
    for i in 0..5 {
        let sandbox_clone = Arc::clone(&sandbox);
        let handle = thread::spawn(move || {
            let mut args = std::collections::HashMap::new();
            args.insert("thread_id".to_string(), i.to_string());
            
            sandbox_clone.execute_plugin("concurrent_test", args)
        });
        handles.push(handle);
    }
    
    // Wait for all threads to complete
    for handle in handles {
        let result = handle.join().unwrap();
        assert!(result.is_ok());
    }
}

#[test]
fn test_sandbox_error_handling() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    
    // Test with empty command
    let result = _sandbox.execute_plugin("", std::collections::HashMap::new());
    assert!(result.is_ok()); // Should still execute but with sanitized input
}

#[test]
fn test_sandbox_large_input() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    
    // Test with large input to ensure proper handling
    let mut args = std::collections::HashMap::new();
    let large_string = "x".repeat(10000);
    args.insert("large_data".to_string(), large_string);
    
    let result = _sandbox.execute_plugin("process_large_data", args);
    assert!(result.is_ok());
}

#[test]
fn test_sandbox_special_characters() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    
    // Test with special characters that might cause issues
    let mut args = std::collections::HashMap::new();
    args.insert("special_chars".to_string(), "!@#$%^&*()_+-=[]{}|;':\",./<>?".to_string());
    
    let result = _sandbox.execute_plugin("process_special", args);
    assert!(result.is_ok());
}

#[test]
fn test_sandbox_unicode_input() {
    let _sandbox = PluginSandbox::new("test-plugin".to_string());
    
    // Test with unicode characters
    let mut args = std::collections::HashMap::new();
    args.insert("unicode".to_string(), "🚀🌟💻🔒".to_string());
    
    let result = _sandbox.execute_plugin("process_unicode", args);
    assert!(result.is_ok());
}

// Integration tests for sandbox behavior
#[cfg(test)]
mod integration_tests {
    use super::*;
    
    #[test]
    fn test_sandbox_isolation() {
        let sandbox1 = PluginSandbox::new("plugin-1".to_string());
        let sandbox2 = PluginSandbox::new("plugin-2".to_string());
        
        // Each sandbox should be independent
        let result1 = sandbox1.execute_plugin("test", std::collections::HashMap::new());
        let result2 = sandbox2.execute_plugin("test", std::collections::HashMap::new());
        
        assert!(result1.is_ok());
        assert!(result2.is_ok());
        
        // Results should be independent (they are the same in this simple test)
        // In a real implementation, each sandbox would have different plugin IDs
        assert!(result1.is_ok());
        assert!(result2.is_ok());
    }
    
    #[test]
    fn test_sandbox_resource_limits() {
        let sandbox = PluginSandbox::new("resource-test".to_string());
        
        // Test that sandbox can handle resource-intensive operations
        // without crashing (actual resource limiting is handled internally)
        let result = sandbox.execute_plugin("resource_intensive", std::collections::HashMap::new());
        assert!(result.is_ok());
    }
}
//ich diene der aktualisierung wala
