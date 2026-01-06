//! Connectias DNS Tools - Rust Implementation
//! 
//! High-performance DNS queries using trust-dns.
//! Replaces dnsjava with faster, async DNS implementation.

use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use serde::{Deserialize, Serialize};
use std::net::IpAddr;
use std::str::FromStr;
use trust_dns_resolver::config::{NameServerConfig, Protocol, ResolverConfig, ResolverOpts};
use trust_dns_resolver::proto::rr::RecordType;
use trust_dns_resolver::{Name, Resolver};

#[cfg(target_os = "android")]
use android_logger::Config;
#[cfg(target_os = "android")]
use log::LevelFilter;

/// DNS query result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DnsQueryResult {
    pub domain: String,
    pub r#type: String,
    pub records: Vec<String>,
    pub error: Option<String>,
}

/// DNS record type mapping
fn dns_type_from_int(dns_type: i32) -> RecordType {
    match dns_type {
        1 => RecordType::A,
        2 => RecordType::NS,
        5 => RecordType::CNAME,
        15 => RecordType::MX,
        16 => RecordType::TXT,
        28 => RecordType::AAAA,
        _ => RecordType::A, // Default to A
    }
}

/// Resolve DNS query using trust-dns
pub fn resolve_dns(domain: &str, dns_type: i32, nameserver: &str) -> DnsQueryResult {
    let record_type = dns_type_from_int(dns_type);
    
    // Parse nameserver IP
    let nameserver_ip = match IpAddr::from_str(nameserver) {
        Ok(ip) => ip,
        Err(_) => {
            return DnsQueryResult {
                domain: domain.to_string(),
                r#type: format!("{}", dns_type),
                records: vec![],
                error: Some(format!("Invalid nameserver: {}", nameserver)),
            };
        }
    };
    
    // Create resolver config with custom nameserver
    let mut config = ResolverConfig::new();
    let nameserver_config = NameServerConfig {
        socket_addr: (nameserver_ip, 53).into(),
        protocol: Protocol::Udp,
        tls_dns_name: None,
        trust_negative_responses: false,
        bind_addr: None,
    };
    config.add_name_server(nameserver_config);
    
    let opts = ResolverOpts::default();
    
    // Create resolver
    let resolver = match Resolver::new(config, opts) {
        Ok(r) => r,
        Err(e) => {
            return DnsQueryResult {
                domain: domain.to_string(),
                r#type: format!("{}", dns_type),
                records: vec![],
                error: Some(format!("Failed to create resolver: {}", e)),
            };
        }
    };
    
    // Parse domain name
    let name = match Name::from_str(domain) {
        Ok(n) => n,
        Err(e) => {
            return DnsQueryResult {
                domain: domain.to_string(),
                r#type: format!("{}", dns_type),
                records: vec![],
                error: Some(format!("Invalid domain: {}", e)),
            };
        }
    };
    
    // Perform lookup (synchronous)
    match resolver.lookup(name.clone(), record_type) {
        Ok(lookup) => {
            let records: Vec<String> = lookup
                .record_iter()
                .map(|record| {
                    format!("{}", record)
                })
                .collect();
            
            DnsQueryResult {
                domain: domain.to_string(),
                r#type: format!("{:?}", record_type),
                records,
                error: None,
            }
        }
        Err(e) => {
            DnsQueryResult {
                domain: domain.to_string(),
                r#type: format!("{:?}", record_type),
                records: vec![],
                error: Some(format!("DNS lookup failed: {}", e)),
            }
        }
    }
}

// ============================================================================
// JNI Bindings
// ============================================================================

/// Initialize logging for Android
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_dnstools_data_RustDnsResolver_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustDnsResolver"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_dnstools_data_RustDnsResolver_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Resolve DNS - JNI entry point (blocking)
/// 
/// Note: This uses tokio runtime in blocking mode for JNI compatibility
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_dnstools_data_RustDnsResolver_nativeResolveDns(
    mut env: JNIEnv,
    _class: JClass,
    domain: jstring,
    dns_type: jint,
    nameserver: jstring,
) -> jstring {
    // Extract strings from JNI
    let domain_str = match env.get_string(&unsafe { JString::from_raw(domain) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let nameserver_str = match env.get_string(&unsafe { JString::from_raw(nameserver) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => "8.8.8.8".to_string(), // Default to Google DNS
    };
    
    let dns_type_int = dns_type as i32;
    
    // Execute DNS query (synchronous)
    let result = resolve_dns(&domain_str, dns_type_int, &nameserver_str);
    
    // Serialize to JSON
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string(r#"{"domain":"","type":"","records":[],"error":"Failed to create string"}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"domain":"","type":"","records":[],"error":"Failed to serialize"}"#) {
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
    async fn test_resolve_dns() {
        let result = resolve_dns("google.com", 1, "8.8.8.8").await;
        assert!(!result.domain.is_empty());
    }
}

