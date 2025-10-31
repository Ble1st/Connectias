use connectias_ipc::{IPCMessage, IPCTransport, MAX_MESSAGE_SIZE};
use connectias_ipc::unix_socket::UnixSocketTransport;
use std::sync::Arc;
use tokio::time::{sleep, Duration};
use uuid::Uuid;

#[tokio::test]
async fn test_unix_socket_send_receive() {
    use std::sync::atomic::{AtomicU64, Ordering};
    use tempfile::tempdir;
    
    // Erstelle eindeutigen Socket-Pfad pro Test-Lauf
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let test_id = COUNTER.fetch_add(1, Ordering::SeqCst);
    let temp_dir = tempdir().expect("Failed to create temp dir");
    let path = temp_dir.path().join(format!("test_connectias_{}.sock", test_id));
    let path_str = path.to_str().unwrap();
    
    // Starte Server in separatem Task
    let server_transport = Arc::new(UnixSocketTransport::new());
    let server_transport_for_spawn = server_transport.clone();
    let server_path = path_str.to_string();
    let server_handle = tokio::spawn(async move {
        server_transport_for_spawn.listen(&server_path).await.unwrap();
        // Server wartet auf eingehende Verbindungen
        sleep(Duration::from_millis(100)).await;
    });
    
    // Warte kurz damit Server startet
    sleep(Duration::from_millis(50)).await;
    
    // Client verbindet sich
    let client_transport = UnixSocketTransport::new();
    client_transport.connect(path_str).await.unwrap();
    
    let msg = IPCMessage::new(
        "test_plugin".to_string(),
        "test_topic".to_string(),
        vec![1, 2, 3, 4, 5],
        1234567890,
        Uuid::new_v4().to_string(), // UUID erforderlich
        "Event".to_string(),
    ).unwrap();
    
    // Server empfängt Messages (nach Client-Connect)
    let server_clone = server_transport.clone();
    let received_msg = Arc::new(tokio::sync::Mutex::new(None));
    let received_msg_clone = received_msg.clone();
    
    let server_task = tokio::spawn(async move {
        // Empfange eingehende Messages mit Timeout
        if let Ok(Some(received)) = server_clone.try_receive(Duration::from_millis(500)).await {
            *received_msg_clone.lock().await = Some(received);
        }
    });
    
    // Warte kurz damit Server bereit ist
    sleep(Duration::from_millis(50)).await;
    
    // Client sendet Nachricht
    client_transport.send("target", msg.clone()).await.unwrap();
    
    // Warte auf Server-Task
    server_task.await.unwrap();
    
    // Prüfe empfangene Message
    let received = received_msg.lock().await;
    if let Some(received) = received.as_ref() {
        assert_eq!(msg.plugin_id(), received.plugin_id());
        assert_eq!(msg.topic(), received.topic());
        assert_eq!(msg.payload(), received.payload());
        assert_eq!(msg.timestamp(), received.timestamp());
        assert_eq!(msg.message_id(), received.message_id());
        assert_eq!(msg.message_type(), received.message_type());
    } else {
        panic!("Server received no message");
    }
    
    // Warte auf Server Task
    let _ = server_handle.await;
    
    // Cleanup durch tempdir Drop (automatisch)
}

#[tokio::test]
async fn test_message_too_large() {
    use tempfile::tempdir;
    let temp_dir = tempdir().expect("Failed to create temp dir");
    let path = temp_dir.path().join("test_connectias_large.sock");
    let path_str = path.to_str().unwrap();
    
    // Starte Server in separatem Task
    let server_transport = Arc::new(UnixSocketTransport::new());
    let server_path = path_str.to_string();
    let server_handle = tokio::spawn(async move {
        server_transport.listen(&server_path).await.unwrap();
        // Server wartet auf eingehende Verbindungen
        sleep(Duration::from_millis(100)).await;
    });
    
    // Warte kurz damit Server startet
    sleep(Duration::from_millis(50)).await;
    
    // Client verbindet sich
    let client_transport = UnixSocketTransport::new();
    client_transport.connect(path_str).await.unwrap();
    
    // Versuche Message mit zu großem Payload zu erstellen - sollte Validierungsfehler geben
    let msg_result = IPCMessage::new(
        "test_plugin".to_string(),
        "test".to_string(),
        vec![0u8; MAX_MESSAGE_SIZE + 1], // 1 Byte zu groß
        946684800, // Gültiger Timestamp
        uuid::Uuid::new_v4().to_string(),
        "Event".to_string(),
    );
    
    // Message-Erstellung sollte fehlschlagen
    assert!(msg_result.is_err());
    
    // Warte auf Server Task
    let _ = server_handle.await;
    
    // Cleanup durch tempdir Drop (automatisch)
}

#[tokio::test]
async fn test_connection_reconnect() {
    use tempfile::tempdir;
    let temp_dir = tempdir().expect("Failed to create temp dir");
    let path = temp_dir.path().join("test_connectias_reconnect.sock");
    let path_str = path.to_str().unwrap();
    
    // Starte Server in separatem Task
    let server_transport = Arc::new(UnixSocketTransport::new());
    let server_path = path_str.to_string();
    let server_handle = tokio::spawn(async move {
        server_transport.listen(&server_path).await.unwrap();
        sleep(Duration::from_millis(200)).await;
    });
    
    // Warte kurz damit Server startet
    sleep(Duration::from_millis(50)).await;
    
    let client_transport = UnixSocketTransport::new();
    
    // Teste erste Verbindung
    client_transport.connect(path_str).await.unwrap();
    
    // Teste Disconnect
    client_transport.disconnect().await.unwrap();
    
    // Teste Reconnect
    client_transport.connect(path_str).await.unwrap();
    
    // Warte auf Server Task
    let _ = server_handle.await;
    
    // Cleanup durch tempdir Drop (automatisch)
}

#[tokio::test]
async fn test_multiple_messages() {
    use tempfile::tempdir;
    let temp_dir = tempdir().expect("Failed to create temp dir");
    let path = temp_dir.path().join("test_connectias_multiple.sock");
    let path_str = path.to_str().unwrap();
    
    // Starte Server in separatem Task
    let server_transport = Arc::new(UnixSocketTransport::new());
    let server_transport_for_spawn = server_transport.clone();
    let server_path = path_str.to_string();
    let server_handle = tokio::spawn(async move {
        server_transport_for_spawn.listen(&server_path).await.unwrap();
        sleep(Duration::from_millis(200)).await;
    });
    
    // Warte kurz damit Server startet
    sleep(Duration::from_millis(50)).await;
    
    // Client verbindet sich
    let client_transport = UnixSocketTransport::new();
    client_transport.connect(path_str).await.unwrap();
    
    let messages = vec![
        IPCMessage::new(
            "plugin1".to_string(),
            "topic1".to_string(),
            vec![1, 2, 3],
            946684801, // Gültiger Timestamp
            Uuid::new_v4().to_string(),
            "Event".to_string(),
        ).unwrap(),
        IPCMessage::new(
            "plugin2".to_string(),
            "topic2".to_string(),
            vec![4, 5, 6],
            946684802,
            Uuid::new_v4().to_string(),
            "Event".to_string(),
        ).unwrap(),
        IPCMessage::new(
            "plugin3".to_string(),
            "topic3".to_string(),
            vec![7, 8, 9],
            946684803,
            Uuid::new_v4().to_string(),
            "Event".to_string(),
        ).unwrap(),
    ];
    let message_count = messages.len();
    let messages_clone = messages.clone(); // Clone für späteren Vergleich
    
    // Server akzeptiert Verbindungen und empfängt Messages
    let server_clone = server_transport.clone();
    let received_messages = Arc::new(tokio::sync::Mutex::new(Vec::new()));
    let received_messages_clone = received_messages.clone();
    
    let server_task = tokio::spawn(async move {
        // Empfange alle eingehenden Messages
        for _ in 0..message_count {
            if let Ok(Some(received)) = server_clone.try_receive(Duration::from_secs(1)).await {
                received_messages_clone.lock().await.push(received);
            }
        }
    });
    
    // Warte kurz damit Server bereit ist
    sleep(Duration::from_millis(50)).await;
    
    // Sende alle Messages
    for msg in &messages {
        client_transport.send("target", msg.clone()).await.unwrap();
    }
    
    // Warte auf Server-Task
    server_task.await.unwrap();
    
    // Prüfe empfangene Messages
    let received = received_messages.lock().await;
    assert_eq!(received.len(), messages_clone.len());
    for (expected, actual) in messages_clone.iter().zip(received.iter()) {
        assert_eq!(expected.plugin_id(), actual.plugin_id());
        assert_eq!(expected.topic(), actual.topic());
        assert_eq!(expected.payload(), actual.payload());
        assert_eq!(expected.timestamp(), actual.timestamp());
        assert_eq!(expected.message_id(), actual.message_id());
        assert_eq!(expected.message_type(), actual.message_type());
    }
    
    // Warte auf Server Task
    let _ = server_handle.await;
    
    // Cleanup durch tempdir Drop (automatisch)
}
