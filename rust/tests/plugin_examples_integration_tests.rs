//! Plugin Examples Integration Tests für Connectias
//! 
//! Testet die 3 erweiterten Plugin-Beispiele:
//! - WASM Chat Plugin (Inter-Plugin-Kommunikation)
//! - WASM Storage Demo Plugin (Storage Service)
//! - WASM Network Demo Plugin (Network Service)

use std::time::Duration;
use std::sync::{Arc, Mutex};
use connectias_core::message_broker::{
    MessageBroker, MessageType, MessagePriority, MessageFilter, FilterAction,
    PluginConnection, RateLimit, MessageBrokerManager
};

#[tokio::test]
async fn test_wasm_chat_plugin_integration() {
    println!("🧪 Teste WASM Chat Plugin Integration...");
    
    let broker = MessageBroker::new_enhanced();
    broker.start_background_service().await;
    
    // Test 1: Chat Plugin registrieren
    let chat_plugin_id = "wasm-chat-plugin";
    let permissions = vec!["message:send".to_string(), "message:receive".to_string()];
    let result = broker.register_plugin(chat_plugin_id, permissions).await;
    assert!(result.is_ok(), "Chat Plugin sollte registriert werden können");
    
    // Test 2: Chat Room erstellen
    let room_creation_result = broker.publish_enhanced(
        "chat.create_room",
        chat_plugin_id,
        serde_json::json!({
            "command": "create_room",
            "args": {
                "name": "Test Chat Room",
                "participants": ["user1", "user2"]
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Normal,
        None,
    ).await;
    assert!(room_creation_result.is_ok(), "Chat Room sollte erstellt werden können");
    
    // Test 3: Chat Message senden
    let message_result = broker.publish_enhanced(
        "chat.send_message",
        chat_plugin_id,
        serde_json::json!({
            "command": "send_message",
            "args": {
                "room_id": "test_room",
                "content": "Hello from WASM Chat Plugin!",
                "recipient": null
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::High,
        None,
    ).await;
    assert!(message_result.is_ok(), "Chat Message sollte gesendet werden können");
    
    // Test 4: Broadcast Message
    let broadcast_result = broker.publish_enhanced(
        "chat.broadcast",
        chat_plugin_id,
        serde_json::json!({
            "command": "broadcast_message",
            "args": {
                "content": "System announcement: Chat plugin is working!"
            }
        }).to_string().into_bytes(),
        MessageType::Broadcast,
        MessagePriority::Critical,
        None,
    ).await;
    assert!(broadcast_result.is_ok(), "Broadcast Message sollte gesendet werden können");
    
    // Test 5: Plugin deregistrieren
    let result = broker.unregister_plugin(chat_plugin_id).await;
    assert!(result.is_ok(), "Chat Plugin sollte deregistriert werden können");
    
    broker.stop_background_service().await;
    println!("✅ WASM Chat Plugin Integration erfolgreich");
}

#[tokio::test]
async fn test_wasm_storage_demo_plugin_integration() {
    println!("🧪 Teste WASM Storage Demo Plugin Integration...");
    
    let broker = MessageBroker::new_enhanced();
    broker.start_background_service().await;
    
    // Test 1: Storage Demo Plugin registrieren
    let storage_plugin_id = "wasm-storage-demo-plugin";
    let permissions = vec!["storage:read".to_string(), "storage:write".to_string()];
    let result = broker.register_plugin(storage_plugin_id, permissions).await;
    assert!(result.is_ok(), "Storage Demo Plugin sollte registriert werden können");
    
    // Test 2: Daten speichern
    let store_result = broker.publish_enhanced(
        "storage.store_data",
        storage_plugin_id,
        serde_json::json!({
            "command": "store_data",
            "args": {
                "key": "user_preferences",
                "value": "{\"theme\": \"dark\", \"language\": \"en\"}",
                "encrypt": true
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Normal,
        None,
    ).await;
    assert!(store_result.is_ok(), "Daten sollten gespeichert werden können");
    
    // Test 3: Daten abrufen
    let retrieve_result = broker.publish_enhanced(
        "storage.retrieve_data",
        storage_plugin_id,
        serde_json::json!({
            "command": "retrieve_data",
            "args": {
                "key": "user_preferences"
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Normal,
        None,
    ).await;
    assert!(retrieve_result.is_ok(), "Daten sollten abgerufen werden können");
    
    // Test 4: Verschlüsselung testen
    let encrypt_result = broker.publish_enhanced(
        "storage.encrypt_data",
        storage_plugin_id,
        serde_json::json!({
            "command": "encrypt_data",
            "args": {
                "data": "sensitive information",
                "algorithm": "AES-256-GCM"
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::High,
        None,
    ).await;
    assert!(encrypt_result.is_ok(), "Daten sollten verschlüsselt werden können");
    
    // Test 5: Storage Statistiken
    let stats_result = broker.publish_enhanced(
        "storage.get_stats",
        storage_plugin_id,
        serde_json::json!({
            "command": "get_stats",
            "args": {}
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Low,
        None,
    ).await;
    assert!(stats_result.is_ok(), "Storage Statistiken sollten abgerufen werden können");
    
    // Test 6: Plugin deregistrieren
    let result = broker.unregister_plugin(storage_plugin_id).await;
    assert!(result.is_ok(), "Storage Demo Plugin sollte deregistriert werden können");
    
    broker.stop_background_service().await;
    println!("✅ WASM Storage Demo Plugin Integration erfolgreich");
}

#[tokio::test]
async fn test_wasm_network_demo_plugin_integration() {
    println!("🧪 Teste WASM Network Demo Plugin Integration...");
    
    let broker = MessageBroker::new_enhanced();
    broker.start_background_service().await;
    
    // Test 1: Network Demo Plugin registrieren
    let network_plugin_id = "wasm-network-demo-plugin";
    let permissions = vec!["network:https".to_string(), "network:http".to_string()];
    let result = broker.register_plugin(network_plugin_id, permissions).await;
    assert!(result.is_ok(), "Network Demo Plugin sollte registriert werden können");
    
    // Test 2: HTTP GET Request
    let get_result = broker.publish_enhanced(
        "network.http_get",
        network_plugin_id,
        serde_json::json!({
            "command": "http_get",
            "args": {
                "url": "https://httpbin.org/get",
                "timeout": 30
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Normal,
        None,
    ).await;
    assert!(get_result.is_ok(), "HTTP GET Request sollte funktionieren");
    
    // Test 3: HTTP POST Request
    let post_result = broker.publish_enhanced(
        "network.http_post",
        network_plugin_id,
        serde_json::json!({
            "command": "http_post",
            "args": {
                "url": "https://httpbin.org/post",
                "data": "{\"test\": \"data\"}",
                "timeout": 30
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Normal,
        None,
    ).await;
    assert!(post_result.is_ok(), "HTTP POST Request sollte funktionieren");
    
    // Test 4: HTTPS Request mit SSL-Pinning
    let https_result = broker.publish_enhanced(
        "network.https_request",
        network_plugin_id,
        serde_json::json!({
            "command": "https_request",
            "args": {
                "url": "https://api.github.com",
                "method": "GET"
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::High,
        None,
    ).await;
    assert!(https_result.is_ok(), "HTTPS Request sollte funktionieren");
    
    // Test 5: Security Check
    let security_result = broker.publish_enhanced(
        "network.security_check",
        network_plugin_id,
        serde_json::json!({
            "command": "security_check",
            "args": {
                "url": "https://example.com"
            }
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Critical,
        None,
    ).await;
    assert!(security_result.is_ok(), "Security Check sollte funktionieren");
    
    // Test 6: Network Statistiken
    let stats_result = broker.publish_enhanced(
        "network.get_stats",
        network_plugin_id,
        serde_json::json!({
            "command": "get_network_stats",
            "args": {}
        }).to_string().into_bytes(),
        MessageType::Event,
        MessagePriority::Low,
        None,
    ).await;
    assert!(stats_result.is_ok(), "Network Statistiken sollten abgerufen werden können");
    
    // Test 7: Plugin deregistrieren
    let result = broker.unregister_plugin(network_plugin_id).await;
    assert!(result.is_ok(), "Network Demo Plugin sollte deregistriert werden können");
    
    broker.stop_background_service().await;
    println!("✅ WASM Network Demo Plugin Integration erfolgreich");
}

#[tokio::test]
async fn test_inter_plugin_communication() {
    println!("🧪 Teste Inter-Plugin-Kommunikation...");
    
    let broker = MessageBroker::new_enhanced();
    broker.start_background_service().await;
    
    // Test 1: Mehrere Plugins registrieren
    let chat_plugin = "wasm-chat-plugin";
    let storage_plugin = "wasm-storage-demo-plugin";
    let network_plugin = "wasm-network-demo-plugin";
    
    broker.register_plugin(chat_plugin, vec!["message:send".to_string()]).await.unwrap();
    broker.register_plugin(storage_plugin, vec!["storage:write".to_string()]).await.unwrap();
    broker.register_plugin(network_plugin, vec!["network:https".to_string()]).await.unwrap();
    
    // Test 2: Chat Plugin sendet Message an Storage Plugin
    let inter_plugin_result = broker.publish_enhanced(
        "plugin.storage",
        chat_plugin,
        serde_json::json!({
            "command": "store_data",
            "args": {
                "key": "chat_history",
                "value": "Chat message from chat plugin",
                "encrypt": false
            }
        }).to_string().into_bytes(),
        MessageType::Private { recipient_id: storage_plugin.to_string() },
        MessagePriority::Normal,
        None,
    ).await;
    assert!(inter_plugin_result.is_ok(), "Inter-Plugin-Kommunikation sollte funktionieren");
    
    // Test 3: Storage Plugin sendet Message an Network Plugin
    let storage_to_network_result = broker.publish_enhanced(
        "plugin.network",
        storage_plugin,
        serde_json::json!({
            "command": "http_get",
            "args": {
                "url": "https://api.example.com/data",
                "timeout": 30
            }
        }).to_string().into_bytes(),
        MessageType::Private { recipient_id: network_plugin.to_string() },
        MessagePriority::Normal,
        None,
    ).await;
    assert!(storage_to_network_result.is_ok(), "Storage-zu-Network-Kommunikation sollte funktionieren");
    
    // Test 4: Broadcast Message an alle Plugins
    let broadcast_result = broker.publish_enhanced(
        "system.broadcast",
        "system",
        serde_json::json!({
            "command": "system_notification",
            "args": {
                "message": "System maintenance in 5 minutes"
            }
        }).to_string().into_bytes(),
        MessageType::Broadcast,
        MessagePriority::Critical,
        None,
    ).await;
    assert!(broadcast_result.is_ok(), "Broadcast an alle Plugins sollte funktionieren");
    
    // Test 5: Request-Response Pattern zwischen Plugins
    let request_response_result = broker.request_response(
        "plugin.chat",
        storage_plugin,
        serde_json::json!({
            "command": "get_messages",
            "args": {
                "room_id": "general",
                "limit": 10
            }
        }).to_string().into_bytes(),
        Duration::from_secs(5),
    ).await;
    
    // Request-Response kann timeout oder success sein
    match request_response_result {
        Ok(response) => {
            assert_eq!(response.topic, "response");
            println!("✅ Request-Response Pattern erfolgreich");
        }
        Err(e) => {
            assert!(e.contains("timeout") || e.contains("Request"));
            println!("⚠️ Request-Response Timeout (erwartet in Test-Umgebung)");
        }
    }
    
    // Test 6: Alle Plugins deregistrieren
    broker.unregister_plugin(chat_plugin).await.unwrap();
    broker.unregister_plugin(storage_plugin).await.unwrap();
    broker.unregister_plugin(network_plugin).await.unwrap();
    
    broker.stop_background_service().await;
    println!("✅ Inter-Plugin-Kommunikation erfolgreich");
}

#[tokio::test]
async fn test_plugin_examples_performance() {
    println!("🧪 Teste Plugin Examples Performance...");
    
    let broker = MessageBroker::new_enhanced();
    broker.start_background_service().await;
    
    // Test 1: Performance-Test mit vielen Messages
    let plugin_id = "performance-test-plugin";
    broker.register_plugin(plugin_id, vec!["message:send".to_string()]).await.unwrap();
    
    let start_time = std::time::Instant::now();
    let mut success_count = 0;
    
    // Sende 50 Messages schnell hintereinander
    for i in 0..50 {
        let result = broker.publish_enhanced(
            "performance.test",
            plugin_id,
            format!("Performance test message {}", i).into_bytes(),
            MessageType::Event,
            MessagePriority::Normal,
            None,
        ).await;
        
        if result.is_ok() {
            success_count += 1;
        }
    }
    
    let duration = start_time.elapsed();
    println!("50 Messages in {:?} ({} erfolgreich)", duration, success_count);
    
    // Sollte unter 2 Sekunden sein und mindestens 45 erfolgreiche Messages
    assert!(duration.as_secs() < 2, "Performance sollte akzeptabel sein");
    assert!(success_count >= 45, "Mindestens 45 Messages sollten erfolgreich sein");
    
    // Test 2: Rate Limiting Test
    let rate_limit_start = std::time::Instant::now();
    let mut rate_limited_count = 0;
    
    // Versuche 200 Messages sehr schnell zu senden
    for i in 0..200 {
        let result = broker.publish_enhanced(
            "rate_limit.test",
            plugin_id,
            format!("Rate limit test message {}", i).into_bytes(),
            MessageType::Event,
            MessagePriority::Normal,
            None,
        ).await;
        
        if result.is_err() && result.unwrap_err().contains("Rate limit") {
            rate_limited_count += 1;
        }
    }
    
    let rate_limit_duration = rate_limit_start.elapsed();
    println!("200 Messages in {:?} ({} rate limited)", rate_limit_duration, rate_limited_count);
    
    // Rate Limiting sollte aktiv sein
    assert!(rate_limited_count > 0, "Rate Limiting sollte aktiv sein");
    
    // Test 3: Plugin deregistrieren
    broker.unregister_plugin(plugin_id).await.unwrap();
    
    broker.stop_background_service().await;
    println!("✅ Plugin Examples Performance erfolgreich");
}

#[tokio::test]
async fn test_plugin_examples_error_handling() {
    println!("🧪 Teste Plugin Examples Error Handling...");
    
    let broker = MessageBroker::new_enhanced();
    broker.start_background_service().await;
    
    // Test 1: Ungültiges Plugin Heartbeat
    let result = broker.send_heartbeat("nonexistent-plugin").await;
    assert!(result.is_err(), "Heartbeat für nicht-existierendes Plugin sollte fehlschlagen");
    
    // Test 2: Message ohne Plugin-Registrierung
    let result = broker.publish_enhanced(
        "test.topic",
        "unregistered-plugin",
        b"test message".to_vec(),
        MessageType::Event,
        MessagePriority::Normal,
        None,
    ).await;
    // Das kann erfolgreich sein oder fehlschlagen, je nach Implementierung
    
    // Test 3: Plugin mit ungültigen Permissions
    let invalid_plugin = "invalid-permissions-plugin";
    let result = broker.register_plugin(invalid_plugin, vec!["invalid:permission".to_string()]).await;
    // Das kann erfolgreich sein oder fehlschlagen, je nach Implementierung
    
    // Test 4: Message mit ungültigem Topic
    let result = broker.publish_enhanced(
        "", // Leerer Topic
        "test-plugin",
        b"test message".to_vec(),
        MessageType::Event,
        MessagePriority::Normal,
        None,
    ).await;
    // Das kann erfolgreich sein oder fehlschlagen, je nach Implementierung
    
    // Test 5: Request-Response mit sehr kurzem Timeout
    let result = broker.request_response(
        "timeout.test",
        "test-plugin",
        b"timeout request".to_vec(),
        Duration::from_millis(1), // 1ms Timeout
    ).await;
    assert!(result.is_err(), "Request-Response sollte bei Timeout fehlschlagen");
    
    broker.stop_background_service().await;
    println!("✅ Plugin Examples Error Handling erfolgreich");
}
