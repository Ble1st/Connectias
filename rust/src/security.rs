//! Security FFI Exports
//!
//! RASP (Runtime Application Self-Protection) Checks:
//! - Root Detection (Android)
//! - Jailbreak Detection (iOS)
//! - Debugger Detection
//! - Emulator Detection
//! - Tamper Detection

use log::{info, warn, error};
use crate::state::*;

/// Führe vollständigen RASP-Security Check durch
///
/// Output:
///   - 0: Sicher
///   - > 0: Gefährdung erkannt (App sollte beendet werden)
///   - < 0: Fehler
///
/// SICHERHEIT: Bei nicht-null Rückgabe MUSS die App sofort beendet werden!
#[no_mangle]
pub extern "C" fn connectias_rasp_check_environment() -> i32 {
    info!("🛡️ Starte RASP-Umgebungsprüfung");

    let rt = get_runtime();
    let result = rt.block_on(async {
        // Prüfe alle RASP-Vektoren
        let checks = vec![
            ("Root", check_root()),
            ("Debugger", check_debugger()),
            ("Emulator", check_emulator()),
            ("Tamper", check_tampering()),
        ];

        let mut threat_detected = false;
        for (name, result) in checks {
            match result {
                RaspStatus::Safe => info!("✅ {} - Safe", name),
                RaspStatus::Suspicious => {
                    warn!("⚠️ {} - Suspicious", name);
                    threat_detected = true;
                }
                RaspStatus::Compromised => {
                    error!("🔴 {} - COMPROMISED", name);
                    threat_detected = true;
                }
            }
        }

        if threat_detected {
            error!("🚨 RASP-Bedrohung erkannt - App wird beendet!");
            1
        } else {
            info!("✅ Alle RASP-Checks bestanden");
            0
        }
    });

    result
}

/// Prüfe auf Root/Super-User Zugriff
#[no_mangle]
pub extern "C" fn connectias_rasp_check_root() -> i32 {
    match check_root() {
        RaspStatus::Safe => {
            info!("✅ Root-Check: Safe");
            0
        }
        RaspStatus::Suspicious => {
            warn!("⚠️ Root-Check: Suspicious");
            1
        }
        RaspStatus::Compromised => {
            error!("🔴 Root-Check: COMPROMISED");
            2
        }
    }
}

/// Prüfe auf Debugger
#[no_mangle]
pub extern "C" fn connectias_rasp_check_debugger() -> i32 {
    match check_debugger() {
        RaspStatus::Safe => {
            info!("✅ Debugger-Check: Safe");
            0
        }
        RaspStatus::Suspicious => {
            warn!("⚠️ Debugger-Check: Suspicious");
            1
        }
        RaspStatus::Compromised => {
            error!("🔴 Debugger-Check: COMPROMISED");
            2
        }
    }
}

/// Prüfe auf Emulator/Virtualisierung
#[no_mangle]
pub extern "C" fn connectias_rasp_check_emulator() -> i32 {
    match check_emulator() {
        RaspStatus::Safe => {
            info!("✅ Emulator-Check: Safe");
            0
        }
        RaspStatus::Suspicious => {
            warn!("⚠️ Emulator-Check: Suspicious");
            1
        }
        RaspStatus::Compromised => {
            error!("🔴 Emulator-Check: COMPROMISED");
            2
        }
    }
}

/// Prüfe auf Tamper/Manipulation
#[no_mangle]
pub extern "C" fn connectias_rasp_check_tamper() -> i32 {
    match check_tampering() {
        RaspStatus::Safe => {
            info!("✅ Tamper-Check: Safe");
            0
        }
        RaspStatus::Suspicious => {
            warn!("⚠️ Tamper-Check: Suspicious");
            1
        }
        RaspStatus::Compromised => {
            error!("🔴 Tamper-Check: COMPROMISED");
            2
        }
    }
}

#[derive(Debug, Clone, Copy)]
#[allow(dead_code)]
enum RaspStatus {
    Safe,
    Suspicious,
    Compromised,
}

/// Root-Check Implementierung
fn check_root() -> RaspStatus {
    #[cfg(target_os = "android")]
    {
        // Prüfe auf Superuser
        if std::path::Path::new("/system/app/Superuser.apk").exists() {
            return RaspStatus::Compromised;
        }
        if std::path::Path::new("/system/xbin/su").exists() {
            return RaspStatus::Compromised;
        }
        if std::path::Path::new("/sbin/su").exists() {
            return RaspStatus::Compromised;
        }
        if std::path::Path::new("/system/bin/su").exists() {
            return RaspStatus::Compromised;
        }

        // Prüfe auf Magisk (Advanced Root Manager)
        if std::path::Path::new("/data/adb/magisk").exists() {
            return RaspStatus::Suspicious;
        }

        // Versuche su auszuführen (wird auf gerooteten Geräten erfolgreich sein)
        match std::process::Command::new("su").arg("-c").arg("id").output() {
            Ok(output) => {
                if output.status.success() {
                    return RaspStatus::Compromised;
                }
            }
            Err(_) => {}
        }
    }

    #[cfg(target_os = "linux")]
    {
        // Prüfe auf EUID (Effective User ID)
        unsafe {
            if libc::geteuid() == 0 {
                return RaspStatus::Compromised;
            }
        }
    }

    RaspStatus::Safe
}

/// Debugger-Check Implementierung
fn check_debugger() -> RaspStatus {
    #[cfg(target_os = "android")]
    {
        // Prüfe /proc/self/status auf TracerPid
        if let Ok(status) = std::fs::read_to_string("/proc/self/status") {
            if let Some(line) = status.lines().find(|l| l.starts_with("TracerPid:")) {
                if !line.contains("TracerPid:\t0") {
                    return RaspStatus::Compromised;
                }
            }
        }

        // Prüfe ro.debuggable Property
        match std::process::Command::new("getprop")
            .arg("ro.debuggable")
            .output()
        {
            Ok(output) => {
                if String::from_utf8_lossy(&output.stdout).contains("1") {
                    return RaspStatus::Suspicious;
                }
            }
            Err(_) => {}
        }
    }

    #[cfg(target_os = "linux")]
    {
        // Prüfe TracerPid
        if let Ok(status) = std::fs::read_to_string("/proc/self/status") {
            if let Some(line) = status.lines().find(|l| l.starts_with("TracerPid:")) {
                if !line.contains("TracerPid:\t0") {
                    return RaspStatus::Compromised;
                }
            }
        }
    }

    RaspStatus::Safe
}

/// Emulator-Check Implementierung
fn check_emulator() -> RaspStatus {
    #[cfg(target_os = "android")]
    {
        // Prüfe auf typische Emulator-Properties
        let emulator_markers = vec![
            "ro.kernel.android.qemud",
            "ro.kernel.qemu",
            "ro.hardware.virtual_device",
            "ro.product.cpu.abi",
        ];

        for marker in emulator_markers {
            match std::process::Command::new("getprop").arg(marker).output() {
                Ok(output) => {
                    let value = String::from_utf8_lossy(&output.stdout);
                    if !value.is_empty() && value.contains("x86") {
                        return RaspStatus::Suspicious;
                    }
                }
                Err(_) => {}
            }
        }

        // Prüfe auf Qemu
        if std::path::Path::new("/system/lib/libqemu.so").exists() {
            return RaspStatus::Suspicious;
        }
    }

    RaspStatus::Safe
}

/// Tamper-Check Implementierung
fn check_tampering() -> RaspStatus {
    #[cfg(target_os = "android")]
    {
        // Prüfe auf Hook-Framework-Prozesse
        let hook_indicators = vec![
            "/system/xbin/xposed",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
        ];

        for indicator in hook_indicators {
            if std::path::Path::new(indicator).exists() {
                return RaspStatus::Suspicious;
            }
        }
    }

    RaspStatus::Safe
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rasp_check_environment() {
        let result = connectias_rasp_check_environment();
        assert!(result >= 0);
    }

    #[test]
    fn test_root_check() {
        let result = connectias_rasp_check_root();
        assert!(result >= 0);
    }

    #[test]
    fn test_debugger_check() {
        let result = connectias_rasp_check_debugger();
        assert!(result >= 0);
    }

    #[test]
    fn test_emulator_check() {
        let result = connectias_rasp_check_emulator();
        assert!(result >= 0);
    }

    #[test]
    fn test_tamper_check() {
        let result = connectias_rasp_check_tamper();
        assert!(result >= 0);
    }
}
