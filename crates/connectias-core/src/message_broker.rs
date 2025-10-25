use std::collections::{HashMap, VecDeque};
use std::sync::{Arc, Mutex, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};
use serde::{Deserialize, Serialize};

/// Message Broker für Inter-Plugin Communication
pub struct MessageBroker {
    subscribers: Arc<RwLock<HashMap<String, Vec<MessageHandler>>>>,
    message_queue: Arc<Mutex<VecDeque<Message>>>,
    message_history: Arc<RwLock<HashMap<String, Vec<Message>>>>,
    max_history_size: usize,
}

impl std::fmt::Debug for MessageBroker {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MessageBroker")
            .field("max_history_size", &self.max_history_size)
            .finish()
    }
}

/// Message Handler für Plugin-Subscriptions
pub type MessageHandler = Box<dyn Fn(&Message) + Send + Sync>;

/// Message für Plugin-Communication
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub topic: String,
    pub sender_id: String,
    pub payload: Vec<u8>,
    pub timestamp: i64,
    pub message_id: String,
    pub message_type: MessageType,
}

/// Message Types für verschiedene Kommunikations-Patterns
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MessageType {
    /// Request-Response Pattern
    Request,
    /// Response zu einem Request
    Response { request_id: String },
    /// Event Notification
    Event,
    /// Broadcast Message
    Broadcast,
    /// Private Message zwischen Plugins
    Private { recipient_id: String },
}

/// Message Subscription für Topic-basierte Kommunikation
pub struct MessageSubscription {
    pub topic: String,
    pub plugin_id: String,
    pub handler: MessageHandler,
}

impl Clone for MessageSubscription {
    fn clone(&self) -> Self {
        // Note: We can't clone the handler, so this creates a subscription without handler
        // In practice, subscriptions would be managed differently
        Self {
            topic: self.topic.clone(),
            plugin_id: self.plugin_id.clone(),
            handler: Box::new(|_| {}), // Dummy handler
        }
    }
}

/// Message Broker Statistics
#[derive(Debug, Clone)]
pub struct MessageBrokerStats {
    pub total_messages: u64,
    pub active_subscriptions: usize,
    pub queue_size: usize,
    pub topics_count: usize,
}

impl MessageBroker {
    pub fn new() -> Self {
        Self {
            subscribers: Arc::new(RwLock::new(HashMap::new())),
            message_queue: Arc::new(Mutex::new(VecDeque::new())),
            message_history: Arc::new(RwLock::new(HashMap::new())),
            max_history_size: 1000,
        }
    }

    /// Publiziert eine Message an ein Topic
    pub async fn publish(&self, topic: &str, sender_id: &str, payload: Vec<u8>, message_type: MessageType) {
        let message = Message {
            topic: topic.to_string(),
            sender_id: sender_id.to_string(),
            payload,
            timestamp: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
            message_id: self.generate_message_id(),
            message_type,
        };

        // Message zur Queue hinzufügen
        {
            let mut queue = self.message_queue.lock().unwrap();
            queue.push_back(message.clone());
        }

        // Message History aktualisieren
        self.update_message_history(&message).await;

        // Message an Subscribers verteilen
        self.distribute_message(&message).await;
    }

    /// Subscribiert zu einem Topic
    pub async fn subscribe<F>(&self, topic: &str, _plugin_id: &str, handler: F)
    where
        F: Fn(&Message) + Send + Sync + 'static,
    {
        let mut subscribers = self.subscribers.write().unwrap();
        let handler = Box::new(handler);

        subscribers
            .entry(topic.to_string())
            .or_insert_with(Vec::new)
            .push(handler);
    }

    /// Unsubscribiert von einem Topic
    pub async fn unsubscribe(&self, topic: &str, _plugin_id: &str) {
        let mut subscribers = self.subscribers.write().unwrap();
        if let Some(handlers) = subscribers.get_mut(topic) {
            // In einer echten Implementierung würde hier der spezifische Handler entfernt
            // Für jetzt entfernen wir alle Handler für das Topic
            handlers.clear();
        }
    }

    /// Sendet eine Request-Message und wartet auf Response
    pub async fn request(&self, topic: &str, sender_id: &str, payload: Vec<u8>) -> Result<Message, String> {
        let _request_id = self.generate_message_id();
        let message_type = MessageType::Request;

        // Request publizieren
        self.publish(topic, sender_id, payload, message_type).await;

        // Warten auf Response (vereinfachte Implementierung)
        // In einer echten Implementierung würde hier ein Response-Handler registriert
        // und auf die entsprechende Response gewartet
        Err("Request-Response pattern not fully implemented".to_string())
    }

    /// Sendet eine Response-Message
    pub async fn respond(&self, topic: &str, sender_id: &str, payload: Vec<u8>, request_id: String) {
        let message_type = MessageType::Response { request_id };
        self.publish(topic, sender_id, payload, message_type).await;
    }

    /// Sendet eine Event-Message
    pub async fn emit_event(&self, topic: &str, sender_id: &str, payload: Vec<u8>) {
        let message_type = MessageType::Event;
        self.publish(topic, sender_id, payload, message_type).await;
    }

    /// Sendet eine Broadcast-Message an alle Plugins
    pub async fn broadcast(&self, topic: &str, sender_id: &str, payload: Vec<u8>) {
        let message_type = MessageType::Broadcast;
        self.publish(topic, sender_id, payload, message_type).await;
    }

    /// Sendet eine Private Message an ein spezifisches Plugin
    pub async fn send_private(&self, topic: &str, sender_id: &str, recipient_id: &str, payload: Vec<u8>) {
        let message_type = MessageType::Private { 
            recipient_id: recipient_id.to_string() 
        };
        self.publish(topic, sender_id, payload, message_type).await;
    }

    /// Gibt Message History für ein Topic zurück
    pub async fn get_message_history(&self, topic: &str) -> Vec<Message> {
        let history = self.message_history.read().unwrap();
        history.get(topic).cloned().unwrap_or_default()
    }

    /// Gibt aktuelle Broker-Statistics zurück
    pub async fn get_stats(&self) -> MessageBrokerStats {
        let subscribers = self.subscribers.read().unwrap();
        let queue = self.message_queue.lock().unwrap();
        let history = self.message_history.read().unwrap();

        MessageBrokerStats {
            total_messages: history.values().map(|v| v.len()).sum::<usize>() as u64,
            active_subscriptions: subscribers.values().map(|v| v.len()).sum(),
            queue_size: queue.len(),
            topics_count: subscribers.len(),
        }
    }

    /// Startet Message Processing Loop
    pub async fn start_processing(&self) {
        let subscribers = self.subscribers.clone();
        let message_queue = self.message_queue.clone();

        tokio::spawn(async move {
            loop {
                // Process messages from queue
                let message = {
                    let mut queue = message_queue.lock().unwrap();
                    queue.pop_front()
                };

                if let Some(message) = message {
                    // Distribute message to subscribers
                    let subscribers = subscribers.read().unwrap();
                    if let Some(handlers) = subscribers.get(&message.topic) {
                        for handler in handlers {
                            handler(&message);
                        }
                    }
                } else {
                    // No messages, wait a bit
                    tokio::time::sleep(tokio::time::Duration::from_millis(10)).await;
                }
            }
        });
    }

    fn generate_message_id(&self) -> String {
        use std::sync::atomic::{AtomicU64, Ordering};
        static COUNTER: AtomicU64 = AtomicU64::new(0);
        
        let id = COUNTER.fetch_add(1, Ordering::Relaxed);
        format!("msg_{}", id)
    }

    async fn distribute_message(&self, message: &Message) {
        let subscribers = self.subscribers.read().unwrap();
        if let Some(handlers) = subscribers.get(&message.topic) {
            for handler in handlers {
                handler(message);
            }
        }
    }

    async fn update_message_history(&self, message: &Message) {
        let mut history = self.message_history.write().unwrap();
        let topic_history = history.entry(message.topic.clone()).or_insert_with(Vec::new);
        
        topic_history.push(message.clone());
        
        // Limit history size
        if topic_history.len() > self.max_history_size {
            topic_history.remove(0);
        }
    }
}

/// Message Broker Manager für zentrale Verwaltung
pub struct MessageBrokerManager {
    broker: Arc<MessageBroker>,
    plugin_permissions: Arc<RwLock<HashMap<String, Vec<String>>>>,
}

impl std::fmt::Debug for MessageBrokerManager {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MessageBrokerManager").finish()
    }
}

impl MessageBrokerManager {
    pub fn new() -> Self {
        let broker = Arc::new(MessageBroker::new());
        
        // Start message processing
        let broker_clone = broker.clone();
        tokio::spawn(async move {
            broker_clone.start_processing().await;
        });

        Self {
            broker,
            plugin_permissions: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Setzt Permissions für ein Plugin
    pub async fn set_plugin_permissions(&self, plugin_id: &str, topics: Vec<String>) {
        let mut permissions = self.plugin_permissions.write().unwrap();
        permissions.insert(plugin_id.to_string(), topics);
    }

    /// Prüft ob ein Plugin Permission für ein Topic hat
    pub async fn has_permission(&self, plugin_id: &str, topic: &str) -> bool {
        let permissions = self.plugin_permissions.read().unwrap();
        if let Some(allowed_topics) = permissions.get(plugin_id) {
            allowed_topics.contains(&topic.to_string())
        } else {
            // Keine Permissions = keine Kommunikation
            false
        }
    }

    /// Publiziert eine Message mit Permission-Check
    pub async fn publish_with_permission(&self, topic: &str, sender_id: &str, payload: Vec<u8>, message_type: MessageType) -> Result<(), String> {
        if !self.has_permission(sender_id, topic).await {
            return Err(format!("Plugin {} has no permission for topic {}", sender_id, topic));
        }

        self.broker.publish(topic, sender_id, payload, message_type).await;
        Ok(())
    }

    /// Subscribiert mit Permission-Check
    pub async fn subscribe_with_permission<F>(&self, topic: &str, plugin_id: &str, handler: F) -> Result<(), String>
    where
        F: Fn(&Message) + Send + Sync + 'static,
    {
        if !self.has_permission(plugin_id, topic).await {
            return Err(format!("Plugin {} has no permission for topic {}", plugin_id, topic));
        }

        self.broker.subscribe(topic, plugin_id, handler).await;
        Ok(())
    }

    /// Gibt den Message Broker zurück
    pub fn get_broker(&self) -> Arc<MessageBroker> {
        self.broker.clone()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_message_publishing() {
        let broker = MessageBroker::new();
        
        // Test message publishing
        broker.publish("test.topic", "plugin1", b"test payload".to_vec(), MessageType::Event).await;
        
        // Verify message was added to history
        let history = broker.get_message_history("test.topic").await;
        assert_eq!(history.len(), 1);
        assert_eq!(history[0].sender_id, "plugin1");
    }

    #[tokio::test]
    async fn test_message_subscription() {
        let broker = MessageBroker::new();
        let received_messages = Arc::new(Mutex::new(Vec::new()));
        let received_clone = received_messages.clone();
        
        // Subscribe to topic
        broker.subscribe("test.topic", "plugin1", move |msg| {
            received_clone.lock().unwrap().push(msg.clone());
        }).await;
        
        // Publish message
        broker.publish("test.topic", "plugin2", b"test payload".to_vec(), MessageType::Event).await;
        
        // Give some time for processing
        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
        
        // Verify message was received
        let received = received_messages.lock().unwrap();
        assert_eq!(received.len(), 1);
        assert_eq!(received[0].sender_id, "plugin2");
    }

    #[tokio::test]
    async fn test_permission_system() {
        let manager = MessageBrokerManager::new();
        
        // Set permissions
        manager.set_plugin_permissions("plugin1", vec!["topic1".to_string(), "topic2".to_string()]).await;
        
        // Test allowed topic
        assert!(manager.has_permission("plugin1", "topic1").await);
        
        // Test disallowed topic
        assert!(!manager.has_permission("plugin1", "topic3").await);
        
        // Test publish with permission
        let result = manager.publish_with_permission("topic1", "plugin1", b"test".to_vec(), MessageType::Event).await;
        assert!(result.is_ok());
        
        // Test publish without permission
        let result = manager.publish_with_permission("topic3", "plugin1", b"test".to_vec(), MessageType::Event).await;
        assert!(result.is_err());
    }
}

