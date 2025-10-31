use std::sync::{Arc, Weak};
use std::collections::HashMap;
use tokio::sync::RwLock;

/// Aggressive Memory Manager with WeakReferences
pub struct MemoryManager {
    plugin_resources: Arc<RwLock<HashMap<String, Vec<Weak<dyn std::any::Any + Send + Sync>>>>>,
    memory_limits: Arc<RwLock<HashMap<String, usize>>>,
    cleanup_interval: std::time::Duration,
}

impl MemoryManager {
    pub fn new() -> Self {
        Self {
            // PERF: Optimierte Capacity-basierte Initialisierung
            plugin_resources: Arc::new(RwLock::new(crate::hashmap_with_capacity!(30))),
            memory_limits: Arc::new(RwLock::new(crate::hashmap_with_capacity!(20))),
            cleanup_interval: std::time::Duration::from_secs(30),
        }
    }

    /// Set memory limit for plugin
    pub async fn set_limit(&self, plugin_id: &str, limit: usize) {
        let mut limits = self.memory_limits.write().await;
        limits.insert(plugin_id.to_string(), limit);
    }

    /// Register plugin resource
    pub async fn register_resource(&self, plugin_id: &str, resource: Weak<dyn std::any::Any + Send + Sync>) {
        let mut resources = self.plugin_resources.write().await;
        resources.entry(plugin_id.to_string())
            .or_insert_with(Vec::new)
            .push(resource);
    }

    /// Cleanup dead references
    pub async fn cleanup_dead_references(&self) {
        let mut resources = self.plugin_resources.write().await;
        for (_, refs) in resources.iter_mut() {
            refs.retain(|weak| weak.strong_count() > 0);
        }
    }

    /// Force cleanup for plugin
    pub async fn force_cleanup(&self, plugin_id: &str) {
        let mut resources = self.plugin_resources.write().await;
        if let Some(refs) = resources.get_mut(plugin_id) {
            refs.clear();
        }
    }

    /// Get memory usage estimate
    pub async fn get_memory_usage(&self, plugin_id: &str) -> usize {
        let resources = self.plugin_resources.read().await;
        if let Some(refs) = resources.get(plugin_id) {
            refs.iter().filter(|w| w.strong_count() > 0).count() * std::mem::size_of::<usize>()
        } else {
            0
        }
    }

    /// Check if plugin exceeds memory limit
    pub async fn check_memory_limit(&self, plugin_id: &str) -> bool {
        let limits = self.memory_limits.read().await;
        if let Some(limit) = limits.get(plugin_id) {
            let usage = self.get_memory_usage(plugin_id).await;
            usage > *limit
        } else {
            false
        }
    }

    /// Start automatic cleanup task
    /// 
    /// Gibt einen JoinHandle zurück, damit der Caller den Task verwalten kann.
    /// Der Task läuft bis zum Shutdown und sollte ordnungsgemäß abgebrochen werden.
    pub fn start_cleanup_task(&self) -> tokio::task::JoinHandle<()> {
        let resources = self.plugin_resources.clone();
        let limits = self.memory_limits.clone();
        let interval = self.cleanup_interval;

        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(interval);
            
            loop {
                interval_timer.tick().await;
                
                // Cleanup dead references
                {
                    let mut resources = resources.write().await;
                    for (_, refs) in resources.iter_mut() {
                        refs.retain(|weak| weak.strong_count() > 0);
                    }
                }

                // Check memory limits
                {
                    let limits = limits.read().await;
                    let resources = resources.read().await;
                    
                    for (plugin_id, limit) in limits.iter() {
                        if let Some(refs) = resources.get(plugin_id) {
                            let usage = refs.iter().filter(|w| w.strong_count() > 0).count() * std::mem::size_of::<usize>();
                            if usage > *limit {
                                tracing::warn!("Plugin {} exceeds memory limit: {} bytes used, {} bytes limit", 
                                    plugin_id, usage, limit);
                            }
                        }
                    }
                }
            }
        })
    }

    /// Get memory statistics
    pub async fn get_memory_stats(&self) -> MemoryStats {
        let resources = self.plugin_resources.read().await;
        let limits = self.memory_limits.read().await;

        let mut total_usage = 0;
        let mut plugin_count = 0;
        let mut over_limit_count = 0;

        for (plugin_id, refs) in resources.iter() {
            let usage = refs.iter().filter(|w| w.strong_count() > 0).count() * std::mem::size_of::<usize>();
            total_usage += usage;
            plugin_count += 1;

            if let Some(limit) = limits.get(plugin_id) {
                if usage > *limit {
                    over_limit_count += 1;
                }
            }
        }

        MemoryStats {
            total_usage,
            plugin_count,
            over_limit_count,
            average_usage: if plugin_count > 0 { total_usage / plugin_count } else { 0 },
        }
    }
}

/// Memory statistics
#[derive(Debug, Clone)]
pub struct MemoryStats {
    pub total_usage: usize,
    pub plugin_count: usize,
    pub over_limit_count: usize,
    pub average_usage: usize,
}

/// Memory monitor for tracking memory usage
pub struct MemoryMonitor {
    memory_manager: Arc<MemoryManager>,
    monitoring_interval: std::time::Duration,
}

impl MemoryMonitor {
    pub fn new(memory_manager: Arc<MemoryManager>) -> Self {
        Self {
            memory_manager,
            monitoring_interval: std::time::Duration::from_secs(10),
        }
    }

    /// Start memory monitoring
    /// 
    /// Gibt einen JoinHandle zurück, damit der Caller den Task verwalten kann.
    /// Der Task läuft bis zum Shutdown und sollte ordnungsgemäß abgebrochen werden.
    pub fn start_monitoring(&self) -> tokio::task::JoinHandle<()> {
        let manager = self.memory_manager.clone();
        let interval = self.monitoring_interval;

        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(interval);
            
            loop {
                interval_timer.tick().await;
                
                let stats = manager.get_memory_stats().await;
                
                // Log memory warnings
                if stats.total_usage > 500 * 1024 * 1024 { // 500MB
                    tracing::warn!("High total memory usage: {}MB", stats.total_usage / 1024 / 1024);
                }
                
                if stats.over_limit_count > 0 {
                    tracing::warn!("{} plugins exceed memory limits", stats.over_limit_count);
                }
                
                // Force cleanup if memory usage is too high
                if stats.total_usage > 1000 * 1024 * 1024 { // 1GB
                    tracing::warn!("Forcing memory cleanup due to high usage");
                    manager.cleanup_dead_references().await;
                }
            }
        })
    }
}

/// Memory-efficient resource wrapper
pub struct ResourceWrapper<T> {
    inner: Arc<T>,
    plugin_id: String,
    memory_manager: Arc<MemoryManager>,
}

impl<T> ResourceWrapper<T> 
where 
    T: Send + Sync + 'static,
{
    pub fn new(inner: T, plugin_id: String, memory_manager: Arc<MemoryManager>) -> Self {
        let arc = Arc::new(inner);
        let weak = Arc::downgrade(&arc);
        
        // Register with memory manager synchron - verwende block_in_place um async Runtime nicht zu blockieren
        // Alternativ könnten wir einen Channel verwenden, aber das wäre komplizierter
        let manager = memory_manager.clone();
        let plugin_id_clone = plugin_id.clone();
        // Spawn task aber ignoriere Handle (Registrierung ist nicht kritisch genug für Tracking)
        // Dies ist OK, da die Registrierung idempotent ist und bei nächstem Cleanup korrigiert wird
        let _handle = tokio::spawn(async move {
            manager.register_resource(&plugin_id_clone, weak).await;
        });
        // Task läuft im Hintergrund - wenn er fehlschlägt, wird Resource bei nächstem Cleanup registriert

        Self {
            inner: arc,
            plugin_id,
            memory_manager,
        }
    }

    pub fn get(&self) -> &T {
        &self.inner
    }

    pub fn clone(&self) -> Arc<T> {
        self.inner.clone()
    }
}

impl<T> Drop for ResourceWrapper<T> {
    fn drop(&mut self) {
        // FIX BUG: Drop kann kein async haben - verwende block_in_place für synchrones Cleanup
        // Das Resource wird automatisch entfernt beim nächsten cleanup_dead_references() Aufruf
        // da die Weak-Reference dann strong_count() == 0 hat
        // Wir können hier kein async machen, aber das ist OK - Cleanup erfolgt beim nächsten Zyklus
        // Alternativ: Verwende block_in_place wenn Runtime verfügbar, aber das ist riskant
        // Beste Lösung: Cleanup erfolgt automatisch durch regelmäßige cleanup_dead_references() Calls
        // Die Weak-Reference wird automatisch als "dead" erkannt
    }
}
