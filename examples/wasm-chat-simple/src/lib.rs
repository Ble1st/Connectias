use serde::{Deserialize, Serialize};
use std::ptr;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

// Global allocator für WASM Memory Management
static mut HEAP: [u8; 1 * 1024 * 1024] = [0; 1 * 1024 * 1024]; // 1MB Heap
static HEAP_PTR: AtomicUsize = AtomicUsize::new(0);

/// Allokiert Memory im WASM-Heap
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

/// Plugin-Info Funktion
#[no_mangle]
pub extern "C" fn plugin_get_info() -> (i32, i32) {
    let info = serde_json::json!({
        "id": "com.connectias.wasm-chat-simple",
        "name": "WASM Chat Simple Plugin",
        "version": "1.0.0",
        "author": "Connectias Team",
        "description": "Einfaches Chat-Plugin für Inter-Plugin-Kommunikation",
        "min_core_version": "1.0.0",
        "max_core_version": null,
        "permissions": ["message:send", "message:receive"],
        "entry_point": "plugin.wasm",
        "dependencies": null
    });
    
    let json = info.to_string();
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

/// Plugin-Init Funktion
#[no_mangle]
pub extern "C" fn plugin_init(_context_ptr: i32, _context_len: i32) -> i32 {
    // Chat-Plugin Initialisierung
    0 // Success
}

/// Plugin-Execute Funktion
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
        "send_message" => {
            let room_id = args.get("room_id").and_then(|v| v.as_str()).unwrap_or("general");
            let content = args.get("content").and_then(|v| v.as_str()).unwrap_or("");
            
            serde_json::json!({
                "status": "success",
                "result": format!("Message sent to room '{}': {}", room_id, content),
                "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs()
            })
        },
        "create_room" => {
            let room_name = args.get("name").and_then(|v| v.as_str()).unwrap_or("New Room");
            let room_id = format!("room_{}", SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis());
            
            serde_json::json!({
                "status": "success",
                "result": format!("Room '{}' created with ID: {}", room_name, room_id),
                "room_id": room_id
            })
        },
        "get_messages" => {
            let room_id = args.get("room_id").and_then(|v| v.as_str()).unwrap_or("general");
            let limit = args.get("limit").and_then(|v| v.as_u64()).unwrap_or(10);
            
            serde_json::json!({
                "status": "success",
                "result": format!("Retrieved {} messages from room '{}'", limit, room_id),
                "room_id": room_id,
                "message_count": limit
            })
        },
        _ => serde_json::json!({
            "status": "error",
            "error": format!("Unknown command: {} (WASM Chat Simple Plugin)", command)
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

/// Plugin-Cleanup Funktion
#[no_mangle]
pub extern "C" fn plugin_cleanup() -> i32 {
    // Chat-Plugin Cleanup
    0 // Success
}
