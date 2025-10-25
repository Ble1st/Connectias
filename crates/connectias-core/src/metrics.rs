use std::time::{Duration, Instant};
use std::sync::Arc;
use tokio::sync::RwLock;
use serde::{Deserialize, Serialize};

/// Performance Metrics für Plugin-Operationen
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginMetrics {
    pub plugin_id: String,
    pub load_time: Duration,
    pub last_execution_time: Duration,
    pub total_executions: u64,
    pub memory_usage: usize,
    pub cpu_usage: f64,
    pub network_requests: u64,
    pub network_bytes_sent: u64,
    pub network_bytes_received: u64,
    pub crash_count: u32,
    pub last_crash_timestamp: Option<i64>,
    // Added fields for EnhancedPluginMetrics compatibility
    pub execution_count: u64,
    pub error_count: u64,
    #[serde(skip)]
    pub last_execution: Option<std::time::Instant>,
    pub average_execution_time: Duration,
}

/// Metrics Collector mit automatischer Aggregation
pub struct MetricsCollector {
    metrics: Arc<RwLock<std::collections::HashMap<String, PluginMetrics>>>,
}

impl MetricsCollector {
    pub fn new() -> Self {
        Self {
            metrics: Arc::new(RwLock::new(std::collections::HashMap::new())),
        }
    }

    /// Record plugin load time
    pub async fn record_load_time(&self, plugin_id: &str, duration: Duration) {
        let mut metrics = self.metrics.write().await;
        let entry = metrics.entry(plugin_id.to_string()).or_insert_with(|| {
            PluginMetrics {
                plugin_id: plugin_id.to_string(),
                load_time: Duration::default(),
                last_execution_time: Duration::default(),
                total_executions: 0,
                memory_usage: 0,
                cpu_usage: 0.0,
                network_requests: 0,
                network_bytes_sent: 0,
                network_bytes_received: 0,
                crash_count: 0,
                last_crash_timestamp: None,
                execution_count: 0,
                error_count: 0,
                last_execution: None,
                average_execution_time: Duration::default(),
            }
        });
        entry.load_time = duration;
    }

    /// Record plugin execution
    pub async fn record_execution(&self, plugin_id: &str, duration: Duration) {
        let mut metrics = self.metrics.write().await;
        if let Some(entry) = metrics.get_mut(plugin_id) {
            entry.last_execution_time = duration;
            entry.total_executions += 1;
            entry.execution_count += 1;
            entry.last_execution = Some(Instant::now());
            entry.average_execution_time = (entry.average_execution_time + duration) / 2;
        }
    }

    /// Record memory usage
    pub async fn record_memory(&self, plugin_id: &str, bytes: usize) {
        let mut metrics = self.metrics.write().await;
        if let Some(entry) = metrics.get_mut(plugin_id) {
            entry.memory_usage = bytes;
        }
    }

    /// Record network activity
    pub async fn record_network(&self, plugin_id: &str, bytes_sent: u64, bytes_received: u64) {
        let mut metrics = self.metrics.write().await;
        if let Some(entry) = metrics.get_mut(plugin_id) {
            entry.network_requests += 1;
            entry.network_bytes_sent += bytes_sent;
            entry.network_bytes_received += bytes_received;
        }
    }

    /// Record plugin crash
    pub async fn record_crash(&self, plugin_id: &str) {
        let mut metrics = self.metrics.write().await;
        if let Some(entry) = metrics.get_mut(plugin_id) {
            entry.crash_count += 1;
            entry.error_count += 1;
            entry.last_crash_timestamp = Some(chrono::Utc::now().timestamp());
        }
    }

    /// Get metrics for a specific plugin
    pub async fn get_metrics(&self, plugin_id: &str) -> Option<PluginMetrics> {
        let metrics = self.metrics.read().await;
        metrics.get(plugin_id).cloned()
    }

    /// Get all metrics
    pub async fn get_all_metrics(&self) -> Vec<PluginMetrics> {
        let metrics = self.metrics.read().await;
        metrics.values().cloned().collect()
    }

    /// Remove plugin metrics
    pub async fn remove_plugin_metrics(&self, plugin_id: &str) {
        let mut metrics = self.metrics.write().await;
        metrics.remove(plugin_id);
    }

    /// Record CPU usage
    pub async fn record_cpu_usage(&self, plugin_id: &str, usage: f64) {
        let mut metrics = self.metrics.write().await;
        if let Some(entry) = metrics.get_mut(plugin_id) {
            entry.cpu_usage = usage;
        }
    }

    /// Get plugin performance summary
    pub async fn get_performance_summary(&self, plugin_id: &str) -> Option<PerformanceSummary> {
        let metrics = self.metrics.read().await;
        if let Some(plugin_metrics) = metrics.get(plugin_id) {
            Some(PerformanceSummary {
                plugin_id: plugin_id.to_string(),
                avg_execution_time: plugin_metrics.last_execution_time,
                total_executions: plugin_metrics.total_executions,
                memory_usage_mb: plugin_metrics.memory_usage as f64 / 1024.0 / 1024.0,
                cpu_usage_percent: plugin_metrics.cpu_usage,
                crash_rate: if plugin_metrics.total_executions > 0 {
                    plugin_metrics.crash_count as f64 / plugin_metrics.total_executions as f64
                } else {
                    0.0
                },
                network_throughput_mbps: (plugin_metrics.network_bytes_sent + plugin_metrics.network_bytes_received) as f64 / 1024.0 / 1024.0,
            })
        } else {
            None
        }
    }
}

/// Performance Summary für Plugin-Monitoring
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceSummary {
    pub plugin_id: String,
    pub avg_execution_time: Duration,
    pub total_executions: u64,
    pub memory_usage_mb: f64,
    pub cpu_usage_percent: f64,
    pub crash_rate: f64,
    pub network_throughput_mbps: f64,
}

/// Performance Monitor für kontinuierliche Überwachung
pub struct PerformanceMonitor {
    collector: Arc<MetricsCollector>,
    monitoring_interval: Duration,
}

impl PerformanceMonitor {
    pub fn new(collector: Arc<MetricsCollector>) -> Self {
        Self {
            collector,
            monitoring_interval: Duration::from_secs(30),
        }
    }

    /// Start kontinuierliche Performance-Überwachung
    pub async fn start_monitoring(&self) {
        let collector = self.collector.clone();
        let interval = self.monitoring_interval;
        
        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(interval);
            
            loop {
                interval_timer.tick().await;
                
                // Collect system metrics
                let all_metrics = collector.get_all_metrics().await;
                
                for metrics in all_metrics {
                    // Log performance warnings
                    if metrics.cpu_usage > 80.0 {
                        tracing::warn!("Plugin {} high CPU usage: {}%", metrics.plugin_id, metrics.cpu_usage);
                    }
                    
                    if metrics.memory_usage > 100 * 1024 * 1024 { // 100MB
                        tracing::warn!("Plugin {} high memory usage: {}MB", 
                            metrics.plugin_id, 
                            metrics.memory_usage / 1024 / 1024
                        );
                    }
                    
                    if metrics.crash_count > 5 {
                        tracing::error!("Plugin {} has crashed {} times", metrics.plugin_id, metrics.crash_count);
                    }
                }
            }
        });
    }
}

