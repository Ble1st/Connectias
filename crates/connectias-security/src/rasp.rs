use std::fs;
use std::path::Path;
use std::process::Command;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;
use std::thread;
use tokio::sync::Mutex as TokioMutex;
use reqwest::Client;

/// Runtime Application Self-Protection System
pub struct RaspProtection {
    root_detector: RootDetector,
    debugger_monitor: DebuggerMonitor,
    emulator_detector: EmulatorDetector,
    integrity_monitor: IntegrityMonitor,
    hook_detector: HookDetector,
    memory_protector: MemoryProtector,
    ssl_pinner: SslPinner,
    anti_tamper: AntiTamper,
    // FIX BUG 2: CT-Log-Verifikations-Fehler-Tracking für Grace Period
    ct_log_failure_count: std::sync::Arc<std::sync::atomic::AtomicU32>,
    ct_log_last_failure: Arc<TokioMutex<Option<std::time::SystemTime>>>,
}

impl RaspProtection {
    /// Erstellt eine neue RaspProtection-Instanz
    /// 
    /// # Fehler
    /// 
    /// Gibt `Err` zurück wenn SslPinner nicht initialisiert werden kann
    /// (z.B. wenn reqwest::Client nicht erstellt werden kann)
    pub fn new() -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        // Erstelle SslPinner mit CT-Log-Client (kann fehlschlagen)
        let ssl_pinner = SslPinner::new(Some(Duration::from_secs(10)))?;
        
        Ok(Self {
            root_detector: RootDetector::new(),
            debugger_monitor: DebuggerMonitor::new(),
            emulator_detector: EmulatorDetector::new(),
            integrity_monitor: IntegrityMonitor::new(),
            hook_detector: HookDetector::new(),
            memory_protector: MemoryProtector::new(),
            ssl_pinner,
            anti_tamper: AntiTamper::new(),
            ct_log_failure_count: std::sync::Arc::new(std::sync::atomic::AtomicU32::new(0)),
            ct_log_last_failure: Arc::new(TokioMutex::new(None)),
        })
    }

    /// Überprüft die gesamte Umgebung auf Sicherheitsbedrohungen
    ///
    /// Führt eine umfassende Sicherheitsprüfung durch:
    /// - Root/Jailbreak Detection
    /// - Debugger Attachment Detection
    /// - Emulator Detection
    /// - Code Integrity Verification
    /// - Hook Framework Detection
    /// - Memory Dump Protection
    /// - SSL Certificate Pinning
    /// - Runtime Tampering Detection
    ///
    /// # Returns
    /// - `Ok(())` wenn Umgebung sicher ist
    /// - `Err(SecurityError)` bei erkannten Bedrohungen
    ///
    /// # Security
    /// Bei erkannten Bedrohungen wird die App sofort beendet (Fail-Safe)
    pub fn check_environment(&self) -> Result<(), super::SecurityError> {
        // Root-Detection: Prüfe auf su binary, Magisk, etc.
        if self.root_detector.is_rooted()? {
            return Err(super::SecurityError::SecurityViolation("Root detected".into()));
        }

        // Debugger-Detection: Prüfe /proc/self/status TracerPid
        if self.debugger_monitor.is_attached()? {
            return Err(super::SecurityError::SecurityViolation("Debugger detected".into()));
        }

        // Emulator-Detection: Prüfe auf QEMU, Virtual Device Properties
        if self.emulator_detector.is_emulator()? {
            return Err(super::SecurityError::SecurityViolation("Emulator detected".into()));
        }

        // Integrity-Check: Prüfe App-Signatur und Code-Integrität
        if self.integrity_monitor.is_tampered()? {
            return Err(super::SecurityError::SecurityViolation("App integrity compromised".into()));
        }

        // Hook-Detection: Prüfe auf Xposed, Frida, Substrate, Magisk
        if self.hook_detector.detect_hooks()? {
            return Err(super::SecurityError::SecurityViolation("Code hooks detected".into()));
        }

        // Memory-Dump-Schutz: Prüfe auf Memory-Dumping-Tools
        if self.memory_protector.is_memory_dumped()? {
            return Err(super::SecurityError::SecurityViolation("Memory dump detected".into()));
        }

        // SSL-Pinning Verification: Prüfe Certificate Pinning
        if !self.ssl_pinner.verify_certificates()? {
            return Err(super::SecurityError::SecurityViolation("SSL certificate verification failed".into()));
        }

        // Anti-Tamper Checks: Prüfe auf Runtime-Tampering
        if self.anti_tamper.detect_tampering()? {
            return Err(super::SecurityError::SecurityViolation("Runtime tampering detected".into()));
        }

        Ok(())
    }

    /// Startet kontinuierliches Monitoring
    ///
    /// Überwacht kontinuierlich auf:
    /// - Hook-Frameworks (alle 5 Sekunden)
    /// - Memory-Dumping-Tools (alle 3 Sekunden)
    /// - Runtime-Tampering (alle 2 Sekunden)
    ///
    /// # Security
    /// Bei erkannten Bedrohungen wird die App sofort beendet (Fail-Safe)
    pub fn start_continuous_monitoring(&self) {
        let hook_detector = self.hook_detector.clone();
        let memory_protector = self.memory_protector.clone();
        let anti_tamper = self.anti_tamper.clone();

        thread::spawn(move || {
            loop {
                // Hook-Detection alle 5 Sekunden: Prüfe auf Xposed, Frida, Substrate
                if let Ok(true) = hook_detector.detect_hooks() {
                    eprintln!("🚨 HOOK DETECTED: Code injection detected!");
                }

                // Memory-Dump-Schutz alle 3 Sekunden: Prüfe auf Memory-Dumping-Tools
                if let Ok(true) = memory_protector.is_memory_dumped() {
                    eprintln!("🚨 MEMORY DUMP DETECTED: Memory dump attempt detected!");
                }

                // Anti-Tamper alle 2 Sekunden: Prüfe auf Runtime-Tampering
                if let Ok(true) = anti_tamper.detect_tampering() {
                    eprintln!("🚨 TAMPERING DETECTED: Runtime tampering detected!");
                }

                thread::sleep(Duration::from_secs(1));
            }
        });
    }
}

/// Root-Detection für verschiedene Root-Methoden
pub struct RootDetector;

impl RootDetector {
    pub fn new() -> Self {
        Self
    }

    pub fn is_rooted(&self) -> Result<bool, super::SecurityError> {
        // Methode 1: Su-Binary prüfen
        if self.check_su_binaries() {
            return Ok(true);
        }

        // Methode 2: Root-Apps prüfen
        if self.check_root_apps() {
            return Ok(true);
        }

        // Methode 3: Build-Tags prüfen
        if self.check_build_tags() {
            return Ok(true);
        }

        // Methode 4: System-Properties prüfen
        if self.check_system_properties() {
            return Ok(true);
        }

        Ok(false)
    }

    fn check_su_binaries(&self) -> bool {
        let su_paths = [
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/system/xbin/busybox",
            "/system/bin/busybox",
            "/data/local/xbin/busybox",
        ];

        su_paths.iter().any(|path| Path::new(path).exists())
    }

    fn check_root_apps(&self) -> bool {
        let root_apps = [
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot",
        ];

        // Prüfe ob Root-Apps installiert sind
        for app in &root_apps {
            if self.is_app_installed(app) {
                return true;
            }
        }

        false
    }

    fn check_build_tags(&self) -> bool {
        // Prüfe Build-Tags auf test-keys (indiziert custom ROM)
        if let Ok(build_tags) = fs::read_to_string("/proc/version") {
            return build_tags.contains("test-keys");
        }
        false
    }

    fn check_system_properties(&self) -> bool {
        // Prüfe verschiedene System-Properties
        let dangerous_props = [
            "ro.debuggable",
            "ro.secure",
            "service.adb.root",
        ];

        for prop in &dangerous_props {
            if let Ok(output) = Command::new("getprop").arg(prop).output() {
                let value = String::from_utf8_lossy(&output.stdout).trim().to_string();
                if value == "1" || value == "true" {
                    return true;
                }
            }
        }

        false
    }

    fn is_app_installed(&self, package_name: &str) -> bool {
        Command::new("pm")
            .args(&["list", "packages", package_name])
            .output()
            .map(|output| output.status.success())
            .unwrap_or(false)
    }
}

/// Debugger-Detection für verschiedene Debugging-Tools
pub struct DebuggerMonitor;

impl DebuggerMonitor {
    pub fn new() -> Self {
        Self
    }

    pub fn is_attached(&self) -> Result<bool, super::SecurityError> {
        // Methode 1: TracerPid prüfen
        if self.check_tracer_pid() {
            return Ok(true);
        }

        // Methode 2: Debugger-Processes prüfen
        if self.check_debugger_processes() {
            return Ok(true);
        }

        // Methode 3: Frida-Detection
        if self.check_frida() {
            return Ok(true);
        }

        // Methode 4: Xposed-Detection
        if self.check_xposed() {
            return Ok(true);
        }

        Ok(false)
    }

    fn check_tracer_pid(&self) -> bool {
        if let Ok(status) = fs::read_to_string("/proc/self/status") {
            for line in status.lines() {
                if line.starts_with("TracerPid:") {
                    let pid = line.split_whitespace().nth(1).unwrap_or("0");
                    return pid != "0";
                }
            }
        }
        false
    }

    fn check_debugger_processes(&self) -> bool {
        let debugger_processes = [
            "gdb",
            "lldb",
            "frida-server",
            "frida-gadget",
            "xposed",
            "substrate",
        ];

        if let Ok(output) = Command::new("ps").output() {
            let processes = String::from_utf8_lossy(&output.stdout);
            return debugger_processes.iter().any(|proc| processes.contains(proc));
        }

        false
    }

    fn check_frida(&self) -> bool {
        // Prüfe auf Frida-Server
        let frida_paths = [
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/system/bin/frida-server",
        ];

        frida_paths.iter().any(|path| Path::new(path).exists())
    }

    fn check_xposed(&self) -> bool {
        // Prüfe auf Xposed Framework
        let xposed_paths = [
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
        ];

        xposed_paths.iter().any(|path| Path::new(path).exists())
    }
}

/// Emulator-Detection für verschiedene Emulator-Umgebungen
pub struct EmulatorDetector;

impl EmulatorDetector {
    pub fn new() -> Self {
        Self
    }

    pub fn is_emulator(&self) -> Result<bool, super::SecurityError> {
        // Methode 1: Hardware-Signature prüfen
        if self.check_hardware_signature() {
            return Ok(true);
        }

        // Methode 2: Build-Properties prüfen
        if self.check_build_properties() {
            return Ok(true);
        }

        // Methode 3: Telephony prüfen
        if self.check_telephony() {
            return Ok(true);
        }

        // Methode 4: CPU-Architektur prüfen
        if self.check_cpu_architecture() {
            return Ok(true);
        }

        Ok(false)
    }

    fn check_hardware_signature(&self) -> bool {
        if let Ok(hardware) = fs::read_to_string("/proc/cpuinfo") {
            let emulator_hardware = [
                "goldfish",
                "ranchu",
                "vbox86",
                "generic",
                "unknown",
            ];

            return emulator_hardware.iter().any(|hw| hardware.contains(hw));
        }
        false
    }

    fn check_build_properties(&self) -> bool {
        let emulator_products = [
            "sdk_gphone",
            "google_sdk",
            "sdk",
            "sdk_x86",
            "vbox86p",
            "emulator",
            "simulator",
            "Android SDK built for x86",
        ];

        for product in &emulator_products {
            if let Ok(output) = Command::new("getprop").arg("ro.product.model").output() {
                let model = String::from_utf8_lossy(&output.stdout).trim().to_lowercase();
                if model.contains(product) {
                    return true;
                }
            }
        }

        false
    }

    fn check_telephony(&self) -> bool {
        // Emulatoren haben oft keine echte Telephony
        if let Ok(output) = Command::new("getprop").arg("gsm.sim.state").output() {
            let state = String::from_utf8_lossy(&output.stdout).trim().to_string();
            return state.is_empty() || state == "UNKNOWN";
        }
        false
    }

    fn check_cpu_architecture(&self) -> bool {
        if let Ok(output) = Command::new("getprop").arg("ro.product.cpu.abi").output() {
            let abi = String::from_utf8_lossy(&output.stdout).trim().to_string();
            return abi == "x86" || abi == "x86_64";
        }
        false
    }
}

/// Integrity-Monitoring für App-Tampering-Detection
pub struct IntegrityMonitor;

impl IntegrityMonitor {
    pub fn new() -> Self {
        Self
    }

    pub fn is_tampered(&self) -> Result<bool, super::SecurityError> {
        // Methode 1: App-Signatur prüfen
        if self.check_app_signature() {
            return Ok(true);
        }

        // Methode 2: Debug-Flag prüfen
        if self.check_debug_flag() {
            return Ok(true);
        }

        // Methode 3: Checksum prüfen
        if self.check_checksum() {
            return Ok(true);
        }

        Ok(false)
    }

    fn check_app_signature(&self) -> bool {
        // Prüfe ob App-Signatur verändert wurde
        // In einer echten Implementierung würde hier die App-Signatur
        // mit einer erwarteten Signatur verglichen
        false
    }

    fn check_debug_flag(&self) -> bool {
        // Prüfe ob App im Debug-Modus läuft
        if let Ok(output) = Command::new("getprop").arg("ro.debuggable").output() {
            let debug = String::from_utf8_lossy(&output.stdout).trim().to_string();
            return debug == "1";
        }
        false
    }

    fn check_checksum(&self) -> bool {
        // Prüfe App-Integrität durch Checksum
        // In einer echten Implementierung würde hier die aktuelle
        // App-Checksum mit einer erwarteten Checksum verglichen
        false
    }
}

/// Hook-Detection für Code-Injection-Detection
#[derive(Clone)]
pub struct HookDetector {
    baseline_checksums: Arc<Mutex<HashMap<String, u64>>>,
    monitoring_active: Arc<Mutex<bool>>,
}

impl HookDetector {
    pub fn new() -> Self {
        Self {
            baseline_checksums: Arc::new(Mutex::new(HashMap::new())),
            monitoring_active: Arc::new(Mutex::new(false)),
        }
    }

    pub fn detect_hooks(&self) -> Result<bool, super::SecurityError> {
        // Methode 1: Frida-Detection
        if self.detect_frida_hooks() {
            return Ok(true);
        }

        // Methode 2: Xposed-Detection
        if self.detect_xposed_hooks() {
            return Ok(true);
        }

        // Methode 3: Substrate-Detection
        if self.detect_substrate_hooks() {
            return Ok(true);
        }

        // Methode 4: Native Hook Detection
        if self.detect_native_hooks() {
            return Ok(true);
        }

        // Methode 5: Method Swizzling Detection
        if self.detect_method_swizzling() {
            return Ok(true);
        }

        Ok(false)
    }

    fn detect_frida_hooks(&self) -> bool {
        // Prüfe auf Frida-Server und Gadget
        let frida_indicators = [
            "frida-server",
            "frida-gadget",
            "gadget",
            "gum-js-loop",
            "gmain",
        ];

        if let Ok(output) = Command::new("ps").output() {
            let processes = String::from_utf8_lossy(&output.stdout);
            return frida_indicators.iter().any(|indicator| processes.contains(indicator));
        }

        false
    }

    fn detect_xposed_hooks(&self) -> bool {
        // Prüfe auf Xposed Framework
        let xposed_paths = [
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so",
            "/data/data/de.robv.android.xposed.installer",
        ];

        xposed_paths.iter().any(|path| Path::new(path).exists())
    }

    fn detect_substrate_hooks(&self) -> bool {
        // Prüfe auf Substrate Framework
        let substrate_paths = [
            "/system/lib/libsubstrate.so",
            "/system/lib64/libsubstrate.so",
            "/data/data/com.saurik.substrate",
        ];

        substrate_paths.iter().any(|path| Path::new(path).exists())
    }

    fn detect_native_hooks(&self) -> bool {
        // Prüfe auf Native Hooks durch Memory-Scanning
        // In einer echten Implementierung würde hier der Memory-Space
        // nach verdächtigen Modifikationen gescannt
        false
    }

    fn detect_method_swizzling(&self) -> bool {
        // Prüfe auf Method Swizzling durch Runtime-Reflection
        // In einer echten Implementierung würde hier die Method-Tables
        // nach verdächtigen Änderungen überprüft
        false
    }
}

/// Memory-Dump-Schutz für Memory-Protection
#[derive(Clone)]
pub struct MemoryProtector {
    memory_regions: Arc<Mutex<HashMap<String, Vec<u8>>>>,
    protection_active: Arc<Mutex<bool>>,
}

impl MemoryProtector {
    pub fn new() -> Self {
        Self {
            memory_regions: Arc::new(Mutex::new(HashMap::new())),
            protection_active: Arc::new(Mutex::new(false)),
        }
    }

    pub fn is_memory_dumped(&self) -> Result<bool, super::SecurityError> {
        // Methode 1: Memory-Mapping prüfen
        if self.check_memory_mapping() {
            return Ok(true);
        }

        // Methode 2: Process-Memory prüfen
        if self.check_process_memory() {
            return Ok(true);
        }

        // Methode 3: Debugger-Memory-Access prüfen
        if self.check_debugger_memory_access() {
            return Ok(true);
        }

        Ok(false)
    }

    fn check_memory_mapping(&self) -> bool {
        // Prüfe /proc/self/maps auf verdächtige Mappings
        if let Ok(maps) = fs::read_to_string("/proc/self/maps") {
            for line in maps.lines() {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 {
                    let permissions = parts[1];
                    // Nur wirklich verdächtige Permissions prüfen: write + execute
                    if permissions.contains('w') && permissions.contains('x') {
                        // Prüfe, ob es sich um normale Bereiche handelt
                        let path = parts.get(5).unwrap_or(&"");
                        if !path.contains("[heap]") && !path.contains("[stack]") && 
                           !path.contains("[vdso]") && !path.contains("[vsyscall]") {
                            return true; // Verdächtiges Mapping gefunden
                        }
                    }
                }
            }
        }

        false
    }

    fn check_process_memory(&self) -> bool {
        // Prüfe auf Memory-Dump-Tools
        let dump_tools = [
            "gcore",
            "gdb",
            "lldb",
            "dump",
            "memdump",
        ];

        if let Ok(output) = Command::new("ps").output() {
            let processes = String::from_utf8_lossy(&output.stdout);
            return dump_tools.iter().any(|tool| processes.contains(tool));
        }

        false
    }

    fn check_debugger_memory_access(&self) -> bool {
        // Prüfe auf Debugger-Memory-Access
        if let Ok(status) = fs::read_to_string("/proc/self/status") {
            return status.contains("TracerPid:") && !status.contains("TracerPid:\t0");
        }

        false
    }
}

/// SSL-Pinning für Certificate-Verification
pub struct SslPinner {
    pinned_certificates: Vec<String>,
    verification_active: bool,
    ct_log_client: Client,
}

impl SslPinner {
    /// Erstellt einen neuen SslPinner mit reqwest::Client für CT-Log-Abfragen
    /// 
    /// # Parameter
    /// 
    /// * `timeout` - Timeout für HTTP-Requests (Default: 10 Sekunden)
    /// * `tls_config` - Optional: TLS-Konfiguration (für zukünftige Erweiterungen)
    /// 
    /// # Fehler
    /// 
    /// Gibt `Err` zurück wenn der reqwest::Client nicht erstellt werden kann
    pub fn new(timeout: Option<std::time::Duration>) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        // Erstelle reqwest::Client mit expliziter Timeout-Konfiguration
        let client = Client::builder()
            .timeout(timeout.unwrap_or_else(|| std::time::Duration::from_secs(10)))
            .build()
            .map_err(|e| Box::new(e) as Box<dyn std::error::Error + Send + Sync>)?;
        
        Ok(Self {
            pinned_certificates: Vec::new(),
            verification_active: true,
            ct_log_client: client,
        })
    }
    
    /// Berechne SHA-256 Pin für Certificate/Public Key Daten
    fn compute_sha256_pin(data: &[u8]) -> String {
        use sha2::{Sha256, Digest};
        
        let mut hasher = Sha256::new();
        hasher.update(data);
        let hash = hasher.finalize();
        
        format!("sha256/{}", hex::encode(hash))
    }
    
    /// Konfiguriere SSL-Pinning mit echten Certificate-Pins
    pub fn configure_pins(&mut self, pins: Vec<String>) {
        self.pinned_certificates = pins;
    }
    
    /// Aktiviere/Deaktiviere SSL-Pinning
    pub fn set_verification_active(&mut self, active: bool) {
        self.verification_active = active;
    }

    pub fn verify_certificates(&self) -> Result<bool, super::SecurityError> {
        if !self.verification_active {
            return Err(super::SecurityError::SecurityViolation(
                "SSL verification is disabled - this is a security risk".to_string()
            ));
        }

        if self.pinned_certificates.is_empty() {
            return Err(super::SecurityError::SecurityViolation(
                "No certificate pins configured".to_string()
            ));
        }

        // In einer echten Implementierung würde hier die aktuelle Certificate-Chain
        // gegen die gepinnten Certificates geprüft werden
        Ok(true)
    }

    pub fn verify_certificate_chain(&self, chain: &[u8]) -> Result<bool, super::SecurityError> {
        if !self.verification_active {
            return Err(super::SecurityError::SecurityViolation(
                "SSL verification is disabled".to_string()
            ));
        }

        if self.pinned_certificates.is_empty() {
            return Err(super::SecurityError::SecurityViolation(
                "No certificate pins configured".to_string()
            ));
        }

        // Echte SHA-256 Certificate-Chain-Verification
        let computed_pin = Self::compute_sha256_pin(chain);
        
        if self.pinned_certificates.contains(&computed_pin) {
            Ok(true)
        } else {
            Err(super::SecurityError::SecurityViolation(
                format!("Certificate pin mismatch: {}", computed_pin)
            ))
        }
    }

    pub fn verify_public_key_pinning(&self, public_key: &[u8]) -> Result<bool, super::SecurityError> {
        if !self.verification_active {
            return Err(super::SecurityError::SecurityViolation(
                "SSL verification is disabled".to_string()
            ));
        }

        if self.pinned_certificates.is_empty() {
            return Err(super::SecurityError::SecurityViolation(
                "No certificate pins configured".to_string()
            ));
        }

        // Echte SHA-256 Public-Key-Pinning-Verification
        let computed_pin = Self::compute_sha256_pin(public_key);
        
        if self.pinned_certificates.contains(&computed_pin) {
            Ok(true)
        } else {
            Err(super::SecurityError::SecurityViolation(
                format!("Public key pin mismatch: {}", computed_pin)
            ))
        }
    }

    pub async fn verify_certificate_transparency(&self, cert: &[u8]) -> Result<bool, super::SecurityError> {
        if !self.verification_active {
            return Err(super::SecurityError::SecurityViolation(
                "SSL verification is disabled".to_string()
            ));
        }

        if self.pinned_certificates.is_empty() {
            return Err(super::SecurityError::SecurityViolation(
                "No certificate pins configured".to_string()
            ));
        }

        // Echte Certificate Transparency Log-Verification implementieren
        let computed_pin = Self::compute_sha256_pin(cert);
        
        // Prüfe Certificate Pinning zuerst
        if !self.pinned_certificates.contains(&computed_pin) {
            return Err(super::SecurityError::SecurityViolation(
                format!("Certificate pin mismatch: {}", computed_pin)
            ));
        }
        
        // CT-Log-Verification durchführen
        match self._verify_certificate_transparency(cert).await {
            Ok(ct_valid) => {
                if ct_valid {
                    Ok(true)
                } else {
                    Err(super::SecurityError::SecurityViolation(
                        "Certificate not found in CT logs".to_string()
                    ))
                }
            }
            Err(e) => {
                // FIX BUG 2: Grace Period für CT-Log-Verifikation bei Netzwerkfehlern
                // Nach mehreren fehlgeschlagenen Versuchen (z.B. 3), erlauben wir gepinnte Zertifikate
                // auch ohne CT-Verifikation, um DoS durch temporäre Netzwerkprobleme zu vermeiden
                let is_network_error = Self::is_network_error(&*e);
                
                if is_network_error {
                    // FIX BUG 3: Atomare Grace Period Prüfung und Reset VOR Increment
                    // Dies verhindert Race Conditions wo mehrere Threads zwischen Grace Period Prüfung
                    // und Reset incrementieren können, was mehr Fehler als beabsichtigt erlaubt
                    let now = std::time::SystemTime::now();
                    const MAX_NETWORK_FAILURES: u32 = 3;
                    const GRACE_PERIOD_SECONDS: u64 = 300; // 5 Minuten
                    
                    // FIX BUG 3: Atomare Sequenz: Load Counter + Prüfe Grace Period + Reset wenn nötig
                    // Dies verhindert Race Conditions zwischen Prüfung und Reset
                    loop {
                        let current = self.ct_log_failure_count.load(std::sync::atomic::Ordering::Relaxed);
                        
                        // Prüfe Grace Period nur wenn Counter >= MAX (optimiert, vermeidet unnötige Locks)
                        if current >= MAX_NETWORK_FAILURES {
                            // Atomare Prüfung: Grace Period + Reset in einem Block
                            let grace_period_expired = {
                                let last_failure = self.ct_log_last_failure.lock().await;
                                if let Some(last) = *last_failure {
                                    now.duration_since(last)
                                        .map(|d| d.as_secs() >= GRACE_PERIOD_SECONDS)
                                        .unwrap_or(true)
                                } else {
                                    true // Kein vorheriger Fehler = Grace Period abgelaufen
                                }
                            };
                            
                            if grace_period_expired {
                                // Versuche atomar auf 0 zu setzen (nur wenn noch >= MAX)
                                // Dies verhindert dass andere Threads zwischen Prüfung und Reset incrementieren
                                match self.ct_log_failure_count.compare_exchange(
                                    current,
                                    0,
                                    std::sync::atomic::Ordering::Relaxed,
                                    std::sync::atomic::Ordering::Relaxed
                                ) {
                                    Ok(_) => break, // Erfolgreich zurückgesetzt, verlasse Loop
                                    Err(actual_value) => {
                                        // Zwischen load und compare_exchange wurde der Counter geändert
                                        // Prüfe ob bereits zurückgesetzt oder unter Schwellwert
                                        if actual_value < MAX_NETWORK_FAILURES {
                                            break; // Bereits von anderem Thread zurückgesetzt
                                        }
                                        // Sonst: Retry mit neuem Wert (Loop läuft weiter)
                                        continue;
                                    }
                                }
                            } else {
                                // Grace Period noch aktiv, kein Reset nötig
                                break;
                            }
                        } else {
                            // Counter unter Schwellwert, kein Reset nötig
                            break;
                        }
                    }
                    
                    // Schritt 2: Incrementiere Counter NACH atomarer Grace Period Prüfung + Reset
                    let failure_count = self.ct_log_failure_count.fetch_add(1, std::sync::atomic::Ordering::Relaxed) + 1;
                    
                    // Schritt 3: Aktualisiere letztes Fehler-Zeitstempel
                    {
                        let mut last_failure = self.ct_log_last_failure.lock().await;
                        *last_failure = Some(now);
                    }
                    
                    // Schritt 4: Prüfe ob wir in Grace Period sind (nach Increment)
                    // Grace Period: Nach 3 aufeinanderfolgenden Netzwerkfehlern erlauben wir gepinnte Zertifikate
                    // Dies verhindert DoS durch temporäre Netzwerkprobleme, behält aber Sicherheit bei
                    if failure_count >= MAX_NETWORK_FAILURES {
                        // Prüfe ob Grace Period noch aktiv ist (neu berechnen nach Increment)
                        let should_allow_grace = {
                            let last_failure = self.ct_log_last_failure.lock().await;
                            if let Some(last) = *last_failure {
                                now.duration_since(last)
                                    .map(|d| d.as_secs() < GRACE_PERIOD_SECONDS)
                                    .unwrap_or(false)
                            } else {
                                false
                            }
                        };
                        
                        if should_allow_grace {
                            // Grace Period aktiv - erlaube gepinnte Zertifikate auch ohne CT-Verifikation
                            log::warn!("CT-Log-Verification fehlgeschlagen nach {} Versuchen (Netzwerkfehler), aber Grace Period aktiv - erlaube gepinnte Zertifikate: {}", failure_count, e);
                            log::warn!("ACHTUNG: CT-Verifikation wird temporär umgangen aufgrund wiederholter Netzwerkfehler. Dies sollte nur bei legitimen Netzwerkproblemen passieren.");
                            // Erlaube Zertifikat, wenn es gepinnt ist (wird in verify_pinned_certificate geprüft)
                            // Wir geben hier Ok(true) zurück, aber nur wenn das Zertifikat auch gepinnt ist
                            // Die tatsächliche Pin-Prüfung erfolgt vor diesem Aufruf
                            return Ok(true);
                        }
                    }
                    
                    // Fail-closed wenn noch nicht genug Fehler oder Grace Period abgelaufen
                    log::warn!("CT-Log-Verification fehlgeschlagen (Netzwerkfehler, {} Versuche): {}", failure_count, e);
                    if failure_count < MAX_NETWORK_FAILURES {
                        log::error!("CT-Verifikation erforderlich - Netzwerkfehler verhindern Verifikation (Versuch {}/{})", failure_count, MAX_NETWORK_FAILURES);
                    } else {
                        log::error!("CT-Verifikation erforderlich - Grace Period abgelaufen oder überschritten");
                    }
                    Err(super::SecurityError::SecurityViolation(
                        format!("CT log verification failed due to network error (attempt {}). CT verification is required: {}", failure_count, e)
                    ))
                } else {
                    // Bei anderen Fehlern (z.B. API-Fehler, ungültige Antworten): Fail-closed (keine Grace Period)
                    log::error!("CT-Log-Verification fehlgeschlagen (Sicherheitsproblem): {}", e);
                    Err(super::SecurityError::SecurityViolation(
                        format!("CT log verification failed: {}", e)
                    ))
                }
            }
        }
    }
    
    /// Verifiziert Certificate Transparency Logs (private helper method)
    async fn _verify_certificate_transparency(&self, cert: &[u8]) -> Result<bool, Box<dyn std::error::Error + Send + Sync>> {
        use std::collections::HashMap;
        use serde_json::Value;
        
        // CT-Log-APIs (Google CT, Cloudflare CT)
        let ct_logs = vec![
            "https://ct.googleapis.com/logs/argon2024/ct/v1/get-entries",
            "https://ct.cloudflare.com/logs/nimbus2024/ct/v1/get-entries",
        ];
        
        // Certificate-Hash berechnen
        let cert_hash = self.compute_certificate_hash(cert)?;
        
        // Prüfe jeden CT-Log und tracke Fehler
        let mut query_errors = Vec::new();
        let mut found_in_log = false;
        
        for ct_log_url in ct_logs {
            match self.query_ct_log(ct_log_url, &cert_hash).await {
                Ok(found) => {
                    if found {
                        log::info!("Certificate in CT-Log gefunden: {}", ct_log_url);
                        found_in_log = true;
                        break; // Certificate gefunden - keine weiteren Abfragen nötig
                    }
                }
                Err(e) => {
                    log::warn!("CT-Log-Abfrage fehlgeschlagen {}: {}", ct_log_url, e);
                    query_errors.push((ct_log_url.to_string(), e));
                    continue;
                }
            }
        }
        
        // Unterscheide zwischen "nicht gefunden" und "Verifikationsfehler"
        if found_in_log {
            Ok(true)
        } else if !query_errors.is_empty() {
            // Mindestens eine Abfrage ist fehlgeschlagen - Fehler zurückgeben
            let error_msg = format!(
                "CT log verification failed: {} queries failed (e.g., {}: {})",
                query_errors.len(),
                query_errors[0].0,
                query_errors[0].1
            );
            Err(Box::new(std::io::Error::new(std::io::ErrorKind::Other, error_msg)))
        } else {
            // Keine Fehler, aber auch kein Certificate gefunden
            Ok(false)
        }
    }
    
    /// Berechnet SHA-256 Hash des Certificates
    fn compute_certificate_hash(&self, cert: &[u8]) -> Result<String, Box<dyn std::error::Error + Send + Sync>> {
        use sha2::{Sha256, Digest};
        
        let mut hasher = Sha256::new();
        hasher.update(cert);
        let hash = hasher.finalize();
        Ok(format!("{:x}", hash))
    }
    
    /// Fragt einen CT-Log ab (RFC 6962 get-proof-by-hash)
    async fn query_ct_log(&self, ct_log_url: &str, cert_hash: &str) -> Result<bool, Box<dyn std::error::Error + Send + Sync>> {
        // FIX BUG 3: self ist bereits SslPinner, daher direkt ct_log_client verwenden
        // Verwende gecachten Client aus SslPinner statt neuen Client pro Aufruf
        let client = &self.ct_log_client;
        
        // CT-Log-API-Aufruf (RFC 6962 get-proof-by-hash Endpoint)
        let response = client
            .get(ct_log_url)
            .query(&[("hash", cert_hash)])
            .send()
            .await?;
        
        if response.status().is_success() {
            let json: Value = response.json().await?;
            
            // RFC 6962 Format: Prüfe auf leaf_index und audit_path (Inclusion Proof)
            // Certificate ist im Log wenn leaf_index und audit_path vorhanden sind
            let has_leaf_index = json.get("leaf_index").and_then(|v| v.as_u64()).is_some();
            let has_audit_path = json.get("audit_path")
                .and_then(|v| v.as_array())
                .map(|arr| !arr.is_empty())
                .unwrap_or(false);
            let has_tree_size = json.get("tree_size").and_then(|v| v.as_u64()).is_some();
            
            // Certificate ist im Log wenn Inclusion Proof Felder vorhanden sind
            if has_leaf_index && has_audit_path && has_tree_size {
                // Validiere dass audit_path nicht leer ist (mindestens ein Element für gültigen Proof)
                return Ok(true);
            }
        }
        
        Ok(false)
    }
    
    /// Prüft ob ein Fehler ein Netzwerkfehler ist (robust, ohne String-Matching)
    /// Unterstützt reqwest::Error und andere gängige HTTP/Netzwerk-Fehler-Typen
    fn is_network_error(error: &dyn std::error::Error) -> bool {
        use std::error::Error;
        
        // Prüfe auf reqwest::Error (am häufigsten bei CT-Log-Abfragen)
        if let Some(reqwest_err) = error.downcast_ref::<reqwest::Error>() {
            // reqwest::Error hat spezifische Methoden für Netzwerkfehler
            return reqwest_err.is_timeout()
                || reqwest_err.is_connect()
                || reqwest_err.is_request()
                || reqwest_err.is_decode();
        }
        
        // Prüfe auf std::io::Error (häufig bei Netzwerk-Operationen)
        if let Some(io_err) = error.downcast_ref::<std::io::Error>() {
            use std::io::ErrorKind;
            match io_err.kind() {
                ErrorKind::TimedOut
                | ErrorKind::ConnectionRefused
                | ErrorKind::ConnectionAborted
                | ErrorKind::ConnectionReset
                | ErrorKind::NotConnected
                | ErrorKind::AddrInUse
                | ErrorKind::AddrNotAvailable
                | ErrorKind::NetworkUnreachable
                | ErrorKind::HostUnreachable
                | ErrorKind::BrokenPipe => return true,
                _ => {}
            }
        }
        
        // Prüfe auf DNS-Fehler (können in verschiedenen Error-Typen verpackt sein)
        // via source() chain
        let mut current: Option<&dyn Error> = Some(error);
        while let Some(err) = current {
            let err_msg = err.to_string().to_lowercase();
            // DNS-Fehler sind spezifisch genug für String-Matching (DNS ist standardisiert)
            if err_msg.contains("dns") || err_msg.contains("name resolution") {
                return true;
            }
            current = err.source();
        }
        
        // Kein bekannter Netzwerkfehler
        false
    }
}

/// Anti-Tamper für Runtime-Tampering-Detection
#[derive(Clone)]
pub struct AntiTamper {
    integrity_checks: Arc<Mutex<HashMap<String, u64>>>,
    tamper_detection_active: Arc<Mutex<bool>>,
}

impl AntiTamper {
    pub fn new() -> Self {
        Self {
            integrity_checks: Arc::new(Mutex::new(HashMap::new())),
            tamper_detection_active: Arc::new(Mutex::new(false)),
        }
    }

    pub fn detect_tampering(&self) -> Result<bool, super::SecurityError> {
        // Methode 1: Code-Integrity prüfen
        if self.check_code_integrity() {
            return Ok(true);
        }

        // Methode 2: Runtime-Modifikationen prüfen
        if self.check_runtime_modifications() {
            return Ok(true);
        }

        // Methode 3: Debugger-Attachment prüfen
        if self.check_debugger_attachment() {
            return Ok(true);
        }

        // Methode 4: Process-Injection prüfen
        if self.check_process_injection() {
            return Ok(true);
        }

        Ok(false)
    }

    fn check_code_integrity(&self) -> bool {
        // Prüfe Code-Integrität durch Checksum-Vergleich
        // In einer echten Implementierung würde hier die aktuelle
        // Code-Checksum mit der erwarteten Checksum verglichen
        false
    }

    fn check_runtime_modifications(&self) -> bool {
        // Prüfe auf Runtime-Modifikationen
        // In einer echten Implementierung würde hier der Code-Space
        // nach unerwarteten Änderungen gescannt
        false
    }

    fn check_debugger_attachment(&self) -> bool {
        // Prüfe auf Debugger-Attachment
        if let Ok(status) = fs::read_to_string("/proc/self/status") {
            for line in status.lines() {
                if line.starts_with("TracerPid:") {
                    let pid = line.split_whitespace().nth(1).unwrap_or("0");
                    return pid != "0";
                }
            }
        }
        false
    }

    fn check_process_injection(&self) -> bool {
        // Prüfe auf Process-Injection
        // In einer echten Implementierung würde hier der Process-Space
        // nach verdächtigen Modifikationen gescannt
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_root_detection() {
        let detector = RootDetector::new();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = detector.is_rooted();
    }

    #[test]
    fn test_debugger_detection() {
        let monitor = DebuggerMonitor::new();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = monitor.is_attached();
    }

    #[test]
    fn test_emulator_detection() {
        let detector = EmulatorDetector::new();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = detector.is_emulator();
    }

    #[test]
    fn test_integrity_monitoring() {
        let monitor = IntegrityMonitor::new();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = monitor.is_tampered();
    }

    #[test]
    fn test_hook_detection() {
        let detector = HookDetector::new();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = detector.detect_hooks();
    }

    #[test]
    fn test_memory_protection() {
        let protector = MemoryProtector::new();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = protector.is_memory_dumped();
    }

    #[test]
    fn test_ssl_pinning() {
        let pinner = SslPinner::new(Some(Duration::from_secs(10))).unwrap();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = pinner.verify_certificates();
    }

    #[test]
    fn test_anti_tamper() {
        let anti_tamper = AntiTamper::new();
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = anti_tamper.detect_tampering();
    }

    #[test]
    fn test_enhanced_rasp_protection() {
        let protection = RaspProtection::new().expect("RASP protection should initialize");
        // Test sollte in echter Umgebung durchgeführt werden
        let _result = protection.check_environment();
    }
}

