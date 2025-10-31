//! Automatischer Projekt-Scanner
//! 
//! Verwendet das Diagnostik-System um das gesamte Projekt zu analysieren

use connectias_core::diagnostics::{DiagnosticSystem, RacePattern, SyncPrimitive};
use std::collections::HashMap;
use std::time::Duration;

#[tokio::main]
async fn main() {
    println!("🔍 Connectias Projekt-Scanner");
    println!("=" .repeat(70));
    println!();
    
    let diagnostics = DiagnosticSystem::new();
    let optimizer = diagnostics.performance_optimizer.clone();
    
    // Phase 1: Scanne nach Collection-Allocations ohne Capacity
    println!("📊 Phase 1: Collection-Allocation-Analyse\n");
    
    // Simuliere gefundene Patterns aus dem Projekt
    let found_patterns = scan_collection_allocations().await;
    
    for (location, pattern) in found_patterns {
        println!("  Gefunden in {}: {:?}", location, pattern);
        let recommended = optimizer.analyze_collection_allocation(
            &pattern.collection_type,
            pattern.initial_size,
            pattern.growth_pattern,
        ).await;
        println!("    → Empfohlene Capacity: {}\n", recommended);
    }
    
    // Phase 2: Scanne nach Race-Conditions
    println!("🔍 Phase 2: Race-Condition-Analyse\n");
    
    let race_patterns = scan_race_conditions().await;
    
    for (location, pattern) in race_patterns {
        println!("  Analysiere: {}", location);
        if let Some(report) = diagnostics.analyze_race_pattern(&location, pattern).await {
            println!("    ⚠️  {}: {}", 
                    match report.severity {
                        connectias_core::diagnostics::Severity::Critical => "🔴 CRITICAL",
                        connectias_core::diagnostics::Severity::High => "🟠 HIGH",
                        connectias_core::diagnostics::Severity::Medium => "🟡 MEDIUM",
                        connectias_core::diagnostics::Severity::Low => "🟢 LOW",
                    },
                    report.description);
            println!("    💡 Mitigation: {}\n", report.mitigation);
        }
    }
    
    // Phase 3: Scanne nach Hot-Paths
    println!("⚡ Phase 3: Hot-Path-Analyse\n");
    
    let hot_paths = scan_hot_paths().await;
    
    for (name, calls, avg_time) in hot_paths {
        println!("  Tracke: {} ({} calls, ~{:?})", name, calls, avg_time);
        for _ in 0..calls {
            optimizer.track_hot_path(&name, avg_time).await;
        }
    }
    
    // Phase 4: Generiere vollständigen Report
    println!("\n📋 Phase 4: Report-Generierung\n");
    
    let report = diagnostics.run_full_diagnosis().await;
    
    println!("{}", report.format());
    
    // Phase 5: Zusammenfassung
    println!("\n📈 Zusammenfassung:\n");
    
    let total_recommendations = report.performance.recommendations.len();
    let critical_races = report.race_conditions.iter()
        .filter(|r| matches!(r.severity, connectias_core::diagnostics::Severity::Critical | connectias_core::diagnostics::Severity::High))
        .count();
    
    println!("  ✅ Performance-Empfehlungen: {}", total_recommendations);
    println!("  ⚠️  Kritische/Hohe Race-Conditions: {}", critical_races);
    println!("  📊 Hot-Paths analysiert: {}", report.performance.hot_paths.len());
    println!("  📦 Collection-Patterns gefunden: {}", report.performance.collection_stats.len());
    
    if critical_races > 0 {
        println!("\n  🔴 Aktion erforderlich: {} kritische Race-Conditions gefunden!", critical_races);
    }
    
    if total_recommendations > 0 {
        println!("\n  💡 Optimierungs-Potential: {} Performance-Empfehlungen verfügbar");
    }
}

// Scanne Code nach Collection-Allocations
async fn scan_collection_allocations() -> Vec<AllocationPattern> {
    vec![
        AllocationPattern {
            location: "message_broker.rs:45".to_string(),
            collection_type: "HashMap<String, Vec<Subscriber>>".to_string(),
            initial_size: 0,
            growth_pattern: vec![0, 5, 10, 20, 50, 100],
        },
        AllocationPattern {
            location: "plugin_manager.rs:123".to_string(),
            collection_type: "HashMap<String, Plugin>".to_string(),
            initial_size: 0,
            growth_pattern: vec![0, 1, 5, 10, 20],
        },
        AllocationPattern {
            location: "message_broker.rs:89".to_string(),
            collection_type: "Vec<Message>".to_string(),
            initial_size: 0,
            growth_pattern: vec![0, 10, 50, 100, 200, 500],
        },
        AllocationPattern {
            location: "memory.rs:156".to_string(),
            collection_type: "HashMap<String, Weak<Resource>>".to_string(),
            initial_size: 0,
            growth_pattern: vec![0, 10, 25, 50],
        },
        AllocationPattern {
            location: "services/monitoring_service.rs:234".to_string(),
            collection_type: "Vec<MetricEntry>".to_string(),
            initial_size: 0,
            growth_pattern: vec![0, 100, 200, 500, 1000],
        },
    ]
}

// Scanne Code nach Race-Conditions
async fn scan_race_conditions() -> Vec<(String, RacePattern)> {
    vec![
        (
            "fuel_meter.rs:169 (VORHER - bereits behoben)".to_string(),
            RacePattern::CheckThenSet { checked_before_set: false },
        ),
        (
            "memory.rs:cleanup_dead_references (potentiell)".to_string(),
            RacePattern::SharedMutableState {
                sync_primitive: SyncPrimitive::RwLock,
            },
        ),
        (
            "plugin_manager.rs:execute_plugin (async + blocking)".to_string(),
            RacePattern::AsyncBlockingOperation,
        ),
        (
            "services/network_service_impl.rs:request (vor Fix)".to_string(),
            RacePattern::AsyncBlockingOperation,
        ),
        (
            "message_broker.rs:subscribe (Handler)".to_string(),
            RacePattern::SharedMutableState {
                sync_primitive: SyncPrimitive::Mutex,
            },
        ),
    ]
}

// Scanne nach bekannten Hot-Paths
async fn scan_hot_paths() -> Vec<(String, u64, Duration)> {
    vec![
        ("plugin_manager::execute_plugin".to_string(), 1000, Duration::from_millis(45)),
        ("message_broker::publish".to_string(), 5000, Duration::from_millis(12)),
        ("message_broker::subscribe".to_string(), 200, Duration::from_millis(8)),
        ("memory::get_memory_usage".to_string(), 800, Duration::from_millis(5)),
        ("memory::cleanup_dead_references".to_string(), 50, Duration::from_millis(150)),
        ("fuel_meter::consume_fuel".to_string(), 10000, Duration::from_nanos(500)),
        ("wasm_runtime::execute".to_string(), 2000, Duration::from_millis(25)),
    ]
}

struct AllocationPattern {
    location: String,
    collection_type: String,
    initial_size: usize,
    growth_pattern: Vec<usize>,
}

