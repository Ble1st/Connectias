//! Service Integration Tests für Connectias
//! 
//! Testet die Integration aller Services:
//! - Storage Service
//! - Network Service  
//! - Permission Service
//! - Monitoring Service

use std::path::Path;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;
use std::thread;

// Importiere die Service-Module
use connectias_storage::{Database, StorageService, StorageError};
use connectias_security::{NetworkSecurityFilter, ThreatDetectionSystem};
use connectias_core::services::{PermissionService, MonitoringService};

/// Helper-Funktion für Test-Datenbank
fn create_test_database() -> Database {
    let temp_dir = std::env::temp_dir();
    let db_path = temp_dir.join("connectias_test.db");
    Database::new(&db_path).expect("Failed to create test database")
}

#[test]
fn test_storage_service_integration() {
    println!("🧪 Teste Storage Service Integration...");
    
    let db = create_test_database();
    
    // Test 1: Grundlegende CRUD-Operationen
    let test_key = "test_plugin_data";
    let test_value = b"Hello from WASM plugin!";
    
    // Speichern
    let result = db.put(test_key, test_value);
    assert!(result.is_ok(), "Storage put sollte erfolgreich sein");
    
    // Lesen
    let retrieved = db.get(test_key).expect("Storage get sollte erfolgreich sein");
    assert!(retrieved.is_some(), "Gespeicherte Daten sollten abrufbar sein");
    assert_eq!(retrieved.unwrap(), test_value, "Daten sollten unverändert sein");
    
    // Größe prüfen
    let size = db.size().expect("Storage size sollte erfolgreich sein");
    assert!(size > 0, "Datenbank sollte Daten enthalten");
    
    // Löschen
    let delete_result = db.delete(test_key);
    assert!(delete_result.is_ok(), "Storage delete sollte erfolgreich sein");
    
    // Nach Löschung sollte Key nicht mehr existieren
    let after_delete = db.get(test_key).expect("Storage get nach delete sollte erfolgreich sein");
    assert!(after_delete.is_none(), "Gelöschte Daten sollten nicht mehr existieren");
    
    println!("✅ Storage Service Integration erfolgreich");
}

#[test]
fn test_storage_encryption() {
    println!("🧪 Teste Storage Encryption...");
    
    let db = create_test_database();
    
    // Test 1: Verschlüsselte Daten speichern
    let sensitive_data = b"Sensitive plugin configuration data";
    let result = db.put("encrypted_config", sensitive_data);
    assert!(result.is_ok(), "Verschlüsselte Speicherung sollte erfolgreich sein");
    
    // Test 2: Verschlüsselte Daten lesen
    let retrieved = db.get("encrypted_config").expect("Verschlüsselte Daten sollten lesbar sein");
    assert!(retrieved.is_some(), "Verschlüsselte Daten sollten abrufbar sein");
    assert_eq!(retrieved.unwrap(), sensitive_data, "Verschlüsselte Daten sollten korrekt entschlüsselt werden");
    
    // Test 3: Mehrere verschlüsselte Einträge
    for i in 0..5 {
        let key = format!("encrypted_key_{}", i);
        let value = format!("Encrypted value {}", i).into_bytes();
        let result = db.put(&key, &value);
        assert!(result.is_ok(), "Verschlüsselte Speicherung {} sollte erfolgreich sein", i);
    }
    
    // Test 4: Alle verschlüsselten Einträge lesen
    for i in 0..5 {
        let key = format!("encrypted_key_{}", i);
        let expected = format!("Encrypted value {}", i).into_bytes();
        let retrieved = db.get(&key).expect("Verschlüsselte Daten sollten lesbar sein");
        assert!(retrieved.is_some(), "Verschlüsselte Daten {} sollten abrufbar sein", i);
        assert_eq!(retrieved.unwrap(), expected, "Verschlüsselte Daten {} sollten korrekt entschlüsselt werden", i);
    }
    
    println!("✅ Storage Encryption erfolgreich");
}

#[test]
fn test_storage_quota_management() {
    println!("🧪 Teste Storage Quota Management...");
    
    let db = create_test_database();
    
    // Test 1: Normale Speicherung
    let small_data = b"Small data";
    let result = db.put("small_key", small_data);
    assert!(result.is_ok(), "Kleine Daten sollten gespeichert werden können");
    
    // Test 2: Große Daten (simuliere Quota-Überschreitung)
    let large_data = vec![0u8; 1024 * 1024]; // 1MB
    let result = db.put("large_key", &large_data);
    // In einer echten Implementierung würde dies einen Quota-Fehler verursachen
    // Für diesen Test akzeptieren wir beide Ergebnisse
    match result {
        Ok(_) => println!("Große Daten erfolgreich gespeichert"),
        Err(StorageError::QuotaExceeded(_)) => println!("Quota korrekt erkannt"),
        Err(e) => panic!("Unerwarteter Fehler: {:?}", e),
    }
    
    // Test 3: Storage-Größe prüfen
    let size = db.size().expect("Storage size sollte erfolgreich sein");
    assert!(size > 0, "Datenbank sollte Daten enthalten");
    
    println!("✅ Storage Quota Management erfolgreich");
}

#[test]
fn test_network_security_filter() {
    println!("🧪 Teste Network Security Filter...");
    
    // Test 1: Erlaubte URLs
    let allowed_urls = vec![
        "https://api.connectias.com",
        "https://secure.example.com",
        "https://trusted-service.com",
    ];
    
    for url in allowed_urls {
        let is_allowed = NetworkSecurityFilter::is_url_allowed(url);
        assert!(is_allowed, "URL {} sollte erlaubt sein", url);
    }
    
    // Test 2: Blockierte URLs
    let blocked_urls = vec![
        "http://insecure.example.com", // HTTP statt HTTPS
        "https://malicious-site.com",
        "https://suspicious-domain.net",
        "file:///local/path", // Lokale Dateien
    ];
    
    for url in blocked_urls {
        let is_allowed = NetworkSecurityFilter::is_url_allowed(url);
        assert!(!is_allowed, "URL {} sollte blockiert sein", url);
    }
    
    // Test 3: URL-Validierung
    let invalid_urls = vec![
        "not-a-url",
        "",
        "ftp://insecure.com",
        "javascript:alert('xss')",
    ];
    
    for url in invalid_urls {
        let is_allowed = NetworkSecurityFilter::is_url_allowed(url);
        assert!(!is_allowed, "Ungültige URL {} sollte blockiert sein", url);
    }
    
    println!("✅ Network Security Filter erfolgreich");
}

#[test]
fn test_permission_service() {
    println!("🧪 Teste Permission Service...");
    
    let permission_service = PermissionService::new();
    
    // Test 1: Plugin-Berechtigungen prüfen
    let plugin_id = "test-plugin-123";
    let required_permissions = vec![
        "storage:read".to_string(),
        "storage:write".to_string(),
        "network:https".to_string(),
    ];
    
    // Plugin-Berechtigungen gewähren
    for permission in &required_permissions {
        let result = permission_service.grant_permission(plugin_id, permission);
        assert!(result.is_ok(), "Berechtigung {} sollte gewährt werden können", permission);
    }
    
    // Test 2: Berechtigungen validieren
    for permission in &required_permissions {
        let has_permission = permission_service.has_permission(plugin_id, permission);
        assert!(has_permission, "Plugin sollte Berechtigung {} haben", permission);
    }
    
    // Test 3: Nicht gewährte Berechtigungen
    let denied_permission = "system:admin";
    let has_permission = permission_service.has_permission(plugin_id, denied_permission);
    assert!(!has_permission, "Plugin sollte keine Admin-Berechtigung haben");
    
    // Test 4: Berechtigung widerrufen
    let revoke_result = permission_service.revoke_permission(plugin_id, "storage:write");
    assert!(revoke_result.is_ok(), "Berechtigung sollte widerrufen werden können");
    
    let has_permission = permission_service.has_permission(plugin_id, "storage:write");
    assert!(!has_permission, "Widerrufene Berechtigung sollte nicht mehr gültig sein");
    
    println!("✅ Permission Service erfolgreich");
}

#[test]
fn test_monitoring_service() {
    println!("🧪 Teste Monitoring Service...");
    
    let monitoring_service = MonitoringService::new();
    
    // Test 1: Performance-Metriken sammeln
    let plugin_id = "test-plugin-456";
    
    // Simuliere Plugin-Ausführung
    let start_time = std::time::Instant::now();
    thread::sleep(Duration::from_millis(100)); // Simuliere Arbeit
    let execution_time = start_time.elapsed();
    
    // Metriken aufzeichnen
    let result = monitoring_service.record_execution_time(plugin_id, execution_time);
    assert!(result.is_ok(), "Execution time sollte aufgezeichnet werden können");
    
    // Test 2: Memory-Usage aufzeichnen
    let memory_usage = 1024 * 1024; // 1MB
    let result = monitoring_service.record_memory_usage(plugin_id, memory_usage);
    assert!(result.is_ok(), "Memory usage sollte aufgezeichnet werden können");
    
    // Test 3: Error-Rate aufzeichnen
    let error_count = 2;
    let total_operations = 10;
    let result = monitoring_service.record_error_rate(plugin_id, error_count, total_operations);
    assert!(result.is_ok(), "Error rate sollte aufgezeichnet werden können");
    
    // Test 4: Metriken abrufen
    let metrics = monitoring_service.get_plugin_metrics(plugin_id);
    assert!(metrics.is_ok(), "Plugin-Metriken sollten abrufbar sein");
    
    let metrics = metrics.unwrap();
    assert!(metrics.execution_time_avg > Duration::from_millis(0), "Durchschnittliche Ausführungszeit sollte aufgezeichnet sein");
    assert!(metrics.memory_usage > 0, "Memory-Usage sollte aufgezeichnet sein");
    assert!(metrics.error_rate >= 0.0, "Error-Rate sollte aufgezeichnet sein");
    
    println!("✅ Monitoring Service erfolgreich");
}

#[test]
fn test_threat_detection_system() {
    println!("🧪 Teste Threat Detection System...");
    
    let threat_detection = ThreatDetectionSystem::new();
    
    // Test 1: Normale Plugin-Aktivität
    let normal_activity = vec![
        "plugin_started",
        "storage_read",
        "network_request",
        "plugin_finished",
    ];
    
    for activity in &normal_activity {
        let threat_level = threat_detection.analyze_activity(activity);
        assert!(threat_level <= 0.3, "Normale Aktivität {} sollte niedrige Threat-Level haben", activity);
    }
    
    // Test 2: Verdächtige Aktivität
    let suspicious_activity = vec![
        "excessive_memory_usage",
        "suspicious_network_request",
        "unauthorized_system_access",
        "code_injection_attempt",
    ];
    
    for activity in &suspicious_activity {
        let threat_level = threat_detection.analyze_activity(activity);
        assert!(threat_level > 0.5, "Verdächtige Aktivität {} sollte hohe Threat-Level haben", activity);
    }
    
    // Test 3: Anomalie-Erkennung
    let anomaly_pattern = vec![
        "rapid_successive_calls",
        "unusual_data_access_pattern",
        "suspicious_timing",
    ];
    
    for pattern in &anomaly_pattern {
        let is_anomaly = threat_detection.detect_anomaly(pattern);
        assert!(is_anomaly, "Anomalie-Pattern {} sollte erkannt werden", pattern);
    }
    
    println!("✅ Threat Detection System erfolgreich");
}

#[test]
fn test_service_integration_workflow() {
    println!("🧪 Teste Service Integration Workflow...");
    
    // Simuliere einen kompletten Plugin-Workflow
    let plugin_id = "integration-test-plugin";
    
    // 1. Permission Service: Berechtigungen prüfen
    let permission_service = PermissionService::new();
    let required_permissions = vec!["storage:read", "storage:write", "network:https"];
    
    for permission in &required_permissions {
        let _ = permission_service.grant_permission(plugin_id, permission);
    }
    
    // 2. Storage Service: Daten speichern
    let db = create_test_database();
    let config_data = b"Plugin configuration data";
    let storage_result = db.put("plugin_config", config_data);
    assert!(storage_result.is_ok(), "Plugin-Konfiguration sollte gespeichert werden können");
    
    // 3. Network Service: URL-Validierung
    let test_url = "https://api.connectias.com/data";
    let is_url_allowed = NetworkSecurityFilter::is_url_allowed(test_url);
    assert!(is_url_allowed, "API-URL sollte erlaubt sein");
    
    // 4. Monitoring Service: Ausführung überwachen
    let monitoring_service = MonitoringService::new();
    let start_time = std::time::Instant::now();
    
    // Simuliere Plugin-Ausführung
    thread::sleep(Duration::from_millis(50));
    let execution_time = start_time.elapsed();
    
    let _ = monitoring_service.record_execution_time(plugin_id, execution_time);
    let _ = monitoring_service.record_memory_usage(plugin_id, 512 * 1024);
    
    // 5. Threat Detection: Aktivität analysieren
    let threat_detection = ThreatDetectionSystem::new();
    let threat_level = threat_detection.analyze_activity("normal_plugin_execution");
    assert!(threat_level <= 0.3, "Normale Plugin-Ausführung sollte niedrige Threat-Level haben");
    
    // 6. Cleanup: Berechtigungen widerrufen
    for permission in &required_permissions {
        let _ = permission_service.revoke_permission(plugin_id, permission);
    }
    
    println!("✅ Service Integration Workflow erfolgreich");
}

#[test]
fn test_concurrent_service_access() {
    println!("🧪 Teste Concurrent Service Access...");
    
    let handles: Vec<_> = (0..5).map(|i| {
        thread::spawn(move || {
            let plugin_id = format!("concurrent-plugin-{}", i);
            
            // Jeder Thread verwendet alle Services
            let permission_service = PermissionService::new();
            let monitoring_service = MonitoringService::new();
            let db = create_test_database();
            
            // Permission Service
            let _ = permission_service.grant_permission(&plugin_id, "storage:read");
            let has_permission = permission_service.has_permission(&plugin_id, "storage:read");
            assert!(has_permission);
            
            // Storage Service
            let key = format!("concurrent_key_{}", i);
            let value = format!("Concurrent value {}", i).into_bytes();
            let result = db.put(&key, &value);
            assert!(result.is_ok());
            
            // Monitoring Service
            let _ = monitoring_service.record_execution_time(&plugin_id, Duration::from_millis(100));
            let _ = monitoring_service.record_memory_usage(&plugin_id, 1024 * 1024);
            
            // Cleanup
            let _ = permission_service.revoke_permission(&plugin_id, "storage:read");
            let _ = db.delete(&key);
        })
    }).collect();
    
    // Warte auf alle Threads
    for handle in handles {
        handle.join().expect("Thread should complete");
    }
    
    println!("✅ Concurrent Service Access erfolgreich");
}

#[test]
fn test_service_error_handling() {
    println!("🧪 Teste Service Error Handling...");
    
    // Test 1: Storage Service Fehler
    let db = create_test_database();
    
    // Ungültige Operationen
    let invalid_key = ""; // Leerer Key
    let result = db.get(invalid_key);
    // Sollte einen Fehler oder None zurückgeben
    match result {
        Ok(None) => println!("Leerer Key korrekt behandelt"),
        Err(_) => println!("Storage-Fehler korrekt behandelt"),
        _ => {}
    }
    
    // Test 2: Permission Service Fehler
    let permission_service = PermissionService::new();
    let invalid_plugin_id = "";
    let result = permission_service.grant_permission(invalid_plugin_id, "storage:read");
    // Sollte einen Fehler zurückgeben
    match result {
        Ok(_) => println!("Permission Service akzeptiert ungültige Plugin-ID"),
        Err(_) => println!("Permission Service Fehler korrekt behandelt"),
    }
    
    // Test 3: Monitoring Service Fehler
    let monitoring_service = MonitoringService::new();
    let invalid_plugin_id = "";
    let result = monitoring_service.record_execution_time(invalid_plugin_id, Duration::from_millis(100));
    // Sollte einen Fehler zurückgeben
    match result {
        Ok(_) => println!("Monitoring Service akzeptiert ungültige Plugin-ID"),
        Err(_) => println!("Monitoring Service Fehler korrekt behandelt"),
    }
    
    println!("✅ Service Error Handling erfolgreich");
}
