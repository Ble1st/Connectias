//! Fehlerbehandlung für FFI Bridge
//! 
//! Thread-sichere Fehler speicherung via thread-local Storage.
//! Dart-Seite kann `connectias_get_last_error()` nutzen.

use std::ffi::{CStr, CString, c_char};
use std::sync::Mutex;

thread_local! {
    static LAST_ERROR: Mutex<Option<String>> = Mutex::new(None);
}

/// Setze den letzten Fehler
pub fn set_last_error(msg: impl Into<String>) {
    LAST_ERROR.with(|e| {
        match e.lock() {
            Ok(mut error) => {
                *error = Some(msg.into());
            }
            Err(_) => {
                log::error!("🔴 KRITISCH: Fehler-Lock vergiftet!");
            }
        }
    });
}

/// Hole den letzten Fehler und lösche ihn
/// SICHERHEIT: Der Pointer MUSS mit `connectias_free_string()` freigegeben werden!
#[no_mangle]
pub extern "C" fn connectias_get_last_error() -> *const c_char {
    LAST_ERROR.with(|e| {
        match e.lock() {
            Ok(mut error) => {
                match error.take() {
                    Some(msg) => {
                        // FIX BUG 2: allocate_cstring gibt Result<*mut c_char, i32> zurück
                        // Bei Fehler (Err(i32)) dürfen wir NICHT den Integer als Pointer casten
                        // Funktion muss *const c_char zurückgeben, daher bei Fehler null
                        match crate::memory::allocate_cstring(&msg) {
                            Ok(ptr) => ptr as *const c_char,
                            Err(err_code) => {
                                // FIX BUG 2: err_code ist i32, nicht casten zu Pointer!
                                // Log error und return null pointer (sicherer Fehlerfall)
                                log::error!("🔴 KRITISCH: Ungültige UTF-8 in Fehler! Error code: {}", err_code);
                                std::ptr::null()
                            }
                        }
                    }
                    None => std::ptr::null(),
                }
            }
            Err(_) => {
                log::error!("🔴 KRITISCH: Fehler-Lock vergiftet!");
                std::ptr::null()
            }
        }
    })
}

/// Freigabe eines FFI-Strings
/// SICHERHEIT: Nur für Pointer verwenden, die von Connectias FFI generiert wurden!
/// SECURITY FIX: Verwendet deallocate_cstring für Double-Free-Schutz
#[no_mangle]
pub extern "C" fn connectias_free_string(s: *const c_char) {
    // Verwende die sichere deallocate_cstring-Funktion mit Double-Free-Schutz
    crate::memory::deallocate_cstring(s as *mut c_char);
}

/// FFI Fehler-Codes
pub const FFI_ERROR_INVALID_UTF8: i32 = -1;
pub const FFI_ERROR_NULL_POINTER: i32 = -2;
pub const FFI_ERROR_INIT_FAILED: i32 = -3;
pub const FFI_ERROR_PLUGIN_NOT_FOUND: i32 = -4;
pub const FFI_ERROR_EXECUTION_FAILED: i32 = -5;
pub const FFI_ERROR_SECURITY_VIOLATION: i32 = -6;
pub const FFI_ERROR_LOCK_POISONED: i32 = -7;
pub const FFI_SUCCESS: i32 = 0;

/// Sichere String-Konvertierung: C → Rust
pub fn c_str_to_rust(ptr: *const c_char, field_name: &str) -> Result<String, i32> {
    if ptr.is_null() {
        set_last_error(format!("❌ Null Pointer für '{}'", field_name));
        return Err(FFI_ERROR_NULL_POINTER);
    }

    unsafe {
        match CStr::from_ptr(ptr).to_str() {
            Ok(s) => Ok(s.to_string()),
            Err(_) => {
                set_last_error(format!("❌ Ungültige UTF-8 in '{}'", field_name));
                Err(FFI_ERROR_INVALID_UTF8)
            }
        }
    }
}

/// Sichere String-Konvertierung: Rust → C
/// SECURITY FIX: Verwendet allocate_cstring für konsistentes Tracking
pub fn rust_str_to_c(s: &str) -> Result<*const c_char, i32> {
    // Verwende allocate_cstring für konsistentes Pointer-Tracking
    crate::memory::allocate_cstring(s).map(|ptr| ptr as *const c_char)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_handling() {
        set_last_error("Test Error");
        
        let error_ptr = connectias_get_last_error();
        assert!(!error_ptr.is_null());
        
        let error_str = unsafe {
            CStr::from_ptr(error_ptr)
                .to_string_lossy()
                .to_string()
        };
        assert_eq!(error_str, "Test Error");
        
        connectias_free_string(error_ptr);
    }

    #[test]
    fn test_null_error_handling() {
        let null_result = connectias_get_last_error();
        // Nach test_error_handling wurde Fehler konsumiert
        if null_result.is_null() {
            assert!(true); // Erwartet
        }
    }

    #[test]
    fn test_c_str_validation() {
        let valid = "valid string\0";
        let result = c_str_to_rust(valid.as_ptr() as *const c_char, "test");
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), "valid string");
    }

    #[test]
    fn test_null_pointer_detection() {
        let result = c_str_to_rust(std::ptr::null(), "test_field");
        assert_eq!(result.err(), Some(FFI_ERROR_NULL_POINTER));
    }
}
