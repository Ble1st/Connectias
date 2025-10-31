use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant};
use connectias_api::PluginError;
use crate::metrics::MetricsCollector;
use thiserror::Error;
use connectias_security::threat_detection::MonitoringServiceTrait;

/// Monitoring Service für Plugin-Überwachung
pub struct MonitoringService {
    sampling_rates: Arc<RwLock<HashMap<String, f64>>>,
    metrics_collector: Arc<MetricsCollector>,
    monitoring_intervals: Arc<RwLock<HashMap<String, Duration>>>,
    last_metrics: Arc<RwLock<HashMap<String, Instant>>>,
}

/// Monitoring Service Errors
#[derive(Debug, Error)]
pub enum MonitoringError {
    #[error("Plugin not found: {plugin_id}")]
    PluginNotFound { plugin_id: String },
    #[error("Invalid sampling rate: {rate}")]
    InvalidSamplingRate { rate: f64 },
    #[error("Monitoring interval too short: {interval:?}")]
    IntervalTooShort { interval: Duration },
    #[error("Metrics collection failed: {reason}")]
    MetricsCollectionFailed { reason: String },
    #[error("Lock poisoned: {context}")]
    LockPoisoned { context: String },
}

impl From<MonitoringError> for PluginError {
    fn from(err: MonitoringError) -> Self {
        match err {
            MonitoringError::PluginNotFound { plugin_id } => {
                PluginError::NotFound(format!("Plugin not found: {}", plugin_id))
            },
            MonitoringError::InvalidSamplingRate { rate } => {
                PluginError::ExecutionFailed(format!("Invalid sampling rate: {}", rate))
            },
            MonitoringError::IntervalTooShort { interval } => {
                PluginError::ExecutionFailed(format!("Monitoring interval too short: {:?}", interval))
            },
            MonitoringError::MetricsCollectionFailed { reason } => {
                PluginError::ExecutionFailed(format!("Metrics collection failed: {}", reason))
            },
            MonitoringError::LockPoisoned { context } => {
                PluginError::ExecutionFailed(format!("Monitoring lock poisoned: {}", context))
            },
        }
    }
}

/// Plugin Metrics mit erweiterten Informationen
#[derive(Debug, Clone)]
pub struct EnhancedPluginMetrics {
    pub plugin_id: String,
    pub cpu_usage: f64,
    pub memory_usage: usize,
    pub execution_count: u64,
    pub error_count: u64,
    pub last_execution: Option<Instant>,
    pub average_execution_time: Duration,
    pub sampling_rate: f64,
    pub is_monitored: bool,
}

impl MonitoringService {
    /// Erstellt einen neuen MonitoringService
    pub fn new(metrics_collector: Arc<MetricsCollector>) -> Self {
        Self {
            sampling_rates: Arc::new(RwLock::new(HashMap::new())),
            metrics_collector,
            monitoring_intervals: Arc::new(RwLock::new(HashMap::new())),
            last_metrics: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Erhöht die Sampling-Rate für ein Plugin
    pub async fn increase_sampling_rate(&self, plugin_id: &str, factor: f64) -> Result<(), MonitoringError> {
        if factor <= 0.0 || factor > 10.0 {
            return Err(MonitoringError::InvalidSamplingRate { rate: factor });
        }

        // Get current rate first
        let current_rate = self.get_sampling_rate(plugin_id).await;
        let new_rate = (current_rate * factor).min(10.0);

        // Set new rate and interval atomically
        {
            let mut rates = self.sampling_rates.write()
                .map_err(|e| MonitoringError::LockPoisoned { context: format!("sampling_rates write: {}", e) })?;
            rates.insert(plugin_id.to_string(), new_rate);
        }

        // Aktualisiere Monitoring-Interval basierend auf Sampling-Rate
        let interval = Duration::from_millis((1000.0 / new_rate) as u64);
        self.set_monitoring_interval(plugin_id, interval).await?;

        Ok(())
    }

    /// Setzt die Sampling-Rate für ein Plugin
    pub async fn set_sampling_rate(&self, plugin_id: &str, rate: f64) -> Result<(), MonitoringError> {
        if rate <= 0.0 || rate > 10.0 {
            return Err(MonitoringError::InvalidSamplingRate { rate });
        }

        let mut rates = self.sampling_rates.write()
            .map_err(|e| MonitoringError::LockPoisoned { context: format!("sampling_rates write: {}", e) })?;
        
        rates.insert(plugin_id.to_string(), rate);
        
        // Aktualisiere Monitoring-Interval
        let interval = Duration::from_millis((1000.0 / rate) as u64);
        self.set_monitoring_interval(plugin_id, interval).await?;
        
        Ok(())
    }

    /// Setzt das Monitoring-Interval für ein Plugin
    pub async fn set_monitoring_interval(&self, plugin_id: &str, interval: Duration) -> Result<(), MonitoringError> {
        if interval < Duration::from_millis(100) {
            return Err(MonitoringError::IntervalTooShort { interval });
        }

        let mut intervals = self.monitoring_intervals.write()
            .map_err(|e| MonitoringError::LockPoisoned { context: format!("monitoring_intervals write: {}", e) })?;
        
        intervals.insert(plugin_id.to_string(), interval);
        Ok(())
    }

    /// Sammelt Metriken für ein Plugin
    pub async fn collect_metrics(&self, plugin_id: &str) -> Result<EnhancedPluginMetrics, MonitoringError> {
        let base_metrics = self.metrics_collector.get_metrics(plugin_id)
            .await
            .ok_or_else(|| MonitoringError::PluginNotFound {
                plugin_id: plugin_id.to_string()
            })?;

        let sampling_rate = self.get_sampling_rate(plugin_id).await;
        let is_monitored = sampling_rate > 0.0;

        let enhanced_metrics = EnhancedPluginMetrics {
            plugin_id: plugin_id.to_string(),
            cpu_usage: base_metrics.cpu_usage,
            memory_usage: base_metrics.memory_usage,
            execution_count: base_metrics.execution_count,
            error_count: base_metrics.error_count,
            last_execution: base_metrics.last_execution,
            average_execution_time: base_metrics.average_execution_time,
            sampling_rate,
            is_monitored,
        };

        // Update last metrics timestamp
        let mut last_metrics = self.last_metrics.write()
            .map_err(|e| MonitoringError::LockPoisoned { context: format!("last_metrics write: {}", e) })?;
        last_metrics.insert(plugin_id.to_string(), Instant::now());

        Ok(enhanced_metrics)
    }

    /// Sammelt Metriken für alle Plugins
    pub async fn collect_all_metrics(&self) -> Result<HashMap<String, EnhancedPluginMetrics>, MonitoringError> {
        let mut all_metrics = HashMap::new();

        // Hole alle Plugin-IDs aus den Sampling-Rates
        let plugin_ids: Vec<String> = {
            let rates = match self.sampling_rates.read() {
                Ok(rates) => rates,
                Err(_) => {
                    log::warn!("Sampling rates lock poisoned, returning empty metrics");
                    return Ok(HashMap::new());
                }
            };
            rates.keys().cloned().collect()
        };

        for plugin_id in plugin_ids {
            if let Ok(metrics) = self.collect_metrics(&plugin_id).await {
                all_metrics.insert(plugin_id, metrics);
            }
        }

        Ok(all_metrics)
    }

    /// Holt die aktuelle Sampling-Rate für ein Plugin
    pub async fn get_sampling_rate(&self, plugin_id: &str) -> f64 {
        let rates = match self.sampling_rates.read() {
            Ok(rates) => rates,
            Err(_) => {
                log::warn!("Sampling rates lock poisoned, using default");
                return 1.0;
            }
        };

        rates.get(plugin_id).copied().unwrap_or(1.0)
    }

    /// Holt das aktuelle Monitoring-Interval für ein Plugin
    pub async fn get_monitoring_interval(&self, plugin_id: &str) -> Duration {
        let intervals = match self.monitoring_intervals.read() {
            Ok(intervals) => intervals,
            Err(_) => {
                log::warn!("Monitoring intervals lock poisoned, using default");
                return Duration::from_secs(1);
            }
        };

        intervals.get(plugin_id).copied().unwrap_or(Duration::from_secs(1))
    }

    /// Startet kontinuierliche Überwachung für ein Plugin
    pub async fn start_monitoring(&self, plugin_id: &str) -> Result<(), MonitoringError> {
        let interval = self.get_monitoring_interval(plugin_id).await;
        let metrics_collector = self.metrics_collector.clone();
        let plugin_id_clone = plugin_id.to_string();
        let sampling_rates = self.sampling_rates.clone();
        let last_metrics = self.last_metrics.clone();

        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(interval);
            
            loop {
                interval_timer.tick().await;
                
                // Prüfe ob Plugin noch überwacht werden soll
                let current_rate = {
                    let rates = match sampling_rates.read() {
                        Ok(rates) => rates,
                        Err(_) => {
                            log::warn!("Sampling rates lock poisoned, stopping monitoring for {}", plugin_id_clone);
                            break;
                        }
                    };
                    rates.get(&plugin_id_clone).copied().unwrap_or(0.0)
                };
                
                if current_rate <= 0.0 {
                    break; // Monitoring beenden
                }
                
                // Sammle Metriken (simplified - in real implementation would collect system metrics)
                if let Some(_metrics) = metrics_collector.get_metrics(&plugin_id_clone).await {
                    // Metrics collected successfully
                } else {
                    log::warn!("Failed to collect metrics for plugin {}", plugin_id_clone);
                }
                
                // Update last metrics timestamp
                {
                    let mut last_metrics = last_metrics.write()
                        .unwrap_or_else(|poisoned| {
                            log::warn!("Last metrics lock poisoned for plugin {}", plugin_id_clone);
                            poisoned.into_inner()
                        });
                    last_metrics.insert(plugin_id_clone.clone(), Instant::now());
                }
            }
        });

        Ok(())
    }

    /// Stoppt die Überwachung für ein Plugin
    pub async fn stop_monitoring(&self, plugin_id: &str) -> Result<(), MonitoringError> {
        let mut rates = self.sampling_rates.write()
            .map_err(|e| MonitoringError::LockPoisoned { context: format!("sampling_rates write: {}", e) })?;
        
        rates.insert(plugin_id.to_string(), 0.0); // 0.0 = Monitoring gestoppt
        Ok(())
    }

    /// Entfernt ein Plugin aus der Überwachung
    pub async fn remove_plugin_monitoring(&self, plugin_id: &str) -> Result<(), MonitoringError> {
        let mut rates = self.sampling_rates.write()
            .map_err(|e| MonitoringError::LockPoisoned { context: format!("sampling_rates write: {}", e) })?;
        
        let mut intervals = self.monitoring_intervals.write()
            .map_err(|e| MonitoringError::LockPoisoned { context: format!("monitoring_intervals write: {}", e) })?;
        
        let mut last_metrics = self.last_metrics.write()
            .map_err(|e| MonitoringError::LockPoisoned { context: format!("last_metrics write: {}", e) })?;
        
        rates.remove(plugin_id);
        intervals.remove(plugin_id);
        last_metrics.remove(plugin_id);
        
        Ok(())
    }

    /// Gibt alle überwachten Plugins zurück
    pub async fn get_monitored_plugins(&self) -> Vec<String> {
        let rates = match self.sampling_rates.read() {
            Ok(rates) => rates,
            Err(_) => {
                log::warn!("Sampling rates lock poisoned, returning empty list");
                return Vec::new();
            }
        };

        rates.iter()
            .filter(|(_, rate)| **rate > 0.0)
            .map(|(plugin_id, _)| plugin_id.clone())
            .collect()
    }

    /// Erstellt einen Monitoring-Report
    pub async fn generate_monitoring_report(&self) -> Result<HashMap<String, serde_json::Value>, MonitoringError> {
        let all_metrics = self.collect_all_metrics().await?;
        let mut report = HashMap::new();
        
        for (plugin_id, metrics) in all_metrics {
            let metrics_json = serde_json::json!({
                "plugin_id": metrics.plugin_id,
                "cpu_usage": metrics.cpu_usage,
                "memory_usage": metrics.memory_usage,
                "execution_count": metrics.execution_count,
                "error_count": metrics.error_count,
                "average_execution_time_ms": metrics.average_execution_time.as_millis(),
                "sampling_rate": metrics.sampling_rate,
                "is_monitored": metrics.is_monitored,
                "last_execution": metrics.last_execution.map(|t| t.elapsed().as_secs()),
            });
            
            report.insert(plugin_id, metrics_json);
        }
        
        Ok(report)
    }
}

impl MonitoringServiceTrait for MonitoringService {
    fn increase_sampling_rate_sync(&self, plugin_id: &str, factor: f64) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        // Run in a blocking context to avoid Send issues
        let rt = tokio::runtime::Handle::current();
        rt.block_on(async {
            self.increase_sampling_rate(plugin_id, factor).await
                .map_err(|e| Box::new(e) as Box<dyn std::error::Error + Send + Sync>)
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::metrics::MetricsCollector;

    #[tokio::test]
    async fn test_monitoring_service_creation() {
        let metrics_collector = Arc::new(MetricsCollector::new());
        let service = MonitoringService::new(metrics_collector);
        
        let monitored = service.get_monitored_plugins().await;
        assert_eq!(monitored.len(), 0);
    }

    #[tokio::test]
    async fn test_set_sampling_rate() {
        let metrics_collector = Arc::new(MetricsCollector::new());
        let service = MonitoringService::new(metrics_collector);
        let plugin_id = "test-plugin";
        
        // Set valid sampling rate
        service.set_sampling_rate(plugin_id, 2.0).await.unwrap();
        assert_eq!(service.get_sampling_rate(plugin_id).await, 2.0);
        
        // Test invalid sampling rate
        let result = service.set_sampling_rate(plugin_id, -1.0).await;
        assert!(result.is_err());
        
        let result = service.set_sampling_rate(plugin_id, 15.0).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_increase_sampling_rate() {
        let metrics_collector = Arc::new(MetricsCollector::new());
        let service = MonitoringService::new(metrics_collector);
        let plugin_id = "test-plugin";
        
        // Set initial rate
        service.set_sampling_rate(plugin_id, 1.0).await.unwrap();
        
        // Increase rate
        service.increase_sampling_rate(plugin_id, 2.0).await.unwrap();
        assert_eq!(service.get_sampling_rate(plugin_id).await, 2.0);
        
        // Test factor limit
        service.increase_sampling_rate(plugin_id, 10.0).await.unwrap();
        assert_eq!(service.get_sampling_rate(plugin_id).await, 10.0); // Max rate
    }

    #[tokio::test]
    async fn test_monitoring_interval() {
        let metrics_collector = Arc::new(MetricsCollector::new());
        let service = MonitoringService::new(metrics_collector);
        let plugin_id = "test-plugin";
        
        let interval = Duration::from_millis(500);
        service.set_monitoring_interval(plugin_id, interval).await.unwrap();
        assert_eq!(service.get_monitoring_interval(plugin_id).await, interval);
        
        // Test too short interval
        let result = service.set_monitoring_interval(plugin_id, Duration::from_millis(50)).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_stop_monitoring() {
        let metrics_collector = Arc::new(MetricsCollector::new());
        let service = MonitoringService::new(metrics_collector);
        let plugin_id = "test-plugin";
        
        service.set_sampling_rate(plugin_id, 2.0).await.unwrap();
        assert!(service.get_sampling_rate(plugin_id).await > 0.0);
        
        service.stop_monitoring(plugin_id).await.unwrap();
        assert_eq!(service.get_sampling_rate(plugin_id).await, 0.0);
    }

    #[tokio::test]
    async fn test_remove_plugin_monitoring() {
        let metrics_collector = Arc::new(MetricsCollector::new());
        let service = MonitoringService::new(metrics_collector);
        let plugin_id = "test-plugin";
        
        service.set_sampling_rate(plugin_id, 2.0).await.unwrap();
        service.set_monitoring_interval(plugin_id, Duration::from_secs(1)).await.unwrap();
        
        let monitored = service.get_monitored_plugins().await;
        assert!(monitored.contains(&plugin_id.to_string()));
        
        service.remove_plugin_monitoring(plugin_id).await.unwrap();
        
        let monitored = service.get_monitored_plugins().await;
        assert!(!monitored.contains(&plugin_id.to_string()));
    }
}
//ich diene der aktualisierung wala
