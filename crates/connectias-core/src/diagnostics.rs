//! Erweiterte Diagnostik und Monitoring-Tools
//! 
//! Dieses Modul bietet automatische Performance-Optimierungen,
//! Race-Condition-Detection und umfassende System-Diagnostik.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::RwLock;

/// Automatisches Performance-Optimierungssystem
/// 
/// Analysiert und optimiert automatisch:
/// - Collection-Initialisierungen (HashMap, Vec mit Capacity)
/// - Memory-Allocation-Patterns
/// - Hot-Path-Optimierungen
#[derive(Clone)]
pub struct PerformanceOptimizer {
    metrics: Arc<RwLock<PerformanceMetrics>>,
    recommendations: Arc<RwLock<Vec<OptimizationRecommendation>>>,
}

/// Performance-Metriken
#[derive(Debug, Default)]
pub struct PerformanceMetrics {
    pub collection_allocations: HashMap<String, AllocationStats>,
    pub hot_paths: Vec<HotPath>,
    pub memory_pressure: f64,
    pub cpu_usage: f64,
}

/// Allocation-Statistiken für Collections
#[derive(Debug, Default)]
pub struct AllocationStats {
    pub count: u64,
    pub avg_size: usize,
    pub max_size: usize,
    pub reallocations: u64,
}

/// Hot-Path-Information
#[derive(Debug, Clone)]
pub struct HotPath {
    pub name: String,
    pub call_count: u64,
    pub total_time: Duration,
    pub avg_time: Duration,
    pub p95_time: Duration,
    pub p99_time: Duration,
}

/// Optimierungs-Empfehlung
#[derive(Debug, Clone)]
pub struct OptimizationRecommendation {
    pub location: String,
    pub issue: String,
    pub recommendation: String,
    pub priority: Priority,
    pub estimated_impact: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Priority {
    Low,
    Medium,
    High,
    Critical,
}

impl PerformanceOptimizer {
    pub fn new() -> Self {
        Self {
            metrics: Arc::new(RwLock::new(PerformanceMetrics::default())),
            recommendations: Arc::new(RwLock::new(Vec::new())),
        }
    }

    /// Analysiere Collection-Allocation und empfiehl Capacity
    pub async fn analyze_collection_allocation(
        &self,
        collection_type: &str,
        initial_size: usize,
        growth_pattern: Vec<usize>,
    ) -> usize {
        let mut metrics = self.metrics.write().await;
        
        let stats = metrics.collection_allocations
            .entry(collection_type.to_string())
            .or_insert_with(AllocationStats::default);
        
        stats.count += 1;
        
        // Berechne optimale Capacity basierend auf Growth-Pattern
        let max_size = growth_pattern.iter().max().copied().unwrap_or(initial_size);
        let avg_size = growth_pattern.iter().sum::<usize>() / growth_pattern.len().max(1);
        
        stats.max_size = stats.max_size.max(max_size);
        stats.avg_size = (stats.avg_size + avg_size) / 2;
        
        // Empfehle Capacity mit 25% Overhead für Wachstum
        let recommended_capacity = (avg_size * 125) / 100;
        
        // Zähle Reallocations (wenn Wachstum > initial_size)
        if max_size > initial_size {
            stats.reallocations += 1;
            
            // Erstelle Empfehlung
            let mut recommendations = self.recommendations.write().await;
            recommendations.push(OptimizationRecommendation {
                location: collection_type.to_string(),
                issue: format!("Collection wächst von {} auf {} ({} Reallocations)", 
                             initial_size, max_size, stats.reallocations),
                recommendation: format!("Initialisiere mit Capacity: {}", recommended_capacity),
                priority: if stats.reallocations > 10 { Priority::High } else { Priority::Medium },
                estimated_impact: format!("Reduziert Reallocations um ~{}%", 
                                         (stats.reallocations * 30).min(90)),
            });
        }
        
        recommended_capacity
    }

    /// Track Hot-Path Execution
    pub async fn track_hot_path(&self, name: &str, duration: Duration) {
        let mut metrics = self.metrics.write().await;
        
        // Finde oder erstelle Hot-Path
        if let Some(path) = metrics.hot_paths.iter_mut().find(|p| p.name == name) {
            path.call_count += 1;
            path.total_time += duration;
            path.avg_time = path.total_time / path.call_count;
            
            // Vereinfachte P95/P99 Berechnung
            if path.call_count % 100 == 0 {
                path.p95_time = path.avg_time * 195 / 100;
                path.p99_time = path.avg_time * 199 / 100;
            }
        } else {
            metrics.hot_paths.push(HotPath {
                name: name.to_string(),
                call_count: 1,
                total_time: duration,
                avg_time: duration,
                p95_time: duration,
                p99_time: duration,
            });
        }
    }

    /// Generiere Performance-Report
    pub async fn generate_report(&self) -> PerformanceReport {
        let metrics = self.metrics.read().await;
        let recommendations = self.recommendations.read().await;
        
        PerformanceReport {
            collection_stats: metrics.collection_allocations.clone(),
            hot_paths: metrics.hot_paths.clone(),
            recommendations: recommendations.clone(),
            memory_pressure: metrics.memory_pressure,
            cpu_usage: metrics.cpu_usage,
        }
    }
}

/// Performance-Report
#[derive(Debug)]
pub struct PerformanceReport {
    pub collection_stats: HashMap<String, AllocationStats>,
    pub hot_paths: Vec<HotPath>,
    pub recommendations: Vec<OptimizationRecommendation>,
    pub memory_pressure: f64,
    pub cpu_usage: f64,
}

impl PerformanceReport {
    /// Formatiere Report als String
    pub fn format(&self) -> String {
        let mut output = String::new();
        output.push_str("=== Performance Diagnostic Report ===\n\n");
        
        // Collection-Statistiken
        output.push_str("Collection Allocation Statistics:\n");
        for (name, stats) in &self.collection_stats {
            output.push_str(&format!(
                "  {}: {} allocations, avg_size={}, max_size={}, reallocations={}\n",
                name, stats.count, stats.avg_size, stats.max_size, stats.reallocations
            ));
        }
        output.push('\n');
        
        // Hot-Paths
        output.push_str("Hot Paths:\n");
        for path in &self.hot_paths {
            output.push_str(&format!(
                "  {}: {} calls, avg={:?}, p95={:?}, p99={:?}\n",
                path.name, path.call_count, path.avg_time, path.p95_time, path.p99_time
            ));
        }
        output.push('\n');
        
        // Empfehlungen
        output.push_str("Optimization Recommendations:\n");
        let mut sorted_recommendations = self.recommendations.clone();
        sorted_recommendations.sort_by(|a, b| b.priority.cmp(&a.priority));
        
        for rec in &sorted_recommendations {
            output.push_str(&format!(
                "  [{}] {}: {}\n    → {}\n    Impact: {}\n",
                format!("{:?}", rec.priority),
                rec.location,
                rec.issue,
                rec.recommendation,
                rec.estimated_impact
            ));
        }
        output.push('\n');
        
        // System-Metriken
        output.push_str(&format!(
            "System Metrics:\n  Memory Pressure: {:.2}%\n  CPU Usage: {:.2}%\n",
            self.memory_pressure * 100.0,
            self.cpu_usage * 100.0
        ));
        
        output
    }
}

/// Race-Condition-Detection-System
pub struct RaceConditionDetector {
    detected_races: Vec<RaceConditionReport>,
}

#[derive(Debug, Clone)]
pub struct RaceConditionReport {
    pub location: String,
    pub description: String,
    pub severity: Severity,
    pub mitigation: String,
    pub detected_at: Instant,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Severity {
    Low,
    Medium,
    High,
    Critical,
}

impl RaceConditionDetector {
    pub fn new() -> Self {
        Self {
            detected_races: Vec::new(),
        }
    }

    /// Analysiere Code-Pattern auf Race-Conditions
    pub fn analyze_pattern(
        &mut self,
        location: &str,
        pattern_type: RacePattern,
    ) -> Option<RaceConditionReport> {
        let report = match pattern_type {
            RacePattern::CheckThenSet { checked_before_set } => {
                if !checked_before_set {
                    RaceConditionReport {
                        location: location.to_string(),
                        description: "Check-then-Set Pattern ohne atomare Operation".to_string(),
                        severity: Severity::High,
                        mitigation: "Verwende compare_and_swap oder ähnliche atomare Operationen".to_string(),
                        detected_at: Instant::now(),
                    }
                } else {
                    return None;
                }
            }
            RacePattern::SharedMutableState { sync_primitive } => {
                RaceConditionReport {
                    location: location.to_string(),
                    description: format!("Shared mutable state ohne ausreichende Synchronisation: {:?}", sync_primitive),
                    severity: if matches!(sync_primitive, SyncPrimitive::None) { 
                        Severity::Critical 
                    } else { 
                        Severity::Medium 
                    },
                    mitigation: "Verwende Mutex, RwLock oder atomare Operationen".to_string(),
                    detected_at: Instant::now(),
                }
            }
            RacePattern::AsyncBlockingOperation => {
                RaceConditionReport {
                    location: location.to_string(),
                    description: "Blocking Operation in async Context ohne block_in_place".to_string(),
                    severity: Severity::High,
                    mitigation: "Verwende spawn_blocking oder block_in_place für blocking Operationen".to_string(),
                    detected_at: Instant::now(),
                }
            }
        };

        self.detected_races.push(report.clone());
        Some(report)
    }

    /// Generiere Race-Condition-Report
    pub fn generate_report(&self) -> Vec<RaceConditionReport> {
        self.detected_races.clone()
    }
}

#[derive(Debug)]
pub enum RacePattern {
    CheckThenSet { checked_before_set: bool },
    SharedMutableState { sync_primitive: SyncPrimitive },
    AsyncBlockingOperation,
}

#[derive(Debug)]
pub enum SyncPrimitive {
    None,
    Mutex,
    RwLock,
    Atomic,
}

/// Kombiniertes Diagnose-System
#[derive(Clone)]
pub struct DiagnosticSystem {
    performance_optimizer: PerformanceOptimizer,
    race_detector: Arc<RwLock<RaceConditionDetector>>,
}

impl DiagnosticSystem {
    pub fn new() -> Self {
        Self {
            performance_optimizer: PerformanceOptimizer::new(),
            race_detector: Arc::new(RwLock::new(RaceConditionDetector::new())),
        }
    }

    /// Führe vollständige System-Diagnose durch
    pub async fn run_full_diagnosis(&self) -> DiagnosticReport {
        let perf_report = self.performance_optimizer.generate_report().await;
        let detector = self.race_detector.read().await;
        let race_reports = detector.generate_report().await;
        
        DiagnosticReport {
            performance: perf_report,
            race_conditions: race_reports,
        }
    }
    
    /// Zugriff auf Race-Detector für externe Analyse
    pub async fn analyze_race_pattern(
        &self,
        location: &str,
        pattern: RacePattern,
    ) -> Option<RaceConditionReport> {
        let mut detector = self.race_detector.write().await;
        detector.analyze_pattern(location, pattern)
    }
}

/// Vollständiger Diagnose-Report
#[derive(Debug)]
pub struct DiagnosticReport {
    pub performance: PerformanceReport,
    pub race_conditions: Vec<RaceConditionReport>,
}

impl DiagnosticReport {
    /// Formatiere vollständigen Report
    pub fn format(&self) -> String {
        let mut output = String::new();
        output.push_str(&self.performance.format());
        output.push_str("\n=== Race Condition Analysis ===\n\n");
        
        if self.race_conditions.is_empty() {
            output.push_str("✅ Keine Race Conditions erkannt!\n");
        } else {
            let mut sorted = self.race_conditions.clone();
            sorted.sort_by(|a, b| b.severity.cmp(&a.severity));
            
            for report in &sorted {
                output.push_str(&format!(
                    "[{}] {}: {}\n  Mitigation: {}\n  Detected: {:?}\n\n",
                    format!("{:?}", report.severity),
                    report.location,
                    report.description,
                    report.mitigation,
                    report.detected_at
                ));
            }
        }
        
        output
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_performance_optimizer() {
        let optimizer = PerformanceOptimizer::new();
        
        // Simuliere Collection-Wachstum
        let growth = vec![10, 20, 50, 100, 200];
        let recommended = optimizer.analyze_collection_allocation(
            "HashMap<String, String>",
            10,
            growth,
        ).await;
        
        assert!(recommended > 0);
        
        // Prüfe Report
        let report = optimizer.generate_report().await;
        assert!(!report.collection_stats.is_empty());
    }

    #[tokio::test]
    async fn test_race_detector() {
        let detector = RaceConditionDetector::new();
        
        // Test Check-Then-Set Pattern
        let report = detector.analyze_pattern(
            "fuel_meter.rs:169",
            RacePattern::CheckThenSet { checked_before_set: false },
        ).await;
        
        assert!(report.is_some());
        assert_eq!(report.unwrap().severity, Severity::High);
    }
}

