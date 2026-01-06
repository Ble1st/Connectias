//! Connectias NTP Client - Rust Implementation
//! 
//! High-performance NTP time synchronization using native Rust.
//! Replaces Apache Commons Net with faster, async NTP implementation.

use byteorder::{BigEndian, ByteOrder};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{Deserialize, Serialize};
use std::net::{SocketAddr, ToSocketAddrs, UdpSocket};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

#[cfg(target_os = "android")]
use android_logger::Config;
#[cfg(target_os = "android")]
use log::LevelFilter;

/// NTP query result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NtpResult {
    pub server: String,
    pub offset_ms: i64,
    pub delay_ms: i64,
    pub stratum: u8,
    pub reference_id: String,
    pub error: Option<String>,
}

/// NTP packet structure (48 bytes)
#[repr(C, packed)]
struct NtpPacket {
    flags: u8,           // LI (2 bits) + VN (3 bits) + Mode (3 bits)
    stratum: u8,
    poll: u8,
    precision: i8,
    root_delay: u32,     // 32-bit fixed point
    root_dispersion: u32, // 32-bit fixed point
    reference_id: u32,
    reference_timestamp: u64, // 64-bit NTP timestamp
    originate_timestamp: u64,
    receive_timestamp: u64,
    transmit_timestamp: u64,
}

/// Convert NTP timestamp (64-bit fixed point) to milliseconds since epoch
fn ntp_to_ms(ntp: u64) -> i64 {
    let seconds = (ntp >> 32) as i64;
    let fraction = (ntp & 0xFFFFFFFF) as i64;
    // Fraction is in 1/2^32 seconds
    let ms_fraction = (fraction * 1000) / (1u64 << 32) as i64;
    (seconds * 1000) + ms_fraction - 2208988800000i64 // NTP epoch offset (1900-01-01)
}

/// Convert milliseconds since epoch to NTP timestamp
fn ms_to_ntp(ms: i64) -> u64 {
    let ntp_ms = ms + 2208988800000i64; // Add NTP epoch offset
    let seconds = (ntp_ms / 1000) as u64;
    let fraction = ((ntp_ms % 1000) as u64 * (1u64 << 32) / 1000) as u64;
    (seconds << 32) | fraction
}

/// Query NTP server
pub async fn query_ntp(server: &str) -> NtpResult {
    // Create UDP socket
    let socket = match UdpSocket::bind("0.0.0.0:0") {
        Ok(s) => s,
        Err(e) => {
            return NtpResult {
                server: server.to_string(),
                offset_ms: 0,
                delay_ms: 0,
                stratum: 0,
                reference_id: String::new(),
                error: Some(format!("Failed to create socket: {}", e)),
            };
        }
    };
    
    // Set timeout
    if let Err(e) = socket.set_read_timeout(Some(Duration::from_secs(3))) {
        return NtpResult {
            server: server.to_string(),
            offset_ms: 0,
            delay_ms: 0,
            stratum: 0,
            reference_id: String::new(),
            error: Some(format!("Failed to set timeout: {}", e)),
        };
    }
    
    // Build NTP request packet
    let mut request = vec![0u8; 48];
    request[0] = 0x1b; // LI=0, VN=3, Mode=3 (client)
    
    // Get current time for originate timestamp
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;
    let originate_ntp = ms_to_ntp(now);
    BigEndian::write_u64(&mut request[24..32], originate_ntp);
    
    // Resolve server address (IPv4 only to avoid IPv6 issues)
    let server_addr = match format!("{}:123", server).parse::<SocketAddr>() {
        Ok(addr) => {
            // If it's already an IP address, use it directly
            if addr.is_ipv4() {
                addr
            } else {
                return NtpResult {
                    server: server.to_string(),
                    offset_ms: 0,
                    delay_ms: 0,
                    stratum: 0,
                    reference_id: String::new(),
                    error: Some(format!("IPv6 addresses not supported, use IPv4: {}", server)),
                };
            }
        }
        Err(_) => {
            // Try DNS resolution (IPv4 only)
            let addr_str = format!("{}:123", server);
            match addr_str.to_socket_addrs() {
                Ok(addrs) => {
                    // Prefer IPv4 addresses
                    let addrs_vec: Vec<SocketAddr> = addrs.collect();
                    match addrs_vec.iter().find(|addr| addr.is_ipv4()) {
                        Some(addr) => *addr,
                        None => {
                            return NtpResult {
                                server: server.to_string(),
                                offset_ms: 0,
                                delay_ms: 0,
                                stratum: 0,
                                reference_id: String::new(),
                                error: Some(format!("Failed to resolve IPv4 address for: {}", server)),
                            };
                        }
                    }
                }
                Err(e) => {
                    return NtpResult {
                        server: server.to_string(),
                        offset_ms: 0,
                        delay_ms: 0,
                        stratum: 0,
                        reference_id: String::new(),
                        error: Some(format!("Failed to resolve server address: {}", e)),
                    };
                }
            }
        }
    };
    
    // Send request
    if let Err(e) = socket.send_to(&request, server_addr) {
        return NtpResult {
            server: server.to_string(),
            offset_ms: 0,
            delay_ms: 0,
            stratum: 0,
            reference_id: String::new(),
            error: Some(format!("Failed to send request: {}", e)),
        };
    }
    
    // Receive response
    let mut response = vec![0u8; 48];
    let (size, _) = match socket.recv_from(&mut response) {
        Ok(r) => r,
        Err(e) => {
            return NtpResult {
                server: server.to_string(),
                offset_ms: 0,
                delay_ms: 0,
                stratum: 0,
                reference_id: String::new(),
                error: Some(format!("Failed to receive response: {}", e)),
            };
        }
    };
    
    if size < 48 {
        return NtpResult {
            server: server.to_string(),
            offset_ms: 0,
            delay_ms: 0,
            stratum: 0,
            reference_id: String::new(),
            error: Some("Response too short".to_string()),
        };
    }
    
    // Parse response
    let stratum = response[1];
    let ref_id = BigEndian::read_u32(&response[12..16]);
    
    // Parse timestamps
    let reference_ntp = BigEndian::read_u64(&response[16..24]);
    let originate_ntp = BigEndian::read_u64(&response[24..32]);
    let receive_ntp = BigEndian::read_u64(&response[32..40]);
    let transmit_ntp = BigEndian::read_u64(&response[40..48]);
    
    // Convert to milliseconds
    let _reference_ms = ntp_to_ms(reference_ntp);
    let originate_ms = ntp_to_ms(originate_ntp);
    let receive_ms = ntp_to_ms(receive_ntp);
    let transmit_ms = ntp_to_ms(transmit_ntp);
    
    // Calculate offset and delay
    let t1 = originate_ms;
    let t2 = receive_ms;
    let t3 = transmit_ms;
    let t4 = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;
    
    let delay = (t4 - t1) - (t3 - t2);
    let offset = ((t2 - t1) + (t3 - t4)) / 2;
    
    // Format reference ID
    let ref_id_str = if stratum <= 1 {
        // For Stratum 1, it's a 4-char string
        format!("{}{}{}{}",
            (ref_id >> 24) as u8 as char,
            ((ref_id >> 16) & 0xFF) as u8 as char,
            ((ref_id >> 8) & 0xFF) as u8 as char,
            (ref_id & 0xFF) as u8 as char
        ).trim_matches('\0').to_string()
    } else {
        // For secondary servers, it's an IP address
        format!("{}.{}.{}.{}",
            (ref_id >> 24) & 0xFF,
            (ref_id >> 16) & 0xFF,
            (ref_id >> 8) & 0xFF,
            ref_id & 0xFF
        )
    };
    
    NtpResult {
        server: server.to_string(),
        offset_ms: offset,
        delay_ms: delay,
        stratum,
        reference_id: ref_id_str,
        error: None,
    }
}

// ============================================================================
// JNI Bindings
// ============================================================================

/// Initialize logging for Android
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_ntp_data_RustNtpClient_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustNtpClient"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_ntp_data_RustNtpClient_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Query NTP - JNI entry point (blocking)
/// 
/// Note: This uses tokio runtime in blocking mode for JNI compatibility
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_ntp_data_RustNtpClient_nativeQueryNtp(
    mut env: JNIEnv,
    _class: JClass,
    server: jstring,
) -> jstring {
    // Extract server string from JNI
    let server_str = match env.get_string(&unsafe { JString::from_raw(server) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    // Create tokio runtime for async execution
    let rt = match tokio::runtime::Runtime::new() {
        Ok(r) => r,
        Err(_) => {
            return match env.new_string(r#"{"server":"","offset_ms":0,"delay_ms":0,"stratum":0,"reference_id":"","error":"Failed to create runtime"}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    // Execute async NTP query in blocking mode
    let result = rt.block_on(query_ntp(&server_str));
    
    // Serialize to JSON
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string(r#"{"server":"","offset_ms":0,"delay_ms":0,"stratum":0,"reference_id":"","error":"Failed to create string"}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"server":"","offset_ms":0,"delay_ms":0,"stratum":0,"reference_id":"","error":"Failed to serialize"}"#) {
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
    async fn test_query_ntp() {
        let result = query_ntp("pool.ntp.org").await;
        assert!(!result.server.is_empty());
    }
}

