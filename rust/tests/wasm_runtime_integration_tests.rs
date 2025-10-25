//! WASM Runtime Integration Tests für Connectias
//! 
//! Testet die Integration der WASM Runtime mit dem Plugin-System:
//! - WASM Runtime Initialisierung
//! - Plugin-Loading über FFI
//! - Plugin-Ausführung
//! - Memory-Management

use std::path::Path;
use std::ffi::{CString, CStr};
use std::ptr;
use std::thread;
use std::time::Duration;

// FFI-Funktionen importieren
extern "C" {
    fn connectias_init() -> i32;
    fn connectias_load_plugin(plugin_path: *const std::ffi::c_char) -> *const std::ffi::c_char;
    fn connectias_execute_plugin(
        plugin_id: *const std::ffi::c_char,
        command: *const std::ffi::c_char,
        args_json: *const std::ffi::c_char,
        output_json: *mut *mut std::ffi::c_char,
    ) -> i32;
    fn connectias_list_plugins() -> *const std::ffi::c_char;
    fn connectias_get_last_error() -> *const std::ffi::c_char;
    fn connectias_free_string(ptr: *const std::ffi::c_char);
    fn connectias_get_memory_stats() -> *const std::ffi::c_char;
}

/// Helper-Funktion für sichere C-String-Erstellung
fn create_cstring(s: &str) -> CString {
    CString::new(s).expect("CString creation failed")
}

/// Helper-Funktion für sichere C-String-Lesung
fn read_cstring(ptr: *const std::ffi::c_char) -> String {
    if ptr.is_null() {
        return String::new();
    }
    unsafe {
        CStr::from_ptr(ptr).to_string_lossy().into_owned()
    }
}

/// Helper-Funktion für sichere String-Freigabe
fn free_cstring(ptr: *const std::ffi::c_char) {
    if !ptr.is_null() {
        unsafe {
            connectias_free_string(ptr);
        }
    }
}

#[test]
fn test_wasm_runtime_initialization() {
    println!("🧪 Teste WASM Runtime Initialisierung...");
    
    // Test 1: FFI-Initialisierung
    let result = unsafe { connectias_init() };
    assert_eq!(result, 0, "FFI-Initialisierung sollte erfolgreich sein");
    
    // Test 2: Memory-Stats nach Initialisierung
    let stats_ptr = unsafe { connectias_get_memory_stats() };
    assert!(!stats_ptr.is_null(), "Memory-Stats sollten verfügbar sein");
    
    let stats = read_cstring(stats_ptr);
    assert!(!stats.is_empty(), "Memory-Stats sollten nicht leer sein");
    free_cstring(stats_ptr);
    
    println!("✅ WASM Runtime Initialisierung erfolgreich");
}

#[test]
fn test_plugin_loading_simulation() {
    println!("🧪 Teste Plugin-Loading Simulation...");
    
    // Test 1: Ungültiger Plugin-Pfad (simuliert WASM-Plugin-Loading)
    let invalid_path = create_cstring("/nonexistent/plugin.wasm");
    let result = unsafe { connectias_load_plugin(invalid_path.as_ptr()) };
    
    // Sollte null sein (Plugin existiert nicht)
    assert!(result.is_null(), "Ungültiger Plugin-Pfad sollte null zurückgeben");
    
    // Test 2: Error-Message prüfen
    let error_ptr = unsafe { connectias_get_last_error() };
    if !error_ptr.is_null() {
        let error_msg = read_cstring(error_ptr);
        assert!(!error_msg.is_empty(), "Error-Message sollte nicht leer sein");
        free_cstring(error_ptr);
    }
    
    // Test 3: Plugin-Liste sollte leer sein
    let plugins_ptr = unsafe { connectias_list_plugins() };
    assert!(!plugins_ptr.is_null(), "Plugin-Liste sollte verfügbar sein");
    
    let plugins_json = read_cstring(plugins_ptr);
    assert!(!plugins_json.is_empty(), "Plugin-Liste sollte nicht leer sein");
    
    // Validiere JSON-Format
    let parsed: Result<serde_json::Value, _> = serde_json::from_str(&plugins_json);
    assert!(parsed.is_ok(), "Plugin-Liste sollte gültiges JSON sein");
    
    free_cstring(plugins_ptr);
    
    println!("✅ Plugin-Loading Simulation erfolgreich");
}

#[test]
fn test_plugin_execution_simulation() {
    println!("🧪 Teste Plugin-Ausführung Simulation...");
    
    // Test 1: Plugin-Ausführung mit ungültiger Plugin-ID
    let plugin_id = create_cstring("nonexistent-plugin");
    let command = create_cstring("hello");
    let args = create_cstring(r#"{"name": "Test"}"#);
    let mut output: *mut std::ffi::c_char = ptr::null_mut();
    
    let result = unsafe {
        connectias_execute_plugin(
            plugin_id.as_ptr(),
            command.as_ptr(),
            args.as_ptr(),
            &mut output,
        )
    };
    
    // Sollte Fehler zurückgeben
    assert!(result < 0, "Ungültige Plugin-ID sollte Fehler zurückgeben");
    
    // Test 2: Error-Message prüfen
    let error_ptr = unsafe { connectias_get_last_error() };
    if !error_ptr.is_null() {
        let error_msg = read_cstring(error_ptr);
        assert!(!error_msg.is_empty(), "Error-Message sollte nicht leer sein");
        free_cstring(error_ptr);
    }
    
    println!("✅ Plugin-Ausführung Simulation erfolgreich");
}

#[test]
fn test_memory_management_integration() {
    println!("🧪 Teste Memory-Management Integration...");
    
    // Test 1: Mehrfache Operationen für Memory-Tracking
    for i in 0..10 {
        let info_ptr = unsafe { connectias_get_system_info() };
        let _info = read_cstring(info_ptr);
        free_cstring(info_ptr);
        
        let plugins_ptr = unsafe { connectias_list_plugins() };
        let _plugins = read_cstring(plugins_ptr);
        free_cstring(plugins_ptr);
        
        let stats_ptr = unsafe { connectias_get_memory_stats() };
        let _stats = read_cstring(stats_ptr);
        free_cstring(stats_ptr);
    }
    
    // Test 2: Finale Memory-Stats
    let final_stats_ptr = unsafe { connectias_get_memory_stats() };
    let final_stats = read_cstring(final_stats_ptr);
    assert!(!final_stats.is_empty(), "Finale Memory-Stats sollten verfügbar sein");
    free_cstring(final_stats_ptr);
    
    println!("✅ Memory-Management Integration erfolgreich");
}

#[test]
fn test_concurrent_wasm_operations() {
    println!("🧪 Teste Concurrent WASM Operations...");
    
    let handles: Vec<_> = (0..5).map(|i| {
        thread::spawn(move || {
            // Jeder Thread führt verschiedene WASM-Operationen aus
            match i % 3 {
                0 => {
                    let info_ptr = unsafe { connectias_get_system_info() };
                    let info = read_cstring(info_ptr);
                    free_cstring(info_ptr);
                    assert!(!info.is_empty());
                }
                1 => {
                    let plugins_ptr = unsafe { connectias_list_plugins() };
                    let plugins = read_cstring(plugins_ptr);
                    free_cstring(plugins_ptr);
                    assert!(!plugins.is_empty());
                }
                _ => {
                    let stats_ptr = unsafe { connectias_get_memory_stats() };
                    let stats = read_cstring(stats_ptr);
                    free_cstring(stats_ptr);
                    assert!(!stats.is_empty());
                }
            }
        })
    }).collect();
    
    // Warte auf alle Threads
    for handle in handles {
        handle.join().expect("Thread should complete");
    }
    
    println!("✅ Concurrent WASM Operations erfolgreich");
}

#[test]
fn test_wasm_plugin_lifecycle() {
    println!("🧪 Teste WASM Plugin Lifecycle...");
    
    // Test 1: Plugin-Loading (simuliert)
    let plugin_path = create_cstring("/tmp/test-plugin.wasm");
    let result = unsafe { connectias_load_plugin(plugin_path.as_ptr()) };
    
    // Sollte null sein (Plugin existiert nicht)
    assert!(result.is_null(), "Nicht-existierendes Plugin sollte null zurückgeben");
    
    // Test 2: Plugin-Liste prüfen
    let plugins_ptr = unsafe { connectias_list_plugins() };
    let plugins_json = read_cstring(plugins_ptr);
    free_cstring(plugins_ptr);
    
    // Plugin-Liste sollte leer sein
    let parsed: Result<serde_json::Value, _> = serde_json::from_str(&plugins_json);
    assert!(parsed.is_ok(), "Plugin-Liste sollte gültiges JSON sein");
    
    let plugins = parsed.unwrap();
    if let Some(plugins_array) = plugins.as_array() {
        assert_eq!(plugins_array.len(), 0, "Plugin-Liste sollte leer sein");
    }
    
    // Test 3: Plugin-Ausführung (simuliert)
    let plugin_id = create_cstring("test-plugin");
    let command = create_cstring("hello");
    let args = create_cstring("{}");
    let mut output: *mut std::ffi::c_char = ptr::null_mut();
    
    let result = unsafe {
        connectias_execute_plugin(
            plugin_id.as_ptr(),
            command.as_ptr(),
            args.as_ptr(),
            &mut output,
        )
    };
    
    // Sollte Fehler zurückgeben (Plugin nicht geladen)
    assert!(result < 0, "Nicht-geladenes Plugin sollte Fehler zurückgeben");
    
    println!("✅ WASM Plugin Lifecycle erfolgreich");
}

#[test]
fn test_wasm_error_recovery() {
    println!("🧪 Teste WASM Error Recovery...");
    
    // Test 1: Fehlerhafte Operation
    let invalid_path = create_cstring("");
    let result = unsafe { connectias_load_plugin(invalid_path.as_ptr()) };
    assert!(result.is_null(), "Leerer Pfad sollte null zurückgeben");
    
    // Test 2: Nach Fehler sollte System noch funktionieren
    let info_ptr = unsafe { connectias_get_system_info() };
    assert!(!info_ptr.is_null(), "System sollte nach Fehler noch funktionieren");
    free_cstring(info_ptr);
    
    // Test 3: Plugin-Liste sollte noch funktionieren
    let plugins_ptr = unsafe { connectias_list_plugins() };
    assert!(!plugins_ptr.is_null(), "Plugin-Liste sollte nach Fehler noch funktionieren");
    free_cstring(plugins_ptr);
    
    // Test 4: Memory-Stats sollten noch funktionieren
    let stats_ptr = unsafe { connectias_get_memory_stats() };
    assert!(!stats_ptr.is_null(), "Memory-Stats sollten nach Fehler noch funktionieren");
    free_cstring(stats_ptr);
    
    println!("✅ WASM Error Recovery erfolgreich");
}

#[test]
fn test_wasm_performance_under_load() {
    println!("🧪 Teste WASM Performance unter Last...");
    
    let start = std::time::Instant::now();
    
    // Führe viele WASM-Operationen schnell aus
    for _ in 0..50 {
        let info_ptr = unsafe { connectias_get_system_info() };
        let _info = read_cstring(info_ptr);
        free_cstring(info_ptr);
        
        let plugins_ptr = unsafe { connectias_list_plugins() };
        let _plugins = read_cstring(plugins_ptr);
        free_cstring(plugins_ptr);
    }
    
    let duration = start.elapsed();
    println!("50 WASM-Operationen in {:?}", duration);
    
    // Sollte unter 1 Sekunde sein
    assert!(duration.as_secs() < 1, "WASM-Performance sollte akzeptabel sein");
    
    println!("✅ WASM Performance unter Last erfolgreich");
}

#[test]
fn test_wasm_utf8_handling() {
    println!("🧪 Teste WASM UTF-8-Handling...");
    
    // Test 1: Gültige UTF-8-Strings
    let valid_strings = vec![
        "Hello World",
        "Hallo Welt",
        "Привет мир",
        "你好世界",
        "مرحبا بالعالم",
    ];
    
    for s in valid_strings {
        let cstring = create_cstring(s);
        let result = unsafe { connectias_load_plugin(cstring.as_ptr()) };
        // Sollte null sein (Plugin existiert nicht), aber kein UTF-8-Fehler
        assert!(result.is_null());
    }
    
    // Test 2: Leere Strings
    let empty = create_cstring("");
    let result = unsafe { connectias_load_plugin(empty.as_ptr()) };
    assert!(result.is_null());
    
    println!("✅ WASM UTF-8-Handling erfolgreich");
}

#[test]
fn test_wasm_resource_cleanup() {
    println!("🧪 Teste WASM Resource Cleanup...");
    
    // Test 1: Mehrfache Initialisierung
    for _ in 0..3 {
        let result = unsafe { connectias_init() };
        assert_eq!(result, 0);
    }
    
    // Test 2: String-Allocation und -Freigabe
    let info_ptr = unsafe { connectias_get_system_info() };
    assert!(!info_ptr.is_null());
    free_cstring(info_ptr);
    
    // Test 3: Plugin-Liste Allocation und -Freigabe
    let plugins_ptr = unsafe { connectias_list_plugins() };
    assert!(!plugins_ptr.is_null());
    free_cstring(plugins_ptr);
    
    // Test 4: Memory-Stats Allocation und -Freigabe
    let stats_ptr = unsafe { connectias_get_memory_stats() };
    assert!(!stats_ptr.is_null());
    free_cstring(stats_ptr);
    
    println!("✅ WASM Resource Cleanup erfolgreich");
}
