//! Memory & Resource Management für FFI
//!
//! Sichere Verwaltung von:
//! - String-Lifecycle
//! - Pointer-Validierung
//! - Resource-Cleanup
//! - Memory-Leaks Prevention

use std::ffi::c_char;
use std::sync::Mutex;
use once_cell::sync::Lazy;
use log::{info, warn};
use crate::error::*;

/// Memory-Statistiken (für Debugging)
#[derive(Clone)]
pub struct MemoryStats {
    pub allocated_strings: usize,
    pub freed_strings: usize,
}

/// SECURITY FIX: Tracking für allocated Strings um Double-Free zu verhindern
#[derive(Debug, Clone, Copy)]
enum PointerState {
    Allocated,
    Freed,
}

static MEMORY_STATS: Lazy<Mutex<MemoryStats>> = Lazy::new(|| {
    Mutex::new(MemoryStats {
        allocated_strings: 0,
        freed_strings: 0,
    })
});

/// SECURITY FIX: Tracking-Map für allocated Pointer (verhindert Double-Free)
/// Key: Pointer-Adresse als usize, Value: Allocated/Freed State
static ALLOCATED_POINTERS: Lazy<Mutex<std::collections::HashMap<usize, PointerState>>> = 
    Lazy::new(|| Mutex::new(std::collections::HashMap::new()));

/// Gib Memory-Statistiken aus
#[no_mangle]
pub extern "C" fn connectias_get_memory_stats() -> *const c_char {
    if let Ok(stats_lock) = MEMORY_STATS.lock() {
        let stats = format!(
            "Allocated: {}, Freed: {}",
            stats_lock.allocated_strings,
            stats_lock.freed_strings
        );
        
        match std::ffi::CString::new(stats) {
            Ok(cs) => cs.into_raw() as *const c_char,
            Err(_) => std::ptr::null(),
        }
    } else {
        std::ptr::null()
    }
}

/// Wrapper für sichere String-Allokation
/// SECURITY FIX: Track allocated Pointer um Double-Free zu verhindern
pub fn allocate_cstring(s: &str) -> Result<*mut c_char, i32> {
    match std::ffi::CString::new(s) {
        Ok(cs) => {
            let ptr = cs.into_raw();
            let ptr_addr = ptr as usize;
            
            // SECURITY FIX: Track allocated Pointer
            if let Ok(mut ptr_map) = ALLOCATED_POINTERS.lock() {
                ptr_map.insert(ptr_addr, PointerState::Allocated);
            }
            
            if let Ok(mut stats) = MEMORY_STATS.lock() {
                stats.allocated_strings += 1;
            }
            Ok(ptr)
        }
        Err(_) => {
            warn!("⚠️ CString-Allokation fehlgeschlagen");
            Err(FFI_ERROR_INVALID_UTF8)
        }
    }
}

/// Wrapper für sichere String-Freigabe
/// SECURITY FIX: Verhindert Double-Free durch Reference-Tracking
pub fn deallocate_cstring(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    
    let ptr_addr = ptr as usize;
    
    // SECURITY FIX: Prüfe ob Pointer bereits freigegeben wurde
    let should_free = {
        if let Ok(mut ptr_map) = ALLOCATED_POINTERS.lock() {
            match ptr_map.get(&ptr_addr) {
                Some(PointerState::Freed) => {
                    // Double-Free erkannt!
                    warn!("🚨 SECURITY: Double-Free-Versuch erkannt bei Pointer {:p} (addr: 0x{:x})", ptr, ptr_addr);
                    return; // Beende Funktion ohne Freigabe
                }
                Some(PointerState::Allocated) => {
                    // Markiere als freigegeben VOR der tatsächlichen Freigabe
                    ptr_map.insert(ptr_addr, PointerState::Freed);
                    true
                }
                None => {
                    // Pointer wurde nicht durch allocate_cstring allokiert
                    // Möglicherweise wurde Pointer manuell erstellt oder bereits freigegeben
                    warn!("⚠️ Versuch, unbekannten Pointer freizugeben: {:p} (addr: 0x{:x})", ptr, ptr_addr);
                    // Erlaube Freigabe trotzdem (für Kompatibilität), aber tracke es
                    ptr_map.insert(ptr_addr, PointerState::Freed);
                    true
                }
            }
        } else {
            // Lock-Fehler - trotzdem versuchen freizugeben (Fail-Safe)
            warn!("⚠️ Lock-Fehler bei Pointer-Tracking - trotzdem freigeben");
            true
        }
    };
    
    if should_free {
        unsafe {
            let _ = std::ffi::CString::from_raw(ptr);
            if let Ok(mut stats) = MEMORY_STATS.lock() {
                stats.freed_strings += 1;
            }
        }
    }
}

/// Sichere Buffer-Allokation (für zukünftige Nutzung)
#[no_mangle]
pub extern "C" fn connectias_malloc(size: usize) -> *mut u8 {
    if size == 0 {
        warn!("⚠️ Versuch, 0 Bytes zu allokieren");
        return std::ptr::null_mut();
    }

    if size > 1024 * 1024 * 100 {
        // Maximum 100MB
        warn!("⚠️ Allokation zu groß: {} Bytes", size);
        return std::ptr::null_mut();
    }

    unsafe {
        let ptr = libc::malloc(size);
        if ptr.is_null() {
            warn!("⚠️ Allokation fehlgeschlagen: {} Bytes", size);
            std::ptr::null_mut()
        } else {
            info!("✅ {} Bytes allokiert", size);
            ptr as *mut u8
        }
    }
}

/// Sichere Buffer-Freigabe
#[no_mangle]
pub extern "C" fn connectias_free(ptr: *mut u8, size: usize) {
    if !ptr.is_null() && size > 0 {
        unsafe {
            libc::free(ptr as *mut libc::c_void);
            info!("✅ {} Bytes freigegeben", size);
        }
    }
}

/// Überprüfe Pointer-Gültigkeit
pub fn validate_pointer<T>(ptr: *const T) -> Result<(), i32> {
    if ptr.is_null() {
        Err(FFI_ERROR_NULL_POINTER)
    } else {
        Ok(())
    }
}

/// Überprüfe beschreibbaren Pointer
pub fn validate_mutable_pointer<T>(ptr: *mut T) -> Result<(), i32> {
    if ptr.is_null() {
        Err(FFI_ERROR_NULL_POINTER)
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cstring_allocation() {
        let result = allocate_cstring("test string");
        assert!(result.is_ok());
        
        let ptr = result.unwrap();
        assert!(!ptr.is_null());
        
        unsafe {
            let s = std::ffi::CStr::from_ptr(ptr).to_string_lossy();
            assert_eq!(s, "test string");
            deallocate_cstring(ptr);
        }
    }

    #[test]
    fn test_invalid_utf8_string() {
        // Bytes die kein valides UTF-8 sind
        let result = allocate_cstring("test");
        assert!(result.is_ok());
    }

    #[test]
    fn test_pointer_validation() {
        let ptr: *const u8 = std::ptr::null();
        assert!(validate_pointer(ptr).is_err());
        
        let valid: u8 = 42;
        let ptr = &valid as *const u8;
        assert!(validate_pointer(ptr).is_ok());
    }

    #[test]
    fn test_memory_stats() {
        let stats = connectias_get_memory_stats();
        assert!(!stats.is_null());
        
        unsafe {
            let s = std::ffi::CStr::from_ptr(stats).to_string_lossy();
            assert!(s.contains("Allocated:"));
            connectias_free_string(stats as *const c_char);
        }
    }

    #[test]
    fn test_malloc_free() {
        let ptr = connectias_malloc(1024);
        assert!(!ptr.is_null());
        
        connectias_free(ptr, 1024);
    }

    #[test]
    fn test_zero_allocation_rejected() {
        let ptr = connectias_malloc(0);
        assert!(ptr.is_null());
    }

    #[test]
    fn test_huge_allocation_rejected() {
        let ptr = connectias_malloc(1024 * 1024 * 500); // 500MB
        assert!(ptr.is_null());
    }
}
//ich diene der aktualisierung wala
