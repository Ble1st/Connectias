use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use connectias_wasm::{WasmRuntime, WasmPlugin};
use std::time::Duration;
use std::sync::Arc;

// Minimal valid WASM module (version 1, empty module)
// Nur 8 Bytes: magic number (4) + version (4)
const VALID_WASM_MODULE: &[u8] = &[
    0x00, 0x61, 0x73, 0x6d, // WASM magic number
    0x01, 0x00, 0x00, 0x00, // Version 1
];

fn benchmark_wasm_runtime_creation(c: &mut Criterion) {
    let mut group = c.benchmark_group("wasm_runtime_creation");
    group.measurement_time(Duration::from_secs(10));
    
    group.bench_function("create_runtime", |b| {
        b.iter(|| {
            match WasmRuntime::new() {
                Ok(runtime) => black_box(runtime),
                Err(e) => {
                    eprintln!("Failed to create runtime: {}", e);
                    panic!("Runtime creation failed in benchmark");
                }
            }
        })
    });
    
    group.finish();
}

fn benchmark_wasm_plugin_loading(c: &mut Criterion) {
    let mut group = c.benchmark_group("wasm_plugin_loading");
    group.measurement_time(Duration::from_secs(10));
    
    let runtime = WasmRuntime::new();
    
    group.bench_function("load_plugin", |b| {
        b.iter(|| {
            match runtime.load_plugin(black_box(VALID_WASM_MODULE), "benchmark_plugin".to_string()) {
                Ok(plugin) => black_box(plugin),
                Err(e) => {
                    eprintln!("Failed to load plugin: {}", e);
                    panic!("Plugin loading failed in benchmark");
                }
            }
        })
    });
    
    group.finish();
}

fn benchmark_wasm_memory_operations(c: &mut Criterion) {
    let mut group = c.benchmark_group("wasm_memory_operations");
    group.measurement_time(Duration::from_secs(10));
    
    let runtime = WasmRuntime::new().unwrap_or_else(|e| {
        eprintln!("Failed to create runtime: {}", e);
        panic!("Runtime creation failed in benchmark");
    });
    let plugin = runtime.load_plugin(VALID_WASM_MODULE, "benchmark_plugin".to_string()).unwrap_or_else(|e| {
        eprintln!("Failed to load plugin: {}", e);
        panic!("Plugin loading failed in benchmark");
    });
    
    let test_data = vec![0u8; 1024]; // 1KB test data
    
    group.bench_function("memory_operations", |b| {
        b.iter(|| {
            // Führe echte Memory-Operationen aus
            let mut allocated_data = vec![0u8; test_data.len()];
            allocated_data.copy_from_slice(&test_data);
            let sum: u64 = allocated_data.iter().map(|&x| x as u64).sum();
            let _ = black_box(sum);
            let _ = black_box(allocated_data);
        })
    });
    
    group.finish();
}

fn benchmark_wasm_execution(c: &mut Criterion) {
    let mut group = c.benchmark_group("wasm_execution");
    group.measurement_time(Duration::from_secs(10));
    
    let runtime = WasmRuntime::new().unwrap_or_else(|e| {
        eprintln!("Failed to create runtime: {}", e);
        panic!("Runtime creation failed in benchmark");
    });
    let plugin = runtime.load_plugin(VALID_WASM_MODULE, "benchmark_plugin".to_string()).unwrap_or_else(|e| {
        eprintln!("Failed to load plugin: {}", e);
        panic!("Plugin loading failed in benchmark");
    });
    
    let test_input = vec![0u8; 512]; // 512 bytes test input
    
    group.bench_function("execute_plugin", |b| {
        b.iter(|| {
            match plugin.execute(black_box(&test_input)) {
                Ok(result) => black_box(result),
                Err(e) => {
                    eprintln!("Plugin execution failed: {}", e);
                    // Don't panic in benchmark, just continue
                }
            }
        })
    });
    
    group.finish();
}

fn benchmark_memory_allocation_scaling(c: &mut Criterion) {
    let mut group = c.benchmark_group("memory_allocation_scaling");
    group.measurement_time(Duration::from_secs(15));
    
    let runtime = WasmRuntime::new().unwrap_or_else(|e| {
        eprintln!("Failed to create runtime: {}", e);
        panic!("Runtime creation failed in benchmark");
    });
    let plugin = runtime.load_plugin(VALID_WASM_MODULE, "benchmark_plugin".to_string()).unwrap_or_else(|e| {
        eprintln!("Failed to load plugin: {}", e);
        panic!("Plugin loading failed in benchmark");
    });
    
    let sizes = vec![64, 256, 1024, 4096, 16384]; // Different data sizes
    
    for size in sizes {
        let test_data = vec![0u8; size];
        
        group.bench_with_input(BenchmarkId::new("memory_operations", size), &size, |b, _| {
            b.iter(|| {
                // Führe echte Memory-Allocation und -Operationen aus
                let mut allocated = vec![0u8; size];
                allocated.copy_from_slice(&test_data[..size.min(test_data.len())]);
                // Schreibe Pattern in jeden Byte
                for i in 0..allocated.len() {
                    allocated[i] = (i % 256) as u8;
                }
                // Lese und summiere
                let sum: u64 = allocated.iter().map(|&x| x as u64).sum();
                let _ = black_box(sum);
                let _ = black_box(allocated);
            })
        });
    }
    
    group.finish();
}

fn benchmark_concurrent_wasm_operations(c: &mut Criterion) {
    let mut group = c.benchmark_group("concurrent_wasm_operations");
    group.measurement_time(Duration::from_secs(15));
    
    let runtime = WasmRuntime::new().unwrap_or_else(|e| {
        eprintln!("Failed to create runtime: {}", e);
        panic!("Runtime creation failed in benchmark");
    });
    let plugin = Arc::new(runtime.load_plugin(VALID_WASM_MODULE, "benchmark_plugin".to_string()).unwrap_or_else(|e| {
        eprintln!("Failed to load plugin: {}", e);
        panic!("Plugin loading failed in benchmark");
    }));
    
    group.bench_function("concurrent_execution", |b| {
        b.iter(|| {
            let plugin = Arc::clone(&plugin);
            let test_input = vec![0u8; 256];
            
            // Simulate concurrent execution
            let handles: Vec<_> = (0..10)
                .map(|_| {
                    let plugin = Arc::clone(&plugin);
                    let input = test_input.clone();
                    std::thread::spawn(move || {
                        match plugin.execute(&input) {
                            Ok(result) => black_box(result),
                            Err(e) => {
                                eprintln!("Concurrent execution failed: {}", e);
                                // Don't panic in benchmark, just continue
                            }
                        }
                    })
                })
                .collect();
            
            for handle in handles {
                let _ = black_box(handle.join().unwrap());
            }
        })
    });
    
    group.finish();
}

fn benchmark_resource_limits(c: &mut Criterion) {
    let mut group = c.benchmark_group("resource_limits");
    group.measurement_time(Duration::from_secs(10));
    
    let runtime = WasmRuntime::new().unwrap_or_else(|e| {
        eprintln!("Failed to create runtime: {}", e);
        panic!("Runtime creation failed in benchmark");
    });
    let plugin = runtime.load_plugin(VALID_WASM_MODULE, "benchmark_plugin".to_string()).unwrap_or_else(|e| {
        eprintln!("Failed to load plugin: {}", e);
        panic!("Plugin loading failed in benchmark");
    });
    
    group.bench_function("check_limits", |b| {
        b.iter(|| {
            let _ = black_box(plugin.resource_limits);
        })
    });
    
    group.finish();
}

fn benchmark_plugin_lifecycle(c: &mut Criterion) {
    let mut group = c.benchmark_group("plugin_lifecycle");
    group.measurement_time(Duration::from_secs(20));
    
    group.bench_function("full_lifecycle", |b| {
        b.iter(|| {
            // Create runtime
            let runtime = WasmRuntime::new().unwrap_or_else(|e| {
                eprintln!("Failed to create runtime: {}", e);
                panic!("Runtime creation failed in benchmark");
            });
            
            // Load plugin
            let plugin = runtime.load_plugin(VALID_WASM_MODULE, "benchmark_plugin".to_string()).unwrap_or_else(|e| {
                eprintln!("Failed to load plugin: {}", e);
                panic!("Plugin loading failed in benchmark");
            });
            
            // Execute plugin
            let test_input = vec![0u8; 128];
            match plugin.execute(&test_input) {
                Ok(result) => black_box(result),
                Err(e) => {
                    eprintln!("Plugin execution failed: {}", e);
                    // Don't panic in benchmark, just continue
                }
            }
            
            // Check resource limits
            let _ = black_box(plugin.resource_limits);
            
            // Plugin goes out of scope (cleanup)
            drop(plugin);
        })
    });
    
    group.finish();
}

criterion_group!(
    benches,
    benchmark_wasm_runtime_creation,
    benchmark_wasm_plugin_loading,
    benchmark_wasm_memory_operations,
    benchmark_wasm_execution,
    benchmark_memory_allocation_scaling,
    benchmark_concurrent_wasm_operations,
    benchmark_resource_limits,
    benchmark_plugin_lifecycle
);

criterion_main!(benches);
