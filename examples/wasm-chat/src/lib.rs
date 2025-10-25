use serde::{Deserialize, Serialize};
use std::ptr;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

// Global allocator für WASM Memory Management
static mut HEAP: [u8; 2 * 1024 * 1024] = [0; 2 * 1024 * 1024]; // 2MB Heap für Chat
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

/// Chat Message Structure
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ChatMessage {
    pub id: String,
    pub sender: String,
    pub recipient: Option<String>, // None für Broadcast
    pub content: String,
    pub timestamp: i64,
    pub message_type: MessageType,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub enum MessageType {
    Text,
    Image,
    File,
    System,
}

/// Chat Room Structure
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ChatRoom {
    pub id: String,
    pub name: String,
    pub participants: Vec<String>,
    pub messages: Vec<ChatMessage>,
    pub created_at: i64,
}

/// Plugin-Info für WASM-Chat-Plugin
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
        id: "com.connectias.wasm-chat".to_string(),
        name: "WASM Chat Plugin".to_string(),
        version: "1.0.0".to_string(),
        author: "Connectias Team".to_string(),
        description: "Chat-Plugin mit Inter-Plugin-Kommunikation über Message Broker".to_string(),
        min_core_version: "1.0.0".to_string(),
        max_core_version: None,
        permissions: vec![
            "message:send".to_string(),
            "message:receive".to_string(),
            "storage:read".to_string(),
            "storage:write".to_string(),
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
    
    // Chat-Plugin Initialisierung
    // Hier würde der Message-Broker registriert werden
    // und Chat-Rooms initialisiert werden
    
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
        "send_message" => {
            let room_id = args.get("room_id").and_then(|v| v.as_str()).unwrap_or("general");
            let content = args.get("content").and_then(|v| v.as_str()).unwrap_or("");
            let recipient = args.get("recipient").and_then(|v| v.as_str());
            
            let message = ChatMessage {
                id: format!("msg_{}", SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis()),
                sender: "wasm-chat-plugin".to_string(),
                recipient: recipient.map(|s| s.to_string()),
                content: content.to_string(),
                timestamp: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
                message_type: MessageType::Text,
            };
            
            serde_json::json!({
                "status": "success",
                "result": format!("Message sent to room '{}': {}", room_id, content),
                "message_id": message.id,
                "timestamp": message.timestamp
            })
        },
        "create_room" => {
            let room_name = args.get("name").and_then(|v| v.as_str()).unwrap_or("New Room");
            let room_id = format!("room_{}", SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis());
            
            let room = ChatRoom {
                id: room_id.clone(),
                name: room_name.to_string(),
                participants: vec!["wasm-chat-plugin".to_string()],
                messages: vec![],
                created_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64,
            };
            
            serde_json::json!({
                "status": "success",
                "result": format!("Room '{}' created with ID: {}", room_name, room_id),
                "room_id": room_id,
                "participants": room.participants
            })
        },
        "join_room" => {
            let room_id = args.get("room_id").and_then(|v| v.as_str()).unwrap_or("");
            let user = args.get("user").and_then(|v| v.as_str()).unwrap_or("anonymous");
            
            serde_json::json!({
                "status": "success",
                "result": format!("User '{}' joined room '{}'", user, room_id),
                "room_id": room_id,
                "user": user
            })
        },
        "get_messages" => {
            let room_id = args.get("room_id").and_then(|v| v.as_str()).unwrap_or("general");
            let limit = args.get("limit").and_then(|v| v.as_u64()).unwrap_or(10);
            
            // Simuliere Chat-Messages
            let mut messages = Vec::new();
            for i in 0..limit {
                messages.push(ChatMessage {
                    id: format!("msg_{}", i),
                    sender: format!("user_{}", i % 3),
                    recipient: None,
                    content: format!("Message {} in room {}", i, room_id),
                    timestamp: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64 - (limit - i) as i64,
                    message_type: MessageType::Text,
                });
            }
            
            serde_json::json!({
                "status": "success",
                "result": format!("Retrieved {} messages from room '{}'", limit, room_id),
                "messages": messages,
                "room_id": room_id
            })
        },
        "broadcast_message" => {
            let content = args.get("content").and_then(|v| v.as_str()).unwrap_or("Broadcast message");
            
            serde_json::json!({
                "status": "success",
                "result": format!("Broadcast message sent: {}", content),
                "message_type": "broadcast",
                "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64
            })
        },
        "get_room_list" => {
            // Simuliere verfügbare Chat-Rooms
            let rooms = vec![
                ChatRoom {
                    id: "general".to_string(),
                    name: "General Chat".to_string(),
                    participants: vec!["user1".to_string(), "user2".to_string()],
                    messages: vec![],
                    created_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64 - 3600,
                },
                ChatRoom {
                    id: "dev".to_string(),
                    name: "Development".to_string(),
                    participants: vec!["developer1".to_string()],
                    messages: vec![],
                    created_at: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64 - 1800,
                },
            ];
            
            serde_json::json!({
                "status": "success",
                "result": format!("Found {} available rooms", rooms.len()),
                "rooms": rooms
            })
        },
        "handle_message" => {
            // Inter-Plugin Message Handler
            let message_content = args.get("content").and_then(|v| v.as_str()).unwrap_or("");
            let sender = args.get("sender").and_then(|v| v.as_str()).unwrap_or("unknown");
            
            serde_json::json!({
                "status": "success",
                "result": format!("Handled message from '{}': {}", sender, message_content),
                "processed": true,
                "response": format!("Echo: {}", message_content)
            })
        },
        _ => serde_json::json!({
            "status": "error",
            "error": format!("Unknown command: {} (WASM Chat Plugin)", command)
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
    // Chat-Plugin Cleanup
    // Hier würden alle Chat-Verbindungen geschlossen werden
    // und Message-Broker deregistriert werden
    
    0 // Success
}

use std::time::{SystemTime, UNIX_EPOCH};
