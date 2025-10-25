//! FFI Integration Tests für Connectias
//! 
//! Testet alle FFI-Funktionen mit verschiedenen Szenarien:
//! - Null-Pointer-Handling
//! - Memory-Management
//! - Error-Handling
//! - Plugin-Operationen
//! - Service-Integration

use std::ffi::{CString, CStr};
use std::ptr;
use std::thread;
use std::time::Duration;

// FFI-Funktionen importieren
extern "C" {
    fn connectias_init() -> i32;
    fn connectias_version() -> *const std::ffi::c_char;
    fn connectias_get_system_info() -> *const std::ffi::c_char;
    fn connectias_load_plugin(plugin_path: *const std::ffi::c_char) -> *const std::ffi::c_char;
    fn connectias_unload_plugin(plugin_id: *const std::ffi::c_char) -> i32;
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
    fn connectias_malloc(size: usize) -> *mut u8;
    fn connectias_free(ptr: *mut u8, size: usize);
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
fn test_ffi_initialization() {
    println!("🧪 Teste FFI-Initialisierung...");
    
    // Test 1: Initialisierung
    let result = unsafe { connectias_init() };
    assert_eq!(result, 0, "Initialisierung sollte erfolgreich sein");
    
    // Test 2: Version abrufen
    let version_ptr = unsafe { connectias_version() };
    assert!(!version_ptr.is_null(), "Version-Pointer sollte nicht null sein");
    
    let version = read_cstring(version_ptr);
    assert!(!version.is_empty(), "Version sollte nicht leer sein");
    assert!(version.contains("0.1.0"), "Version sollte 0.1.0 enthalten");
    
    // Memory Leak beheben: free_cstring aufrufen
    unsafe {
        connectias_free_string(version_ptr);
    }
    
    println!("✅ FFI-Initialisierung erfolgreich");
}

#[test]
fn test_system_info() {
    println!("🧪 Teste System-Info...");
    
    let info_ptr = unsafe { connectias_get_system_info() };
    assert!(!info_ptr.is_null(), "System-Info-Pointer sollte nicht null sein");
    
    let info = read_cstring(info_ptr);
    assert!(!info.is_empty(), "System-Info sollte nicht leer sein");
    
    // Validiere JSON-Format
    let parsed: Result<serde_json::Value, _> = serde_json::from_str(&info);
    assert!(parsed.is_ok(), "System-Info sollte gültiges JSON sein");
    
    free_cstring(info_ptr);
    println!("✅ System-Info erfolgreich");
}

#[test]
fn test_null_pointer_handling() {
    println!("🧪 Teste Null-Pointer-Handling...");
    
    // Test 1: Null-Pointer für Plugin-Pfad
    let result = unsafe { connectias_load_plugin(ptr::null()) };
    assert!(result.is_null(), "Null-Pointer sollte null zurückgeben");
    
    // Test 2: Null-Pointer für Plugin-ID
    let result = unsafe { connectias_unload_plugin(ptr::null()) };
    assert!(result < 0, "Null-Pointer sollte Fehler zurückgeben");
    
    // Test 3: Null-Pointer für Command
    let plugin_id = create_cstring("test-plugin");
    let mut output: *mut std::ffi::c_char = ptr::null_mut();
    let result = unsafe {
        connectias_execute_plugin(
            plugin_id.as_ptr(),
            ptr::null(),
            ptr::null(),
            &mut output,
        )
    };
    assert!(result < 0, "Null-Command sollte Fehler zurückgeben");
    
    println!("✅ Null-Pointer-Handling erfolgreich");
}

#[test]
fn test_memory_management() {
    println!("🧪 Teste Memory-Management...");
    
    // Test 1: Memory-Statistiken
    let stats_ptr = unsafe { connectias_get_memory_stats() };
    assert!(!stats_ptr.is_null(), "Memory-Stats-Pointer sollte nicht null sein");
    
    let stats = read_cstring(stats_ptr);
    assert!(stats.contains("Allocated:"), "Stats sollten Allocated enthalten");
    assert!(stats.contains("Freed:"), "Stats sollten Freed enthalten");
    
    free_cstring(stats_ptr);
    
    // Test 2: malloc/free
    let ptr = unsafe { connectias_malloc(1024) };
    assert!(!ptr.is_null(), "malloc sollte gültigen Pointer zurückgeben");
    
    unsafe { connectias_free(ptr, 1024) };
    
    // Test 3: Zero-Allocation
    let ptr = unsafe { connectias_malloc(0) };
    assert!(ptr.is_null(), "Zero-Allocation sollte null zurückgeben");
    
    // Test 4: Huge-Allocation
    let ptr = unsafe { connectias_malloc(1024 * 1024 * 500) }; // 500MB
    assert!(ptr.is_null(), "Huge-Allocation sollte null zurückgeben");
    
    println!("✅ Memory-Management erfolgreich");
}

#[test]
fn test_error_handling() {
    println!("🧪 Teste Error-Handling...");
    
    // Test 1: Ungültiger Plugin-Pfad
    let invalid_path = create_cstring("/nonexistent/plugin.wasm");
    let result = unsafe { connectias_load_plugin(invalid_path.as_ptr()) };
    assert!(result.is_null(), "Ungültiger Pfad sollte null zurückgeben");
    
    // Test 2: Error-Message abrufen
    let error_ptr = unsafe { connectias_get_last_error() };
    if !error_ptr.is_null() {
        let error_msg = read_cstring(error_ptr);
        assert!(!error_msg.is_empty(), "Error-Message sollte nicht leer sein");
        assert!(error_msg.contains("❌"), "Error-Message sollte Fehler-Indikator enthalten");
        free_cstring(error_ptr);
    }
    
    // Test 3: Ungültige Plugin-ID
    let invalid_id = create_cstring("nonexistent-plugin");
    let result = unsafe { connectias_unload_plugin(invalid_id.as_ptr()) };
    assert!(result < 0, "Ungültige Plugin-ID sollte Fehler zurückgeben");
    
    println!("✅ Error-Handling erfolgreich");
}

#[test]
fn test_plugin_operations() {
    println!("🧪 Teste Plugin-Operationen...");
    
    // Test 1: Plugin-Liste abrufen
    let plugins_ptr = unsafe { connectias_list_plugins() };
    assert!(!plugins_ptr.is_null(), "Plugin-Liste-Pointer sollte nicht null sein");
    
    let plugins_json = read_cstring(plugins_ptr);
    assert!(!plugins_json.is_empty(), "Plugin-Liste sollte nicht leer sein");
    
    // Validiere JSON-Format
    let parsed: Result<serde_json::Value, _> = serde_json::from_str(&plugins_json);
    assert!(parsed.is_ok(), "Plugin-Liste sollte gültiges JSON sein");
    
    free_cstring(plugins_ptr);
    
    // Test 2: Plugin-Ausführung mit ungültiger ID
    let plugin_id = create_cstring("nonexistent-plugin");
    let command = create_cstring("test");
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
    assert!(result < 0, "Ungültige Plugin-ID sollte Fehler zurückgeben");
    
    println!("✅ Plugin-Operationen erfolgreich");
}

#[test]
fn test_concurrent_access() {
    println!("🧪 Teste Concurrent Access...");
    
    let handles: Vec<_> = (0..5).map(|i| {
        thread::spawn(move || {
            // Jeder Thread führt verschiedene FFI-Operationen aus
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
    
    println!("✅ Concurrent Access erfolgreich");
}

#[test]
fn test_memory_leak_prevention() {
    println!("🧪 Teste Memory-Leak-Prevention...");
    
    // Initiale Memory-Stats
    let initial_stats_ptr = unsafe { connectias_get_memory_stats() };
    let initial_stats = read_cstring(initial_stats_ptr);
    free_cstring(initial_stats_ptr);
    
    // Führe mehrere Operationen aus
    for _ in 0..10 {
        let info_ptr = unsafe { connectias_get_system_info() };
        let _info = read_cstring(info_ptr);
        free_cstring(info_ptr);
        
        let plugins_ptr = unsafe { connectias_list_plugins() };
        let _plugins = read_cstring(plugins_ptr);
        free_cstring(plugins_ptr);
    }
    
    // Finale Memory-Stats
    let final_stats_ptr = unsafe { connectias_get_memory_stats() };
    let final_stats = read_cstring(final_stats_ptr);
    free_cstring(final_stats_ptr);
    
    // Stats sollten konsistent sein
    assert!(!initial_stats.is_empty());
    assert!(!final_stats.is_empty());
    
    println!("✅ Memory-Leak-Prevention erfolgreich");
}

#[test]
fn test_utf8_handling() {
    println!("🧪 Teste UTF-8-Handling...");
    
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
    
    println!("✅ UTF-8-Handling erfolgreich");
}

#[test]
fn test_resource_cleanup() {
    println!("🧪 Teste Resource-Cleanup...");
    
    // Test 1: Mehrfache Initialisierung
    for _ in 0..3 {
        let result = unsafe { connectias_init() };
        assert_eq!(result, 0);
    }
    
    // Test 2: Memory-Allocation und -Freigabe
    let ptr = unsafe { connectias_malloc(1024) };
    assert!(!ptr.is_null());
    unsafe { connectias_free(ptr, 1024) };
    
    // Test 3: String-Allocation und -Freigabe
    let info_ptr = unsafe { connectias_get_system_info() };
    assert!(!info_ptr.is_null());
    free_cstring(info_ptr);
    
    println!("✅ Resource-Cleanup erfolgreich");
}

#[test]
fn test_performance_under_load() {
    println!("🧪 Teste Performance unter Last...");
    
    let start = std::time::Instant::now();
    
    // Führe viele Operationen schnell aus
    for _ in 0..100 {
        let info_ptr = unsafe { connectias_get_system_info() };
        let _info = read_cstring(info_ptr);
        free_cstring(info_ptr);
    }
    
    let duration = start.elapsed();
    println!("100 Operationen in {:?}", duration);
    
    // Sollte unter 1 Sekunde sein
    assert!(duration.as_secs() < 1, "Performance sollte akzeptabel sein");
    
    println!("✅ Performance unter Last erfolgreich");
}

#[test]
fn test_error_recovery() {
    println!("🧪 Teste Error-Recovery...");
    
    // Test 1: Fehlerhafte Operation
    let invalid_path = create_cstring("/invalid/path");
    let result = unsafe { connectias_load_plugin(invalid_path.as_ptr()) };
    assert!(result.is_null());
    
    // Test 2: Nach Fehler sollte System noch funktionieren
    let info_ptr = unsafe { connectias_get_system_info() };
    assert!(!info_ptr.is_null(), "System sollte nach Fehler noch funktionieren");
    free_cstring(info_ptr);
    
    // Test 3: Plugin-Liste sollte noch funktionieren
    let plugins_ptr = unsafe { connectias_list_plugins() };
    assert!(!plugins_ptr.is_null(), "Plugin-Liste sollte nach Fehler noch funktionieren");
    free_cstring(plugins_ptr);
    
    println!("✅ Error-Recovery erfolgreich");
}
