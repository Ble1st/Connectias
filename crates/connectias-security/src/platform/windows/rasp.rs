#[cfg(windows)]
use windows::Win32::System::Diagnostics::Debug::IsDebuggerPresent;
#[cfg(windows)]
use windows::Win32::System::Registry::*;
#[cfg(windows)]
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
#[cfg(windows)]
use std::collections::HashMap;

#[cfg(windows)]
pub struct WindowsRASP;

#[cfg(windows)]
impl WindowsRASP {
    pub fn new() -> Self {
        Self
    }
    
    pub fn detect_debugger(&self) -> bool {
        unsafe { IsDebuggerPresent().as_bool() }
    }
    
    pub fn detect_vm(&self) -> bool {
        // Prüfe auf VM-Artefakte in Registry
        unsafe {
            let mut hkey = HKEY::default();
            let key_wide: Vec<u16> = "SYSTEM\\CurrentControlSet\\Services\\VBoxService"
                .encode_utf16()
                .chain(std::iter::once(0))
                .collect();
            let result = RegOpenKeyExW(
                HKEY_LOCAL_MACHINE,
                &key_wide,
                0,
                KEY_READ,
                &mut hkey,
            );
            
            if result == ERROR_SUCCESS {
                RegCloseKey(hkey);
                return true;
            }
        }
        
        // Prüfe auf VMware
        unsafe {
            let mut hkey = HKEY::default();
            let key_wide: Vec<u16> = "SYSTEM\\CurrentControlSet\\Services\\VMTools"
                .encode_utf16()
                .chain(std::iter::once(0))
                .collect();
            let result = RegOpenKeyExW(
                HKEY_LOCAL_MACHINE,
                &key_wide,
                0,
                KEY_READ,
                &mut hkey,
            );
            
            if result == ERROR_SUCCESS {
                RegCloseKey(hkey);
                return true;
            }
        }
        
        false
    }
    
    pub fn detect_hooks(&self) -> bool {
        // Prüfe auf verdächtige DLLs (ohne Duplikate)
        let suspicious_dlls = [
            "frida-agent.dll", "xposed.dll", "substrate.dll", "cydia.dll",
            "libhook.dll", "libinject.dll", "libhack.dll", "libpatch.dll",
            "gadget.dll", "inject.dll", "hook.dll", "patch.dll",
            "libsubstrate.dll", "libxposed.dll", "libfrida.dll", "libcydia.dll",
            "libgadget.dll", "frida.dll",
        ];
        
        for dll in &suspicious_dlls {
            unsafe {
                let dll_wide: Vec<u16> = dll.encode_utf16().chain(std::iter::once(0)).collect();
                let handle = GetModuleHandleW(&dll_wide);
                if !handle.is_invalid() {
                    return true;
                }
            }
        }
        
        // Prüfe auf verdächtige Prozesse
        use windows::Win32::System::Threading::*;
        use windows::Win32::Foundation::*;
        
        unsafe {
            let snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
            if snapshot.is_invalid() {
                let error = std::io::Error::last_os_error();
                // Bei Fehler keine Hook-Erkennung möglich, aber nicht kritisch
                // Einfach weitermachen mit anderen Checks
                return false;
            }
            
            // Snapshot ist gültig, weiter mit Prozess-Enumeration
            let mut pe32 = PROCESSENTRY32W {
                dwSize: std::mem::size_of::<PROCESSENTRY32W>() as u32,
                ..Default::default()
            };
            
            if Process32FirstW(snapshot, &mut pe32).as_bool() {
                loop {
                    let process_name = String::from_utf16_lossy(&pe32.szExeFile)
                        .to_lowercase();
                    
                    if process_name.contains("frida") || 
                       process_name.contains("gdb") || 
                       process_name.contains("lldb") ||
                       process_name.contains("windbg") ||
                       process_name.contains("ollydbg") ||
                       process_name.contains("x64dbg") ||
                       process_name.contains("ida") {
                        CloseHandle(snapshot);
                        return true;
                    }
                    
                    if !Process32NextW(snapshot, &mut pe32).as_bool() {
                        break;
                    }
                }
            }
            CloseHandle(snapshot);
        }
        
        // Prüfe auf verdächtige Registry-Einträge
        let suspicious_keys = [
            "SOFTWARE\\Frida",
            "SOFTWARE\\Xposed",
            "SOFTWARE\\Substrate",
            "SOFTWARE\\Cydia",
            "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run\\Frida",
            "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run\\Xposed",
            "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run\\Substrate",
        ];
        
        for key_path in &suspicious_keys {
            unsafe {
                let mut hkey = HKEY::default();
                let key_wide: Vec<u16> = key_path.encode_utf16().chain(std::iter::once(0)).collect();
                let result = RegOpenKeyExW(
                    HKEY_LOCAL_MACHINE,
                    &key_wide,
                    0,
                    KEY_READ,
                    &mut hkey,
                );
                
                if result == ERROR_SUCCESS {
                    RegCloseKey(hkey);
                    return true;
                }
            }
        }
        
        false
    }
    
    pub fn perform_security_check(&self) -> SecurityCheckResult {
        let debugger = self.detect_debugger();
        let vm = self.detect_vm();
        let hooks = self.detect_hooks();
        
        SecurityCheckResult {
            passed: !debugger && !vm && !hooks,
            details: vec![
                ("debugger", debugger),
                ("vm", vm),
                ("hooks", hooks),
            ].into_iter().collect(),
        }
    }
}

#[cfg(windows)]
#[derive(Debug)]
pub struct SecurityCheckResult {
    pub passed: bool,
    pub details: HashMap<&'static str, bool>,
}