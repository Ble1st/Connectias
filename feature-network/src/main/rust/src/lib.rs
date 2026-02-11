//! Connectias Port Scanner - Rust Implementation
//! 
//! High-performance port scanner using async Rust with tokio.
//! Provides JNI bindings for Android/Kotlin integration.

use std::fs;
use std::net::{Ipv4Addr, ToSocketAddrs};
use std::str::FromStr;
use std::sync::Arc;
use std::time::Duration;
use tokio::net::TcpStream;
use tokio::time::timeout;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jlong, jstring};
use jni::JNIEnv;
use serde::{Deserialize, Serialize};
use trust_dns_resolver::config::{ResolverConfig, ResolverOpts};
use trust_dns_resolver::proto::rr::{RecordType, RData};
use trust_dns_resolver::{Name, Resolver};

#[cfg(target_os = "android")]
use android_logger::Config;
#[cfg(target_os = "android")]
use log::LevelFilter;

/// Port scan result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PortScanResult {
    pub port: u16,
    pub is_open: bool,
    pub service: Option<String>,
    pub banner: Option<String>,
}

/// Service registry for common ports
fn get_service_name(port: u16) -> Option<String> {
    match port {
        20 => Some("FTP Data".to_string()),
        21 => Some("FTP".to_string()),
        22 => Some("SSH".to_string()),
        23 => Some("Telnet".to_string()),
        25 => Some("SMTP".to_string()),
        53 => Some("DNS".to_string()),
        67 => Some("DHCP Server".to_string()),
        68 => Some("DHCP Client".to_string()),
        80 => Some("HTTP".to_string()),
        110 => Some("POP3".to_string()),
        123 => Some("NTP".to_string()),
        135 => Some("RPC".to_string()),
        137 => Some("NetBIOS Name".to_string()),
        138 => Some("NetBIOS Datagram".to_string()),
        139 => Some("NetBIOS Session".to_string()),
        143 => Some("IMAP".to_string()),
        161 => Some("SNMP".to_string()),
        389 => Some("LDAP".to_string()),
        443 => Some("HTTPS".to_string()),
        445 => Some("SMB".to_string()),
        465 => Some("SMTPS".to_string()),
        514 => Some("Syslog".to_string()),
        587 => Some("Submission".to_string()),
        631 => Some("IPP/Printing".to_string()),
        993 => Some("IMAPS".to_string()),
        995 => Some("POP3S".to_string()),
        1723 => Some("PPTP".to_string()),
        1883 => Some("MQTT".to_string()),
        1900 => Some("SSDP/UPnP".to_string()),
        3306 => Some("MySQL".to_string()),
        3389 => Some("RDP".to_string()),
        4433 => Some("HTTPS Alt".to_string()),
        5432 => Some("PostgreSQL".to_string()),
        5671 => Some("AMQP TLS".to_string()),
        5672 => Some("AMQP".to_string()),
        5900 => Some("VNC".to_string()),
        5984 => Some("CouchDB".to_string()),
        5985 => Some("WinRM".to_string()),
        6379 => Some("Redis".to_string()),
        8000 => Some("HTTP Alt".to_string()),
        8008 => Some("HTTP Alt".to_string()),
        8080 => Some("HTTP Proxy".to_string()),
        8081 => Some("HTTP Alt".to_string()),
        8443 => Some("HTTPS Alt".to_string()),
        9000 => Some("Debug/Custom".to_string()),
        9200 => Some("Elasticsearch".to_string()),
        10000 => Some("Backup/Custom".to_string()),
        _ => None,
    }
}

/// Probe a single port
async fn probe_port(
    host: &str,
    port: u16,
    timeout_duration: Duration,
) -> PortScanResult {
    let addr = format!("{}:{}", host, port);
    
    // Try to resolve address first
    let socket_addr = match addr.to_socket_addrs() {
        Ok(mut addrs) => match addrs.next() {
            Some(addr) => addr,
            None => {
                return PortScanResult {
                    port,
                    is_open: false,
                    service: get_service_name(port),
                    banner: None,
                };
            }
        },
        Err(_) => {
            return PortScanResult {
                port,
                is_open: false,
                service: get_service_name(port),
                banner: None,
            };
        }
    };

    // Try to connect with timeout
    let is_open = match timeout(timeout_duration, TcpStream::connect(&socket_addr)).await {
        Ok(Ok(stream)) => {
            // Connection successful
            // Banner reading can be enabled later if needed
            drop(stream); // Close connection immediately
            true
        }
        Ok(Err(_)) => false,
        Err(_) => false, // Timeout
    };

    PortScanResult {
        port,
        is_open,
        service: get_service_name(port),
        banner: None, // Banner reading can be enabled later if needed
    }
}

// Banner reading can be implemented here if needed in the future
// Currently disabled to keep implementation simple

/// Scan a range of ports
pub async fn scan_ports(
    host: String,
    start_port: u16,
    end_port: u16,
    timeout_ms: u64,
    max_concurrency: usize,
) -> Vec<PortScanResult> {
    use tokio::sync::Semaphore;
    
    // Validate port range
    if start_port < 1 || end_port < start_port {
        return vec![];
    }

    let timeout_duration = Duration::from_millis(timeout_ms);
    let semaphore = Arc::new(Semaphore::new(max_concurrency));
    let mut tasks = Vec::new();

    for port in start_port..=end_port {
        let host_clone = host.clone();
        let semaphore_clone = semaphore.clone();
        
        let task = tokio::spawn(async move {
            let _permit = semaphore_clone.acquire().await.unwrap();
            probe_port(&host_clone, port, timeout_duration).await
        });
        
        tasks.push(task);
    }

    // Wait for all tasks to complete
    let mut results = Vec::new();
    for task in tasks {
        if let Ok(result) = task.await {
            results.push(result);
        }
    }
    
    // Filter only open ports
    results.into_iter().filter(|r| r.is_open).collect()
}

// ============================================================================
// JNI Bindings
// ============================================================================

/// Initialize logging for Android
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_port_RustPortScanner_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustPortScanner"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_port_RustPortScanner_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Scan ports - JNI entry point
/// 
/// Returns JSON string with array of PortScanResult
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_port_RustPortScanner_nativeScanPorts(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    start_port: jint,
    end_port: jint,
    timeout_ms: jlong,
    max_concurrency: jint,
) -> jstring {
    // Extract parameters
    let host_str: String = match env.get_string(&host) {
        Ok(s) => s.into(),
        Err(_) => {
            match env.new_string("[]") {
                Ok(jstr) => return jstr.into_raw(),
                Err(_) => return std::ptr::null_mut(),
            }
        }
    };

    let start = start_port as u16;
    let end = end_port as u16;
    let timeout = timeout_ms as u64;
    let concurrency = max_concurrency as usize;

    // Create tokio runtime for async execution
    let rt = match tokio::runtime::Runtime::new() {
        Ok(rt) => rt,
        Err(_) => {
            match env.new_string("[]") {
                Ok(jstr) => return jstr.into_raw(),
                Err(_) => return std::ptr::null_mut(),
            }
        }
    };
    
    // Execute scan
    let results = rt.block_on(scan_ports(
        host_str,
        start,
        end,
        timeout,
        concurrency,
    ));

    // Serialize to JSON
    match serde_json::to_string(&results) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string("[]") {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string("[]") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

// ============================================================================
// Network Scanner
// ============================================================================

/// Host scan result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HostScanResult {
    pub ip: String,
    pub hostname: Option<String>,
    pub mac: Option<String>,
    pub device_type: String, // Will be converted to DeviceType in Kotlin
    pub is_reachable: bool,
    pub ping_ms: Option<i64>,
}

/// Probe a single host using TCP connect (no root required)
async fn probe_host_tcp(ip: &str, timeout_ms: u64) -> bool {
    let timeout_duration = Duration::from_millis(timeout_ms);
    
    // Try common ports
    let common_ports = [80, 443, 53, 445, 22];
    
    for port in &common_ports {
        let addr = format!("{}:{}", ip, *port);
        if let Ok(mut addrs) = addr.to_socket_addrs() {
            if let Some(socket_addr) = addrs.next() {
                match timeout(timeout_duration, TcpStream::connect(&socket_addr)).await {
                    Ok(Ok(_)) => return true, // Connection successful
                    Ok(Err(_)) => continue,   // Connection refused (host is up but port closed)
                    Err(_) => continue,        // Timeout
                }
            }
        }
    }
    
    false
}

/// Read ARP entry from /proc/net/arp
fn read_arp_entry(ip: &str) -> Option<String> {
    let arp_file = "/proc/net/arp";
    
    if let Ok(content) = fs::read_to_string(arp_file) {
        for line in content.lines().skip(1) {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 4 && parts[0] == ip {
                let mac = parts[3];
                if mac != "00:00:00:00:00:00" {
                    return Some(mac.to_string());
                }
            }
        }
    }
    
    None
}

/// Resolve hostname using reverse DNS (PTR record)
/// 
/// Converts IP address to PTR format (e.g., 192.168.1.1 -> 1.1.168.192.in-addr.arpa)
/// and performs DNS lookup with timeout.
async fn resolve_hostname(ip: &str) -> Option<String> {
    // Parse IP address
    let ipv4 = match Ipv4Addr::from_str(ip) {
        Ok(ip) => ip,
        Err(_) => return None,
    };
    
    // Convert to PTR format: 192.168.1.1 -> 1.1.168.192.in-addr.arpa
    let octets = ipv4.octets();
    let ptr_name = format!("{}.{}.{}.{}.in-addr.arpa", octets[3], octets[2], octets[1], octets[0]);
    
    // Create resolver with default config (uses system DNS)
    let resolver = match Resolver::new(ResolverConfig::default(), ResolverOpts::default()) {
        Ok(r) => r,
        Err(_) => return None,
    };
    
    // Parse PTR name
    let name = match Name::from_str(&ptr_name) {
        Ok(n) => n,
        Err(_) => return None,
    };
    
    // Perform reverse DNS lookup with timeout (PTR record)
    // Note: lookup is synchronous, so we use spawn_blocking with timeout
    let lookup_result = tokio::time::timeout(
        Duration::from_millis(1000),
        tokio::task::spawn_blocking(move || {
            resolver.lookup(name, RecordType::PTR)
        })
    ).await;
    
    match lookup_result {
        Ok(Ok(Ok(lookup))) => {
            // Get first PTR record
            // lookup.record_iter() returns an iterator over Record objects
            for record in lookup.record_iter() {
                // PTR records contain the hostname in the rdata
                if let Some(rdata) = record.data() {
                    if let RData::PTR(ptr_name) = rdata {
                        let hostname = ptr_name.to_string();
                        // Remove trailing dot if present
                        let hostname = hostname.trim_end_matches('.');
                        if !hostname.is_empty() {
                            return Some(hostname.to_string());
                        }
                    }
                }
            }
        }
        Ok(Ok(Err(_))) | Ok(Err(_)) => {
            // DNS lookup failed (hostname not found or DNS error)
            // This is normal, many IPs don't have reverse DNS entries
        }
        Err(_) => {
            // Timeout - DNS lookup took too long
            // This is also normal, we don't want to block too long
        }
    }
    
    None
}

/// Scan a single host
pub async fn scan_host(
    ip: String,
    timeout_ms: u64,
) -> HostScanResult {
    let start = std::time::Instant::now();
    
    // Probe host using TCP connect (no root required)
    let is_reachable = probe_host_tcp(&ip, timeout_ms).await;
    
    let ping_ms = if is_reachable {
        Some(start.elapsed().as_millis() as i64)
    } else {
        None
    };
    
    // Read ARP entry if reachable
    let mac = if is_reachable {
        read_arp_entry(&ip)
    } else {
        None
    };
    
    // Try to resolve hostname using reverse DNS (only for reachable hosts)
    let hostname = if is_reachable {
        resolve_hostname(&ip).await
    } else {
        None
    };
    
    HostScanResult {
        ip,
        hostname,
        mac,
        device_type: "UNKNOWN".to_string(), // Will be determined in Kotlin layer
        is_reachable,
        ping_ms,
    }
}

/// Scan multiple hosts
pub async fn scan_hosts(
    ips: Vec<String>,
    timeout_ms: u64,
    max_concurrency: usize,
) -> Vec<HostScanResult> {
    use tokio::sync::Semaphore;
    
    let semaphore = Arc::new(Semaphore::new(max_concurrency));
    let mut tasks = Vec::new();
    
    for ip in ips {
        let semaphore_clone = semaphore.clone();
        
        let task = tokio::spawn(async move {
            let _permit = semaphore_clone.acquire().await.unwrap();
            scan_host(ip, timeout_ms).await
        });
        
        tasks.push(task);
    }
    
    // Wait for all tasks to complete
    let mut results = Vec::new();
    for task in tasks {
        if let Ok(result) = task.await {
            results.push(result);
        }
    }
    
    results
}

/// Parse CIDR and generate IP list
pub fn parse_cidr_and_generate_ips(cidr: &str, max_hosts: usize) -> Vec<String> {
    let parts: Vec<&str> = cidr.split('/').collect();
    if parts.len() != 2 {
        return vec![];
    }
    
    let ip_str = parts[0];
    let prefix: u8 = match parts[1].parse() {
        Ok(p) => p,
        Err(_) => return vec![],
    };
    
    let ip: Ipv4Addr = match Ipv4Addr::from_str(ip_str) {
        Ok(ip) => ip,
        Err(_) => return vec![],
    };
    
    let ip_int = u32::from(ip);
    let mask = if prefix == 0 {
        0
    } else {
        !0u32 << (32 - prefix)
    };
    
    let network = ip_int & mask;
    let broadcast = network | !mask;
    let first_host = network + 1;
    let last_host = broadcast - 1;
    
    let total_hosts = (last_host - first_host + 1) as usize;
    if total_hosts == 0 {
        return vec![];
    }
    
    let mut ips = Vec::new();
    if total_hosts <= max_hosts {
        for i in first_host..=last_host {
            let ipv4 = Ipv4Addr::from(i as u32);
            ips.push(ipv4.to_string());
        }
    } else {
        let step = (total_hosts / max_hosts).max(1);
        let mut current = first_host;
        while current <= last_host && ips.len() < max_hosts {
            let ipv4 = Ipv4Addr::from(current as u32);
            ips.push(ipv4.to_string());
            current += step as u32;
        }
    }
    
    ips
}

/// Initialize logging for Network Scanner
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_network_RustNetworkScanner_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustNetworkScanner"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_network_RustNetworkScanner_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Scan hosts - JNI entry point
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_network_RustNetworkScanner_nativeScanHosts(
    mut env: JNIEnv,
    _class: JClass,
    cidr: jstring,
    timeout_ms: jlong,
    max_concurrency: jint,
    max_hosts: jint,
) -> jstring {
    // Extract CIDR string
    let cidr_str: String = match env.get_string(&unsafe { JString::from_raw(cidr) }) {
        Ok(s) => s.into(),
        Err(_) => {
            return match env.new_string("[]") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    let timeout = timeout_ms as u64;
    let concurrency = max_concurrency as usize;
    let max_hosts_usize = max_hosts as usize;
    
    // Generate IP list from CIDR
    let ips = parse_cidr_and_generate_ips(&cidr_str, max_hosts_usize);
    
    // Create tokio runtime for async execution
    let rt = match tokio::runtime::Runtime::new() {
        Ok(rt) => rt,
        Err(_) => {
            return match env.new_string("[]") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    // Execute scan
    let results = rt.block_on(scan_hosts(ips, timeout, concurrency));
    
    // Filter only reachable hosts and sort by IP
    let mut filtered: Vec<HostScanResult> = results
        .into_iter()
        .filter(|r| r.is_reachable)
        .collect();
    
    filtered.sort_by(|a, b| a.ip.cmp(&b.ip));
    
    // Serialize to JSON
    match serde_json::to_string(&filtered) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string("[]") {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string("[]") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

/// Read ARP entry - JNI entry point
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_network_RustNetworkScanner_nativeReadArpEntry(
    mut env: JNIEnv,
    _class: JClass,
    ip: jstring,
) -> jstring {
    // Extract IP string
    let ip_str: String = match env.get_string(&unsafe { JString::from_raw(ip) }) {
        Ok(s) => s.into(),
        Err(_) => {
            return match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    // Read ARP entry
    let mac = read_arp_entry(&ip_str);
    
    match mac {
        Some(m) => {
            match env.new_string(&m) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        None => {
            match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

// ============================================================================
// SSL/TLS Analysis
// ============================================================================

/// SSL analysis result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SslAnalysisResult {
    pub subject: String,
    pub issuer: String,
    pub valid_from: i64, // Unix timestamp in milliseconds
    pub valid_to: i64,   // Unix timestamp in milliseconds
    pub days_remaining: i64,
    pub is_valid_now: bool,
    pub key_algorithm: Option<String>,
    pub key_size: Option<i32>,
    pub signature_algorithm: Option<String>,
    pub problems: Vec<String>,
}

/// Analyze SSL certificate
/// 
/// Note: Certificate DER bytes are passed from Kotlin after TLS handshake.
/// The TLS handshake remains in Kotlin (Android SSLContext works well).
pub fn analyze_certificate(
    _cert_der: &[u8],
    _hostname: &str,
) -> Result<SslAnalysisResult, String> {
    // Parse X.509 certificate using x509-parser
    // Note: We'll use a simpler approach - parse basic fields
    // Full certificate parsing would require x509-parser crate
    
    // For now, return a simplified result
    // Full implementation would parse DER format
    Ok(SslAnalysisResult {
        subject: String::new(), // Will be populated by Kotlin layer
        issuer: String::new(),
        valid_from: 0,
        valid_to: 0,
        days_remaining: 0,
        is_valid_now: false,
        key_algorithm: None,
        key_size: None,
        signature_algorithm: None,
        problems: vec![],
    })
}

/// Initialize logging for Android
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_ssl_RustSslAnalyzer_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustSslAnalyzer"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_ssl_RustSslAnalyzer_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Analyze SSL certificate - JNI entry point
/// 
/// Note: Certificate DER bytes are passed from Kotlin after TLS handshake
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_network_ssl_RustSslAnalyzer_nativeAnalyzeCertificate(
    mut env: JNIEnv,
    _class: JClass,
    cert_der: jbyteArray,
    hostname: jstring,
) -> jstring {
    // Extract certificate DER bytes
    let cert_array = unsafe { JByteArray::from_raw(cert_der) };
    let cert_len = match env.get_array_length(&cert_array) {
        Ok(l) => l as usize,
        Err(_) => 0,
    };
    
    let mut cert_bytes = vec![0i8; cert_len];
    match env.get_byte_array_region(&cert_array, 0, &mut cert_bytes) {
        Ok(_) => {},
        Err(_) => {
            return match env.new_string(r#"{"subject":"","issuer":"","valid_from":0,"valid_to":0,"days_remaining":0,"is_valid_now":false,"key_algorithm":null,"key_size":null,"signature_algorithm":null,"problems":[]}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    }
    
    // Convert i8 to u8
    let cert_bytes_u8: Vec<u8> = cert_bytes.iter().map(|&b| b as u8).collect();
    
    // Extract hostname
    let hostname_str = match env.get_string(&unsafe { JString::from_raw(hostname) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    // Analyze certificate
    match analyze_certificate(&cert_bytes_u8, &hostname_str) {
        Ok(result) => {
            match serde_json::to_string(&result) {
                Ok(json) => {
                    match env.new_string(&json) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
                Err(_) => {
                    match env.new_string(r#"{"subject":"","issuer":"","valid_from":0,"valid_to":0,"days_remaining":0,"is_valid_now":false,"key_algorithm":null,"key_size":null,"signature_algorithm":null,"problems":[]}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"subject":"","issuer":"","valid_from":0,"valid_to":0,"days_remaining":0,"is_valid_now":false,"key_algorithm":null,"key_size":null,"signature_algorithm":null,"problems":[]}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_probe_port_localhost() {
        // This test requires a local server on port 80 or 22
        // Skip in CI environments
        let result = probe_port("127.0.0.1", 80, Duration::from_millis(100)).await;
        // Just verify it doesn't panic
        assert!(result.port == 80);
    }

    #[tokio::test]
    async fn test_get_service_name() {
        assert_eq!(get_service_name(22), Some("SSH".to_string()));
        assert_eq!(get_service_name(80), Some("HTTP".to_string()));
        assert_eq!(get_service_name(443), Some("HTTPS".to_string()));
        assert_eq!(get_service_name(9999), None);
    }
}

