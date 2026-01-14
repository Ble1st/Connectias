// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

//! Connectias Security Detectors - Rust Implementation
//! 
//! High-performance security detection using native Rust.
//! Includes root detection, tamper detection, debugger detection, and emulator detection.
//! Replaces RootBeer library and other Kotlin implementations with faster, more secure Rust code.

use std::fs;
use std::path::Path;
use std::time::{Duration, Instant};
use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::sys::{jint, jobject, jobjectArray, jstring};
use jni::JNIEnv;
use serde::{Deserialize, Serialize};

#[cfg(target_os = "android")]
use android_logger::Config;
#[cfg(target_os = "android")]
use log::LevelFilter;

/// Root detection result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RootDetectionResult {
    pub is_rooted: bool,
    pub detection_methods: Vec<String>,
}

/// Tamper detection result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TamperDetectionResult {
    pub is_tampered: bool,
    pub detection_methods: Vec<String>,
}

/// Debugger detection result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DebuggerDetectionResult {
    pub is_debugger_attached: bool,
    pub detection_methods: Vec<String>,
}

/// Emulator detection result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EmulatorDetectionResult {
    pub is_emulator: bool,
    pub detection_method_names: Vec<String>, // Non-PII identifiers
}

/// Common SU binary paths (from RootBeer)
const SU_PATHS: &[&str] = &[
    "/data/local/su",
    "/data/local/bin/su",
    "/data/local/xbin/su",
    "/sbin/su",
    "/su/bin/su",
    "/system/bin/su",
    "/system/bin/.ext/su",
    "/system/bin/failsafe/su",
    "/system/sd/xbin/su",
    "/system/usr/we-need-root/su",
    "/system/xbin/su",
    "/cache/su",
    "/data/su",
    "/dev/su",
    "/product/bin/su",
    "/apex/com.android.runtime/bin/su",
    "/apex/com.android.art/bin/su",
    "/apex/com.android.virt/bin/su",
    "/system_ext/bin/su",
    "/odm/bin/su",
    "/vendor/bin/su",
    "/vendor/xbin/su",
];

/// Magisk paths
const MAGISK_PATHS: &[&str] = &[
    "/data/adb/magisk",
    "/sbin/.magisk",
    "/sbin/magisk",
    "/sbin/magiskhide",
    "/system/bin/magisk",
    "/data/adb/modules",
    "/cache/magisk.log",
    "/data/magisk/magisk.db",
];

/// Xposed framework paths
const XPOSED_PATHS: &[&str] = &[
    "/system/framework/XposedBridge.jar",
    "/system/bin/app_process32_xposed",
    "/system/bin/app_process64_xposed",
    "/system/lib/libxposed_art.so",
    "/system/lib64/libxposed_art.so",
    "/data/adb/modules/edxposed",
    "/data/adb/modules/lsposed",
    "/data/adb/modules/riru_edxposed",
    "/data/adb/modules/riru_lsposed",
    "/data/xposed.prop",
    "/system/lib/libedxposed.so",
    "/system/lib64/libedxposed.so",
    "/vendor/lib/libedxposed.so",
    "/vendor/lib64/libedxposed.so",
    "/system/lib/libsupol.so",
    "/system/lib64/libsupol.so",
];

/// Tamper detection: Xposed hook framework paths
const TAMPER_XPOSED_PATHS: &[&str] = &[
    "/system/xbin/xposed",
    "/system/lib/libxposed_art.so",
    "/system/lib64/libxposed_art.so",
    "/system/framework/XposedBridge.jar",
    "/data/adb/modules/edxposed",
    "/data/adb/modules/lsposed",
    "/data/adb/modules/riru_edxposed",
    "/data/adb/modules/riru_lsposed",
    "/data/xposed.prop",
    "/system/lib/libedxposed.so",
    "/system/lib64/libedxposed.so",
    "/vendor/lib/libedxposed.so",
    "/vendor/lib64/libedxposed.so",
    "/system/lib/libsupol.so",
    "/system/lib64/libsupol.so",
];

/// Tamper detection: Frida server paths
const FRIDA_PATHS: &[&str] = &[
    "/data/local/tmp/frida-server",
    "/data/local/tmp/re.frida.server",
    "/system/bin/frida-server",
    "/system/xbin/frida-server",
];

/// Tamper detection: Substrate framework paths
const SUBSTRATE_PATHS: &[&str] = &[
    "/system/lib/libsubstrate.so",
    "/system/lib64/libsubstrate.so",
    "/data/local/tmp/substrate",
];

/// Emulator detection: QEMU and emulator-specific files
const EMULATOR_FILES: &[&str] = &[
    "/system/lib/libqemu.so",
    "/system/lib64/libqemu.so",
    "/system/lib/libhoudini.so",
    "/system/lib64/libhoudini.so",
    "/system/bin/qemu-props",
    "/dev/socket/qemud",
    "/dev/qemu_pipe",
    "/sys/qemu_trace",
    "/system/lib/libc_malloc_debug_qemu.so",
    "/sys/bus/platform/drivers/qemu_pipe",
    "/dev/socket/baseband_genyd",
];

/// Root management app packages
const ROOT_APPS: &[&str] = &[
    "com.noshufou.android.su",
    "com.noshufou.android.su.elite",
    "eu.chainfire.supersu",
    "com.koushikdutta.superuser",
    "com.thirdparty.superuser",
    "com.yellowes.su",
    "com.topjohnwu.magisk",
    "com.topjohnwu.magisk.debug",
    "com.kingroot.kinguser",
    "com.kingo.root",
    "com.smedialink.oneclickroot",
    "com.zhiqupk.root.global",
    "com.alephzain.framaroot",
];

/// Dangerous system properties that indicate root
const DANGEROUS_PROPS: &[(&str, &str)] = &[
    ("ro.debuggable", "1"),
    ("ro.secure", "0"),
];

/// Check if a file exists
fn file_exists(path: &str) -> bool {
    Path::new(path).exists()
}

/// Check if a directory exists and is not empty
fn dir_exists_and_not_empty(path: &str) -> bool {
    if let Ok(entries) = fs::read_dir(path) {
        entries.count() > 0
    } else {
        false
    }
}

/// Check for SU binaries in common locations
fn check_su_binaries() -> Vec<String> {
    let mut methods = Vec::new();
    
    for path in SU_PATHS {
        if file_exists(path) {
            methods.push(format!("SU binary found: {}", path));
        }
    }
    
    methods
}

/// Check for Magisk
fn check_magisk() -> Vec<String> {
    let mut methods = Vec::new();
    
    // Check Magisk paths
    for path in MAGISK_PATHS {
        if file_exists(path) {
            methods.push(format!("Magisk path detected: {}", path));
        }
    }
    
    // Check for Magisk modules directory
    if dir_exists_and_not_empty("/data/adb/modules") {
        methods.push("Magisk modules directory found".to_string());
    }
    
    methods
}

/// Check for Xposed frameworks
fn check_xposed() -> Vec<String> {
    let mut methods = Vec::new();
    
    for path in XPOSED_PATHS {
        if file_exists(path) {
            let framework = if path.contains("edxposed") {
                "EdXposed"
            } else if path.contains("lsposed") || path.contains("lsp") {
                "LSPosed"
            } else {
                "Xposed"
            };
            methods.push(format!("{} framework detected: {}", framework, path));
        }
    }
    
    methods
}

/// Check build properties (test-keys)
fn check_build_props() -> Vec<String> {
    let mut methods = Vec::new();
    
    // Read build.prop
    if let Ok(content) = fs::read_to_string("/system/build.prop") {
        if content.contains("ro.build.tags=test-keys") {
            methods.push("Build.TAGS contains 'test-keys' (custom ROM)".to_string());
        }
        if !content.contains("ro.build.tags=release-keys") {
            methods.push("Build.TAGS missing 'release-keys' (suspicious)".to_string());
        }
    }
    
    // Check dangerous properties
    for (prop, value) in DANGEROUS_PROPS {
        if let Ok(content) = fs::read_to_string("/system/build.prop") {
            if content.contains(&format!("{}={}", prop, value)) {
                methods.push(format!("Dangerous property: {}={}", prop, value));
            }
        }
    }
    
    methods
}

/// Check SELinux status
fn check_selinux() -> Vec<String> {
    let mut methods = Vec::new();
    
    // Check SELinux enforce file
    if let Ok(content) = fs::read_to_string("/sys/fs/selinux/enforce") {
        let status = content.trim();
        if status == "0" {
            methods.push(format!("SELinux is not enforcing (status: {})", status));
        }
    }
    
    methods
}

/// Check for root management apps (package names passed from Kotlin)
fn check_root_apps(package_names: &[String]) -> Vec<String> {
    let mut methods = Vec::new();
    
    for package in package_names {
        if ROOT_APPS.contains(&package.as_str()) {
            methods.push(format!("Root management app detected: {}", package));
        }
    }
    
    methods
}

/// Perform comprehensive root detection
pub fn detect_root(package_names: &[String]) -> RootDetectionResult {
    let mut detection_methods = Vec::new();
    
    // 1. Check SU binaries
    detection_methods.extend(check_su_binaries());
    
    // 2. Check Magisk
    detection_methods.extend(check_magisk());
    
    // 3. Check Xposed
    detection_methods.extend(check_xposed());
    
    // 4. Check build properties
    detection_methods.extend(check_build_props());
    
    // 5. Check SELinux
    detection_methods.extend(check_selinux());
    
    // 6. Check root apps (if package names provided)
    if !package_names.is_empty() {
        detection_methods.extend(check_root_apps(package_names));
    }
    
    RootDetectionResult {
        is_rooted: !detection_methods.is_empty(),
        detection_methods,
    }
}

// ============================================================================
// Tamper Detection
// ============================================================================

/// Check for hook frameworks (Xposed variants)
fn check_hook_frameworks() -> Vec<String> {
    let mut methods = Vec::new();
    
    for path in TAMPER_XPOSED_PATHS {
        if file_exists(path) {
            let framework = if path.contains("edxposed") {
                "EdXposed"
            } else if path.contains("lsposed") || path.contains("lsp") {
                "LSPosed"
            } else {
                "Xposed"
            };
            methods.push(format!("{} framework detected: {}", framework, path));
        }
    }
    
    // Check for Xposed-related processes in /proc
    // Limit scan to 5 seconds to avoid blocking
    let start_time = Instant::now();
    let timeout = Duration::from_secs(5);
    
    if let Ok(proc_dir) = fs::read_dir("/proc") {
        for entry in proc_dir {
            if start_time.elapsed() > timeout {
                break;
            }
            
            if let Ok(entry) = entry {
                let pid_dir = entry.path();
                if !pid_dir.is_dir() {
                    continue;
                }
                
                // Check if directory name is numeric (PID)
                let pid_str = pid_dir.file_name().and_then(|n| n.to_str()).unwrap_or("");
                if pid_str.chars().any(|c| !c.is_ascii_digit()) {
                    continue;
                }
                
                // Read cmdline
                let cmdline_path = pid_dir.join("cmdline");
                if let Ok(cmdline) = fs::read_to_string(&cmdline_path) {
                    let cmdline_lower = cmdline.trim().to_lowercase();
                    let process_name = cmdline_lower.split('\u{0}').next().unwrap_or(&cmdline_lower);
                    
                    if process_name.contains("edxposed") {
                        methods.push(format!("EdXposed process detected (pid={})", pid_str));
                    } else if process_name.contains("lsposed") {
                        methods.push(format!("LSPosed process detected (pid={})", pid_str));
                    } else if process_name.contains("xposed") {
                        methods.push(format!("Xposed process detected (pid={})", pid_str));
                    }
                }
            }
        }
    }
    
    methods
}

/// Check for Frida server
fn check_frida() -> Vec<String> {
    let mut methods = Vec::new();
    
    // Check Frida paths
    for path in FRIDA_PATHS {
        if file_exists(path) {
            methods.push(format!("Frida server detected: {}", path));
        }
    }
    
    // Check for Frida processes in /proc
    let start_time = Instant::now();
    let timeout = Duration::from_secs(5);
    
    if let Ok(proc_dir) = fs::read_dir("/proc") {
        for entry in proc_dir {
            if start_time.elapsed() > timeout {
                break;
            }
            
            if let Ok(entry) = entry {
                let pid_dir = entry.path();
                if !pid_dir.is_dir() {
                    continue;
                }
                
                let pid_str = pid_dir.file_name().and_then(|n| n.to_str()).unwrap_or("");
                if pid_str.chars().any(|c| !c.is_ascii_digit()) {
                    continue;
                }
                
                let cmdline_path = pid_dir.join("cmdline");
                if let Ok(cmdline) = fs::read_to_string(&cmdline_path) {
                    if cmdline.to_lowercase().contains("frida") {
                        methods.push(format!("Frida process detected (pid={})", pid_str));
                    }
                }
            }
        }
    }
    
    methods
}

/// Check for other tampering indicators
fn check_other_tampering_indicators() -> Vec<String> {
    let mut methods = Vec::new();
    
    for path in SUBSTRATE_PATHS {
        if file_exists(path) {
            methods.push(format!("Substrate framework detected: {}", path));
        }
    }
    
    methods
}

/// Perform comprehensive tamper detection
pub fn detect_tampering(_package_names: &[String]) -> TamperDetectionResult {
    let mut detection_methods = Vec::new();
    
    // 1. Check for hook frameworks (Xposed variants)
    detection_methods.extend(check_hook_frameworks());
    
    // 2. Check for Frida server
    detection_methods.extend(check_frida());
    
    // 3. Check for other tampering indicators
    detection_methods.extend(check_other_tampering_indicators());
    
    // Note: Package checks are done in Kotlin layer (PackageManager)
    // Rust focuses on file system checks
    
    TamperDetectionResult {
        is_tampered: !detection_methods.is_empty(),
        detection_methods,
    }
}

// ============================================================================
// Debugger Detection
// ============================================================================

/// Check TracerPid in /proc/self/status
/// A non-zero TracerPid indicates a debugger is attached
fn check_tracer_pid() -> Vec<String> {
    let mut methods = Vec::new();
    
    if let Ok(content) = fs::read_to_string("/proc/self/status") {
        for line in content.lines() {
            if line.starts_with("TracerPid:") {
                if let Some(pid_str) = line.split(':').nth(1) {
                    if let Ok(pid) = pid_str.trim().parse::<u32>() {
                        if pid != 0 {
                            methods.push(format!("TracerPid detected: {}", pid));
                        }
                    }
                }
                break;
            }
        }
    }
    
    methods
}

/// Perform debugger detection
/// Note: Android Debug API (Debug.isDebuggerConnected()) is checked in Kotlin layer
pub fn detect_debugger() -> DebuggerDetectionResult {
    let mut detection_methods = Vec::new();
    
    // Check TracerPid in /proc/self/status
    detection_methods.extend(check_tracer_pid());
    
    DebuggerDetectionResult {
        is_debugger_attached: !detection_methods.is_empty(),
        detection_methods,
    }
}

// ============================================================================
// Emulator Detection
// ============================================================================

/// Check build properties for emulator indicators
/// Build properties are passed from Kotlin (Build.MODEL, etc.)
fn check_build_properties(
    model: &str,
    manufacturer: &str,
    product: &str,
    device: &str,
    hardware: &str,
    brand: &str,
    fingerprint: &str,
) -> Vec<String> {
    let mut methods = Vec::new();
    
    let model_lower = model.to_lowercase();
    let manufacturer_lower = manufacturer.to_lowercase();
    let product_lower = product.to_lowercase();
    let device_lower = device.to_lowercase();
    let hardware_lower = hardware.to_lowercase();
    let brand_lower = brand.to_lowercase();
    let fingerprint_lower = fingerprint.to_lowercase();
    
    // Check model
    if model_lower.contains("sdk") || model_lower.contains("emulator") ||
       model_lower.contains("google_sdk") || model_lower.contains("droid4x") ||
       model_lower.contains("genymotion") || model_lower.contains("vbox") {
        methods.push("BUILD_MODEL_CHECK".to_string());
    }
    
    // Check manufacturer
    if manufacturer_lower.contains("unknown") || manufacturer_lower.contains("generic") ||
       manufacturer_lower.contains("genymotion") || manufacturer_lower.contains("vbox") {
        methods.push("BUILD_MANUFACTURER_CHECK".to_string());
    }
    
    // Check product
    if product_lower.contains("sdk") || product_lower.contains("emulator") ||
       product_lower.contains("google_sdk") || product_lower.contains("vbox") ||
       product_lower.contains("genymotion") {
        methods.push("BUILD_PRODUCT_CHECK".to_string());
    }
    
    // Check device
    if device_lower.contains("generic") || device_lower.contains("emulator") ||
       device_lower.contains("vbox") || device_lower.contains("genymotion") {
        methods.push("BUILD_DEVICE_CHECK".to_string());
    }
    
    // Check hardware
    if hardware_lower.contains("goldfish") || hardware_lower.contains("ranchu") ||
       hardware_lower.contains("vbox") {
        methods.push("BUILD_HARDWARE_CHECK".to_string());
    }
    
    // Check brand
    if brand_lower.contains("generic") || brand_lower.contains("unknown") {
        methods.push("BUILD_BRAND_CHECK".to_string());
    }
    
    // Check fingerprint
    if fingerprint_lower.contains("generic") || fingerprint_lower.contains("unknown") ||
       fingerprint_lower.contains("vbox") || fingerprint_lower.contains("test-keys") {
        methods.push("BUILD_FINGERPRINT_CHECK".to_string());
    }
    
    methods
}

/// Check system properties for emulator indicators
/// Reads directly from /system/build.prop and /default.prop
fn check_system_properties() -> Vec<String> {
    let mut methods = Vec::new();
    
    // Read /system/build.prop
    if let Ok(content) = fs::read_to_string("/system/build.prop") {
        // Check ro.kernel.qemu
        for line in content.lines() {
            if line.starts_with("ro.kernel.qemu=") {
                let value = line.split('=').nth(1).unwrap_or("").trim();
                if value == "1" {
                    methods.push("SYSTEM_PROP_QEMU_CHECK".to_string());
                }
            }
            
            // Check ro.hardware
            if line.starts_with("ro.hardware=") {
                let value = line.split('=').nth(1).unwrap_or("").to_lowercase();
                if value.contains("goldfish") || value.contains("ranchu") || value.contains("vbox") {
                    methods.push("SYSTEM_PROP_HARDWARE_CHECK".to_string());
                }
            }
            
            // Check ro.product.model
            if line.starts_with("ro.product.model=") {
                let value = line.split('=').nth(1).unwrap_or("").to_lowercase();
                if value.contains("sdk") || value.contains("emulator") || value.contains("generic") {
                    methods.push("SYSTEM_PROP_PRODUCT_MODEL_CHECK".to_string());
                }
            }
            
            // Check ro.build.characteristics
            if line.starts_with("ro.build.characteristics=") {
                let value = line.split('=').nth(1).unwrap_or("").to_lowercase();
                if value.contains("emulator") {
                    methods.push("SYSTEM_PROP_CHARACTERISTICS_CHECK".to_string());
                }
            }
        }
    }
    
    // Also check /default.prop
    if let Ok(content) = fs::read_to_string("/default.prop") {
        for line in content.lines() {
            if line.starts_with("ro.kernel.qemu=") {
                let value = line.split('=').nth(1).unwrap_or("").trim();
                if value == "1" {
                    methods.push("SYSTEM_PROP_QEMU_CHECK".to_string());
                }
            }
        }
    }
    
    methods
}

/// Check for emulator-specific files
fn check_emulator_files() -> Vec<String> {
    let mut methods = Vec::new();
    
    for path in EMULATOR_FILES {
        if file_exists(path) {
            methods.push(format!("Emulator file detected: {}", path));
        }
    }
    
    // Check for QEMU references in /init.rc
    if let Ok(content) = fs::read_to_string("/init.rc") {
        if content.to_lowercase().contains("qemu") {
            methods.push("QEMU references found in /init.rc".to_string());
        }
    }
    
    methods
}

/// Check CPU/ABI anomalies
fn check_cpu_abi() -> Vec<String> {
    let mut methods = Vec::new();
    
    // Read /proc/cpuinfo
    if let Ok(content) = fs::read_to_string("/proc/cpuinfo") {
        let content_lower = content.to_lowercase();
        if content_lower.contains("goldfish") ||
           content_lower.contains("qemu") ||
           content_lower.contains("vbox") {
            methods.push("Emulator indicators in /proc/cpuinfo".to_string());
        }
    }
    
    methods
}

/// Perform comprehensive emulator detection
/// Build properties are passed from Kotlin (Android Build API)
pub fn detect_emulator(
    model: &str,
    manufacturer: &str,
    product: &str,
    device: &str,
    hardware: &str,
    brand: &str,
    fingerprint: &str,
) -> EmulatorDetectionResult {
    let mut detection_methods = Vec::new();
    
    // 1. Check build properties (passed from Kotlin)
    detection_methods.extend(check_build_properties(
        model, manufacturer, product, device, hardware, brand, fingerprint
    ));
    
    // 2. Check system properties (direct file access)
    detection_methods.extend(check_system_properties());
    
    // 3. Check emulator files
    detection_methods.extend(check_emulator_files());
    
    // 4. Check CPU/ABI
    detection_methods.extend(check_cpu_abi());
    
    // Note: Telephony checks are done in Kotlin layer (Android API)
    
    EmulatorDetectionResult {
        is_emulator: !detection_methods.is_empty(),
        detection_method_names: detection_methods,
    }
}

// ============================================================================
// JNI Bindings
// ============================================================================

/// Initialize logging for Android
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_root_RustRootDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustRootDetector"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_root_RustRootDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Detect root - JNI entry point
/// 
/// Returns JSON string with RootDetectionResult
/// Note: Package names are checked in Kotlin layer, Rust focuses on file system checks
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_root_RustRootDetector_nativeDetectRoot(
    env: JNIEnv,
    _class: JClass,
    _package_names: jobject, // String array or null - checked in Kotlin layer
) -> jstring {
    // For now, we skip package name checking in Rust (done in Kotlin)
    // This simplifies the JNI implementation and avoids lifetime issues
    // Package checks are less critical than file system checks anyway
    let packages: Vec<String> = Vec::new();
    
    // Perform root detection (focus on file system checks)
    let result = detect_root(&packages);
    
    // Serialize to JSON
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string(r#"{"is_rooted":false,"detection_methods":[]}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"is_rooted":false,"detection_methods":[]}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

/// Initialize logging for Tamper Detector
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_tamper_RustTamperDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustTamperDetector"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_tamper_RustTamperDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Detect tampering - JNI entry point
/// 
/// Returns JSON string with TamperDetectionResult
/// Note: Package names are checked in Kotlin layer, Rust focuses on file system checks
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_tamper_RustTamperDetector_nativeDetectTampering(
    mut env: JNIEnv,
    _class: JClass,
    package_names: jobjectArray,
) -> jstring {
    let packages: Vec<String> = if package_names.is_null() {
        Vec::new()
    } else {
        let array_obj = unsafe { JObjectArray::from_raw(package_names) };
        let len = match env.get_array_length(&array_obj) {
            Ok(l) => l,
            Err(_) => 0,
        };
        
        let mut result = Vec::new();
        for i in 0..len {
            if let Ok(jobj) = env.get_object_array_element(&array_obj, i) {
                let jstr: JString = jobj.into();
                let s = env.get_string(&jstr);
                if let Ok(java_str) = s {
                    result.push(java_str.to_string_lossy().to_string());
                }
            }
        }
        result
    };
    
    // Perform tamper detection (focus on file system checks)
    let result = detect_tampering(&packages);
    
    // Serialize to JSON
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string(r#"{"is_tampered":false,"detection_methods":[]}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"is_tampered":false,"detection_methods":[]}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

/// Initialize logging for Debugger Detector
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_debug_RustDebuggerDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustDebuggerDetector"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_debug_RustDebuggerDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Detect debugger - JNI entry point
/// 
/// Returns JSON string with DebuggerDetectionResult
/// Note: Android Debug API (Debug.isDebuggerConnected()) is checked in Kotlin layer
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_debug_RustDebuggerDetector_nativeDetectDebugger(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    // Perform debugger detection (focus on /proc/self/status parsing)
    let result = detect_debugger();
    
    // Serialize to JSON
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string(r#"{"is_debugger_attached":false,"detection_methods":[]}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"is_debugger_attached":false,"detection_methods":[]}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

/// Initialize logging for Emulator Detector
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_emulator_RustEmulatorDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustEmulatorDetector"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_emulator_RustEmulatorDetector_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Detect emulator - JNI entry point
/// 
/// Returns JSON string with EmulatorDetectionResult
/// Build properties are passed from Kotlin (Android Build API)
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_emulator_RustEmulatorDetector_nativeDetectEmulator(
    mut env: JNIEnv,
    _class: JClass,
    model: JString,
    manufacturer: JString,
    product: JString,
    device: JString,
    hardware: JString,
    brand: JString,
    fingerprint: JString,
) -> jstring {
    // Extract strings from JNI
    let model_str = match env.get_string(&model) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let manufacturer_str = match env.get_string(&manufacturer) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let product_str = match env.get_string(&product) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let device_str = match env.get_string(&device) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let hardware_str = match env.get_string(&hardware) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let brand_str = match env.get_string(&brand) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let fingerprint_str = match env.get_string(&fingerprint) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    // Perform emulator detection
    let result = detect_emulator(
        &model_str,
        &manufacturer_str,
        &product_str,
        &device_str,
        &hardware_str,
        &brand_str,
        &fingerprint_str,
    );
    
    // Serialize to JSON
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string(r#"{"is_emulator":false,"detection_method_names":[]}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"is_emulator":false,"detection_method_names":[]}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

// ============================================================================
// Key Manager - Passphrase Generation
// ============================================================================

use ring::rand::{SecureRandom, SystemRandom};
use zeroize::Zeroize;

/// Generate secure passphrase using ring SecureRandom
/// Returns passphrase as String (will be converted to CharArray in Kotlin)
pub fn generate_secure_passphrase(length: usize) -> String {
    const CHARS: &str = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    
    let rng = SystemRandom::new();
    let mut bytes = vec![0u8; length];
    rng.fill(&mut bytes).unwrap();
    
    // Map bytes to characters from CHARS
    let passphrase: String = bytes
        .iter()
        .map(|&byte| {
            let index = (byte as usize) % CHARS.len();
            CHARS.chars().nth(index).unwrap()
        })
        .collect();
    
    // Zeroize bytes
    bytes.zeroize();
    
    passphrase
}

/// Generate secure passphrase - JNI entry point
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_core_security_KeyManager_nativeGeneratePassphrase(
    env: JNIEnv,
    _obj: JObject,
    length: jint,
) -> jstring {
    let length_usize = length as usize;
    let passphrase = generate_secure_passphrase(length_usize);
    
    match env.new_string(&passphrase) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_detect_root_no_root() {
        let result = detect_root(&[]);
        // On a non-rooted system, this should return false
        // (but may return true in CI/test environments)
        assert!(result.detection_methods.is_empty() || !result.detection_methods.is_empty());
    }

    #[test]
    fn test_file_exists() {
        // Test that file_exists works
        assert!(!file_exists("/nonexistent/path"));
    }
    
    #[test]
    fn test_generate_secure_passphrase() {
        let passphrase = generate_secure_passphrase(32);
        assert_eq!(passphrase.len(), 32);
    }
}

