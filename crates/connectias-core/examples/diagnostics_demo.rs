//! Demo für das automatische Diagnostik-System
//! 
//! Zeigt automatische Performance-Optimierungen und Race-Condition-Detection

use connectias_core::diagnostics::{DiagnosticSystem, PerformanceOptimizer};
use std::time::Duration;

#[tokio::main]
async fn main() {
    println!("🚀 Connectias Automatic Diagnostics & Performance Optimizer\n");
    println!("=" .repeat(60));
    
    // Erstelle Diagnostik-System
    let diagnostics = DiagnosticSystem::new();
    let optimizer = PerformanceOptimizer::new();
    
    println!("\n📊 Phase 1: Performance-Analyse\n");
    
    // Simuliere verschiedene Collection-Allocations
    println!("Analyzing HashMap allocations...");
    for i in 0..5 {
        let growth_pattern = (0..i*10+10).map(|x| x).collect::<Vec<_>>();
        optimizer.analyze_collection_allocation(
            "HashMap<String, PluginData>",
            10,
            growth_pattern,
        ).await;
    }
    
    println!("Analyzing Vec allocations...");
    for i in 0..3 {
        let growth_pattern = (0..i*5+5).map(|x| x).collect::<Vec<_>>();
        optimizer.analyze_collection_allocation(
            "Vec<Message>",
            5,
            growth_pattern,
        ).await;
    }
    
    // Track Hot-Paths
    println!("Tracking hot paths...");
    optimizer.track_hot_path("plugin_manager::execute_plugin", Duration::from_millis(45)).await;
    optimizer.track_hot_path("message_broker::publish", Duration::from_millis(12)).await;
    optimizer.track_hot_path("message_broker::subscribe", Duration::from_millis(8)).await;
    for i in 0..100 {
        // Simuliere variierende Execution-Times
        let variance = (i % 20) as u64;
        optimizer.track_hot_path("plugin_manager::execute_plugin", Duration::from_millis(40 + variance)).await;
    }
    
    println!("\n🔍 Phase 2: Race-Condition-Detection\n");
    
    // Simuliere Race-Condition-Analyse
    diagnostics.analyze_race_pattern(
        "fuel_meter.rs:169 (vor Fix)",
        connectias_core::diagnostics::RacePattern::CheckThenSet {
            checked_before_set: false,
        },
    ).await;
    
    diagnostics.analyze_race_pattern(
        "memory.rs:old_pattern (vor Fix)",
        connectias_core::diagnostics::RacePattern::SharedMutableState {
            sync_primitive: connectias_core::diagnostics::SyncPrimitive::None,
        },
    ).await;
    
    println!("\n📋 Phase 3: Report-Generierung\n");
    
    // Generiere vollständigen Report
    let report = diagnostics.run_full_diagnosis().await;
    
    println!("{}", report.format());
    
    println!("\n✅ Diagnose abgeschlossen!");
    println!("\n💡 Nächste Schritte:");
    println!("   - Implementiere empfohlene Capacity-Optimierungen");
    println!("   - Prüfe identifizierte Hot-Paths auf Optimierungsmöglichkeiten");
    println!("   - Behebe erkannte Race-Conditions");
}

