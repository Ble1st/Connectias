#![allow(non_snake_case)]

mod error;
mod state;
mod plugin_ops;
mod security;
mod memory;

pub use error::*;
pub use state::*;
pub use plugin_ops::*;
pub use security::*;
pub use memory::*;

use std::sync::Once;
use log::LevelFilter;

static INIT: Once = Once::new();

/// Initialisiere die FFI Library mit Logging
/// MUSS einmalig von der Dart-Seite aufgerufen werden
#[no_mangle]
pub extern "C" fn connectias_init() -> i32 {
    INIT.call_once(|| {
        // Initialisiere Logging
        env_logger::Builder::from_default_env()
            .filter_level(LevelFilter::Info)
            .try_init()
            .ok();
        
        log::info!("🔒 Connectias FFI Bridge initialized");
    });
    0
}

/// Gibt die FFI Bridge Version zurück
#[no_mangle]
pub extern "C" fn connectias_version() -> *const std::ffi::c_char {
    std::ffi::CStr::from_bytes_with_nul(b"0.1.0\0")
        .unwrap()
        .as_ptr()
}
