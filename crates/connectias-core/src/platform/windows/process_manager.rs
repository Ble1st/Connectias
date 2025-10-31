#[cfg(windows)]
use windows::Win32::System::Threading::*;
#[cfg(windows)]
use windows::Win32::Foundation::*;
#[cfg(windows)]
use std::collections::HashMap;
#[cfg(windows)]
use std::path::PathBuf;
#[cfg(windows)]
use log::{info, warn, error};

#[cfg(windows)]
pub struct WindowsProcessManager {
    processes: HashMap<String, PROCESS_INFORMATION>,
    plugin_exe_path: PathBuf,
}

#[cfg(windows)]
impl WindowsProcessManager {
    /// Erstellt einen neuen WindowsProcessManager mit konfigurierbarem Plugin-Executable-Pfad
    /// 
    /// # Arguments
    /// * `plugin_exe_path` - Pfad zur Plugin-Executable (z.B. "connectias_plugin.exe" oder vollständiger Pfad)
    /// 
    /// # Errors
    /// Gibt einen Fehler zurück, wenn der Pfad nicht existiert oder nicht kanonisiert werden kann
    pub fn new(plugin_exe_path: PathBuf) -> std::io::Result<Self> {
        // Validiere und kanonisiere den Pfad
        let canonical_path = plugin_exe_path.canonicalize()
            .map_err(|e| std::io::Error::new(
                std::io::ErrorKind::NotFound,
                format!("Plugin executable path not found or cannot be canonicalized: {:?}, error: {}", plugin_exe_path, e)
            ))?;
        
        if !canonical_path.is_file() {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                format!("Plugin executable path is not a file: {:?}", canonical_path)
            ));
        }
        
        Ok(Self {
            processes: HashMap::new(),
            plugin_exe_path: canonical_path,
        })
    }
    
    /// Erstellt einen WindowsProcessManager mit Standard-Pfad (für Backward-Compatibility)
    #[deprecated(note = "Use new(PathBuf) instead with explicit path")]
    pub fn new_default() -> std::io::Result<Self> {
        Self::new(PathBuf::from("connectias_plugin.exe"))
    }
    
    pub fn spawn_plugin(&mut self, plugin_id: &str, plugin_path: &PathBuf) -> Result<u32, std::io::Error> {
        // Validiere plugin_id gegen Command Injection
        if !is_valid_plugin_id(plugin_id) {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                format!("Ungültiger Plugin-ID: {}", plugin_id)
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
        
        // Verwende konfigurierten Plugin-Executable-Pfad
        let exe_path_str = self.plugin_exe_path.to_string_lossy().to_string();
        let exe_path_wide: Vec<u16> = self.plugin_exe_path.to_string_lossy()
            .encode_utf16()
            .chain(std::iter::once(0))
            .collect();
        
        let args = format!("\"{}\" \"{}\"", plugin_id, plugin_path.display());
        let command_line = format!("\"{}\" {}", exe_path_str, args);
        let command_line_wide: Vec<u16> = command_line.encode_utf16().chain(std::iter::once(0)).collect();
        
        let mut startup_info = STARTUPINFOW {
            cb: std::mem::size_of::<STARTUPINFOW>() as u32,
            ..Default::default()
        };
        
        let mut process_info = PROCESS_INFORMATION::default();
        
        unsafe {
            let result = CreateProcessW(
                Some(&windows::core::HSTRING::from(self.plugin_exe_path.to_string_lossy().as_ref())),
                windows::core::PWSTR(command_line_wide.as_ptr() as *mut u16),
                None,
                None,
                false,
                PROCESS_CREATION_FLAGS(0),
                None,
                Some(&windows::core::HSTRING::from(plugin_path.parent().unwrap_or(std::path::Path::new(".")))),
                &startup_info,
                &mut process_info,
            );
            
            if result.as_bool() {
                let pid = process_info.dwProcessId;
                self.processes.insert(plugin_id.to_string(), process_info);
                info!("Plugin {} gestartet mit PID {}", plugin_id, pid);
                Ok(pid)
            } else {
                let error = std::io::Error::last_os_error();
                error!("Plugin {} starten fehlgeschlagen: {}", plugin_id, error);
                Err(error)
            }
        }
    }
    
    pub fn terminate_plugin(&mut self, plugin_id: &str) -> Result<(), std::io::Error> {
        if let Some(process_info) = self.processes.remove(plugin_id) {
            unsafe {
                let result = TerminateProcess(process_info.hProcess, 1);
                
                // Schließe Handles IMMER, unabhängig vom TerminateProcess Ergebnis
                let _ = CloseHandle(process_info.hProcess);
                let _ = CloseHandle(process_info.hThread);
                
                if result.as_bool() {
                    info!("Plugin {} (PID {}) beendet", plugin_id, process_info.dwProcessId);
                    Ok(())
                } else {
                    let error = std::io::Error::last_os_error();
                    warn!("Plugin {} (PID {}) konnte nicht beendet werden: {}", 
                          plugin_id, process_info.dwProcessId, error);
                    Err(error)
                }
            }
        } else {
            Ok(())
        }
    }
    
    pub fn is_plugin_running(&self, plugin_id: &str) -> bool {
        if let Some(process_info) = self.processes.get(plugin_id) {
            unsafe {
                use windows::Win32::System::Threading::GetExitCodeProcess;
                use windows::Win32::Foundation::STILL_ACTIVE;
                
                let mut exit_code = 0u32;
                let result = GetExitCodeProcess(process_info.hProcess, &mut exit_code);
                
                if result.as_bool() {
                    // Prozess ist noch aktiv wenn Exit-Code STILL_ACTIVE ist
                    exit_code == STILL_ACTIVE
                } else {
                    // Fehler beim Abrufen des Exit-Codes - Prozess wahrscheinlich beendet
                    false
                }
            }
        } else {
            false
        }
    }
}

/// Validiert Plugin-ID gegen Command Injection
fn is_valid_plugin_id(plugin_id: &str) -> bool {
    // Erlaube nur alphanumerische Zeichen, Bindestriche und Unterstriche
    plugin_id.chars().all(|c| c.is_alphanumeric() || c == '-' || c == '_') &&
    !plugin_id.is_empty() &&
    plugin_id.len() <= 64 // Maximale Länge
}

#[cfg(windows)]
impl Drop for WindowsProcessManager {
    fn drop(&mut self) {
        // Sammle Plugin-IDs zuerst, um borrow checker Konflikte zu vermeiden
        let plugin_ids: Vec<String> = self.processes.keys().cloned().collect();
        
        // Beende jeden Plugin-Prozess
        for plugin_id in plugin_ids {
            if let Some(process_info) = self.processes.remove(&plugin_id) {
                use windows::Win32::Foundation::CloseHandle;
                use windows::Win32::System::Threading::TerminateProcess;
                
                unsafe {
                    // Beende Prozess - TerminateProcess gibt BOOL zurück, kein Result
                    let result = TerminateProcess(process_info.hProcess, 1);
                    if !result.as_bool() {
                        let error = std::io::Error::last_os_error();
                        warn!("Plugin {} (PID {}) konnte nicht beendet werden: {}", 
                              plugin_id, process_info.dwProcessId, error);
                    } else {
                        info!("Plugin {} (PID {}) beendet", plugin_id, process_info.dwProcessId);
                    }
                    
                    // Schließe Handles IMMER, unabhängig vom TerminateProcess Ergebnis
                    let _ = CloseHandle(process_info.hProcess);
                    let _ = CloseHandle(process_info.hThread);
                }
            }
        }
    }
}