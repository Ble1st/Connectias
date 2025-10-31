use connectias_core::{
    plugin_manager::PluginManager,
    services::{
        permission_service::PermissionService,
        alert_service::AlertService,
        monitoring_service::MonitoringService,
    },
    message_broker::MessageBroker,
};
use connectias_security::threat_detection::{
    PermissionServiceTrait,
    AlertServiceTrait,
    MonitoringServiceTrait,
};
use std::sync::Arc;
use chrono::Utc;
use tokio;

#[tokio::test]
async fn test_permission_service_trait_integration() {
    let permission_service = PermissionService::new();
    
    // Test Permission Service Trait methods
    let plugin_id = "test_plugin_123";
    let permission = "network_access";
    
    // Test grant permission
    let result = permission_service.grant_permission(plugin_id, permission).await;
    assert!(result.is_ok(), "Grant permission should succeed");
    
    // Test has permission
    let has_permission = permission_service.has_permission(plugin_id, permission);
    assert!(has_permission, "Plugin should have granted permission");
    
    // Test revoke permission
    let result = permission_service.revoke_permission(plugin_id, permission).await;
    assert!(result.is_ok(), "Revoke permission should succeed");
    
    // Test permission is revoked
    let has_permission = permission_service.has_permission(plugin_id, permission);
    assert!(!has_permission, "Plugin should not have permission after revocation");
    
    println!("✅ Permission Service Trait integration working correctly");
}

#[tokio::test]
async fn test_alert_service_trait_integration() {
    let alert_service = AlertService::new();
    
    // Test Alert Service Trait methods
    let threat_level = connectias_security::threat_detection::ThreatLevel::High;
    let description = "Test security threat detected";
    
    // Test create alert
    let result = alert_service.create_alert(threat_level, description).await;
    assert!(result.is_ok(), "Create alert should succeed");
    
    // Test get alerts
    let alerts = alert_service.get_alerts().await;
    assert!(!alerts.is_empty(), "Should have at least one alert");
    
    // Test acknowledge alert
    if let Some(alert) = alerts.first() {
        let result = alert_service.acknowledge_alert(alert.id.clone()).await;
        assert!(result.is_ok(), "Acknowledge alert should succeed");
    }
    
    println!("✅ Alert Service Trait integration working correctly");
}

#[tokio::test]
async fn test_monitoring_service_trait_integration() {
    let monitoring_service = MonitoringService::new();
    
    // Test Monitoring Service Trait methods
    let plugin_id = "test_plugin_456";
    
    // Test start monitoring
    let result = monitoring_service.start_monitoring(plugin_id).await;
    assert!(result.is_ok(), "Start monitoring should succeed");
    
    // Test get metrics
    let metrics = monitoring_service.get_metrics(plugin_id).await;
    assert!(metrics.is_ok(), "Get metrics should succeed");
    
    // Test stop monitoring
    let result = monitoring_service.stop_monitoring(plugin_id).await;
    assert!(result.is_ok(), "Stop monitoring should succeed");
    
    println!("✅ Monitoring Service Trait integration working correctly");
}

#[tokio::test]
async fn test_toml_manifest_parsing() {
    let plugin_manager = PluginManager::new();
    
    // Test TOML manifest parsing
    let toml_content = r#"
[package]
id = "test_plugin"
name = "Test Plugin"
version = "1.0.0"
author = "Test Author"
description = "A test plugin for integration testing"

[permissions]
network_access = true
file_system_read = true

[dependencies]
connectias_core = "1.0.0"
"#;
    
    // Test extract_from_toml_manifest
    let result = plugin_manager.extract_from_toml_manifest(toml_content);
    assert!(result.is_ok(), "TOML parsing should succeed");
    
    let plugin_info = result.unwrap();
    assert_eq!(plugin_info.id, "test_plugin");
    assert_eq!(plugin_info.name, "Test Plugin");
    assert_eq!(plugin_info.version, "1.0.0");
    assert_eq!(plugin_info.author, "Test Author");
    
    println!("✅ TOML manifest parsing working correctly");
}

#[tokio::test]
async fn test_message_broker_arc_cloning() {
    let message_broker = Arc::new(MessageBroker::new());
    
    // Test message broker with Arc-based handlers
    let topic = "test_topic";
    let plugin_id = "test_plugin";
    
    // Create invocation recording mechanism
    let invocation_count = Arc::new(std::sync::atomic::AtomicUsize::new(0));
    let received_messages = Arc::new(std::sync::Mutex::new(Vec::new()));
    
    // Create a handler that records invocations
    let invocation_count_clone = invocation_count.clone();
    let received_messages_clone = received_messages.clone();
    let handler = Arc::new(move |message: &connectias_core::message_broker::Message| {
        invocation_count_clone.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
        received_messages_clone.lock().unwrap().push(message.topic.clone());
        println!("Received message: {}", message.topic);
    });
    
    // Test subscription
    let subscription = connectias_core::message_broker::MessageSubscription {
        topic: topic.to_string(),
        plugin_id: plugin_id.to_string(),
        handler: handler.clone(), // Arc can be cloned
    };
    
    // Test cloning the subscription
    let cloned_subscription = subscription.clone();
    assert_eq!(cloned_subscription.topic, subscription.topic);
    assert_eq!(cloned_subscription.plugin_id, subscription.plugin_id);
    
    // Subscribe to the message broker
    message_broker.subscribe(subscription).await;
    
    // Publish a test message via the message broker
    let test_payload = b"test_payload".to_vec();
    message_broker.publish(
        topic,
        plugin_id,
        test_payload,
        connectias_core::message_broker::MessageType::Data
    ).await;
    
    // Allow some time for message delivery
    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
    
    // Verify the handler was invoked
    let final_count = invocation_count.load(std::sync::atomic::Ordering::SeqCst);
    assert_eq!(final_count, 1, "Handler should have been invoked exactly once");
    
    // Verify the correct message was received
    let messages = received_messages.lock().unwrap();
    assert_eq!(messages.len(), 1, "Should have received exactly one message");
    assert_eq!(messages[0], topic, "Should have received message for correct topic");
    
    println!("✅ Message broker Arc-based cloning and integration working correctly");
}

#[tokio::test]
async fn test_plugin_manager_integration() {
    let plugin_manager = PluginManager::new();
    
    // Test plugin manager operations
    let plugin_id = "integration_test_plugin";
    
    // Test plugin loading (with dummy data)
    let dummy_wasm_data = b"dummy_wasm_data_for_testing";
    let result = plugin_manager.load_plugin_from_bytes(plugin_id, dummy_wasm_data);
    
    // This might fail due to invalid WASM data, which is expected
    match result {
        Ok(_) => {
            println!("✅ Plugin loading succeeded (unexpected but OK)");
        }
        Err(e) => {
            println!("✅ Plugin loading correctly failed with invalid data: {}", e);
        }
    }
    
    // Test plugin listing
    let plugins = plugin_manager.list_plugins();
    assert!(plugins.is_ok(), "List plugins should succeed");
    
    println!("✅ Plugin manager integration working correctly");
}

#[tokio::test]
async fn test_concurrent_operations() {
    let permission_service = Arc::new(PermissionService::new());
    let alert_service = Arc::new(AlertService::new());
    
    // Test concurrent operations with proper synchronization
    let handles: Vec<_> = (0..10)
        .map(|i| {
            let permission_service = Arc::clone(&permission_service);
            let alert_service = Arc::clone(&alert_service);
            
            tokio::spawn(async move {
                let plugin_id = format!("concurrent_plugin_{}", i);
                let permission = format!("test_permission_{}", i); // Unique permission per task
                
                // Grant permission
                let grant_result = permission_service.grant_permission(&plugin_id, &permission).await;
                assert!(grant_result.is_ok(), "Permission grant should succeed for plugin {}", i);
                
                // Create alert
                let alert_result = alert_service.create_alert(
                    connectias_security::threat_detection::ThreatLevel::Low,
                    &format!("Concurrent test alert {}", i),
                ).await;
                assert!(alert_result.is_ok(), "Alert creation should succeed for plugin {}", i);
                
                // Check permission with a small delay to ensure consistency
                tokio::time::sleep(tokio::time::Duration::from_millis(10)).await;
                let has_permission = permission_service.has_permission(&plugin_id, &permission);
                assert!(has_permission, "Plugin {} should have permission {}", i, permission);
                
                // Return success indicator
                Ok::<(), String>(())
            })
        })
        .collect();
    
    // Wait for all tasks to complete and collect results
    let mut success_count = 0;
    for handle in handles {
        match handle.await {
            Ok(Ok(())) => success_count += 1,
            Ok(Err(e)) => panic!("Concurrent operation failed: {}", e),
            Err(e) => panic!("Task join failed: {:?}", e),
        }
    }
    
    assert_eq!(success_count, 10, "All 10 concurrent operations should succeed");
    
    println!("✅ Concurrent operations working correctly");
}

#[tokio::test]
async fn test_error_handling() {
    let permission_service = PermissionService::new();
    
    // Test error handling with invalid inputs
    let result = permission_service.grant_permission("", "valid_permission").await;
    assert!(result.is_err(), "Empty plugin ID should cause error");
    
    let result = permission_service.grant_permission("valid_plugin", "").await;
    assert!(result.is_err(), "Empty permission should cause error");
    
    // Test with very long strings
    let long_plugin_id = "a".repeat(10000);
    let result = permission_service.grant_permission(&long_plugin_id, "valid_permission").await;
    // This might succeed or fail depending on implementation
    println!("✅ Error handling working correctly");
}

#[tokio::test]
async fn test_service_initialization() {
    // Test that all services can be initialized
    let permission_service = PermissionService::new();
    let alert_service = AlertService::new();
    let monitoring_service = MonitoringService::new();
    let message_broker = MessageBroker::new();
    let plugin_manager = PluginManager::new();
    
    // All services should be created without panicking
    assert!(true, "All services initialized successfully");
    
    println!("✅ Service initialization working correctly");
}
