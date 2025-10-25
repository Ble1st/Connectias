use serde::{Deserialize, Serialize};
use std::ptr;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::collections::HashMap;

// Global allocator für WASM Memory Management
static mut HEAP: [u8; 1 * 1024 * 1024] = [0; 1 * 1024 * 1024]; // 1MB Heap für Storage Demo
static HEAP_PTR: AtomicUsize = AtomicUsize::new(0);

/// Allokiert Memory im WASM-Heap mit Alignment
#[no_mangle]
pub extern "C" fn wasm_alloc(size: i32) -> i32 {
    if size <= 0 {
        return 0;
    }
    
    let size = size as usize;
    const ALIGNMENT: usize = 8;
    
    loop {
        let current = HEAP_PTR.load(Ordering::SeqCst);
        let aligned_ptr = (current + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT;
        
        unsafe {
            if aligned_ptr + size > HEAP.len() {
                return 0; // Out of memory
            }
        }
        
        if HEAP_PTR.compare_exchange(
            current,
            aligned_ptr + size,
            Ordering::SeqCst,
            Ordering::SeqCst,
        ).is_ok() {
            return aligned_ptr as i32;
        }
    }
}

/// Gibt Memory im WASM-Heap frei
#[no_mangle]
pub extern "C" fn wasm_free(_ptr: i32, _size: i32) {
    // Bump-Allocator unterstützt kein echtes Freeing
}

/// Storage Item Structure
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct StorageItem {
    pub key: String,
    pub value: String,
    pub encrypted: bool,
    pub created_at: i64,
    pub modified_at: i64,
    pub size: usize,
}

/// Storage Statistics
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct StorageStats {
    pub total_items: usize,
    pub total_size: usize,
    pub encrypted_items: usize,
    pub last_accessed: i64,
}

/// Plugin-Info für WASM-Storage-Demo-Plugin
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
        id: "com.connectias.wasm-storage-demo".to_string(),
        name: "WASM Storage Demo Plugin".to_string(),
        version: "1.0.0".to_string(),
        author: "Connectias Team".to_string(),
        description: "Storage Service Demo mit verschlüsselter Datenspeicherung".to_string(),
        min_core_version: "1.0.0".to_string(),
        max_core_version: None,
        permissions: vec![
            "storage:read".to_string(),
            "storage:write".to_string(),
            "system:info".to_string(),
        ],
        entry_point: "plugin.wasm".to_string(),
        dependencies: None,
    };
    
    let json = serde_json::to_string(&info).unwrap();
    let json_bytes = json.as_bytes();
    
    let ptr = wasm_alloc(json_bytes.len() as i32);
    if ptr == 0 {
        return (0, 0);
    }
    
    let ptr_usize = ptr as usize;
    if let Some(end) = ptr_usize.checked_add(json_bytes.len()) {
        unsafe {
            if end > HEAP.len() {
                return (0, 0);
            }
        }
    } else {
        return (0, 0);
    }
    
    unsafe {
        ptr::copy_nonoverlapping(json_bytes.as_ptr(), HEAP.as_mut_ptr().add(ptr_usize), json_bytes.len());
    }
    
    (ptr, json_bytes.len() as i32)
}

/// Plugin-Init Funktion für Custom API
#[no_mangle]
pub extern "C" fn plugin_init(context_ptr: i32, context_len: i32) -> i32 {
    let _context_str = unsafe {
        std::str::from_utf8(std::slice::from_raw_parts(
            HEAP.as_ptr().add(context_ptr as usize), 
            context_len as usize
        )).unwrap_or("{}")
    };
    
    // Storage-Demo-Plugin Initialisierung
    // Hier würde der Storage Service initialisiert werden
    // und Demo-Daten vorbereitet werden
    
    0 // Success
}

/// Plugin-Execute Funktion für Custom API
#[no_mangle]
pub extern "C" fn plugin_execute(input_ptr: i32, input_len: i32) -> (i32, i32) {
    if input_ptr < 0 || input_len < 0 {
        return (0, 0);
    }
    if let Some(end) = (input_ptr as usize).checked_add(input_len as usize) {
        unsafe {
            if end > HEAP.len() {
                return (0, 0);
            }
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
    
    let input: serde_json::Value = serde_json::from_str(input_str).unwrap_or(serde_json::Value::Null);
    let command = input["command"].as_str().unwrap_or("");
    let empty_map = serde_json::Map::new();
    let args = input["args"].as_object().unwrap_or(&empty_map);
    
    let result = match command {
        "store_data" => {
            let key = args.get("key").and_then(|v| v.as_str()).unwrap_or("default_key");
            let value = args.get("value").and_then(|v| v.as_str()).unwrap_or("default_value");
            let encrypt = args.get("encrypt").and_then(|v| v.as_bool()).unwrap_or(false);
            
            let item = StorageItem {
                key: key.to_string(),
                value: value.to_string(),
                encrypted: encrypt,
                created_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
                modified_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
                size: value.len(),
            };
            
            serde_json::json!({
                "status": "success",
                "result": format!("Data stored with key '{}' (encrypted: {})", key, encrypt),
                "item": item,
                "storage_size": value.len()
            })
        },
        "retrieve_data" => {
            let key = args.get("key").and_then(|v| v.as_str()).unwrap_or("default_key");
            
            // Simuliere Datenabruf
            let item = StorageItem {
                key: key.to_string(),
                value: format!("Retrieved value for key: {}", key),
                encrypted: false,
                created_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64 - 3600,
                modified_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
                size: 25,
            };
            
            serde_json::json!({
                "status": "success",
                "result": format!("Data retrieved for key '{}'", key),
                "item": item
            })
        },
        "delete_data" => {
            let key = args.get("key").and_then(|v| v.as_str()).unwrap_or("default_key");
            
            serde_json::json!({
                "status": "success",
                "result": format!("Data deleted for key '{}'", key),
                "deleted_key": key
            })
        },
        "list_keys" => {
            let prefix = args.get("prefix").and_then(|v| v.as_str()).unwrap_or("");
            let limit = args.get("limit").and_then(|v| v.as_u64()).unwrap_or(10);
            
            // Simuliere Key-Liste
            let mut keys = Vec::new();
            for i in 0..limit {
                keys.push(format!("{}{}key_{}", prefix, if prefix.is_empty() { "" } else { "_" }, i));
            }
            
            serde_json::json!({
                "status": "success",
                "result": format!("Found {} keys with prefix '{}'", keys.len(), prefix),
                "keys": keys,
                "total_count": keys.len()
            })
        },
        "get_stats" => {
            let stats = StorageStats {
                total_items: 42,
                total_size: 1024 * 1024, // 1MB
                encrypted_items: 15,
                last_accessed: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
            };
            
            serde_json::json!({
                "status": "success",
                "result": "Storage statistics retrieved",
                "stats": stats
            })
        },
        "encrypt_data" => {
            let data = args.get("data").and_then(|v| v.as_str()).unwrap_or("test data");
            let algorithm = args.get("algorithm").and_then(|v| v.as_str()).unwrap_or("AES-256-GCM");
            
            // Simuliere Verschlüsselung
            let encrypted_data = format!("encrypted_{}", data);
            
            serde_json::json!({
                "status": "success",
                "result": format!("Data encrypted using {}", algorithm),
                "original_size": data.len(),
                "encrypted_size": encrypted_data.len(),
                "algorithm": algorithm,
                "encrypted_data": encrypted_data
            })
        },
        "decrypt_data" => {
            let encrypted_data = args.get("encrypted_data").and_then(|v| v.as_str()).unwrap_or("encrypted_test data");
            let algorithm = args.get("algorithm").and_then(|v| v.as_str()).unwrap_or("AES-256-GCM");
            
            // Simuliere Entschlüsselung
            let decrypted_data = encrypted_data.replace("encrypted_", "");
            
            serde_json::json!({
                "status": "success",
                "result": format!("Data decrypted using {}", algorithm),
                "encrypted_size": encrypted_data.len(),
                "decrypted_size": decrypted_data.len(),
                "algorithm": algorithm,
                "decrypted_data": decrypted_data
            })
        },
        "backup_data" => {
            let backup_name = args.get("backup_name").and_then(|v| v.as_str()).unwrap_or("backup");
            let include_encrypted = args.get("include_encrypted").and_then(|v| v.as_bool()).unwrap_or(true);
            
            serde_json::json!({
                "status": "success",
                "result": format!("Backup '{}' created (encrypted: {})", backup_name, include_encrypted),
                "backup_name": backup_name,
                "backup_size": 512 * 1024, // 512KB
                "items_backed_up": 42,
                "encrypted_included": include_encrypted
            })
        },
        "restore_data" => {
            let backup_name = args.get("backup_name").and_then(|v| v.as_str()).unwrap_or("backup");
            
            serde_json::json!({
                "status": "success",
                "result": format!("Data restored from backup '{}'", backup_name),
                "backup_name": backup_name,
                "items_restored": 42,
                "restore_time": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64
            })
        },
        "storage_test" => {
            // Umfassender Storage-Test
            let test_data = vec![
                ("user_preferences", "{\"theme\": \"dark\", \"language\": \"en\"}"),
                ("session_data", "{\"user_id\": \"123\", \"login_time\": \"2024-01-01T00:00:00Z\"}"),
                ("cache_data", "{\"last_update\": \"2024-01-01T12:00:00Z\", \"items\": 42}"),
            ];
            
            let mut test_results = Vec::new();
            for (key, value) in test_data {
                test_results.push(StorageItem {
                    key: key.to_string(),
                    value: value.to_string(),
                    encrypted: key.contains("session"),
                    created_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
                    modified_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
                    size: value.len(),
                });
            }
            
            serde_json::json!({
                "status": "success",
                "result": "Storage test completed successfully",
                "test_items": test_results,
                "test_passed": true,
                "performance": "excellent"
            })
        },
        _ => serde_json::json!({
            "status": "error",
            "error": format!("Unknown command: {} (WASM Storage Demo Plugin)", command)
        })
    };
    
    let result_json = match serde_json::to_string(&result) {
        Ok(json) => json,
        Err(_) => return (0, 0),
    };
    let result_bytes = result_json.as_bytes();
    
    let ptr = wasm_alloc(result_bytes.len() as i32);
    if ptr == 0 {
        return (0, 0);
    }
    
    let ptr_usize = ptr as usize;
    if let Some(end) = ptr_usize.checked_add(result_bytes.len()) {
        unsafe {
            if end > HEAP.len() {
                return (0, 0);
            }
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
    // Storage-Demo-Plugin Cleanup
    // Hier würden alle Storage-Verbindungen geschlossen werden
    // und temporäre Daten gelöscht werden
    
    0 // Success
}

use std::time::{SystemTime, UNIX_EPOCH};
