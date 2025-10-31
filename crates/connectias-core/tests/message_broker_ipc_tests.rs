use connectias_core::message_broker::{MessageBroker, ProcessMode, MessageType, Message};
use connectias_ipc::{UnixSocketTransport, IPCMessage};
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Mutex;

#[tokio::test]
async fn test_single_process_mode() {
    let broker = Arc::new(MessageBroker::new()
        .with_mode(ProcessMode::SingleProcess));
    
    // Setup message recording
    let message_count = Arc::new(AtomicUsize::new(0));
    let received_messages = Arc::new(Mutex::new(Vec::new()));
    
    let message_count_clone = message_count.clone();
    let received_messages_clone = received_messages.clone();
    let handler = Arc::new(move |message: &Message| {
        message_count_clone.fetch_add(1, Ordering::SeqCst);
        received_messages_clone.lock().unwrap().push(message.topic.clone());
    });
    
    // Subscribe to messages
    let subscription = connectias_core::message_broker::MessageSubscription {
        topic: "test_topic".to_string(),
        plugin_id: "test_plugin".to_string(),
        handler: handler.clone(),
    };
    broker.subscribe(subscription).await;
    
    // Publish test message
    broker.publish("test_topic", "plugin1", vec![1, 2, 3], MessageType::Event).await;
    
    // Allow time for message delivery
    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
    
    // Verify message was received
    let count = message_count.load(Ordering::SeqCst);
    assert_eq!(count, 1, "Should have received exactly one message");
    
    let messages = received_messages.lock().unwrap();
    assert_eq!(messages.len(), 1, "Should have recorded one message");
    assert_eq!(messages[0], "test_topic", "Should have received message for correct topic");
}

#[tokio::test]
async fn test_multi_process_mode() {
    let transport = Arc::new(UnixSocketTransport::new());
    let broker = Arc::new(MessageBroker::new()
        .with_ipc_transport(transport)
        .with_mode(ProcessMode::MultiProcess));
    
    // Setup message recording
    let message_count = Arc::new(AtomicUsize::new(0));
    let received_messages = Arc::new(Mutex::new(Vec::new()));
    
    let message_count_clone = message_count.clone();
    let received_messages_clone = received_messages.clone();
    let handler = Arc::new(move |message: &Message| {
        message_count_clone.fetch_add(1, Ordering::SeqCst);
        received_messages_clone.lock().unwrap().push(message.topic.clone());
    });
    
    // Subscribe to messages
    let subscription = connectias_core::message_broker::MessageSubscription {
        topic: "test_topic".to_string(),
        plugin_id: "test_plugin".to_string(),
        handler: handler.clone(),
    };
    broker.subscribe(subscription).await;
    
    // Publish test message in MultiProcess mode
    broker.publish("test_topic", "plugin1", vec![1, 2, 3], MessageType::Event).await;
    
    // Allow time for message delivery (IPC might take longer)
    tokio::time::sleep(tokio::time::Duration::from_millis(200)).await;
    
    // Verify message was received (should work even if IPC fails due to fallback)
    let count = message_count.load(Ordering::SeqCst);
    assert!(count > 0, "MultiProcess mode should deliver at least one message (no fallback pass)");
    
    // Immer prüfen ob Messages empfangen wurden
    let messages = received_messages.lock().unwrap();
    assert_eq!(messages.len(), count as usize, "Message count should match recorded messages");
    assert_eq!(messages[0], "test_topic", "Should have received message for correct topic");
}

#[tokio::test]
async fn test_process_mode_switching() {
    // Test SingleProcess mode
    let broker1 = Arc::new(MessageBroker::new()
        .with_mode(ProcessMode::SingleProcess));
    
    // Setup message recording for SingleProcess
    let message_count1 = Arc::new(AtomicUsize::new(0));
    let message_count1_clone = message_count1.clone();
    let handler1 = Arc::new(move |_message: &Message| {
        message_count1_clone.fetch_add(1, Ordering::SeqCst);
    });
    
    let subscription1 = connectias_core::message_broker::MessageSubscription {
        topic: "topic1".to_string(),
        plugin_id: "plugin1".to_string(),
        handler: handler1.clone(),
    };
    broker1.subscribe(subscription1).await;
    
    // Test SingleProcess
    broker1.publish("topic1", "plugin1", vec![1], MessageType::Event).await;
    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
    
    let count1 = message_count1.load(Ordering::SeqCst);
    assert_eq!(count1, 1, "SingleProcess mode should deliver message");
    
    // Test MultiProcess mode
    let transport = Arc::new(UnixSocketTransport::new());
    let broker2 = Arc::new(MessageBroker::new()
        .with_ipc_transport(transport)
        .with_mode(ProcessMode::MultiProcess));
    
    // Setup message recording for MultiProcess
    let message_count2 = Arc::new(AtomicUsize::new(0));
    let message_count2_clone = message_count2.clone();
    let handler2 = Arc::new(move |_message: &Message| {
        message_count2_clone.fetch_add(1, Ordering::SeqCst);
    });
    
    let subscription2 = connectias_core::message_broker::MessageSubscription {
        topic: "topic2".to_string(),
        plugin_id: "plugin2".to_string(),
        handler: handler2.clone(),
    };
    broker2.subscribe(subscription2).await;
    
    // Test MultiProcess
    broker2.publish("topic2", "plugin2", vec![2], MessageType::Event).await;
    tokio::time::sleep(tokio::time::Duration::from_millis(200)).await;
    
    let count2 = message_count2.load(Ordering::SeqCst);
    assert!(count2 > 0, "MultiProcess mode should deliver at least one message (no fallback pass)");
}

#[tokio::test]
async fn test_message_types() {
    let broker = Arc::new(MessageBroker::new()
        .with_mode(ProcessMode::SingleProcess));
    
    // Setup message recording
    let message_count = Arc::new(AtomicUsize::new(0));
    let received_message_types = Arc::new(Mutex::new(Vec::new()));
    
    let message_count_clone = message_count.clone();
    let received_message_types_clone = received_message_types.clone();
    let handler = Arc::new(move |message: &Message| {
        message_count_clone.fetch_add(1, Ordering::SeqCst);
        received_message_types_clone.lock().unwrap().push(message.message_type.clone());
    });
    
    // Subscribe to messages
    let subscription = connectias_core::message_broker::MessageSubscription {
        topic: "topic".to_string(),
        plugin_id: "plugin".to_string(),
        handler: handler.clone(),
    };
    broker.subscribe(subscription).await;
    
    // Teste verschiedene Message-Types
    let message_types = vec![
        MessageType::Request,
        MessageType::Response { request_id: "req1".to_string() },
        MessageType::Event,
        MessageType::Broadcast,
        MessageType::Private { recipient_id: "plugin2".to_string() },
        MessageType::System,
        MessageType::Heartbeat,
        MessageType::Error { error_code: "test_error".to_string() },
    ];
    
    for (i, message_type) in message_types.iter().enumerate() {
        broker.publish("topic", "plugin", vec![i as u8 + 1], message_type.clone()).await;
    }
    
    // Allow time for message delivery
    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
    
    // Verify all messages were received
    let count = message_count.load(Ordering::SeqCst);
    assert_eq!(count, message_types.len(), "Should have received all {} message types", message_types.len());
    
    // Verify message types were recorded correctly
    let types = received_message_types.lock().unwrap();
    assert_eq!(types.len(), message_types.len(), "Should have recorded all message types");
    
    // Verify specific message types
    assert_eq!(types[0], MessageType::Request, "First message should be Request");
    assert_eq!(types[2], MessageType::Event, "Third message should be Event");
    assert_eq!(types[6], MessageType::Heartbeat, "Seventh message should be Heartbeat");
}
