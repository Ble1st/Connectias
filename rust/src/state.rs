//! Global State Management für FFI Bridge
//! 
//! Sichere Verwaltung von PluginManager und Tokio Runtime
//! mit thread-safe Zugriffen via Lazy + Mutex.

use once_cell::sync::Lazy;
use tokio::sync::Mutex;
use log::info;
use crate::error::set_last_error;
use std::path::PathBuf;
use std::sync::Arc;

/// PluginManager Instance - nur EINE pro Prozess (in Arc gewrappt)
static PLUGIN_MANAGER: Lazy<Mutex<Option<Arc<connectias_core::plugin_manager::PluginManager>>>> = 
    Lazy::new(|| {
        info!("🔧 PluginManager Lazy-Initialisierung startet");
        Mutex::new(None)
    });

/// Tokio Runtime - nur EINE pro Prozess
static RUNTIME: Lazy<tokio::runtime::Runtime> = Lazy::new(|| {
    info!("🔧 Tokio Runtime wird erstellt");
    match tokio::runtime::Runtime::new() {
        Ok(rt) => {
            info!("✅ Tokio Runtime erfolgreich erstellt");
            rt
        }
        Err(e) => {
            log::error!("🔴 KRITISCH: Tokio Runtime Fehler: {}", e);
            panic!("Tokio Runtime konnte nicht initialisiert werden: {}", e);
        }
    }
});

/// Initialisiere oder hole den PluginManager
pub async fn get_or_init_manager() -> Result<
    Arc<connectias_core::plugin_manager::PluginManager>,
    Box<dyn std::error::Error + Send + Sync>,
> {
    let mut manager = PLUGIN_MANAGER.lock().await;

    if manager.is_none() {
        info!("🚀 Erstelle neuen PluginManager");
        
        // Bestimme App-Daten-Verzeichnis
        let app_data_dir = if cfg!(target_os = "android") {
            PathBuf::from("/data/local/tmp/connectias")
        } else {
            PathBuf::from("/tmp/connectias")
        };
        
        match connectias_core::plugin_manager::PluginManager::new(app_data_dir) {
            Ok(new_manager) => {
                *manager = Some(Arc::new(new_manager));
                info!("✅ PluginManager erfolgreich erstellt");
            }
            Err(e) => {
                let msg = format!("🔴 PluginManager-Initialisierung fehlgeschlagen: {}", e);
                log::error!("{}", msg);
                set_last_error(&msg);
                return Err(msg.into());
            }
        }
    }

    match manager.as_ref() {
        Some(m) => Ok(m.clone()),
        None => {
            let msg = "🔴 KRITISCH: PluginManager ist None nach Initialisierung";
            log::error!("{}", msg);
            set_last_error(msg);
            Err(msg.into())
        }
    }
}

/// Hole die Tokio Runtime
pub fn get_runtime() -> &'static tokio::runtime::Runtime {
    &RUNTIME
}

/// Gib Systeminfo aus (für Debugging)
#[no_mangle]
pub extern "C" fn connectias_get_system_info() -> *const std::ffi::c_char {
    use std::ffi::CString;
    
    let info = format!(
        "OS: {}, CPU: {}, Arch: {}",
        std::env::consts::OS,
        std::env::consts::FAMILY,
        std::env::consts::ARCH,
    );
    
    match CString::new(info) {
        Ok(cs) => cs.into_raw() as *const std::ffi::c_char,
        Err(_) => std::ptr::null(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_system_info() {
        let info = connectias_get_system_info();
        assert!(!info.is_null());
        unsafe {
            let s = std::ffi::CStr::from_ptr(info).to_string_lossy();
            assert!(s.contains("OS:") && s.contains("Arch:"));
        }
    }
}
