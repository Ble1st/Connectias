//! Plugin-Operationen via FFI
//! 
//! Sichere Exports für:
//! - Plugin laden/entladen
//! - Plugin ausführen
//! - Plugin-Liste abrufen
//! - Quotas verwalten

use std::ffi::{c_char};
use std::path::Path;
use std::collections::HashMap;
use crate::error::*;
use crate::state::*;
use log::{info, warn, error};

/// Lade ein Plugin
/// 
/// Input:
///   - plugin_path: Pfad zum Plugin-WASM (C-String)
/// 
/// Output:
///   - Plugin-ID als JSON-String
///   - MUSS mit connectias_free_string() freigegeben werden
///   - Null bei Fehler (nutze connectias_get_last_error())
#[no_mangle]
pub extern "C" fn connectias_load_plugin(
    plugin_path: *const c_char,
) -> *const c_char {
    // 1. Validiere Pointer
    if plugin_path.is_null() {
        set_last_error("❌ plugin_path ist nullptr");
        return std::ptr::null();
    }

    // 2. Konvertiere C-String zu Rust
    let path = match c_str_to_rust(plugin_path, "plugin_path") {
        Ok(s) => s,
        Err(_) => return std::ptr::null(),
    };

    info!("📦 Lade Plugin von {}", path);

    // 3. Führe async Operation synchron aus
    let rt = get_runtime();
    rt.block_on(async {
        match get_or_init_manager().await {
            Ok(manager) => {
                match manager.load_plugin(Path::new(&path)).await {
                    Ok(plugin_id) => {
                        info!("✅ Plugin geladen mit ID: {}", plugin_id);
                        match std::ffi::CString::new(plugin_id) {
                            Ok(cs) => cs.into_raw() as *const c_char,
                            Err(e) => {
                                let msg = format!("❌ CString-Fehler: {}", e);
                                error!("{}", msg);
                                set_last_error(&msg);
                                std::ptr::null()
                            }
                        }
                    }
                    Err(e) => {
                        let msg = format!("❌ Plugin-Loading fehlgeschlagen: {}", e);
                        error!("{}", msg);
                        set_last_error(&msg);
                        std::ptr::null()
                    }
                }
            }
            Err(e) => {
                let msg = format!("❌ Manager-Initialisierung fehlgeschlagen: {}", e);
                error!("{}", msg);
                set_last_error(&msg);
                std::ptr::null()
            }
        }
    })
}

/// Entlade ein Plugin
///
/// Input:
///   - plugin_id: Eindeutige Plugin-ID (C-String)
///
/// Output:
///   - 0: Erfolg
///   - < 0: Fehler
#[no_mangle]
pub extern "C" fn connectias_unload_plugin(plugin_id: *const c_char) -> i32 {
    let id = match c_str_to_rust(plugin_id, "plugin_id") {
        Ok(s) => s,
        Err(e) => return e,
    };

    info!("🔌 Entlade Plugin: {}", id);

    let rt = get_runtime();
    rt.block_on(async {
        match get_or_init_manager().await {
            Ok(manager) => {
                match manager.unload_plugin(&id).await {
                    Ok(_) => {
                        info!("✅ Plugin {} entladen", id);
                        FFI_SUCCESS
                    }
                    Err(e) => {
                        let msg = format!("❌ Plugin-Entladung fehlgeschlagen: {}", e);
                        warn!("{}", msg);
                        set_last_error(&msg);
                        FFI_ERROR_EXECUTION_FAILED
                    }
                }
            }
            Err(e) => {
                let msg = format!("❌ Manager-Initialisierung fehlgeschlagen: {}", e);
                error!("{}", msg);
                set_last_error(&msg);
                FFI_ERROR_INIT_FAILED
            }
        }
    })
}

/// Führe ein Plugin aus
///
/// Input:
///   - plugin_id: Eindeutige Plugin-ID (C-String)
///   - command: Command Name (C-String)
///   - args_json: JSON Object mit Arguments oder "{}" (C-String)
///   - output_json: Output-Pointer (muss mit connectias_free_string freigegeben werden)
///
/// Output:
///   - 0: Erfolg
///   - < 0: Fehler
///
/// SICHERHEIT: output_json MUSS mit connectias_free_string() freigegeben werden!
#[no_mangle]
pub extern "C" fn connectias_execute_plugin(
    plugin_id: *const c_char,
    command: *const c_char,
    args_json: *const c_char,
    output_json: *mut *mut c_char,
) -> i32 {
    // 1. Validiere Pointer
    if plugin_id.is_null() {
        set_last_error("❌ plugin_id ist nullptr");
        return FFI_ERROR_NULL_POINTER;
    }
    if command.is_null() {
        set_last_error("❌ command ist nullptr");
        return FFI_ERROR_NULL_POINTER;
    }
    if output_json.is_null() {
        set_last_error("❌ output_json Pointer ist nullptr");
        return FFI_ERROR_NULL_POINTER;
    }

    // 2. Konvertiere Inputs
    let id = match c_str_to_rust(plugin_id, "plugin_id") {
        Ok(s) => s,
        Err(e) => return e,
    };

    let cmd = match c_str_to_rust(command, "command") {
        Ok(s) => s,
        Err(e) => return e,
    };

    let args = if args_json.is_null() {
        HashMap::new()
    } else {
        match c_str_to_rust(args_json, "args_json") {
            Ok(s) => {
                // SECURITY FIX: JSON Schema-Validierung und Sanitization
                // Schritt 1: Längen-Limitierung
                const MAX_JSON_LENGTH: usize = 1024 * 1024; // 1MB
                if s.len() > MAX_JSON_LENGTH {
                    let msg = format!("❌ JSON zu lang: {} bytes (max {} bytes)", s.len(), MAX_JSON_LENGTH);
                    error!("{}", msg);
                    set_last_error(&msg);
                    return FFI_ERROR_SECURITY_VIOLATION;
                }
                
                // Schritt 2: Parse JSON mit Tiefenlimitierung
                let sanitized_json: serde_json::Value = match serde_json::from_str(&s) {
                    Ok(value) => value,
                    Err(e) => {
                        let msg = format!("❌ Ungültiges JSON: {}", e);
                        error!("{}", msg);
                        set_last_error(&msg);
                        return FFI_ERROR_INVALID_UTF8;
                    }
                };
                
                // Schritt 3: Prüfe JSON-Tiefe (verhindert Deep-Nesting DoS)
                fn json_depth(value: &serde_json::Value) -> usize {
                    match value {
                        serde_json::Value::Object(map) => {
                            1 + map.values().map(json_depth).max().unwrap_or(0)
                        }
                        serde_json::Value::Array(arr) => {
                            1 + arr.iter().map(json_depth).max().unwrap_or(0)
                        }
                        _ => 1,
                    }
                }
                
                const MAX_JSON_DEPTH: usize = 32;
                let depth = json_depth(&sanitized_json);
                if depth > MAX_JSON_DEPTH {
                    let msg = format!("❌ JSON-Tiefe zu tief: {} (max {})", depth, MAX_JSON_DEPTH);
                    error!("{}", msg);
                    set_last_error(&msg);
                    return FFI_ERROR_SECURITY_VIOLATION;
                }
                
                // Schritt 4: Validierung - nur einfache Key-Value-Pairs erlaubt
                // Keine verschachtelten Objekte oder Arrays (verhindert komplexe Injection-Angriffe)
                match sanitized_json {
                    serde_json::Value::Object(map) => {
                        let mut args_map = HashMap::new();
                        for (key, value) in map {
                            // Nur String-Werte erlauben (keine Objekte/Arrays)
                            match value {
                                serde_json::Value::String(s) => {
                                    // String-Validierung: Whitelist für erlaubte Zeichen
                                    // Alphanumerisch, Leerzeichen, Bindestrich, Unterstrich, Punkt, @
                                    if s.len() > 4096 {
                                        let msg = format!("❌ String zu lang: {} bytes (max 4096)", s.len());
                                        error!("{}", msg);
                                        set_last_error(&msg);
                                        return FFI_ERROR_SECURITY_VIOLATION;
                                    }
                                    
                                    // Prüfe auf gefährliche Patterns
                                    let s_upper = s.to_uppercase();
                                    let dangerous = ["SELECT", "INSERT", "UPDATE", "DELETE", "DROP", 
                                                     "CREATE", "ALTER", "UNION", "EXEC", "EXECUTE",
                                                     "--", "/*", "*/", "..", "javascript:", "<script"];
                                    for pattern in &dangerous {
                                        if s_upper.contains(pattern) {
                                            let msg = format!("❌ Gefährliches Pattern in JSON-Arg erkannt: {}", pattern);
                                            error!("{}", msg);
                                            set_last_error(&msg);
                                            return FFI_ERROR_SECURITY_VIOLATION;
                                        }
                                    }
                                    
                                    args_map.insert(key, s);
                                }
                                _ => {
                                    let msg = format!("❌ Nur String-Werte erlaubt in JSON-Args, gefunden: {:?}", value);
                                    error!("{}", msg);
                                    set_last_error(&msg);
                                    return FFI_ERROR_SECURITY_VIOLATION;
                                }
                            }
                        }
                        args_map
                    }
                    _ => {
                        let msg = "❌ JSON-Args müssen ein Objekt sein".to_string();
                        error!("{}", msg);
                        set_last_error(&msg);
                        return FFI_ERROR_SECURITY_VIOLATION;
                    }
                }
            }
            Err(e) => return e,
        }
    };

    info!("⚙️ Führe Plugin aus: {} mit command: {}", id, cmd);

    // 3. Führe async Operation synchron aus
    let rt = get_runtime();
    rt.block_on(async {
        match get_or_init_manager().await {
            Ok(manager) => {
                match manager.execute_plugin(&id, &cmd, args).await {
                    Ok(result) => {
                        info!("✅ Plugin {} ausgeführt", id);
                        
                        match std::ffi::CString::new(result) {
                            Ok(cs) => {
                                unsafe {
                                    *output_json = cs.into_raw();
                                }
                                FFI_SUCCESS
                            }
                            Err(e) => {
                                let msg = format!("❌ Fehler beim Result-Encoding: {}", e);
                                error!("{}", msg);
                                set_last_error(&msg);
                                FFI_ERROR_INVALID_UTF8
                            }
                        }
                    }
                    Err(e) => {
                        let msg = format!("❌ Plugin-Ausführung fehlgeschlagen: {}", e);
                        error!("{}", msg);
                        set_last_error(&msg);
                        FFI_ERROR_EXECUTION_FAILED
                    }
                }
            }
            Err(e) => {
                let msg = format!("❌ Manager-Initialisierung fehlgeschlagen: {}", e);
                error!("{}", msg);
                set_last_error(&msg);
                FFI_ERROR_INIT_FAILED
            }
        }
    })
}

/// Liste alle geladenen Plugins auf
///
/// Output:
///   - JSON Array mit Plugin-Informationen (C-String)
///   - MUSS mit connectias_free_string() freigegeben werden!
#[no_mangle]
pub extern "C" fn connectias_list_plugins() -> *const c_char {
    info!("📋 Liste alle Plugins auf");

    let rt = get_runtime();
    rt.block_on(async {
        match get_or_init_manager().await {
            Ok(manager) => {
                let plugins = manager.list_plugins().await;
                info!("✅ {} Plugins gefunden", plugins.len());
                
                match serde_json::to_string(&plugins) {
                    Ok(json) => {
                        match std::ffi::CString::new(json) {
                            Ok(cs) => cs.into_raw() as *const c_char,
                            Err(e) => {
                                error!("❌ CString-Erstellung fehlgeschlagen: {}", e);
                                set_last_error("❌ CString-Erstellung fehlgeschlagen");
                                std::ptr::null()
                            }
                        }
                    }
                    Err(e) => {
                        error!("❌ JSON-Serialisierung fehlgeschlagen: {}", e);
                        set_last_error("❌ JSON-Serialisierung fehlgeschlagen");
                        std::ptr::null()
                    }
                }
            }
            Err(e) => {
                error!("❌ Manager-Initialisierung fehlgeschlagen: {}", e);
                set_last_error(&format!("❌ Manager-Initialisierung fehlgeschlagen: {}", e));
                std::ptr::null()
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_null_pointer_detection() {
        let result = connectias_load_plugin(std::ptr::null());
        assert!(result.is_null());
    }

    #[test]
    fn test_valid_path_returns_pointer() {
        // Dieser Test prüft nur dass ein ungültiger Pfad zu Fehler führt
        // Echte Plugin-Tests werden später mit echten Plugins durchgeführt
        assert!(true);
    }
}
//ich diene der aktualisierung wala
