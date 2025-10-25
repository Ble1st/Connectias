//! Plugin Registry Integration Tests für Connectias
//! 
//! Testet die erweiterte Plugin-Registry mit:
//! - Plugin Discovery
//! - Dependency Management
//! - Registry Statistics
//! - Plugin Status Management

use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};
use connectias_core::plugin_manager::{
    PluginManager, PluginRegistryEntry, PluginStatus, ResourceUsage, PerformanceMetrics,
    PluginDiscoveryResult, DependencyResolutionResult, PluginRegistryStats
};

#[tokio::test]
async fn test_plugin_discovery() {
    println!("🧪 Teste Plugin Discovery...");
    
    // Erstelle temporäres Verzeichnis für Tests
    let temp_dir = std::env::temp_dir().join("connectias_test_plugins");
    std::fs::create_dir_all(&temp_dir).unwrap();
    
    // Erstelle PluginManager
    let plugin_manager = PluginManager::new(temp_dir.clone()).unwrap();
    
    // Test 1: Discovery in leerem Verzeichnis
    let scan_paths = vec![temp_dir.clone()];
    let discovery_result = plugin_manager.discover_plugins(scan_paths).await.unwrap();
    
    assert_eq!(discovery_result.discovered_plugins.len(), 0);
    assert!(discovery_result.scan_duration > 0);
    assert_eq!(discovery_result.scan_paths.len(), 1);
    
    // Test 2: Discovery mit nicht-existierendem Verzeichnis
    let non_existent_path = temp_dir.join("non_existent");
    let scan_paths = vec![non_existent_path];
    let discovery_result = plugin_manager.discover_plugins(scan_paths).await.unwrap();
    
    assert_eq!(discovery_result.discovered_plugins.len(), 0);
    assert!(discovery_result.errors.is_empty());
    
    println!("✅ Plugin Discovery erfolgreich");
}

#[tokio::test]
async fn test_plugin_registry_management() {
    println!("🧪 Teste Plugin Registry Management...");
    
    let temp_dir = std::env::temp_dir().join("connectias_test_registry");
    std::fs::create_dir_all(&temp_dir).unwrap();
    
    let plugin_manager = PluginManager::new(temp_dir).unwrap();
    
    // Test 1: Registry-Statistiken initial
    let initial_stats = plugin_manager.get_registry_stats().await;
    assert_eq!(initial_stats.total_plugins, 0);
    assert_eq!(initial_stats.loaded_plugins, 0);
    assert_eq!(initial_stats.running_plugins, 0);
    assert_eq!(initial_stats.disabled_plugins, 0);
    assert_eq!(initial_stats.error_plugins, 0);
    
    // Test 2: Plugin registrieren
    let plugin_entry = PluginRegistryEntry {
        plugin_id: "test-plugin-1".to_string(),
        plugin_info: connectias_api::PluginInfo {
            id: "test-plugin-1".to_string(),
            name: "Test Plugin 1".to_string(),
            version: "1.0.0".to_string(),
            author: "Test Author".to_string(),
            description: "Test Plugin for Registry".to_string(),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![connectias_api::PluginPermission::Storage],
            entry_point: "plugin.wasm".to_string(),
            dependencies: None,
        },
        file_path: PathBuf::from("/tmp/test-plugin-1.wasm"),
        installed_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        last_accessed: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        version: "1.0.0".to_string(),
        status: PluginStatus::Installed,
        dependencies: vec![],
        dependents: vec![],
                permissions: vec!["storage:read".to_string()],
        resource_usage: ResourceUsage {
            memory_usage: 1024,
            cpu_usage: 0.1,
            storage_usage: 2048,
            network_usage: 0,
            last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        },
        performance_metrics: PerformanceMetrics {
            execution_count: 0,
            average_execution_time: 0.0,
            error_count: 0,
            success_rate: 1.0,
            last_execution: None,
        },
    };
    
    plugin_manager.register_plugin(plugin_entry).await.unwrap();
    
    // Test 3: Registry-Statistiken nach Registrierung
    let stats_after_register = plugin_manager.get_registry_stats().await;
    assert_eq!(stats_after_register.total_plugins, 1);
    assert_eq!(stats_after_register.loaded_plugins, 0);
    assert_eq!(stats_after_register.running_plugins, 0);
    assert_eq!(stats_after_register.disabled_plugins, 0);
    assert_eq!(stats_after_register.error_plugins, 0);
    assert_eq!(stats_after_register.total_memory_usage, 1024);
    assert_eq!(stats_after_register.total_storage_usage, 2048);
    
    // Test 4: Plugin-Status abrufen
    let plugin_status = plugin_manager.get_plugin_status("test-plugin-1").await;
    assert_eq!(plugin_status, Some(PluginStatus::Installed));
    
    // Test 5: Plugin-Status aktualisieren
    plugin_manager.update_plugin_status("test-plugin-1", PluginStatus::Loaded).await.unwrap();
    let updated_status = plugin_manager.get_plugin_status("test-plugin-1").await;
    assert_eq!(updated_status, Some(PluginStatus::Loaded));
    
    // Test 6: Registry-Statistiken nach Status-Update
    let stats_after_status_update = plugin_manager.get_registry_stats().await;
    assert_eq!(stats_after_status_update.loaded_plugins, 1);
    
    // Test 7: Plugin deregistrieren
    plugin_manager.unregister_plugin("test-plugin-1").await.unwrap();
    
    // Test 8: Registry-Statistiken nach Deregistrierung
    let stats_after_unregister = plugin_manager.get_registry_stats().await;
    assert_eq!(stats_after_unregister.total_plugins, 0);
    assert_eq!(stats_after_unregister.loaded_plugins, 0);
    
    println!("✅ Plugin Registry Management erfolgreich");
}

#[tokio::test]
async fn test_dependency_resolution() {
    println!("🧪 Teste Dependency Resolution...");
    
    let temp_dir = std::env::temp_dir().join("connectias_test_dependencies");
    std::fs::create_dir_all(&temp_dir).unwrap();
    
    let plugin_manager = PluginManager::new(temp_dir).unwrap();
    
    // Test 1: Plugin ohne Dependencies registrieren
    let base_plugin = PluginRegistryEntry {
        plugin_id: "base-plugin".to_string(),
        plugin_info: connectias_api::PluginInfo {
            id: "base-plugin".to_string(),
            name: "Base Plugin".to_string(),
            version: "1.0.0".to_string(),
            author: "Test Author".to_string(),
            description: "Base Plugin without dependencies".to_string(),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![connectias_api::PluginPermission::Storage],
            entry_point: "plugin.wasm".to_string(),
            dependencies: None,
        },
        file_path: PathBuf::from("/tmp/base-plugin.wasm"),
        installed_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        last_accessed: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        version: "1.0.0".to_string(),
        status: PluginStatus::Installed,
        dependencies: vec![],
        dependents: vec![],
                permissions: vec!["storage:read".to_string()],
        resource_usage: ResourceUsage {
            memory_usage: 512,
            cpu_usage: 0.05,
            storage_usage: 1024,
            network_usage: 0,
            last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        },
        performance_metrics: PerformanceMetrics {
            execution_count: 0,
            average_execution_time: 0.0,
            error_count: 0,
            success_rate: 1.0,
            last_execution: None,
        },
    };
    
    plugin_manager.register_plugin(base_plugin).await.unwrap();
    
    // Test 2: Plugin mit Dependencies registrieren
    let dependent_plugin = PluginRegistryEntry {
        plugin_id: "dependent-plugin".to_string(),
        plugin_info: connectias_api::PluginInfo {
            id: "dependent-plugin".to_string(),
            name: "Dependent Plugin".to_string(),
            version: "1.0.0".to_string(),
            author: "Test Author".to_string(),
            description: "Plugin with dependencies".to_string(),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![connectias_api::PluginPermission::Storage, connectias_api::PluginPermission::Network],
            entry_point: "plugin.wasm".to_string(),
            dependencies: Some(vec!["base-plugin".to_string()]),
        },
        file_path: PathBuf::from("/tmp/dependent-plugin.wasm"),
        installed_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        last_accessed: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        version: "1.0.0".to_string(),
        status: PluginStatus::Installed,
        dependencies: vec!["base-plugin".to_string()],
        dependents: vec![],
        permissions: vec!["storage:read".to_string(), "network:https".to_string()],
        resource_usage: ResourceUsage {
            memory_usage: 1024,
            cpu_usage: 0.1,
            storage_usage: 2048,
            network_usage: 0,
            last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        },
        performance_metrics: PerformanceMetrics {
            execution_count: 0,
            average_execution_time: 0.0,
            error_count: 0,
            success_rate: 1.0,
            last_execution: None,
        },
    };
    
    plugin_manager.register_plugin(dependent_plugin).await.unwrap();
    
    // Test 3: Dependency Resolution für Plugin mit verfügbaren Dependencies
    let resolution_result = plugin_manager.resolve_dependencies("dependent-plugin").await.unwrap();
    assert_eq!(resolution_result.plugin_id, "dependent-plugin");
    assert_eq!(resolution_result.resolved_dependencies, vec!["base-plugin"]);
    assert!(resolution_result.missing_dependencies.is_empty());
    assert!(resolution_result.circular_dependencies.is_empty());
    assert!(resolution_result.is_resolvable);
    assert_eq!(resolution_result.load_order, vec!["base-plugin", "dependent-plugin"]);
    
    // Test 4: Dependency Resolution für Plugin mit fehlenden Dependencies
    let missing_dep_plugin = PluginRegistryEntry {
        plugin_id: "missing-dep-plugin".to_string(),
        plugin_info: connectias_api::PluginInfo {
            id: "missing-dep-plugin".to_string(),
            name: "Missing Dep Plugin".to_string(),
            version: "1.0.0".to_string(),
            author: "Test Author".to_string(),
            description: "Plugin with missing dependencies".to_string(),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![connectias_api::PluginPermission::Storage],
            entry_point: "plugin.wasm".to_string(),
            dependencies: Some(vec!["non-existent-plugin".to_string()]),
        },
        file_path: PathBuf::from("/tmp/missing-dep-plugin.wasm"),
        installed_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        last_accessed: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        version: "1.0.0".to_string(),
        status: PluginStatus::Installed,
        dependencies: vec!["non-existent-plugin".to_string()],
        dependents: vec![],
                permissions: vec!["storage:read".to_string()],
        resource_usage: ResourceUsage {
            memory_usage: 512,
            cpu_usage: 0.05,
            storage_usage: 1024,
            network_usage: 0,
            last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        },
        performance_metrics: PerformanceMetrics {
            execution_count: 0,
            average_execution_time: 0.0,
            error_count: 0,
            success_rate: 1.0,
            last_execution: None,
        },
    };
    
    plugin_manager.register_plugin(missing_dep_plugin).await.unwrap();
    
    let missing_resolution = plugin_manager.resolve_dependencies("missing-dep-plugin").await.unwrap();
    assert_eq!(missing_resolution.plugin_id, "missing-dep-plugin");
    assert!(missing_resolution.resolved_dependencies.is_empty());
    assert_eq!(missing_resolution.missing_dependencies, vec!["non-existent-plugin"]);
    assert!(!missing_resolution.is_resolvable);
    
    println!("✅ Dependency Resolution erfolgreich");
}

#[tokio::test]
async fn test_plugin_resource_tracking() {
    println!("🧪 Teste Plugin Resource Tracking...");
    
    let temp_dir = std::env::temp_dir().join("connectias_test_resources");
    std::fs::create_dir_all(&temp_dir).unwrap();
    
    let plugin_manager = PluginManager::new(temp_dir).unwrap();
    
    // Test 1: Plugin mit Resource Usage registrieren
    let plugin_entry = PluginRegistryEntry {
        plugin_id: "resource-plugin".to_string(),
        plugin_info: connectias_api::PluginInfo {
            id: "resource-plugin".to_string(),
            name: "Resource Plugin".to_string(),
            version: "1.0.0".to_string(),
            author: "Test Author".to_string(),
            description: "Plugin for resource tracking".to_string(),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![connectias_api::PluginPermission::Storage],
            entry_point: "plugin.wasm".to_string(),
            dependencies: None,
        },
        file_path: PathBuf::from("/tmp/resource-plugin.wasm"),
        installed_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        last_accessed: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        version: "1.0.0".to_string(),
        status: PluginStatus::Running,
        dependencies: vec![],
        dependents: vec![],
                permissions: vec!["storage:read".to_string()],
        resource_usage: ResourceUsage {
            memory_usage: 2048,
            cpu_usage: 0.2,
            storage_usage: 4096,
            network_usage: 1024,
            last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
        },
        performance_metrics: PerformanceMetrics {
            execution_count: 10,
            average_execution_time: 150.0,
            error_count: 1,
            success_rate: 0.9,
            last_execution: Some(SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64),
        },
    };
    
    plugin_manager.register_plugin(plugin_entry).await.unwrap();
    
    // Test 2: Resource Usage aktualisieren
    let updated_resource_usage = ResourceUsage {
        memory_usage: 3072,
        cpu_usage: 0.3,
        storage_usage: 6144,
        network_usage: 2048,
        last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
    };
    
    plugin_manager.update_plugin_resource_usage("resource-plugin", updated_resource_usage).await.unwrap();
    
    // Test 3: Performance Metrics aktualisieren
    let updated_performance = PerformanceMetrics {
        execution_count: 20,
        average_execution_time: 120.0,
        error_count: 2,
        success_rate: 0.9,
        last_execution: Some(SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64),
    };
    
    plugin_manager.update_plugin_performance("resource-plugin", updated_performance).await.unwrap();
    
    // Test 4: Registry-Statistiken prüfen
    let stats = plugin_manager.get_registry_stats().await;
    assert_eq!(stats.total_plugins, 1);
    assert_eq!(stats.running_plugins, 1);
    assert_eq!(stats.total_memory_usage, 3072);
    assert_eq!(stats.total_storage_usage, 6144);
    assert_eq!(stats.average_performance, 0.9);
    
    println!("✅ Plugin Resource Tracking erfolgreich");
}

#[tokio::test]
async fn test_plugin_status_management() {
    println!("🧪 Teste Plugin Status Management...");
    
    let temp_dir = std::env::temp_dir().join("connectias_test_status");
    std::fs::create_dir_all(&temp_dir).unwrap();
    
    let plugin_manager = PluginManager::new(temp_dir).unwrap();
    
    // Test 1: Plugin mit verschiedenen Status registrieren
    let plugins = vec![
        ("installed-plugin", PluginStatus::Installed),
        ("loaded-plugin", PluginStatus::Loaded),
        ("running-plugin", PluginStatus::Running),
        ("disabled-plugin", PluginStatus::Disabled),
        ("error-plugin", PluginStatus::Error { error: "Test error".to_string() }),
    ];
    
    for (plugin_id, status) in plugins {
        let plugin_entry = PluginRegistryEntry {
            plugin_id: plugin_id.to_string(),
            plugin_info: connectias_api::PluginInfo {
                id: plugin_id.to_string(),
                name: format!("{} Plugin", plugin_id),
                version: "1.0.0".to_string(),
                author: "Test Author".to_string(),
                description: format!("Plugin with status {:?}", status),
                min_core_version: "1.0.0".to_string(),
                max_core_version: None,
                permissions: vec![connectias_api::PluginPermission::Storage],
                entry_point: "plugin.wasm".to_string(),
                dependencies: None,
            },
            file_path: PathBuf::from(format!("/tmp/{}.wasm", plugin_id)),
            installed_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
            last_accessed: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
            version: "1.0.0".to_string(),
            status: status.clone(),
            dependencies: vec![],
            dependents: vec![],
            permissions: vec!["storage:read".to_string()],
            resource_usage: ResourceUsage {
                memory_usage: 1024,
                cpu_usage: 0.1,
                storage_usage: 2048,
                network_usage: 0,
                last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
            },
            performance_metrics: PerformanceMetrics {
                execution_count: 0,
                average_execution_time: 0.0,
                error_count: 0,
                success_rate: 1.0,
                last_execution: None,
            },
        };
        
        plugin_manager.register_plugin(plugin_entry).await.unwrap();
    }
    
    // Test 2: Registry-Statistiken prüfen
    let stats = plugin_manager.get_registry_stats().await;
    assert_eq!(stats.total_plugins, 5);
    assert_eq!(stats.loaded_plugins, 1);
    assert_eq!(stats.running_plugins, 1);
    assert_eq!(stats.disabled_plugins, 1);
    assert_eq!(stats.error_plugins, 1);
    
    // Test 3: Status-Änderungen
    plugin_manager.update_plugin_status("installed-plugin", PluginStatus::Loaded).await.unwrap();
    plugin_manager.update_plugin_status("running-plugin", PluginStatus::Stopped).await.unwrap();
    
    // Test 4: Aktualisierte Statistiken prüfen
    let updated_stats = plugin_manager.get_registry_stats().await;
    assert_eq!(updated_stats.loaded_plugins, 2);
    assert_eq!(updated_stats.running_plugins, 0);
    
    // Test 5: Nicht-existierendes Plugin
    let result = plugin_manager.update_plugin_status("non-existent", PluginStatus::Running).await;
    assert!(result.is_err());
    
    println!("✅ Plugin Status Management erfolgreich");
}

#[tokio::test]
async fn test_registry_error_handling() {
    println!("🧪 Teste Registry Error Handling...");
    
    let temp_dir = std::env::temp_dir().join("connectias_test_errors");
    std::fs::create_dir_all(&temp_dir).unwrap();
    
    let plugin_manager = PluginManager::new(temp_dir).unwrap();
    
    // Test 1: Dependency Resolution für nicht-existierendes Plugin
    let result = plugin_manager.resolve_dependencies("non-existent-plugin").await;
    assert!(result.is_err());
    assert!(result.unwrap_err().contains("nicht in Registry gefunden"));
    
    // Test 2: Status-Update für nicht-existierendes Plugin
    let result = plugin_manager.update_plugin_status("non-existent", PluginStatus::Running).await;
    assert!(result.is_err());
    assert!(result.unwrap_err().contains("nicht in Registry gefunden"));
    
    // Test 3: Resource Usage Update für nicht-existierendes Plugin
    let resource_usage = ResourceUsage {
        memory_usage: 1024,
        cpu_usage: 0.1,
        storage_usage: 2048,
        network_usage: 0,
        last_updated: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
    };
    
    let result = plugin_manager.update_plugin_resource_usage("non-existent", resource_usage).await;
    assert!(result.is_err());
    assert!(result.unwrap_err().contains("nicht in Registry gefunden"));
    
    // Test 4: Performance Update für nicht-existierendes Plugin
    let performance = PerformanceMetrics {
        execution_count: 10,
        average_execution_time: 100.0,
        error_count: 0,
        success_rate: 1.0,
        last_execution: Some(SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64),
    };
    
    let result = plugin_manager.update_plugin_performance("non-existent", performance).await;
    assert!(result.is_err());
    assert!(result.unwrap_err().contains("nicht in Registry gefunden"));
    
    // Test 5: Deregistrierung von nicht-existierendem Plugin
    let result = plugin_manager.unregister_plugin("non-existent").await;
    assert!(result.is_ok()); // Sollte erfolgreich sein (kein Fehler)
    
    println!("✅ Registry Error Handling erfolgreich");
}
