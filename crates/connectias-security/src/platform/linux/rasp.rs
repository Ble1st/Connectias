use std::fs;
use std::path::Path;

pub struct LinuxRASP;

impl LinuxRASP {
    pub fn new() -> Self {
        Self
    }
    
    pub fn detect_debugger(&self) -> bool {
        if let Ok(status) = fs::read_to_string("/proc/self/status") {
            for line in status.lines() {
                if line.starts_with("TracerPid:") {
                    let pid: i32 = line.split_whitespace()
                        .nth(1)
                        .and_then(|s| s.parse().ok())
                        .unwrap_or(0);
                    return pid != 0;
                }
            }
        }
        false
    }
    
    pub fn detect_vm(&self) -> bool {
        // Prüfe auf VM-Artefakte
        let vm_indicators = [
            "/proc/sys/kernel/hostname",
            "/proc/sys/kernel/ostype",
            "/proc/sys/kernel/osrelease",
        ];
        
        for indicator in &vm_indicators {
            if let Ok(content) = fs::read_to_string(indicator) {
                let content = content.to_lowercase();
                if content.contains("qemu") || 
                   content.contains("vmware") || 
                   content.contains("virtualbox") ||
                   content.contains("xen") {
                    return true;
                }
            }
        }
        
        // Prüfe DMI-Informationen
        if let Ok(dmi) = fs::read_to_string("/sys/class/dmi/id/product_name") {
            let dmi = dmi.to_lowercase();
            if dmi.contains("virtual") || 
               dmi.contains("vmware") || 
               dmi.contains("qemu") ||
               dmi.contains("virtualbox") {
                return true;
            }
        }
        
        false
    }
    
    pub fn detect_hooks(&self) -> bool {
        // Prüfe auf LD_PRELOAD
        if std::env::var("LD_PRELOAD").is_ok() {
            return true;
        }
        
        // Prüfe auf verdächtige Libraries in /proc/self/maps
        if let Ok(maps) = fs::read_to_string("/proc/self/maps") {
            let suspicious_patterns = [
                "substrate", "xposed", "frida", "cydia", "substrate",
                "libhook", "libinject", "libhack", "libpatch",
                "gadget", "gadget.so", "inject", "hook", "patch",
                "libsubstrate", "libxposed", "libfrida", "libcydia",
                "libgadget", "libinject", "libhack", "libpatch",
                "substrate.so", "xposed.so", "frida.so", "cydia.so",
                "gadget.so", "inject.so", "hook.so", "patch.so",
            ];
            
            for line in maps.lines() {
                let line_lower = line.to_lowercase();
                for pattern in &suspicious_patterns {
                    if line_lower.contains(pattern) {
                        return true;
                    }
                }
            }
        }
        
        // Prüfe auf verdächtige Prozesse
        if let Ok(entries) = fs::read_dir("/proc") {
            for entry in entries.flatten() {
                if let Ok(comm) = fs::read_to_string(entry.path().join("comm")) {
                    let comm_lower = comm.trim().to_lowercase();
                    if comm_lower.contains("frida") || 
                       comm_lower.contains("gdb") || 
                       comm_lower.contains("lldb") ||
                       comm_lower.contains("strace") ||
                       comm_lower.contains("ltrace") {
                        return true;
                    }
                }
            }
        }
        
        // Prüfe auf verdächtige Environment Variables
        let suspicious_env_vars = [
            "FRIDA_", "XPOSED_", "SUBSTRATE_", "CYDIA_",
            "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_",
        ];
        
        for (key, _) in std::env::vars() {
            let key_upper = key.to_uppercase();
            for pattern in &suspicious_env_vars {
                if key_upper.starts_with(pattern) {
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

#[derive(Debug)]
pub struct SecurityCheckResult {
    pub passed: bool,
    pub details: std::collections::HashMap<&'static str, bool>,
}
