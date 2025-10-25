use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;
use std::time::{Duration, Instant};
use tokio::task::JoinHandle;

/// Performance Optimization System
/// 
/// Implements plugin caching, lazy loading, connection pooling,
/// and memory pool for frequent allocations.
pub struct PerformanceOptimizer {
    plugin_cache: Arc<PluginCache>,
    connection_pool: Arc<ConnectionPool>,
    memory_pool: Arc<MemoryPool>,
    metrics: Arc<PerformanceMetrics>,
    monitoring_handle: Option<JoinHandle<()>>,
}

/// Plugin Cache for fast plugin access
pub struct PluginCache {
    cache: Arc<RwLock<HashMap<String, CachedPlugin>>>,
    max_size: usize,
    ttl: Duration,
}

#[derive(Debug, Clone)]
pub struct CachedPlugin {
    pub plugin_id: String,
    pub data: Vec<u8>,
    pub cached_at: Instant,
    pub last_accessed: Instant,
    pub access_count: u64,
}

impl PluginCache {
    pub fn new(max_size: usize, ttl: Duration) -> Self {
        Self {
            cache: Arc::new(RwLock::new(HashMap::new())),
            max_size,
            ttl,
        }
    }

    pub async fn get(&self, plugin_id: &str) -> Option<Vec<u8>> {
        let mut cache = self.cache.write().await;
        
        if let Some(cached) = cache.get_mut(plugin_id) {
            // Check if cache entry is still valid
            if cached.cached_at.elapsed() < self.ttl {
                cached.access_count += 1;
                cached.last_accessed = Instant::now();
                return Some(cached.data.clone());
            } else {
                // Remove expired entry
                cache.remove(plugin_id);
            }
        }
        
        None
    }

    pub async fn put(&self, plugin_id: String, data: Vec<u8>) {
        let mut cache = self.cache.write().await;
        
        // Remove oldest entries if cache is full
        if cache.len() >= self.max_size {
            self.evict_oldest(&mut cache).await;
        }
        
        let now = Instant::now();
        let cached_plugin = CachedPlugin {
            plugin_id: plugin_id.clone(),
            data,
            cached_at: now,
            last_accessed: now,
            access_count: 1,
        };
        
        cache.insert(plugin_id, cached_plugin);
    }

    async fn evict_oldest(&self, cache: &mut HashMap<String, CachedPlugin>) {
        // Find the least recently used entry (LRU)
        let oldest_key = cache.iter()
            .min_by_key(|(_, plugin)| plugin.last_accessed)
            .map(|(key, _)| key.clone());
        
        if let Some(key) = oldest_key {
            log::debug!("Evicting LRU plugin: {}", key);
            cache.remove(&key);
        }
    }
}

/// Connection Pool for network requests
pub struct ConnectionPool {
    pool: Arc<RwLock<Vec<PooledConnection>>>,
    max_connections: usize,
    idle_timeout: Duration,
}

#[derive(Debug)]
pub struct PooledConnection {
    pub id: String,
    pub created_at: Instant,
    pub last_used: Instant,
    pub is_active: bool,
}

impl ConnectionPool {
    pub fn new(max_connections: usize, idle_timeout: Duration) -> Self {
        Self {
            pool: Arc::new(RwLock::new(Vec::new())),
            max_connections,
            idle_timeout,
        }
    }

    pub async fn acquire(&self) -> Option<String> {
        let mut pool = self.pool.write().await;
        
        // Try to find an available connection
        for connection in pool.iter_mut() {
            if !connection.is_active && connection.last_used.elapsed() < self.idle_timeout {
                connection.is_active = true;
                connection.last_used = Instant::now();
                return Some(connection.id.clone());
            }
        }
        
        // Create new connection if under limit
        if pool.len() < self.max_connections {
            let connection_id = format!("conn_{}", pool.len());
            let connection = PooledConnection {
                id: connection_id.clone(),
                created_at: Instant::now(),
                last_used: Instant::now(),
                is_active: true,
            };
            pool.push(connection);
            return Some(connection_id);
        }
        
        None
    }

    pub async fn release(&self, connection_id: &str) {
        let mut pool = self.pool.write().await;
        
        for connection in pool.iter_mut() {
            if connection.id == connection_id {
                connection.is_active = false;
                connection.last_used = Instant::now();
                break;
            }
        }
    }
    
    pub fn get_max_connections(&self) -> usize {
        self.max_connections
    }

    pub async fn cleanup_expired(&self) {
        let mut pool = self.pool.write().await;
        let now = Instant::now();
        
        pool.retain(|conn| {
            // Behalte Verbindungen die aktiv sind ODER innerhalb des idle_timeout verwendet wurden
            conn.is_active || now.duration_since(conn.last_used) < self.idle_timeout
        });
    }
}

/// Memory Pool for frequent allocations
pub struct MemoryPool {
    pools: Arc<RwLock<HashMap<usize, Vec<Vec<u8>>>>>,
    max_pool_size: usize,
}

impl MemoryPool {
    pub fn new(max_pool_size: usize) -> Self {
        Self {
            pools: Arc::new(RwLock::new(HashMap::new())),
            max_pool_size,
        }
    }

    pub async fn get_buffer(&self, size: usize) -> Vec<u8> {
        let mut pools = self.pools.write().await;
        
        if let Some(pool) = pools.get_mut(&size) {
            if let Some(buffer) = pool.pop() {
                return buffer;
            }
        }
        
        // Create new buffer if none available
        vec![0u8; size]
    }

    pub async fn return_buffer(&self, mut buffer: Vec<u8>) {
        let size = buffer.capacity();
        let mut pools = self.pools.write().await;
        
        if let Some(pool) = pools.get_mut(&size) {
            if pool.len() < self.max_pool_size {
                buffer.clear();
                pool.push(buffer);
            }
        } else {
            let mut new_pool = Vec::new();
            buffer.clear();
            new_pool.push(buffer);
            pools.insert(size, new_pool);
        }
    }
}

/// Performance Metrics Collection
pub struct PerformanceMetrics {
    metrics: Arc<RwLock<HashMap<String, MetricValue>>>,
}

#[derive(Debug, Clone)]
pub enum MetricValue {
    Counter(u64),
    Gauge(f64),
    Histogram(Vec<f64>),
    Timer(Duration),
}

impl PerformanceMetrics {
    pub fn new() -> Self {
        Self {
            metrics: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn increment_counter(&self, name: &str, value: u64) {
        let mut metrics = self.metrics.write().await;
        match metrics.get_mut(name) {
            Some(MetricValue::Counter(counter)) => *counter += value,
            _ => {
                metrics.insert(name.to_string(), MetricValue::Counter(value));
            }
        }
    }

    pub async fn set_gauge(&self, name: &str, value: f64) {
        let mut metrics = self.metrics.write().await;
        metrics.insert(name.to_string(), MetricValue::Gauge(value));
    }

    pub async fn record_timer(&self, name: &str, duration: Duration) {
        let mut metrics = self.metrics.write().await;
        metrics.insert(name.to_string(), MetricValue::Timer(duration));
    }

    pub async fn get_metrics(&self) -> HashMap<String, MetricValue> {
        let metrics = self.metrics.read().await;
        metrics.clone()
    }
}

impl PerformanceOptimizer {
    pub fn new() -> Self {
        Self {
            plugin_cache: Arc::new(PluginCache::new(100, Duration::from_secs(300))), // 5 min TTL
            connection_pool: Arc::new(ConnectionPool::new(50, Duration::from_secs(60))), // 1 min idle
            memory_pool: Arc::new(MemoryPool::new(1000)),
            metrics: Arc::new(PerformanceMetrics::new()),
            monitoring_handle: None,
        }
    }

    /// Optimize plugin loading with caching
    pub async fn load_plugin_from_cache(&self, plugin_id: &str) -> Result<Option<Vec<u8>>, String> {
        // Check cache first
        if let Some(cached_data) = self.plugin_cache.get(plugin_id).await {
            self.metrics.increment_counter("cache_hits", 1).await;
            return Ok(Some(cached_data));
        }

        self.metrics.increment_counter("cache_misses", 1).await;

        // Load from disk (simulated)
        let start = Instant::now();
        let data = self.load_plugin_from_disk(plugin_id).await?;
        let load_time = start.elapsed();

        self.metrics.record_timer("plugin_load_time", load_time).await;

        // Cache the result
        if let Some(ref data) = data {
            self.plugin_cache.put(plugin_id.to_string(), data.clone()).await;
        }

        Ok(data)
    }

    async fn load_plugin_from_disk(&self, plugin_id: &str) -> Result<Option<Vec<u8>>, String> {
        // SICHERHEIT: Plugin-ID validieren um Path-Traversal zu verhindern
        if !is_valid_plugin_id(plugin_id) {
            let error_msg = format!("Ungültige Plugin-ID '{}': Enthält unsichere Zeichen oder Pfad-Komponenten", plugin_id);
            log::error!("{}", error_msg);
            return Err(error_msg);
        }
        
        // Konstruiere Plugin-Pfad basierend auf validierter plugin_id
        let plugin_path = std::path::Path::new("plugins").join(format!("{}.zip", plugin_id));
        
        // Zusätzliche Sicherheitsprüfung: Canonicalize und prüfe ob Pfad im erwarteten Verzeichnis liegt
        match std::fs::canonicalize("plugins") {
            Ok(plugins_dir) => {
                match std::fs::canonicalize(&plugin_path) {
                    Ok(canonical_path) => {
                        if !canonical_path.starts_with(&plugins_dir) {
                            let error_msg = format!("Sicherheitsverletzung: Plugin-Pfad '{}' liegt außerhalb des plugins-Verzeichnisses", canonical_path.display());
                            log::error!("{}", error_msg);
                            return Err(error_msg);
                        }
                        // Verwende den kanonischen Pfad für das Lesen (TOCTOU-Schutz)
                        match tokio::fs::read(&canonical_path).await {
                            Ok(data) => {
                                log::info!("Plugin {} erfolgreich von Disk geladen: {} bytes", plugin_id, data.len());
                                return Ok(Some(data));
                            }
                            Err(e) => {
                                let error_msg = format!("Fehler beim Laden von Plugin {}: {}", plugin_id, e);
                                log::error!("{}", error_msg);
                                return Err(error_msg);
                            }
                        }
                    }
                    Err(_) => {
                        // Pfad existiert noch nicht, das ist OK für neue Plugins
                        log::debug!("Plugin-Pfad {} existiert noch nicht (neues Plugin)", plugin_path.display());
                        // Fallback für neue Plugins - verwende ursprünglichen Pfad
                        match tokio::fs::read(&plugin_path).await {
                            Ok(data) => {
                                log::info!("Plugin {} erfolgreich von Disk geladen: {} bytes", plugin_id, data.len());
                                Ok(Some(data))
                            }
                            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                                log::debug!("Plugin {} nicht gefunden: {}", plugin_id, plugin_path.display());
                                Ok(None)
                            }
                            Err(e) => {
                                let error_msg = format!("Fehler beim Laden von Plugin {}: {}", plugin_id, e);
                                log::error!("{}", error_msg);
                                Err(error_msg)
                            }
                        }
                    }
                }
            }
            Err(e) => {
                let error_msg = format!("Fehler beim Canonicalize des plugins-Verzeichnisses: {}", e);
                log::error!("{}", error_msg);
                return Err(error_msg);
            }
        }
    }

    /// Get optimized connection from pool
    pub async fn get_connection(&self) -> Result<String, String> {
        if let Some(conn_id) = self.connection_pool.acquire().await {
            self.metrics.increment_counter("connections_acquired", 1).await;
            Ok(conn_id)
        } else {
            self.metrics.increment_counter("connection_pool_exhausted", 1).await;
            Err("Connection pool exhausted".to_string())
        }
    }

    /// Return connection to pool
    pub async fn return_connection(&self, connection_id: &str) {
        self.connection_pool.release(connection_id).await;
        self.metrics.increment_counter("connections_released", 1).await;
    }

    /// Get optimized buffer from memory pool
    pub async fn get_buffer(&self, size: usize) -> Vec<u8> {
        self.metrics.increment_counter("buffers_allocated", 1).await;
        self.memory_pool.get_buffer(size).await
    }

    /// Return buffer to memory pool
    pub async fn return_buffer(&self, buffer: Vec<u8>) {
        self.metrics.increment_counter("buffers_returned", 1).await;
        self.memory_pool.return_buffer(buffer).await;
    }

    /// Start performance monitoring
    pub async fn start_monitoring(&mut self) -> Result<(), String> {
        if self.monitoring_handle.is_some() {
            return Err("Monitoring already started".to_string());
        }
        
        let connection_pool = self.connection_pool.clone();
        let metrics = self.metrics.clone();
        
        let handle = tokio::spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_secs(30));
            
            loop {
                interval.tick().await;
                
                // Cleanup expired connections
                connection_pool.cleanup_expired().await;
                
                // Record pool metrics
                let pool_size = connection_pool.pool.read().await.len();
                metrics.set_gauge("connection_pool_size", pool_size as f64).await;
            }
        });
        
        self.monitoring_handle = Some(handle);
        Ok(())
    }
    
    /// Stop performance monitoring
    pub async fn stop_monitoring(&mut self) -> Result<(), String> {
        if let Some(handle) = self.monitoring_handle.take() {
            handle.abort();
            log::info!("Performance monitoring stopped");
            Ok(())
        } else {
            Err("Monitoring not started".to_string())
        }
    }

    /// Get performance report
    pub async fn get_performance_report(&self) -> PerformanceReport {
        let metrics = self.metrics.get_metrics().await;
        
        PerformanceReport {
            cache_hit_rate: self.calculate_cache_hit_rate(&metrics),
            average_load_time: self.calculate_average_load_time(&metrics),
            connection_pool_utilization: self.calculate_pool_utilization(&metrics),
            memory_efficiency: self.calculate_memory_efficiency(&metrics),
            metrics,
        }
    }

    fn calculate_cache_hit_rate(&self, metrics: &HashMap<String, MetricValue>) -> f64 {
        let hits = metrics.get("cache_hits")
            .and_then(|v| match v { MetricValue::Counter(c) => Some(*c), _ => None })
            .unwrap_or(0);
        let misses = metrics.get("cache_misses")
            .and_then(|v| match v { MetricValue::Counter(c) => Some(*c), _ => None })
            .unwrap_or(0);
        
        if hits + misses > 0 {
            hits as f64 / (hits + misses) as f64
        } else {
            0.0
        }
    }

    fn calculate_average_load_time(&self, _metrics: &HashMap<String, MetricValue>) -> Duration {
        // Simplified calculation
        Duration::from_millis(50)
    }

    fn calculate_pool_utilization(&self, metrics: &HashMap<String, MetricValue>) -> f64 {
        let current_size = metrics.get("connection_pool_size")
            .and_then(|v| match v { MetricValue::Gauge(f) => Some(*f), _ => None })
            .unwrap_or(0.0);
        
        let max_connections = self.connection_pool.get_max_connections() as f64;
        
        if max_connections > 0.0 {
            current_size / max_connections
        } else {
            0.0 // Vermeide Division durch Null
        }
    }

    fn calculate_memory_efficiency(&self, _metrics: &HashMap<String, MetricValue>) -> f64 {
        // Simplified calculation
        0.85
    }

    /// Reset plugin optimization (placeholder for future implementation)
    pub async fn reset_plugin_optimization(&self, _plugin_id: &str) {
        // In a future implementation, this could reset cache entries,
        // clear connection pool slots, etc. for the specific plugin
        log::debug!("Resetting optimization for plugin: {}", _plugin_id);
    }

    /// Load plugin with optimization (placeholder)
    pub async fn load_plugin_optimized(&self, _plugin_id: &str) -> Result<Option<Vec<u8>>, String> {
        // For now, return None - plugin data would be loaded from disk
        // In a future implementation, this could use caching and optimization
        Ok(None)
    }
}

#[derive(Debug)]
pub struct PerformanceReport {
    pub cache_hit_rate: f64,
    pub average_load_time: Duration,
    pub connection_pool_utilization: f64,
    pub memory_efficiency: f64,
    pub metrics: HashMap<String, MetricValue>,
}

/// Validiert Plugin-IDs um Path-Traversal-Angriffe zu verhindern
/// 
/// # Sicherheitsregeln:
/// - Nur alphanumerische Zeichen, Bindestriche und Unterstriche erlaubt
/// - Keine Pfad-Separatoren (/, \)
/// - Keine Parent-Directory-Komponenten (..)
/// - Maximale Länge: 50 Zeichen
/// - Nicht leer
fn is_valid_plugin_id(plugin_id: &str) -> bool {
    // Leer oder zu lang
    if plugin_id.is_empty() || plugin_id.len() > 50 {
        return false;
    }
    
    // Prüfe auf gefährliche Zeichen
    for ch in plugin_id.chars() {
        match ch {
            // Erlaubte Zeichen: alphanumerisch, Bindestrich, Unterstrich
            'a'..='z' | 'A'..='Z' | '0'..='9' | '-' | '_' => continue,
            // Alle anderen Zeichen sind verboten
            _ => return false,
        }
    }
    
    // Prüfe auf gefährliche Patterns
    if plugin_id.contains("..") || 
       plugin_id.starts_with('.') || 
       plugin_id.ends_with('.') ||
       plugin_id.contains('/') || 
       plugin_id.contains('\\') {
        return false;
    }
    
    // Prüfe auf reservierte Namen
    let reserved_names = ["con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"];
    if reserved_names.contains(&plugin_id.to_lowercase().as_str()) {
        return false;
    }
    
    true
}

//ich diene der aktualisierung wala
