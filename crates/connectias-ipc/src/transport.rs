use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH, Duration};
use uuid::Uuid;

/// Maximale Größe einer IPC-Nachricht in Bytes (10MB)
pub const MAX_MESSAGE_SIZE: usize = 10 * 1024 * 1024;

/// Minimale und maximale Timestamp-Grenzen (Jahr 2000 bis 2100)
const MIN_TIMESTAMP: i64 = 946_684_800; // 2000-01-01 00:00:00 UTC
const MAX_TIMESTAMP: i64 = 4_102_444_800; // 2100-01-01 00:00:00 UTC

/// IPC-Nachricht für die Kommunikation zwischen Plugins und dem Host-System
/// 
/// Diese Struktur repräsentiert eine vollständige Nachricht, die über IPC-Transport
/// zwischen verschiedenen Prozessen übertragen wird. Sie enthält alle notwendigen
/// Metadaten für die Nachrichtenverarbeitung und -routing.
/// 
/// # Validierung
/// 
/// IPCMessage sollte über IPCMessage::new() oder IPCMessage::create() erstellt werden
/// für automatische Validierung:
/// - payload.len() <= MAX_MESSAGE_SIZE
/// - message_id ist non-empty und UUID-format-kompatibel
/// - timestamp ist innerhalb akzeptabler Grenzen (2000-2100)
/// - plugin_id und topic sind non-empty
/// 
/// **WARNUNG**: Felder sind jetzt private - verwende IPCMessage::new() oder IPCMessage::create()
/// für Validierung. Direkte Konstruktion ist nicht mehr möglich.
#[derive(Debug, Clone, Serialize)]
pub struct IPCMessage {
    /// Eindeutige ID des sendenden Plugins
    plugin_id: String,
    /// Topic/Channel für die Nachricht (z.B. "plugin.events", "system.alerts")
    topic: String,
    /// Binäre Nutzdaten der Nachricht
    payload: Vec<u8>,
    /// Unix-Timestamp der Nachrichtenerstellung
    timestamp: i64,
    /// Eindeutige ID der Nachricht für Tracking und Deduplication (muss UUID sein)
    message_id: String,
    /// Typ der Nachricht (z.B. "Request", "Response", "Event", "Broadcast")
    message_type: String,
}

/// Validierungsfehler für IPCMessage
#[derive(Debug)]
pub enum ValidationError {
    PayloadTooLarge { size: usize, max: usize },
    EmptyPluginId,
    EmptyTopic,
    EmptyMessageId,
    InvalidMessageId(String),
    InvalidTimestamp { timestamp: i64, min: i64, max: i64 },
}

impl std::fmt::Display for ValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ValidationError::PayloadTooLarge { size, max } => {
                write!(f, "Payload too large: {} > {}", size, max)
            }
            ValidationError::EmptyPluginId => write!(f, "plugin_id cannot be empty"),
            ValidationError::EmptyTopic => write!(f, "topic cannot be empty"),
            ValidationError::EmptyMessageId => write!(f, "message_id cannot be empty"),
            ValidationError::InvalidMessageId(id) => write!(f, "Invalid message_id format: {}", id),
            ValidationError::InvalidTimestamp { timestamp, min, max } => {
                write!(f, "Timestamp out of bounds: {} (valid range: {} - {})", timestamp, min, max)
            }
        }
    }
}

impl std::error::Error for ValidationError {}

/// Helper-Struktur für Deserialisierung ohne Validierung
#[derive(Deserialize)]
struct IPCMessageHelper {
    plugin_id: String,
    topic: String,
    payload: Vec<u8>,
    timestamp: i64,
    message_id: String,
    message_type: String,
}

impl<'de> Deserialize<'de> for IPCMessage {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        let helper = IPCMessageHelper::deserialize(deserializer)?;
        
        // Validiere durch Aufruf von IPCMessage::new()
        IPCMessage::new(
            helper.plugin_id,
            helper.topic,
            helper.payload,
            helper.timestamp,
            helper.message_id,
            helper.message_type,
        ).map_err(|e| serde::de::Error::custom(format!("IPCMessage validation failed: {}", e)))
    }
}

impl IPCMessage {
    /// Erstellt eine neue validierte IPCMessage
    /// 
    /// # Validierung
    /// 
    /// - payload.len() <= MAX_MESSAGE_SIZE
    /// - message_id ist non-empty und UUID-format-kompatibel (falls UUID erwartet)
    /// - timestamp ist innerhalb akzeptabler Grenzen (2000-2100)
    /// - plugin_id und topic sind non-empty
    pub fn new(
        plugin_id: String,
        topic: String,
        payload: Vec<u8>,
        timestamp: i64,
        message_id: String,
        message_type: String,
    ) -> Result<Self, ValidationError> {
        // Validiere payload size
        if payload.len() > MAX_MESSAGE_SIZE {
            return Err(ValidationError::PayloadTooLarge {
                size: payload.len(),
                max: MAX_MESSAGE_SIZE,
            });
        }
        
        // Validiere plugin_id
        if plugin_id.is_empty() {
            return Err(ValidationError::EmptyPluginId);
        }
        
        // Validiere topic
        if topic.is_empty() {
            return Err(ValidationError::EmptyTopic);
        }
        
        // Validiere message_id
        if message_id.is_empty() {
            return Err(ValidationError::EmptyMessageId);
        }
        
        // Validiere message_id als UUID (strict) - muss mit generate_message_id() kompatibel sein
        if Uuid::parse_str(&message_id).is_err() {
            return Err(ValidationError::InvalidMessageId(message_id.clone()));
        }
        
        // Validiere timestamp
        if timestamp < MIN_TIMESTAMP || timestamp > MAX_TIMESTAMP {
            return Err(ValidationError::InvalidTimestamp {
                timestamp,
                min: MIN_TIMESTAMP,
                max: MAX_TIMESTAMP,
            });
        }
        
        Ok(Self {
            plugin_id,
            topic,
            payload,
            timestamp,
            message_id,
            message_type,
        })
    }
    
    /// Erstellt eine IPCMessage mit automatischem Timestamp und Message-ID
    pub fn create(
        plugin_id: String,
        topic: String,
        payload: Vec<u8>,
        message_type: String,
    ) -> Result<Self, ValidationError> {
        let timestamp = match SystemTime::now().duration_since(UNIX_EPOCH) {
            Ok(duration) => duration.as_secs() as i64,
            Err(_) => {
                return Err(ValidationError::InvalidTimestamp {
                    timestamp: 0,
                    min: MIN_TIMESTAMP,
                    max: MAX_TIMESTAMP,
                });
            }
        };
        
        let message_id = Uuid::new_v4().to_string();
        
        Self::new(plugin_id, topic, payload, timestamp, message_id, message_type)
    }
    
    // Accessor-Methoden für read-only Zugriff
    pub fn plugin_id(&self) -> &str {
        &self.plugin_id
    }
    
    pub fn topic(&self) -> &str {
        &self.topic
    }
    
    pub fn payload(&self) -> &[u8] {
        &self.payload
    }
    
    pub fn timestamp(&self) -> i64 {
        self.timestamp
    }
    
    pub fn message_id(&self) -> &str {
        &self.message_id
    }
    
    pub fn message_type(&self) -> &str {
        &self.message_type
    }
}

/// Trait für IPC-Transport-Implementierungen
/// 
/// Dieser Trait definiert die Schnittstelle für verschiedene IPC-Transport-Mechanismen
/// wie Named Pipes (Windows), Unix Sockets (Linux/macOS) oder andere Kommunikationskanäle.
/// 
/// # Implementierungen
/// 
/// - `NamedPipeTransport`: Windows Named Pipes
/// - `UnixSocketTransport`: Unix Domain Sockets
/// 
/// # Thread Safety
/// 
/// Alle Implementierungen müssen `Send + Sync` sein, um in multi-threaded Umgebungen
/// sicher verwendet werden zu können.
/// 
/// # Error Handling
/// 
/// Alle Methoden geben `Result<(), IPCError>` zurück, um Fehlerbehandlung zu ermöglichen.
/// Fehler können auftreten bei:
/// - Netzwerk-/Socket-Fehlern
/// - Serialisierungs-/Deserialisierungsfehlern
/// - Verbindungsfehlern
/// - Nachrichtengrößen-Limits
#[async_trait]
pub trait IPCTransport: Send + Sync {
    /// Sendet eine IPC-Nachricht an einen Zielprozess
    /// 
    /// # Parameter
    /// 
    /// * `target` - Ziel-ID oder Pfad für die Nachricht (kann je nach Transport variieren)
    /// * `msg` - Die zu sendende IPC-Nachricht
    /// 
    /// # Rückgabe
    /// 
    /// * `Ok(())` - Nachricht erfolgreich gesendet
    /// * `Err(IPCError)` - Fehler beim Senden (z.B. Verbindungsfehler, Serialisierungsfehler)
    /// 
    /// # Beispiele
    /// 
    /// ```rust,no_run
    /// # use connectias_ipc::{IPCTransport, IPCMessage};
    /// # async fn example(transport: impl IPCTransport) -> Result<(), Box<dyn std::error::Error>> {
    /// // Erstelle validierte IPCMessage - Felder sind privat, direkte Konstruktion nicht möglich
    /// let msg = IPCMessage::new(
    ///     "my_plugin".to_string(),
    ///     "events".to_string(),
    ///     b"Hello World".to_vec(),
    ///     1234567890,
    ///     "550e8400-e29b-41d4-a716-446655440000".to_string(), // UUID erforderlich
    ///     "Event".to_string(),
    /// ).expect("Failed to create IPC message");
    /// 
    /// transport.send("target_process", msg).await?;
    /// # Ok(())
    /// # }
    /// ```
    async fn send(&self, target: &str, msg: IPCMessage) -> Result<(), crate::IPCError>;
    
    /// Empfängt eine IPC-Nachricht vom Transport
    /// 
    /// Diese Methode blockiert, bis eine Nachricht verfügbar ist oder ein Fehler auftritt.
    /// 
    /// # Rückgabe
    /// 
    /// * `Ok(IPCMessage)` - Erfolgreich empfangene Nachricht
    /// * `Err(IPCError)` - Fehler beim Empfangen (z.B. Verbindungsfehler, Deserialisierungsfehler)
    /// 
    /// # Beispiele
    /// 
    /// ```rust,no_run
    /// # use connectias_ipc::IPCTransport;
    /// # async fn example(transport: impl IPCTransport) -> Result<(), Box<dyn std::error::Error>> {
    /// let msg = transport.receive().await?;
    /// println!("Received message from {}: {}", msg.plugin_id(), msg.topic());
    /// # Ok(())
    /// # }
    /// ```
    async fn receive(&self) -> Result<IPCMessage, crate::IPCError>;
    
    /// Empfängt eine IPC-Nachricht mit Timeout
    /// 
    /// Diese Methode blockiert bis zu `timeout` Dauer, bis eine Nachricht verfügbar ist.
    /// 
    /// # Parameter
    /// 
    /// * `timeout` - Maximale Wartezeit bis eine Nachricht empfangen wird
    /// 
    /// # Rückgabe
    /// 
    /// * `Ok(Some(IPCMessage))` - Erfolgreich empfangene Nachricht
    /// * `Ok(None)` - Timeout erreicht, keine Nachricht empfangen
    /// * `Err(IPCError)` - Fehler beim Empfangen
    /// 
    /// # Beispiele
    /// 
    /// ```rust,no_run
    /// # use connectias_ipc::IPCTransport;
    /// # use std::time::Duration;
    /// # async fn example(transport: impl IPCTransport) -> Result<(), Box<dyn std::error::Error>> {
    /// match transport.try_receive(Duration::from_secs(5)).await? {
    ///     Some(msg) => println!("Received: {}", msg.topic()),
    ///     None => println!("Timeout - no message received"),
    /// }
    /// # Ok(())
    /// # }
    /// ```
    async fn try_receive(&self, timeout: Duration) -> Result<Option<IPCMessage>, crate::IPCError>;
    
    /// Verbindet sich mit einem IPC-Endpunkt
    /// 
    /// # Parameter
    /// 
    /// * `path` - Pfad oder Name des IPC-Endpunkts (z.B. Socket-Pfad, Pipe-Name)
    /// 
    /// # Rückgabe
    /// 
    /// * `Ok(())` - Erfolgreich verbunden
    /// * `Err(IPCError)` - Verbindungsfehler
    /// 
    /// # Beispiele
    /// 
    /// ```rust,no_run
    /// # use connectias_ipc::IPCTransport;
    /// # async fn example(transport: impl IPCTransport) -> Result<(), Box<dyn std::error::Error>> {
    /// transport.connect("/tmp/connectias.sock").await?;
    /// # Ok(())
    /// # }
    /// ```
    async fn connect(&self, path: &str) -> Result<(), crate::IPCError>;
    
    /// Startet das Lauschen auf eingehende Verbindungen
    /// 
    /// Diese Methode richtet den Transport als Server ein und wartet auf eingehende
    /// Verbindungen von Clients.
    /// 
    /// # Parameter
    /// 
    /// * `path` - Pfad oder Name des IPC-Endpunkts zum Lauschen
    /// 
    /// # Rückgabe
    /// 
    /// * `Ok(())` - Erfolgreich gestartet und wartet auf Verbindungen
    /// * `Err(IPCError)` - Fehler beim Starten des Servers
    /// 
    /// # Beispiele
    /// 
    /// ```rust,no_run
    /// # use connectias_ipc::IPCTransport;
    /// # async fn example(transport: impl IPCTransport) -> Result<(), Box<dyn std::error::Error>> {
    /// transport.listen("/tmp/connectias.sock").await?;
    /// // Transport wartet jetzt auf eingehende Verbindungen
    /// # Ok(())
    /// # }
    /// ```
    async fn listen(&self, path: &str) -> Result<(), crate::IPCError>;
    
    /// Trennt die IPC-Verbindung
    /// 
    /// Schließt alle offenen Verbindungen und gibt Ressourcen frei.
    /// 
    /// # Rückgabe
    /// 
    /// * `Ok(())` - Erfolgreich getrennt
    /// * `Err(IPCError)` - Fehler beim Trennen (z.B. beim Schließen von Handles)
    /// 
    /// # Beispiele
    /// 
    /// ```rust,no_run
    /// # use connectias_ipc::IPCTransport;
    /// # async fn example(transport: impl IPCTransport) -> Result<(), Box<dyn std::error::Error>> {
    /// transport.disconnect().await?;
    /// # Ok(())
    /// # }
    /// ```
    async fn disconnect(&self) -> Result<(), crate::IPCError>;
}
