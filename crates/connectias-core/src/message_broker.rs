use std::collections::{HashMap, VecDeque, HashSet};
use std::sync::{Arc, Mutex, RwLock};
use std::time::{SystemTime, UNIX_EPOCH, Duration};
use serde::{Deserialize, Serialize};
use serde_json;
use tokio::time::{interval, timeout};
use tokio::sync::{broadcast, oneshot};
use log::{info, warn, debug, error};
use connectias_ipc::{IPCTransport, IPCMessage};

/// Process Mode für MessageBroker
#[derive(Debug, Clone)]
pub enum ProcessMode {
    /// Alle Plugins im selben Prozess (aktuell)
    SingleProcess,
    /// Jedes Plugin in separatem Prozess
    MultiProcess,
}

/// Message Broker für Inter-Plugin Communication
pub struct MessageBroker {
    subscribers: Arc<RwLock<HashMap<String, Vec<MessageHandler>>>>,
    message_queue: Arc<Mutex<VecDeque<Message>>>,
    message_history: Arc<RwLock<HashMap<String, Vec<Message>>>>,
    max_history_size: usize,
    // Erweiterte Features
    plugin_connections: Arc<RwLock<HashMap<String, PluginConnection>>>,
    message_filters: Arc<RwLock<HashMap<String, MessageFilter>>>,
    rate_limits: Arc<RwLock<HashMap<String, RateLimit>>>,
    broadcast_sender: Arc<broadcast::Sender<Message>>,
    is_running: Arc<Mutex<bool>>,
    // Request-Response Pattern
    pending_requests: Arc<RwLock<HashMap<String, oneshot::Sender<Message>>>>,
    // IPC Support
    ipc_transport: Option<Arc<dyn IPCTransport>>,
    process_mode: ProcessMode,
}

impl std::fmt::Debug for MessageBroker {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MessageBroker")
            .field("max_history_size", &self.max_history_size)
            .finish()
    }
}

impl Clone for MessageBroker {
    fn clone(&self) -> Self {
        Self {
            subscribers: self.subscribers.clone(),
            message_queue: self.message_queue.clone(),
            message_history: self.message_history.clone(),
            max_history_size: self.max_history_size,
            plugin_connections: self.plugin_connections.clone(),
            message_filters: self.message_filters.clone(),
            rate_limits: self.rate_limits.clone(),
            broadcast_sender: self.broadcast_sender.clone(),
            is_running: self.is_running.clone(),
            pending_requests: self.pending_requests.clone(),
            ipc_transport: self.ipc_transport.clone(),
            process_mode: self.process_mode.clone(),
        }
    }
}

/// Message Handler für Plugin-Subscriptions
pub type MessageHandler = Arc<dyn Fn(&Message) + Send + Sync>;

/// Message für Plugin-Communication
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub topic: String,
    pub sender_id: String,
    pub payload: Vec<u8>,
    pub timestamp: i64,
    pub message_id: String,
    pub message_type: MessageType,
    /// FIX BUG 2: Delivery Status um Audit-Trail-Konsistenz sicherzustellen
    /// None = erfolgreich an alle Prozesse verteilt (MultiProcess) oder verteilt (SingleProcess)
    /// Some(error) = lokal verteilt, aber IPC zu anderen Prozessen fehlgeschlagen
    #[serde(skip_serializing_if = "Option::is_none")]
    pub delivery_error: Option<String>,
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
    /// System Message (für interne Kommunikation)
    System,
    /// Heartbeat für Plugin-Health-Checks
    Heartbeat,
    /// Error Message
    Error { error_code: String },
}

/// Plugin Connection Information
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginConnection {
    pub plugin_id: String,
    pub connected_at: i64,
    pub last_heartbeat: i64,
    pub subscribed_topics: HashSet<String>,
    pub message_count: u64,
    pub is_active: bool,
}

/// Message Filter für erweiterte Routing-Logik
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MessageFilter {
    pub filter_id: String,
    pub topic_pattern: String,
    pub sender_pattern: Option<String>,
    pub payload_filter: Option<String>,
    pub action: FilterAction,
}

/// Filter Actions
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum FilterAction {
    /// Message weiterleiten
    Forward,
    /// Message blockieren
    Block,
    /// Message transformieren
    Transform { transform_rule: String },
    /// Message loggen
    Log,
}

/// Rate Limiting für Message-Flood-Schutz
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RateLimit {
    pub plugin_id: String,
    pub max_messages_per_second: u32,
    pub max_messages_per_minute: u32,
    pub current_second_count: u32,
    pub current_minute_count: u32,
    pub last_reset_second: i64,
    pub last_reset_minute: i64,
    pub is_blocked: bool,
}

/// Message Priority für QoS
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, PartialOrd, Ord)]
pub enum MessagePriority {
    Low = 1,
    Normal = 2,
    High = 3,
    Critical = 4,
}

/// Erweiterte Message mit Priority und QoS
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EnhancedMessage {
    pub base_message: Message,
    pub priority: MessagePriority,
    pub ttl: Option<Duration>, // Time To Live
    pub retry_count: u32,
    pub max_retries: u32,
    pub correlation_id: Option<String>,
}

/// Message Subscription für Topic-basierte Kommunikation
pub struct MessageSubscription {
    pub topic: String,
    pub plugin_id: String,
    pub handler: MessageHandler,
}

impl Clone for MessageSubscription {
    fn clone(&self) -> Self {
        // Arc-basierte Handler können jetzt geklont werden
        Self {
            topic: self.topic.clone(),
            plugin_id: self.plugin_id.clone(),
            handler: self.handler.clone(), // Arc kann geklont werden
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
        let (broadcast_sender, _) = broadcast::channel(1000);
        Self {
            subscribers: Arc::new(RwLock::new(HashMap::new())),
            message_queue: Arc::new(Mutex::new(VecDeque::new())),
            message_history: Arc::new(RwLock::new(HashMap::new())),
            max_history_size: 1000,
            plugin_connections: Arc::new(RwLock::new(HashMap::new())),
            message_filters: Arc::new(RwLock::new(HashMap::new())),
            rate_limits: Arc::new(RwLock::new(HashMap::new())),
            broadcast_sender: Arc::new(broadcast_sender),
            is_running: Arc::new(Mutex::new(false)),
            pending_requests: Arc::new(RwLock::new(HashMap::new())),
            ipc_transport: None,
            process_mode: ProcessMode::SingleProcess,
        }
    }
    
    /// Erweitere MessageBroker um IPC-Transport
    pub fn with_ipc_transport(mut self, transport: Arc<dyn IPCTransport>) -> Self {
        self.ipc_transport = Some(transport);
        self.process_mode = ProcessMode::MultiProcess;
        self
    }
    
    /// Setze Process-Mode
    pub fn with_mode(mut self, mode: ProcessMode) -> Self {
        self.process_mode = mode;
        self
    }

    /// Erweiterte Message Broker mit erweiterten Features
    pub fn new_enhanced() -> Self {
        let broker = Self::new();
        info!("🚀 Enhanced Message Broker initialisiert");
        broker
    }

    /// Starte den Message Broker Background Service
    pub async fn start_background_service(&self) {
        let mut is_running = self.is_running.lock().unwrap();
        if *is_running {
            warn!("⚠️ Message Broker Background Service läuft bereits");
            return;
        }
        *is_running = true;
        drop(is_running);

        info!("🔄 Starte Message Broker Background Service...");
        info!("✅ Message Broker Background Service gestartet");
    }

    /// Stoppe den Message Broker Background Service
    pub async fn stop_background_service(&self) {
        let mut is_running = self.is_running.lock().unwrap();
        if !*is_running {
            warn!("⚠️ Message Broker Background Service läuft nicht");
            return;
        }
        *is_running = false;
        drop(is_running);

        info!("🛑 Stoppe Message Broker Background Service...");
        info!("✅ Message Broker Background Service gestoppt");
    }

    /// Publiziert eine Message an ein Topic
    pub async fn publish(&self, topic: &str, sender_id: &str, payload: Vec<u8>, message_type: MessageType) {
        match self.process_mode {
            ProcessMode::SingleProcess => {
                self.publish_in_memory(topic, sender_id, payload, message_type).await;
            },
            ProcessMode::MultiProcess => {
                // In MultiProcess-Modus: Versuche IPC zuerst, aber bei Fehlern
                // dennoch lokale Subscriber benachrichtigen (same-process guarantee)
                let ipc_result = self.publish_via_ipc(topic, sender_id, payload.clone(), message_type.clone()).await;
                
                match ipc_result {
                    Ok(_) => {
                        // IPC publish erfolgreich - lokale Subscriber wurden bereits in publish_via_ipc benachrichtigt
                        debug!("IPC publish successful for topic '{}', sender '{}', type '{:?}'", 
                               topic, sender_id, message_type);
                    },
                    Err(e) => {
                        // IPC-Fehler: Message wurde nicht an entfernte Prozesse geliefert
                        // ABER: Lokale Subscriber müssen trotzdem benachrichtigt werden
                        // (publish-subscribe contract: same-process subscribers always receive)
                        error!("IPC publish failed for topic '{}', sender '{}', type '{:?}': {}. Message NOT delivered to remote processes, but delivering to local subscribers.", 
                               topic, sender_id, message_type, e);
                        
                        // Erstelle Message für lokale Verteilung mit Fehler-Status
                        let message = Message {
                            topic: topic.to_string(),
                            sender_id: sender_id.to_string(),
                            payload: payload.clone(),
                            timestamp: SystemTime::now()
                                .duration_since(UNIX_EPOCH)
                                .unwrap()
                                .as_secs() as i64,
                            message_id: self.generate_message_id(),
                            message_type: message_type.clone(),
                            // FIX BUG 2: Setze delivery_error um zu markieren, dass IPC fehlgeschlagen ist
                            delivery_error: Some(e.to_string()),
                        };
                        
                        // Message ebenfalls in lokale Queue einreihen, um dieselbe Reihenfolge/Verarbeitung
                        // wie im SingleProcess-Pfad sicherzustellen
                        {
                            let mut queue = self.message_queue.lock().unwrap();
                            queue.push_back(message.clone());
                        }

                        // Benachrichtige lokale Subscriber trotz IPC-Fehler
                        // Dies stellt sicher, dass der publish-subscribe contract erfüllt wird
                        self.distribute_message(&message).await;
                        
                        // FIX BUG 2: History wird JETZT MIT FEHLER-STATUS aktualisiert
                        // Der Fehler-Status zeigt klar: Diese Message wurde nur lokal verteilt,
                        // nicht an entfernte Prozesse. Audit-Trails sind konsistent und aussagekräftig.
                        self.update_message_history(&message).await;
                    }
                }
            }
        }
    }
    
    /// INTERN: Bestehende Logik für Single-Process
    async fn publish_in_memory(&self, topic: &str, sender_id: &str, payload: Vec<u8>, message_type: MessageType) {
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
            delivery_error: None,
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
    
    /// INTERN: Neue IPC-Logik für Multi-Process
    async fn publish_via_ipc(&self, topic: &str, sender_id: &str, payload: Vec<u8>, message_type: MessageType) -> Result<(), String> {
        // Prüfe ob IPC Transport verfügbar ist
        let ipc = match &self.ipc_transport {
            Some(ipc) => ipc,
            None => {
                return Err("No IPC transport configured".to_string());
            }
        };
        
        let message_id = self.generate_message_id();
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64;
        
        // Erstelle vollständige Message für History
        let message = Message {
            topic: topic.to_string(),
            sender_id: sender_id.to_string(),
            payload: payload.clone(),
            timestamp,
            message_id: message_id.clone(),
            message_type: message_type.clone(),
            delivery_error: None,
        };
        
        // Serialisiere MessageType für IPC Message
        let message_type_str = serde_json::to_string(&message_type)
            .map_err(|e| format!("Failed to serialize message type: {}", e))?;
        
        // Erstelle IPC Message mit Validierung
        let ipc_msg = IPCMessage::new(
            sender_id.to_string(),
            topic.to_string(),
            payload.clone(),
            timestamp,
            message_id.clone(),
            message_type_str,
        ).map_err(|e| format!("Failed to create IPC message: {}", e))?;
        
        // Sende via IPC - ACHTUNG: History wird erst NACH erfolgreichem Send aktualisiert
        match ipc.send(sender_id, ipc_msg).await {
            Ok(_) => {
                // Nach erfolgreichem IPC Send: Persistiere in History
                self.update_message_history(&message).await;
                
                // Nach erfolgreichem IPC Send: Notify local subscribers
                self.distribute_message(&message).await;
                
                Ok(())
            },
            Err(e) => {
                // IPC Send fehlgeschlagen - KEINE History-Update
                Err(e.to_string())
            }
        }
    }

    /// Subscribiert zu einem Topic
    pub async fn subscribe<F>(&self, topic: &str, _plugin_id: &str, handler: F)
    where
        F: Fn(&Message) + Send + Sync + 'static,
    {
        let mut subscribers = self.subscribers.write().unwrap();
        let handler = Arc::new(handler);

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
        use uuid::Uuid;
        Uuid::new_v4().to_string()
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

// =========================================================================
// ENHANCED MESSAGE BROKER FEATURES
// =========================================================================

impl MessageBroker {
    /// Registriere ein Plugin für erweiterte Features
    pub async fn register_plugin(&self, plugin_id: &str, _permissions: Vec<String>) -> Result<(), String> {
        debug!("📝 Registriere Plugin: {}", plugin_id);
        
        let connection = PluginConnection {
            plugin_id: plugin_id.to_string(),
            connected_at: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
            last_heartbeat: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
            subscribed_topics: HashSet::new(),
            message_count: 0,
            is_active: true,
        };

        // Plugin-Verbindung registrieren
        {
            let mut connections = self.plugin_connections.write().unwrap();
            connections.insert(plugin_id.to_string(), connection);
        }

        // Rate Limiting für Plugin einrichten
        let rate_limit = RateLimit {
            plugin_id: plugin_id.to_string(),
            max_messages_per_second: 100,
            max_messages_per_minute: 1000,
            current_second_count: 0,
            current_minute_count: 0,
            last_reset_second: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
            last_reset_minute: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
            is_blocked: false,
        };

        {
            let mut rate_limits = self.rate_limits.write().unwrap();
            rate_limits.insert(plugin_id.to_string(), rate_limit);
        }

        info!("✅ Plugin registriert: {}", plugin_id);
        Ok(())
    }

    /// Deregistriere ein Plugin
    pub async fn unregister_plugin(&self, plugin_id: &str) -> Result<(), String> {
        debug!("🗑️ Deregistriere Plugin: {}", plugin_id);
        
        // Plugin-Verbindung entfernen
        {
            let mut connections = self.plugin_connections.write().unwrap();
            connections.remove(plugin_id);
        }

        // Rate Limiting entfernen
        {
            let mut rate_limits = self.rate_limits.write().unwrap();
            rate_limits.remove(plugin_id);
        }

        // Alle Subscriptions des Plugins entfernen
        {
            let mut subscribers = self.subscribers.write().unwrap();
            for (_, handlers) in subscribers.iter_mut() {
                handlers.retain(|_| true); // Hier würde die echte Filterung stattfinden
            }
        }

        info!("✅ Plugin deregistriert: {}", plugin_id);
        Ok(())
    }

    /// Erweiterte Message-Publikation mit Rate Limiting und Filtern
    pub async fn publish_enhanced(
        &self,
        topic: &str,
        sender_id: &str,
        payload: Vec<u8>,
        message_type: MessageType,
        priority: MessagePriority,
        ttl: Option<Duration>,
    ) -> Result<String, String> {
        // Atomare Rate-Limiting-Prüfung und -Aktualisierung
        if !self.check_and_consume_rate_limit(sender_id).await {
            return Err("Rate limit exceeded".to_string());
        }

        // Message-Filter anwenden
        let filtered_message = self.apply_message_filters(topic, sender_id, &payload).await;
        if filtered_message.is_none() {
            return Err("Message blocked by filter".to_string());
        }

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
            delivery_error: None,
        };

        // Enhanced Message erstellen
        let enhanced_message = EnhancedMessage {
            base_message: message,
            priority,
            ttl,
            retry_count: 0,
            max_retries: 3,
            correlation_id: None,
        };

        // Message zur Queue hinzufügen (mit Priority)
        {
            let mut queue = self.message_queue.lock().unwrap();
            queue.push_back(enhanced_message.base_message.clone());
        }

        // Message History aktualisieren
        self.update_message_history(&enhanced_message.base_message).await;

        // Message an Subscribers verteilen
        self.distribute_message(&enhanced_message.base_message).await;

        Ok(enhanced_message.base_message.message_id)
    }

    /// Verarbeite eingehende Response-Message
    pub async fn handle_response(&self, response: Message) -> Result<(), String> {
        if let MessageType::Response { request_id } = &response.message_type {
            let mut pending = self.pending_requests.write().unwrap();
            if let Some(sender) = pending.remove(request_id) {
                if let Err(_) = sender.send(response.clone()) {
                    warn!("Failed to send response for request {}", request_id);
                }
            }
        }
        Ok(())
    }

    /// Request-Response Pattern implementieren
    pub async fn request_response(
        &self,
        topic: &str,
        sender_id: &str,
        payload: Vec<u8>,
        timeout: Duration,
    ) -> Result<Message, String> {
        let request_id = self.generate_message_id();
        let correlation_id = request_id.clone();

        // Request Message erstellen
        let request_message = Message {
            topic: topic.to_string(),
            sender_id: sender_id.to_string(),
            payload,
            timestamp: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
            message_id: request_id,
            message_type: MessageType::Request,
            delivery_error: None,
        };

        // Request publizieren
        self.publish_enhanced(
            topic,
            sender_id,
            request_message.payload.clone(),
            MessageType::Request,
            MessagePriority::Normal,
            Some(timeout),
        ).await?;

        // Auf Response warten
        let response = self.wait_for_response(&correlation_id, timeout).await?;
        Ok(response)
    }

    /// Warte auf Response für Request-Response Pattern
    async fn wait_for_response(&self, correlation_id: &str, timeout_duration: Duration) -> Result<Message, String> {
        // Erstelle oneshot channel für die Response
        let (tx, rx) = oneshot::channel();
        
        // Registriere den Request
        {
            let mut pending = self.pending_requests.write().unwrap();
            pending.insert(correlation_id.to_string(), tx);
        }
        
        // Warte auf Response mit Timeout
        match timeout(timeout_duration, rx).await {
            Ok(Ok(response)) => Ok(response),
            Ok(Err(_)) => {
                // Sender wurde dropped, entferne aus pending
                let mut pending = self.pending_requests.write().unwrap();
                pending.remove(correlation_id);
                Err("Response channel closed".to_string())
            }
            Err(_) => {
                // Timeout erreicht, entferne aus pending
                let mut pending = self.pending_requests.write().unwrap();
                pending.remove(correlation_id);
                Err("Request timeout".to_string())
            }
        }
    }

    /// Atomare Rate-Limiting-Prüfung und -Aktualisierung
    async fn check_and_consume_rate_limit(&self, plugin_id: &str) -> bool {
        let mut rate_limits = self.rate_limits.write().unwrap();
        if let Some(rate_limit) = rate_limits.get_mut(plugin_id) {
            if rate_limit.is_blocked {
                return false;
            }
            
            let current_time = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64;
            
            // Second-Reset prüfen und durchführen
            if current_time - rate_limit.last_reset_second >= 1 {
                rate_limit.current_second_count = 0;
                rate_limit.last_reset_second = current_time;
            }
            
            // Minute-Reset prüfen und durchführen
            if current_time - rate_limit.last_reset_minute >= 60 {
                rate_limit.current_minute_count = 0;
                rate_limit.last_reset_minute = current_time;
                rate_limit.is_blocked = false;
            }
            
            // Prüfe Limits
            if rate_limit.current_second_count >= rate_limit.max_messages_per_second {
                return false;
            }
            
            if rate_limit.current_minute_count >= rate_limit.max_messages_per_minute {
                rate_limit.is_blocked = true;
                return false;
            }
            
            // Inkrementiere Zähler
            rate_limit.current_second_count += 1;
            rate_limit.current_minute_count += 1;
        }
        
        true
    }

    /// Message-Filter anwenden
    async fn apply_message_filters(&self, topic: &str, sender_id: &str, payload: &[u8]) -> Option<Vec<u8>> {
        let filters = self.message_filters.read().unwrap();
        
        for (_, filter) in filters.iter() {
            // Topic-Pattern prüfen
            if !self.matches_pattern(topic, &filter.topic_pattern) {
                continue;
            }
            
            // Sender-Pattern prüfen
            if let Some(sender_pattern) = &filter.sender_pattern {
                if !self.matches_pattern(sender_id, sender_pattern) {
                    continue;
                }
            }
            
            // Payload-Filter prüfen
            if let Some(payload_filter) = &filter.payload_filter {
                if !payload.windows(payload_filter.len()).any(|window| window == payload_filter.as_bytes()) {
                    continue;
                }
            }
            
            // Filter-Action ausführen
            match &filter.action {
                FilterAction::Block => return None,
                FilterAction::Forward => return Some(payload.to_vec()),
                FilterAction::Transform { transform_rule: _ } => {
                    // Hier würde die echte Transformation stattfinden
                    return Some(payload.to_vec());
                }
                FilterAction::Log => {
                    info!("📝 Message gefiltert: topic={}, sender={}", topic, sender_id);
                    return Some(payload.to_vec());
                }
            }
        }
        
        Some(payload.to_vec())
    }

    /// Pattern-Matching für Filter
    fn matches_pattern(&self, text: &str, pattern: &str) -> bool {
        // Einfache Wildcard-Implementierung
        if pattern.contains('*') {
            let parts: Vec<&str> = pattern.split('*').collect();
            if parts.len() == 2 {
                return text.starts_with(parts[0]) && text.ends_with(parts[1]);
            }
            if parts.len() == 1 {
                return text.starts_with(parts[0]);
            }
        }
        
        text == pattern
    }

    /// Heartbeat Monitor für Plugin-Health-Checks
    async fn heartbeat_monitor(&self) {
        let mut interval = interval(Duration::from_secs(30));
        
        loop {
            interval.tick().await;
            
            let current_time = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64;
            
            let mut connections = self.plugin_connections.write().unwrap();
            for (plugin_id, connection) in connections.iter_mut() {
                // Prüfe Heartbeat-Timeout (5 Minuten)
                if current_time - connection.last_heartbeat > 300 {
                    warn!("💔 Plugin {} heartbeat timeout", plugin_id);
                    connection.is_active = false;
                }
            }
        }
    }

    /// Rate Limit Reset Background Task
    async fn rate_limit_reset(&self) {
        let mut interval = interval(Duration::from_secs(60));
        
        loop {
            interval.tick().await;
            
            let current_time = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64;
            
            let mut rate_limits = self.rate_limits.write().unwrap();
            for (_, rate_limit) in rate_limits.iter_mut() {
                // Minute-Reset
                if current_time - rate_limit.last_reset_minute >= 60 {
                    rate_limit.current_minute_count = 0;
                    rate_limit.last_reset_minute = current_time;
                    rate_limit.is_blocked = false;
                }
            }
        }
    }

    /// Message Processor Background Task
    async fn message_processor(&self) {
        let mut interval = interval(Duration::from_millis(100));
        
        loop {
            interval.tick().await;
            
            // Verarbeite Messages aus der Queue
            let message = {
                let mut queue = self.message_queue.lock().unwrap();
                queue.pop_front()
            };
            
            if let Some(message) = message {
                // Hier würde die echte Message-Verarbeitung stattfinden
                debug!("📨 Verarbeite Message: {}", message.message_id);
            }
        }
    }

    /// Füge Message-Filter hinzu
    pub async fn add_message_filter(&self, filter: MessageFilter) -> Result<(), String> {
        debug!("🔍 Füge Message-Filter hinzu: {}", filter.filter_id);
        
        let mut filters = self.message_filters.write().unwrap();
        filters.insert(filter.filter_id.clone(), filter);
        
        info!("✅ Message-Filter hinzugefügt");
        Ok(())
    }

    /// Entferne Message-Filter
    pub async fn remove_message_filter(&self, filter_id: &str) -> Result<(), String> {
        debug!("🗑️ Entferne Message-Filter: {}", filter_id);
        
        let mut filters = self.message_filters.write().unwrap();
        filters.remove(filter_id);
        
        info!("✅ Message-Filter entfernt");
        Ok(())
    }

    /// Hole Plugin-Verbindungs-Status
    pub async fn get_plugin_connection_status(&self, plugin_id: &str) -> Option<PluginConnection> {
        let connections = self.plugin_connections.read().unwrap();
        connections.get(plugin_id).cloned()
    }

    /// Hole alle aktiven Plugin-Verbindungen
    pub async fn get_active_connections(&self) -> Vec<PluginConnection> {
        let connections = self.plugin_connections.read().unwrap();
        connections.values()
            .filter(|conn| conn.is_active)
            .cloned()
            .collect()
    }

    /// Sende Heartbeat für Plugin
    pub async fn send_heartbeat(&self, plugin_id: &str) -> Result<(), String> {
        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64;
        
        let mut connections = self.plugin_connections.write().unwrap();
        if let Some(connection) = connections.get_mut(plugin_id) {
            connection.last_heartbeat = current_time;
            connection.is_active = true;
            debug!("💓 Heartbeat von Plugin {} empfangen", plugin_id);
        } else {
            return Err(format!("Plugin {} nicht registriert", plugin_id));
        }
        
        Ok(())
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
