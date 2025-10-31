use thiserror::Error;

#[derive(Debug, Error)]
pub enum SecurityError {
    #[error("Memory protection failed: {0}")]
    MemoryProtectionFailed(std::io::Error),
    #[error("Invalid memory alignment: pointer 0x{0:x} is not page-aligned")]
    InvalidAlignment(usize),
    #[error("Invalid size: {0} is not page-aligned")]
    InvalidSize(usize),
}

/// Holt die System-Seitengröße zur Laufzeit
/// 
/// Fragt die OS-Seitengröße ab und cached das Ergebnis.
/// Validiert dass die Page-Size eine Power-of-Two ist (erforderlich für bitwise alignment).
/// Panic bei ungültiger Page-Size (sollte in der Praxis nie vorkommen).
pub fn get_page_size() -> usize {
    use std::sync::OnceLock;
    static PAGE_SIZE_CACHE: OnceLock<usize> = OnceLock::new();
    
    *PAGE_SIZE_CACHE.get_or_init(|| {
        let page_size = {
            #[cfg(unix)]
            {
                unsafe {
                    let page_size = libc::sysconf(libc::_SC_PAGESIZE);
                    if page_size > 0 {
                        page_size as usize
                    } else {
                        4096 // Fallback
                    }
                }
            }
            #[cfg(windows)]
            {
                use windows::Win32::System::SystemInformation::GetSystemInfo;
                use std::mem::zeroed;
                unsafe {
                    let mut system_info = zeroed();
                    GetSystemInfo(&mut system_info);
                    system_info.dwPageSize as usize
                }
            }
            #[cfg(not(any(unix, windows)))]
            {
                4096 // Fallback für unbekannte Plattformen
            }
        };
        
        // Validiere dass Page-Size eine Power-of-Two ist (erforderlich für bitwise alignment)
        if !page_size.is_power_of_two() {
            panic!(
                "System page size ({}) is not a power of two - this breaks alignment operations. \
                 This should never happen on standard systems.",
                page_size
            );
        }
        
        page_size
    })
}

/// Helper functions for page alignment
pub mod alignment {
    use super::{get_page_size, SecurityError};
    
    /// Check if a pointer is page-aligned
    pub fn is_page_aligned(ptr: *mut u8) -> bool {
        let page_size = get_page_size();
        (ptr as usize) % page_size == 0
    }
    
    /// Check if a size is page-aligned
    pub fn is_size_page_aligned(size: usize) -> bool {
        let page_size = get_page_size();
        size % page_size == 0
    }
    
    /// Align a pointer down to the nearest page boundary
    /// 
    /// # Voraussetzungen
    /// 
    /// `get_page_size()` muss eine Power-of-Two zurückgeben (wird zur Laufzeit validiert).
    pub fn align_down(ptr: *mut u8) -> *mut u8 {
        let page_size = get_page_size();
        // Defensive Check: Page-Size sollte Power-of-Two sein (wird in get_page_size() validiert)
        debug_assert!(page_size.is_power_of_two(), "Page size must be power of two for bitwise alignment");
        
        let addr = ptr as usize;
        let aligned_addr = addr & !(page_size - 1);
        aligned_addr as *mut u8
    }
    
    /// Align a size up to the nearest page boundary
    /// 
    /// # Voraussetzungen
    /// 
    /// `get_page_size()` muss eine Power-of-Two zurückgeben (wird zur Laufzeit validiert).
    pub fn align_up(size: usize) -> usize {
        let page_size = get_page_size();
        // Defensive Check: Page-Size sollte Power-of-Two sein (wird in get_page_size() validiert)
        debug_assert!(page_size.is_power_of_two(), "Page size must be power of two for bitwise alignment");
        
        (size + page_size - 1) & !(page_size - 1)
    }
    
    /// Validate that pointer and size are page-aligned
    pub fn validate_alignment(ptr: *mut u8, size: usize) -> Result<(), SecurityError> {
        if !is_page_aligned(ptr) {
            return Err(SecurityError::InvalidAlignment(ptr as usize));
        }
        if !is_size_page_aligned(size) {
            return Err(SecurityError::InvalidSize(size));
        }
        Ok(())
    }
}

pub trait MemoryProtection {
    fn protect_readonly(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError>;
    fn protect_no_access(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError>;
    fn protect_read_write(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError>;
}

#[cfg(unix)]
pub struct UnixMemoryProtection;

#[cfg(unix)]
impl UnixMemoryProtection {
    pub fn new() -> Self {
        Self
    }
}

#[cfg(unix)]
impl MemoryProtection for UnixMemoryProtection {
    fn protect_readonly(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError> {
        // Validate page alignment
        alignment::validate_alignment(ptr, size)?;
        
        unsafe {
            let result = libc::mprotect(
                ptr as *mut libc::c_void,
                size,
                libc::PROT_READ
            );
            if result != 0 {
                return Err(SecurityError::MemoryProtectionFailed(
                    std::io::Error::last_os_error()
                ));
            }
        }
        Ok(())
    }
    
    fn protect_no_access(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError> {
        // Validate page alignment
        alignment::validate_alignment(ptr, size)?;
        
        unsafe {
            let result = libc::mprotect(
                ptr as *mut libc::c_void,
                size,
                libc::PROT_NONE
            );
            if result != 0 {
                return Err(SecurityError::MemoryProtectionFailed(
                    std::io::Error::last_os_error()
                ));
            }
        }
        Ok(())
    }
    
    fn protect_read_write(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError> {
        // Validate page alignment
        alignment::validate_alignment(ptr, size)?;
        
        unsafe {
            let result = libc::mprotect(
                ptr as *mut libc::c_void,
                size,
                libc::PROT_READ | libc::PROT_WRITE
            );
            if result != 0 {
                return Err(SecurityError::MemoryProtectionFailed(
                    std::io::Error::last_os_error()
                ));
            }
        }
        Ok(())
    }
}

#[cfg(windows)]
pub struct WindowsMemoryProtection;

#[cfg(windows)]
impl WindowsMemoryProtection {
    pub fn new() -> Self {
        Self
    }
}

#[cfg(windows)]
impl MemoryProtection for WindowsMemoryProtection {
    fn protect_readonly(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError> {
        use windows::Win32::System::Memory::*;
        
        // Validate page alignment
        alignment::validate_alignment(ptr, size)?;
        
        unsafe {
            let mut old_protect = 0u32;
            let result = VirtualProtect(
                ptr as *mut std::ffi::c_void,
                size,
                PAGE_READONLY,
                &mut old_protect,
            );
            
            if !result.as_bool() {
                return Err(SecurityError::MemoryProtectionFailed(
                    std::io::Error::last_os_error()
                ));
            }
        }
        Ok(())
    }
    
    fn protect_no_access(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError> {
        use windows::Win32::System::Memory::*;
        
        // Validate page alignment
        alignment::validate_alignment(ptr, size)?;
        
        unsafe {
            let mut old_protect = 0u32;
            let result = VirtualProtect(
                ptr as *mut std::ffi::c_void,
                size,
                PAGE_NOACCESS,
                &mut old_protect,
            );
            
            if !result.as_bool() {
                return Err(SecurityError::MemoryProtectionFailed(
                    std::io::Error::last_os_error()
                ));
            }
        }
        Ok(())
    }
    
    fn protect_read_write(&self, ptr: *mut u8, size: usize) -> Result<(), SecurityError> {
        use windows::Win32::System::Memory::*;
        
        // Validate page alignment
        alignment::validate_alignment(ptr, size)?;
        
        unsafe {
            let mut old_protect = 0u32;
            let result = VirtualProtect(
                ptr as *mut std::ffi::c_void,
                size,
                PAGE_READWRITE,
                &mut old_protect,
            );
            
            if !result.as_bool() {
                return Err(SecurityError::MemoryProtectionFailed(
                    std::io::Error::last_os_error()
                ));
            }
        }
        Ok(())
    }
}

/// Fallback implementation for unknown targets
#[cfg(not(any(unix, windows)))]
pub struct FallbackMemoryProtection;

#[cfg(not(any(unix, windows)))]
impl FallbackMemoryProtection {
    pub fn new() -> Self {
        Self
    }
}

#[cfg(not(any(unix, windows)))]
impl MemoryProtection for FallbackMemoryProtection {
    fn protect_readonly(&self, _ptr: *mut u8, _size: usize) -> Result<(), SecurityError> {
        Err(SecurityError::MemoryProtectionFailed(
            std::io::Error::new(
                std::io::ErrorKind::Unsupported,
                "Memory protection not supported on this platform"
            )
        ))
    }
    
    fn protect_no_access(&self, _ptr: *mut u8, _size: usize) -> Result<(), SecurityError> {
        Err(SecurityError::MemoryProtectionFailed(
            std::io::Error::new(
                std::io::ErrorKind::Unsupported,
                "Memory protection not supported on this platform"
            )
        ))
    }
    
    fn protect_read_write(&self, _ptr: *mut u8, _size: usize) -> Result<(), SecurityError> {
        Err(SecurityError::MemoryProtectionFailed(
            std::io::Error::new(
                std::io::ErrorKind::Unsupported,
                "Memory protection not supported on this platform"
            )
        ))
    }
}

/// Factory für plattformspezifische Memory Protection
pub fn create_memory_protection() -> Box<dyn MemoryProtection> {
    #[cfg(unix)]
    {
        Box::new(UnixMemoryProtection::new())
    }
    
    #[cfg(windows)]
    {
        Box::new(WindowsMemoryProtection::new())
    }
    
    #[cfg(not(any(unix, windows)))]
    {
        Box::new(FallbackMemoryProtection::new())
    }
}
