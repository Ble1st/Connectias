use std::collections::HashMap;
use std::sync::Arc;
use connectias_storage::Database;
use connectias_security::threat_detection::{ThreatAssessment, AlertServiceTrait};
use connectias_api::PluginError;
use serde::{Deserialize, Serialize};
use chrono::{DateTime, Utc};
use thiserror::Error;
use async_trait::async_trait;

/// Alert Service für Security-Alerts und Event-Logging
#[derive(Clone)]
pub struct AlertService {
    #[allow(dead_code)]
    database: Arc<Database>,
    alert_history: Arc<std::sync::RwLock<HashMap<String, Vec<SecurityAlert>>>>,
    notification_channels: Arc<std::sync::RwLock<Vec<NotificationChannel>>>,
}

/// Security Alert
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityAlert {
    pub id: String,
    pub plugin_id: String,
    pub alert_type: AlertType,
    pub severity: AlertSeverity,
    pub message: String,
    pub timestamp: DateTime<Utc>,
    pub threat_score: f64,
    pub context: HashMap<String, String>,
    pub resolved: bool,
    pub resolution_notes: Option<String>,
}

/// Alert Types
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum AlertType {
    ThreatDetected,
    PermissionViolation,
    ResourceLimitExceeded,
    SuspiciousActivity,
    PluginCrash,
    SecurityViolation,
    PerformanceAnomaly,
    SystemError,
}

/// Alert Severity Levels
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, PartialOrd, Ord)]
pub enum AlertSeverity {
    Low = 1,
    Medium = 2,
    High = 3,
    Critical = 4,
}

impl std::fmt::Display for AlertSeverity {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AlertSeverity::Low => write!(f, "Low"),
            AlertSeverity::Medium => write!(f, "Medium"),
            AlertSeverity::High => write!(f, "High"),
            AlertSeverity::Critical => write!(f, "Critical"),
        }
    }
}

/// Security Event
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityEvent {
    pub event_id: String,
    pub plugin_id: String,
    pub event_type: String,
    pub description: String,
    pub timestamp: DateTime<Utc>,
    pub metadata: HashMap<String, String>,
}

/// Notification Channel
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NotificationChannel {
    Database,
    LogFile,
    Email { recipient: String },
    Webhook { url: String },
    Console,
}

/// Alert Service Errors
#[derive(Debug, Error)]
pub enum AlertError {
    #[error("Database error: {message}")]
    DatabaseError { message: String },
    #[error("Alert not found: {alert_id}")]
    AlertNotFound { alert_id: String },
    #[error("Invalid alert severity: {severity}")]
    InvalidSeverity { severity: String },
    #[error("Notification failed: {channel:?}")]
    NotificationFailed { channel: NotificationChannel },
    #[error("Alert creation failed: {reason}")]
    AlertCreationFailed { reason: String },
}

impl From<AlertError> for PluginError {
    fn from(err: AlertError) -> Self {
        match err {
            AlertError::DatabaseError { message } => {
                PluginError::ExecutionFailed(format!("Database error: {}", message))
            },
            AlertError::AlertNotFound { alert_id } => {
                PluginError::NotFound(format!("Alert not found: {}", alert_id))
            },
            AlertError::InvalidSeverity { severity } => {
                PluginError::ExecutionFailed(format!("Invalid severity: {}", severity))
            },
            AlertError::NotificationFailed { channel } => {
                PluginError::ExecutionFailed(format!("Notification failed for channel: {:?}", channel))
            },
            AlertError::AlertCreationFailed { reason } => {
                PluginError::ExecutionFailed(format!("Alert creation failed: {}", reason))
            },
        }
    }
}

impl AlertService {
    /// Erstellt einen neuen AlertService
    pub fn new(database: Arc<Database>) -> Self {
        Self {
            database,
            alert_history: Arc::new(std::sync::RwLock::new(HashMap::new())),
            notification_channels: Arc::new(std::sync::RwLock::new(vec![
                NotificationChannel::Database,
                NotificationChannel::LogFile,
                NotificationChannel::Console,
            ])),
        }
    }

    /// Sendet einen Security Alert
    pub async fn send_security_alert(&self, assessment: &ThreatAssessment) -> Result<String, AlertError> {
        let alert_id = format!("alert_{}", uuid::Uuid::new_v4());
        
        let severity = match assessment.threat_score {
            score if score >= 0.8 => AlertSeverity::Critical,
            score if score >= 0.6 => AlertSeverity::High,
            score if score >= 0.4 => AlertSeverity::Medium,
            _ => AlertSeverity::Low,
        };

        let alert = SecurityAlert {
            id: alert_id.clone(),
            plugin_id: assessment.plugin_id.clone(),
            alert_type: AlertType::ThreatDetected,
            severity,
            message: format!("Threat detected in plugin {}: score {}", assessment.plugin_id, assessment.threat_score),
            timestamp: Utc::now(),
            threat_score: assessment.threat_score,
            context: HashMap::new(),
            resolved: false,
            resolution_notes: None,
        };

        // Speichere Alert in Database
        self.save_alert_to_database(&alert).await?;
        
        // Speichere Alert in History
        self.add_alert_to_history(&alert).await;
        
        // Sende Notifications
        self.send_notifications(&alert).await?;
        
        Ok(alert_id)
    }

    /// Loggt ein Security Event
    pub async fn log_security_event(&self, event: SecurityEvent) -> Result<(), AlertError> {
        // Speichere Event in Database
        self.save_event_to_database(&event).await?;
        
        // Log Event
        log::info!("Security Event: {} - {} - {}", 
            event.event_type, 
            event.plugin_id, 
            event.description
        );
        
        Ok(())
    }

    /// Erstellt einen Alert für Permission Violation
    pub async fn create_permission_alert(&self, plugin_id: &str, permission: &str, action: &str) -> Result<String, AlertError> {
        let alert_id = format!("perm_alert_{}", uuid::Uuid::new_v4());
        
        let alert = SecurityAlert {
            id: alert_id.clone(),
            plugin_id: plugin_id.to_string(),
            alert_type: AlertType::PermissionViolation,
            severity: AlertSeverity::High,
            message: format!("Permission violation: Plugin {} attempted {} without {} permission", 
                plugin_id, action, permission),
            timestamp: Utc::now(),
            threat_score: 0.7, // High threat for permission violations
            context: HashMap::from([
                ("permission".to_string(), permission.to_string()),
                ("action".to_string(), action.to_string()),
            ]),
            resolved: false,
            resolution_notes: None,
        };

        self.save_alert_to_database(&alert).await?;
        self.add_alert_to_history(&alert).await;
        self.send_notifications(&alert).await?;
        
        Ok(alert_id)
    }

    /// Erstellt einen Alert für Resource Limit Exceeded
    pub async fn create_resource_alert(&self, plugin_id: &str, resource_type: &str, usage: f64, limit: f64) -> Result<String, AlertError> {
        let alert_id = format!("resource_alert_{}", uuid::Uuid::new_v4());
        
        let alert = SecurityAlert {
            id: alert_id.clone(),
            plugin_id: plugin_id.to_string(),
            alert_type: AlertType::ResourceLimitExceeded,
            severity: AlertSeverity::Medium,
            message: format!("Resource limit exceeded: Plugin {} used {:.2}% of {} (limit: {:.2}%)", 
                plugin_id, usage, resource_type, limit),
            timestamp: Utc::now(),
            threat_score: 0.5,
            context: HashMap::from([
                ("resource_type".to_string(), resource_type.to_string()),
                ("usage".to_string(), usage.to_string()),
                ("limit".to_string(), limit.to_string()),
            ]),
            resolved: false,
            resolution_notes: None,
        };

        self.save_alert_to_database(&alert).await?;
        self.add_alert_to_history(&alert).await;
        self.send_notifications(&alert).await?;
        
        Ok(alert_id)
    }

    /// Holt alle Alerts für ein Plugin
    pub async fn get_plugin_alerts(&self, plugin_id: &str) -> Result<Vec<SecurityAlert>, AlertError> {
        let history = match self.alert_history.read() {
            Ok(history) => history,
            Err(_) => {
                log::warn!("Alert history lock poisoned, returning empty list");
                return Ok(Vec::new());
            }
        };

        let plugin_alerts = history.get(plugin_id)
            .map(|alerts| alerts.clone())
            .unwrap_or_default();

        Ok(plugin_alerts)
    }

    /// Holt alle ungelösten Alerts
    pub async fn get_unresolved_alerts(&self) -> Result<Vec<SecurityAlert>, AlertError> {
        let history = match self.alert_history.read() {
            Ok(history) => history,
            Err(_) => {
                log::warn!("Alert history lock poisoned, returning empty list");
                return Ok(Vec::new());
            }
        };

        let mut unresolved = Vec::new();
        for alerts in history.values() {
            for alert in alerts {
                if !alert.resolved {
                    unresolved.push(alert.clone());
                }
            }
        }

        // Sortiere nach Severity (Critical zuerst)
        unresolved.sort_by(|a, b| b.severity.cmp(&a.severity));

        Ok(unresolved)
    }

    /// Markiert einen Alert als gelöst
    pub async fn resolve_alert(&self, alert_id: &str, resolution_notes: Option<String>) -> Result<(), AlertError> {
        let mut history = match self.alert_history.write() {
            Ok(history) => history,
            Err(_) => {
                log::warn!("Alert history lock poisoned, cannot resolve alert");
                return Err(AlertError::AlertCreationFailed {
                    reason: "Alert history lock poisoned".to_string()
                });
            }
        };

        for alerts in history.values_mut() {
            if let Some(alert) = alerts.iter_mut().find(|a| a.id == alert_id) {
                alert.resolved = true;
                alert.resolution_notes = resolution_notes;
                return Ok(());
            }
        }

        Err(AlertError::AlertNotFound { alert_id: alert_id.to_string() })
    }

    /// Fügt einen Notification Channel hinzu
    pub async fn add_notification_channel(&self, channel: NotificationChannel) -> Result<(), AlertError> {
        let mut channels = match self.notification_channels.write() {
            Ok(channels) => channels,
            Err(_) => {
                log::warn!("Notification channels lock poisoned, cannot add channel");
                return Err(AlertError::AlertCreationFailed {
                    reason: "Notification channels lock poisoned".to_string()
                });
            }
        };

        channels.push(channel);
        Ok(())
    }

    /// Entfernt einen Notification Channel
    pub async fn remove_notification_channel(&self, channel: &NotificationChannel) -> Result<(), AlertError> {
        let mut channels = match self.notification_channels.write() {
            Ok(channels) => channels,
            Err(_) => {
                log::warn!("Notification channels lock poisoned, cannot remove channel");
                return Err(AlertError::AlertCreationFailed {
                    reason: "Notification channels lock poisoned".to_string()
                });
            }
        };

        channels.retain(|c| c != channel);
        Ok(())
    }

    /// Startet kontinuierliche Alert-Überwachung
    pub async fn start_monitoring(&self) {
        let alert_service = self.clone();
        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(std::time::Duration::from_secs(60));
            
            loop {
                interval_timer.tick().await;
                
                // Überwache ungelöste Alerts
                if let Ok(unresolved_alerts) = alert_service.get_unresolved_alerts().await {
                    if !unresolved_alerts.is_empty() {
                        log::warn!("{} ungelöste Security-Alerts gefunden", unresolved_alerts.len());
                    }
                }
            }
        });
    }

    /// Generiert einen Security Report
    pub async fn generate_security_report(&self, hours: u64) -> Result<HashMap<String, serde_json::Value>, AlertError> {
        let cutoff_time = Utc::now() - chrono::Duration::hours(hours as i64);

        let history = match self.alert_history.read() {
            Ok(history) => history,
            Err(_) => {
                log::warn!("Alert history lock poisoned, returning empty report");
                return Ok(HashMap::new());
            }
        };
        
        let mut report = HashMap::new();
        let mut total_alerts = 0;
        let mut critical_alerts = 0;
        let mut resolved_alerts = 0;
        
        for (plugin_id, alerts) in history.iter() {
            let recent_alerts: Vec<&SecurityAlert> = alerts.iter()
                .filter(|alert| alert.timestamp >= cutoff_time)
                .collect();
            
            if !recent_alerts.is_empty() {
                let plugin_report = serde_json::json!({
                    "plugin_id": plugin_id,
                    "alert_count": recent_alerts.len(),
                    "critical_count": recent_alerts.iter().filter(|a| a.severity == AlertSeverity::Critical).count(),
                    "resolved_count": recent_alerts.iter().filter(|a| a.resolved).count(),
                    "unresolved_count": recent_alerts.iter().filter(|a| !a.resolved).count(),
                });
                
                report.insert(plugin_id.clone(), plugin_report);
                
                total_alerts += recent_alerts.len();
                critical_alerts += recent_alerts.iter().filter(|a| a.severity == AlertSeverity::Critical).count();
                resolved_alerts += recent_alerts.iter().filter(|a| a.resolved).count();
            }
        }
        
        // Füge Summary hinzu
        report.insert("summary".to_string(), serde_json::json!({
            "total_alerts": total_alerts,
            "critical_alerts": critical_alerts,
            "resolved_alerts": resolved_alerts,
            "unresolved_alerts": total_alerts - resolved_alerts,
            "time_period_hours": hours,
        }));
        
        Ok(report)
    }

    // Private Helper Methods

    async fn save_alert_to_database(&self, alert: &SecurityAlert) -> Result<(), AlertError> {
        // In einer echten Implementierung würde hier die Alert-Daten in die Database gespeichert
        // Für jetzt simulieren wir das
        log::info!("Saving alert to database: {}", alert.id);
        Ok(())
    }

    async fn get_alerts_by_plugin(&self, plugin_id: &str) -> Result<Vec<SecurityAlert>, AlertError> {
        let history = match self.alert_history.read() {
            Ok(history) => history,
            Err(_) => {
                log::warn!("Alert history lock poisoned, returning empty list");
                return Ok(Vec::new());
            }
        };

        let alerts = history.get(plugin_id)
            .map(|alerts| alerts.clone())
            .unwrap_or_default();

        Ok(alerts)
    }

    async fn save_event_to_database(&self, event: &SecurityEvent) -> Result<(), AlertError> {
        // In einer echten Implementierung würde hier das Event in die Database gespeichert
        log::info!("Saving security event to database: {}", event.event_id);
        Ok(())
    }

    async fn add_alert_to_history(&self, alert: &SecurityAlert) {
        let mut history = self.alert_history.write().unwrap_or_else(|poisoned| {
            log::warn!("Alert history lock poisoned, recovering existing data");
            poisoned.into_inner()
        });
        
        history.entry(alert.plugin_id.clone())
            .or_insert_with(Vec::new)
            .push(alert.clone());
    }

    async fn send_notifications(&self, alert: &SecurityAlert) -> Result<(), AlertError> {
        let channels = match self.notification_channels.read() {
            Ok(channels) => channels,
            Err(_) => {
                log::warn!("Notification channels lock poisoned, using default channels");
                return Ok(());
            }
        };
        
        for channel in channels.iter() {
            match channel {
                NotificationChannel::Console => {
                    log::error!("SECURITY ALERT [{:?}]: {} - {}", 
                        alert.severity, 
                        alert.plugin_id, 
                        alert.message
                    );
                },
                NotificationChannel::LogFile => {
                    log::error!("SECURITY ALERT [{:?}]: {} - {} - Context: {:?}", 
                        alert.severity, 
                        alert.plugin_id, 
                        alert.message,
                        alert.context
                    );
                },
                NotificationChannel::Database => {
                    // Bereits in save_alert_to_database behandelt
                },
                NotificationChannel::Email { recipient } => {
                    log::info!("Would send email alert to {}: {}", recipient, alert.message);
                },
                NotificationChannel::Webhook { url } => {
                    log::info!("Would send webhook alert to {}: {}", url, alert.message);
                },
            }
        }
        
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use connectias_storage::Database;
    use tempfile::TempDir;

    async fn create_test_alert_service() -> AlertService {
        let temp_dir = TempDir::new().unwrap();
        let db_path = temp_dir.path().join("test.db");
        let database = Arc::new(Database::new(&db_path).unwrap());
        AlertService::new(database)
    }

    #[tokio::test]
    async fn test_alert_service_creation() {
        let service = create_test_alert_service().await;
        let unresolved = service.get_unresolved_alerts().await.unwrap();
        assert_eq!(unresolved.len(), 0);
    }

    #[tokio::test]
    async fn test_permission_alert() {
        let service = create_test_alert_service().await;
        let alert_id = service.create_permission_alert("test-plugin", "Network", "HTTP request").await.unwrap();
        
        let alerts = service.get_plugin_alerts("test-plugin").await.unwrap();
        assert_eq!(alerts.len(), 1);
        assert_eq!(alerts[0].id, alert_id);
        assert_eq!(alerts[0].alert_type, AlertType::PermissionViolation);
    }

    #[tokio::test]
    async fn test_resource_alert() {
        let service = create_test_alert_service().await;
        let alert_id = service.create_resource_alert("test-plugin", "Memory", 95.0, 90.0).await.unwrap();
        
        let alerts = service.get_plugin_alerts("test-plugin").await.unwrap();
        assert_eq!(alerts.len(), 1);
        assert_eq!(alerts[0].id, alert_id);
        assert_eq!(alerts[0].alert_type, AlertType::ResourceLimitExceeded);
    }

    #[tokio::test]
    async fn test_resolve_alert() {
        let service = create_test_alert_service().await;
        let alert_id = service.create_permission_alert("test-plugin", "Network", "HTTP request").await.unwrap();
        
        // Alert sollte ungelöst sein
        let unresolved = service.get_unresolved_alerts().await.unwrap();
        assert_eq!(unresolved.len(), 1);
        
        // Alert als gelöst markieren
        service.resolve_alert(&alert_id, Some("Permission granted".to_string())).await.unwrap();
        
        // Alert sollte jetzt gelöst sein
        let unresolved = service.get_unresolved_alerts().await.unwrap();
        assert_eq!(unresolved.len(), 0);
    }

    #[tokio::test]
    async fn test_security_event_logging() {
        let service = create_test_alert_service().await;
        
        let event = SecurityEvent {
            event_id: "event_1".to_string(),
            plugin_id: "test-plugin".to_string(),
            event_type: "PluginStart".to_string(),
            description: "Plugin started successfully".to_string(),
            timestamp: Utc::now(),
            metadata: HashMap::new(),
        };
        
        service.log_security_event(event).await.unwrap();
        // Test erfolgreich wenn keine Panic auftritt
    }

    #[tokio::test]
    async fn test_notification_channels() {
        let service = create_test_alert_service().await;
        
        // Füge Email Channel hinzu
        service.add_notification_channel(NotificationChannel::Email { 
            recipient: "admin@example.com".to_string() 
        }).await.unwrap();
        
        // Test erfolgreich wenn keine Panic auftritt
    }

    #[tokio::test]
    async fn test_security_report() {
        let service = create_test_alert_service().await;
        
        // Erstelle einige Test-Alerts
        service.create_permission_alert("plugin1", "Network", "HTTP request").await.unwrap();
        service.create_resource_alert("plugin2", "Memory", 95.0, 90.0).await.unwrap();
        
        let report = service.generate_security_report(24).await.unwrap();
        
        assert!(report.contains_key("summary"));
        assert!(report.contains_key("plugin1"));
        assert!(report.contains_key("plugin2"));
    }
}

#[async_trait]
impl AlertServiceTrait for AlertService {
    async fn send_security_alert(&self, assessment: &ThreatAssessment) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let alert = SecurityAlert {
            id: uuid::Uuid::new_v4().to_string(),
            plugin_id: assessment.plugin_id.clone(),
            severity: match assessment.threat_score {
                score if score >= 0.8 => AlertSeverity::Critical,
                score if score >= 0.6 => AlertSeverity::High,
                score if score >= 0.4 => AlertSeverity::Medium,
                _ => AlertSeverity::Low,
            },
            alert_type: AlertType::ThreatDetected,
            message: format!("Threat detected: {} threats found", assessment.detected_threats.len()),
            timestamp: Utc::now(),
            resolved: false,
            threat_score: assessment.threat_score,
            context: {
                let mut ctx = HashMap::new();
                for threat in &assessment.detected_threats {
                    ctx.insert("threat".to_string(), threat.clone());
                }
                ctx
            },
            resolution_notes: None,
        };

        self.add_alert_to_history(&alert).await;
        let _ = self.save_alert_to_database(&alert).await;
        let _ = self.send_notifications(&alert).await;
        Ok(alert.id)
    }

    async fn create_permission_alert(&self, plugin_id: &str, permission: &str, action: &str) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let alert = SecurityAlert {
            id: uuid::Uuid::new_v4().to_string(),
            plugin_id: plugin_id.to_string(),
            severity: AlertSeverity::Medium,
            alert_type: AlertType::PermissionViolation,
            message: format!("Permission {} {} for plugin {}", permission, action, plugin_id),
            timestamp: Utc::now(),
            resolved: false,
            threat_score: 0.5,
            context: HashMap::new(),
            resolution_notes: None,
        };

        self.add_alert_to_history(&alert).await;
        let _ = self.save_alert_to_database(&alert).await;
        let _ = self.send_notifications(&alert).await;
        Ok(alert.id)
    }

    async fn create_resource_alert(&self, plugin_id: &str, resource_type: &str, usage: f64, limit: f64) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        let alert = SecurityAlert {
            id: uuid::Uuid::new_v4().to_string(),
            plugin_id: plugin_id.to_string(),
            severity: AlertSeverity::High,
            alert_type: AlertType::ResourceLimitExceeded,
            message: format!("Resource {} usage {} exceeds limit {} for plugin {}", resource_type, usage, limit, plugin_id),
            timestamp: Utc::now(),
            resolved: false,
            threat_score: (usage / limit).min(1.0),
            context: HashMap::new(),
            resolution_notes: None,
        };

        self.add_alert_to_history(&alert).await;
        let _ = self.save_alert_to_database(&alert).await;
        let _ = self.send_notifications(&alert).await;
        Ok(alert.id)
    }
}
