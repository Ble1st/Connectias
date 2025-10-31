use libc;
use nix::sys::resource::{setrlimit, Resource, Rlim};
use std::collections::HashMap;
use std::process::Command;
use std::path::PathBuf;
use log::{info, warn, error};

pub struct LinuxProcessManager {
    processes: HashMap<String, u32>,
}

impl LinuxProcessManager {
    pub fn new() -> Self {
        Self {
            processes: HashMap::new(),
        }
    }
    
    pub fn spawn_plugin(&mut self, plugin_id: &str, plugin_path: &PathBuf) -> Result<u32, std::io::Error> {
        // Validiere plugin_id gegen Command Injection
        if !is_valid_plugin_id(plugin_id) {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                format!("Ungültige plugin_id: {}", plugin_id)
            ));
        }
        
        // Validiere Plugin-Pfad
        if !plugin_path.exists() {
            return Err(std::io::Error::new(
                std::io::ErrorKind::NotFound,
                format!("Plugin-Datei nicht gefunden: {:?}", plugin_path)
            ));
        }
        
        if !plugin_path.is_file() {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                format!("Plugin-Pfad ist keine Datei: {:?}", plugin_path)
            ));
        }
        
        // Erstelle Command mit Resource-Limits
        let mut cmd = std::process::Command::new(plugin_path);
        
        // Setze Resource-Limits in pre_exec
        cmd.pre_exec(move || {
            if let Err(e) = set_resource_limits_in_child() {
                error!("Resource-Limits setzen fehlgeschlagen: {}", e);
                return Err(e);
            }
            Ok(())
        });
        
        // Spawn Plugin-Prozess
        match cmd.spawn() {
            Ok(child) => {
                let pid = child.id();
                self.processes.insert(plugin_id.to_string(), pid);
                info!("Plugin {} gestartet mit PID {}", plugin_id, pid);
                Ok(pid)
            },
            Err(e) => {
                error!("Plugin-Spawn fehlgeschlagen: {}", e);
                Err(e)
            }
        }
    }
    
}

/// Setzt Resource-Limits im Child-Prozess
fn set_resource_limits_in_child() -> std::io::Result<()> {
    use nix::errno::Errno;
    
    // Memory-Limit: 100MB (sowohl soft als auch hard limit)
    setrlimit(Resource::RLIMIT_AS, Rlim::from_bytes(100 * 1024 * 1024), Rlim::from_bytes(100 * 1024 * 1024))
        .map_err(|e| {
            // Extrahiere errno aus nix::Error
            let errno = e.as_errno()
                .map(|errno| errno as i32)
                .unwrap_or(libc::EINVAL);
            std::io::Error::from_raw_os_error(errno)
        })?;
    
    // CPU-Limit: 10 Sekunden (sowohl soft als auch hard limit)
    setrlimit(Resource::RLIMIT_CPU, Rlim::from_raw(10), Rlim::from_raw(10))
        .map_err(|e| {
            let errno = e.as_errno()
                .map(|errno| errno as i32)
                .unwrap_or(libc::EINVAL);
            std::io::Error::from_raw_os_error(errno)
        })?;
    
    // File-Descriptor-Limit: 50 (sowohl soft als auch hard limit)
    setrlimit(Resource::RLIMIT_NOFILE, Rlim::from_raw(50), Rlim::from_raw(50))
        .map_err(|e| {
            let errno = e.as_errno()
                .map(|errno| errno as i32)
                .unwrap_or(libc::EINVAL);
            std::io::Error::from_raw_os_error(errno)
        })?;
    
    Ok(())
}

impl LinuxProcessManager {
    pub fn terminate_plugin(&mut self, plugin_id: &str) -> Result<(), std::io::Error> {
        if let Some(pid) = self.processes.remove(plugin_id) {
            use nix::sys::signal::{kill, Signal};
            use nix::unistd::Pid;
            
            let pid = Pid::from_raw(pid as i32);
            kill(pid, Signal::SIGTERM)?;
            info!("Plugin {} (PID {}) beendet", plugin_id, pid);
        }
        Ok(())
    }
    
    pub fn is_plugin_running(&self, plugin_id: &str) -> bool {
        self.processes.contains_key(plugin_id)
    }
}

impl Drop for LinuxProcessManager {
    fn drop(&mut self) {
        // Sammle Plugin-IDs zuerst, um borrow checker Konflikte zu vermeiden
        let plugin_ids: Vec<String> = self.processes.keys().cloned().collect();
        
        // Beende jeden Plugin-Prozess
        for plugin_id in plugin_ids {
            if let Some(pid) = self.processes.remove(&plugin_id) {
                use nix::sys::signal::{kill, Signal};
                use nix::unistd::Pid;
                
                let pid = Pid::from_raw(pid as i32);
                if let Err(e) = kill(pid, Signal::SIGTERM) {
                    warn!("Plugin {} (PID {}) konnte nicht beendet werden: {}", plugin_id, pid, e);
                } else {
                    info!("Plugin {} (PID {}) beendet", plugin_id, pid);
                }
            }
        }
    }
}

/// Validiert Plugin-ID gegen Command Injection
/// Erlaubt nur alphanumerische Zeichen, Bindestriche und Unterstriche
/// Maximale Länge: 64 Zeichen
fn is_valid_plugin_id(plugin_id: &str) -> bool {
    // Erlaube nur alphanumerische Zeichen, Bindestriche und Unterstriche
    plugin_id.chars().all(|c| c.is_alphanumeric() || c == '-' || c == '_') &&
    !plugin_id.is_empty() &&
    plugin_id.len() <= 64 // Maximale Länge
}
