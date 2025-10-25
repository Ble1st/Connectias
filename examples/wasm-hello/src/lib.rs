use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::alloc::{alloc, dealloc, Layout};
use std::ptr;
use std::sync::atomic::{AtomicUsize, Ordering};

// Global allocator für WASM Memory Management
static mut HEAP: [u8; 1024 * 1024] = [0; 1024 * 1024]; // 1MB Heap
static HEAP_PTR: AtomicUsize = AtomicUsize::new(0);

/// Allokiert Memory im WASM-Heap mit Alignment
#[no_mangle]
pub extern "C" fn alloc(size: i32) -> i32 {
    if size <= 0 {
        return 0;
    }
    
    let size = size as usize;
    const ALIGNMENT: usize = 8;
    
    loop {
        let current = HEAP_PTR.load(Ordering::SeqCst);
        // Runde auf Alignment auf
        let aligned_ptr = (current + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT;
        
        if aligned_ptr + size > HEAP.len() {
            return 0; // Out of memory
        }
        
        // Atomares Compare-and-Swap für thread-safe Allokation
        if HEAP_PTR.compare_exchange(
            current,
            aligned_ptr + size,
            Ordering::SeqCst,
            Ordering::SeqCst,
        ).is_ok() {
            return aligned_ptr as i32;
        }
        // Bei Konflikt erneut versuchen
    }
}

/// Gibt Memory im WASM-Heap frei
#[no_mangle]
pub extern "C" fn free(_ptr: i32, _size: i32) {
    // Hinweis: Dieser Bump-Allocator unterstützt kein echtes Freeing
    // Memory wird nur bei Programm-Ende freigegeben
    log::warn!("free() called on bump allocator - memory not reclaimed");
}

/// Plugin-Info für WASM-Plugin
#[derive(Serialize, Deserialize)]
pub struct PluginInfo {
    pub id: String,
    pub name: String,
    pub version: String,
    pub author: String,
    pub description: String,
    pub min_core_version: String,
    pub max_core_version: Option<String>,
    pub permissions: Vec<String>,
    pub entry_point: String,
    pub dependencies: Option<Vec<String>>,
}

/// Plugin-Info Funktion für Custom API
#[no_mangle]
pub extern "C" fn plugin_get_info() -> (i32, i32) {
    let info = PluginInfo {
        id: "com.example.wasm-hello".to_string(),
        name: "WASM Hello Plugin".to_string(),
        version: "1.0.0".to_string(),
        author: "Connectias Team".to_string(),
        description: "Simple WASM plugin that greets users".to_string(),
        min_core_version: "1.0.0".to_string(),
        max_core_version: None,
        permissions: vec!["Storage".to_string()],
        entry_point: "plugin.wasm".to_string(),
        dependencies: None,
    };
    
    let json = serde_json::to_string(&info).unwrap();
    let json_bytes = json.as_bytes();
    
    // Allokiere Memory und kopiere JSON
    let ptr = alloc(json_bytes.len() as i32);
    if ptr == 0 {
        return (0, 0); // Out of memory
    }
    
    // Validiere Bounds vor unsafe copy
    let ptr_usize = ptr as usize;
    if let Some(end) = ptr_usize.checked_add(json_bytes.len()) {
        if end > HEAP.len() {
            return (0, 0); // Bounds check failed
        }
    } else {
        return (0, 0); // Overflow
    }
    
    unsafe {
        ptr::copy_nonoverlapping(json_bytes.as_ptr(), HEAP.as_mut_ptr().add(ptr_usize), json_bytes.len());
    }
    
    (ptr, json_bytes.len() as i32)
}

/// Plugin-Init Funktion für Custom API
#[no_mangle]
pub extern "C" fn plugin_init(context_ptr: i32, context_len: i32) -> i32 {
    let context_str = unsafe {
        std::str::from_utf8(std::slice::from_raw_parts(
            HEAP.as_ptr().add(context_ptr as usize), 
            context_len as usize
        )).unwrap_or("{}")
    };
    
    // Log initialization (in production würde hier echte Logging stattfinden)
    // println!("WASM Plugin initialized with context: {}", context_str);
    
    // In einer echten Implementierung würde hier der PluginContext
    // deserialisiert und die Services initialisiert werden
    // context: PluginContext {
    //     plugin_id: String,
    //     storage: Arc<dyn StorageService>,
    //     network: Arc<dyn NetworkService>,
    //     logger: Arc<dyn Logger>,
    //     system_info: Arc<dyn SystemInfo>,
    // }
    
    0 // Success (0 = OK, non-zero = Error)
}

/// Plugin-Execute Funktion für Custom API
#[no_mangle]
pub extern "C" fn plugin_execute(input_ptr: i32, input_len: i32) -> (i32, i32) {
    // Validiere Input-Parameter
    if input_ptr < 0 || input_len < 0 {
        return (0, 0);
    }
    if let Some(end) = (input_ptr as usize).checked_add(input_len as usize) {
        if end > HEAP.len() {
            return (0, 0);
        }
    } else {
        return (0, 0);
    }
    
    let input_str = unsafe {
        std::str::from_utf8(std::slice::from_raw_parts(
            HEAP.as_ptr().add(input_ptr as usize), 
            input_len as usize
        )).unwrap_or("{}")
    };
    
    // Parse input JSON
    let input: serde_json::Value = serde_json::from_str(input_str).unwrap_or(serde_json::Value::Null);
    let command = input["command"].as_str().unwrap_or("");
    let args = input["args"].as_object().unwrap_or(&serde_json::Map::new());
    
    let result = match command {
        "hello" => {
            let name = args.get("name").and_then(|v| v.as_str()).unwrap_or("World");
            serde_json::json!({
                "status": "success",
                "result": format!("Hello, {}! This is a WASM plugin with enhanced services.", name)
            })
        },
        "calculate" => {
            let a: f64 = args.get("a").and_then(|v| v.as_f64()).unwrap_or(0.0);
            let b: f64 = args.get("b").and_then(|v| v.as_f64()).unwrap_or(0.0);
            let result = a + b;
            serde_json::json!({
                "status": "success",
                "result": format!("Result: {} (calculated with enhanced WASM plugin)", result)
            })
        },
        "storage_test" => {
            serde_json::json!({
                "status": "success",
                "result": "Storage service test: Plugin data would be stored here"
            })
        },
        "network_test" => {
            serde_json::json!({
                "status": "success",
                "result": "Network service test: HTTP requests would be made here"
            })
        },
        "system_info" => {
            serde_json::json!({
                "status": "success",
                "result": "System info: OS and hardware information would be retrieved here"
            })
        },
        _ => serde_json::json!({
            "status": "error",
            "error": format!("Unknown command: {} (WASM plugin with enhanced services)", command)
        })
    };
    
    let result_json = match serde_json::to_string(&result) {
        Ok(json) => json,
        Err(_) => return (0, 0), // Serialisierungsfehler
    };
    let result_bytes = result_json.as_bytes();
    
    // Allokiere Memory für Result
    let ptr = alloc(result_bytes.len() as i32);
    if ptr == 0 {
        return (0, 0); // Out of memory
    }
    
    // Validiere Bounds vor unsafe copy
    let ptr_usize = ptr as usize;
    if let Some(end) = ptr_usize.checked_add(result_bytes.len()) {
        if end > HEAP.len() {
            return (0, 0);
        }
    } else {
        return (0, 0);
    }
    
    unsafe {
        ptr::copy_nonoverlapping(result_bytes.as_ptr(), HEAP.as_mut_ptr().add(ptr_usize), result_bytes.len());
    }
    
    (ptr, result_bytes.len() as i32)
}

/// Plugin-Cleanup Funktion für Custom API
#[no_mangle]
pub extern "C" fn plugin_cleanup() -> i32 {
    // Log cleanup (in production würde hier echte Logging stattfinden)
    // println!("WASM Plugin cleanup");
    
    // In einer echten Implementierung würde hier:
    // - Services cleanup
    // - Memory cleanup
    // - Resource cleanup
    
    0 // Success
}

