//! Connectias Password Generator - Rust Implementation
//! 
//! High-performance password generation and analysis using native Rust.
//! Uses ring for cryptographically secure random number generation.

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{Deserialize, Serialize};
use zeroize::{Zeroize, ZeroizeOnDrop};

#[cfg(target_os = "android")]
use android_logger::Config;
#[cfg(target_os = "android")]
use log::LevelFilter;

/// Password generation config
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PasswordConfig {
    pub length: usize,
    pub include_lowercase: bool,
    pub include_uppercase: bool,
    pub include_digits: bool,
    pub include_symbols: bool,
}

/// Password generation result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PasswordResult {
    pub password: String,
}

/// Password analysis result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PasswordAnalysisResult {
    pub score: i32, // 0-100
    pub strength: String, // WEAK, MEDIUM, STRONG, VERY_STRONG
}

/// Character sets
const LOWER: &str = "abcdefghijklmnopqrstuvwxyz";
const UPPER: &str = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
const DIGITS: &str = "0123456789";
const SYMBOLS: &str = "!@#$%^&*()-_=+[{]}|;:'\",<.>/?`~";

/// Generate password using ring SecureRandom
pub fn generate_password(config: PasswordConfig) -> PasswordResult {
    use ring::rand::{SecureRandom, SystemRandom};
    
    let length = config.length.max(8).min(256);
    
    // Build character pool
    let mut pool = String::new();
    if config.include_lowercase {
        pool.push_str(LOWER);
    }
    if config.include_uppercase {
        pool.push_str(UPPER);
    }
    if config.include_digits {
        pool.push_str(DIGITS);
    }
    if config.include_symbols {
        pool.push_str(SYMBOLS);
    }
    
    // Fallback to lowercase if pool is empty
    if pool.is_empty() {
        pool = LOWER.to_string();
    }
    
    let pool_bytes = pool.as_bytes();
    let rng = SystemRandom::new();
    
    // Generate password bytes
    let mut password_bytes = vec![0u8; length];
    rng.fill(&mut password_bytes).unwrap();
    
    // Map bytes to characters from pool
    let password: String = password_bytes
        .iter()
        .map(|&byte| {
            let index = (byte as usize) % pool_bytes.len();
            pool_bytes[index] as char
        })
        .collect();
    
    // Zeroize password_bytes
    password_bytes.zeroize();
    
    PasswordResult { password }
}

/// Analyze password strength
pub fn analyze_password_strength(password: &str) -> PasswordAnalysisResult {
    if password.is_empty() {
        return PasswordAnalysisResult {
            score: 0,
            strength: "WEAK".to_string(),
        };
    }
    
    let mut score = 0;
    
    // 1. Length
    if password.len() >= 8 {
        score += 10;
    }
    if password.len() >= 12 {
        score += 10;
    }
    if password.len() >= 16 {
        score += 10;
    }
    if password.len() >= 20 {
        score += 10;
    }
    
    // 2. Character types
    let has_upper = password.chars().any(|c| c.is_uppercase());
    let has_lower = password.chars().any(|c| c.is_lowercase());
    let has_digit = password.chars().any(|c| c.is_ascii_digit());
    let has_special = password.chars().any(|c| !c.is_alphanumeric());
    
    if has_upper {
        score += 10;
    }
    if has_lower {
        score += 10;
    }
    if has_digit {
        score += 10;
    }
    if has_special {
        score += 15; // Special chars worth more
    }
    
    // 3. Variety bonus (if all types present)
    if has_upper && has_lower && has_digit && has_special {
        score += 15;
    }
    
    // 4. Penalties
    // Only digits
    if password.chars().all(|c| c.is_ascii_digit()) {
        score -= 10;
    }
    // Only letters
    if password.chars().all(|c| c.is_alphabetic()) {
        score -= 10;
    }
    // Repeating characters (e.g. "aaaa")
    let mut repeat_count = 0;
    let chars: Vec<char> = password.chars().collect();
    for i in 0..chars.len().saturating_sub(1) {
        if chars[i] == chars[i + 1] {
            repeat_count += 1;
        }
    }
    if repeat_count > 2 {
        score -= 10;
    }
    
    // Clamp score
    score = score.max(0).min(100);
    
    let strength = if score < 40 {
        "WEAK"
    } else if score < 70 {
        "MEDIUM"
    } else if score < 90 {
        "STRONG"
    } else {
        "VERY_STRONG"
    };
    
    PasswordAnalysisResult {
        score,
        strength: strength.to_string(),
    }
}

// ============================================================================
// JNI Bindings
// ============================================================================

/// Initialize logging for Android
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_password_domain_RustPasswordGenerator_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustPasswordGenerator"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_password_domain_RustPasswordGenerator_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Generate password - JNI entry point
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_password_domain_RustPasswordGenerator_nativeGeneratePassword(
    mut env: JNIEnv,
    _class: JClass,
    config_json: jstring,
) -> jstring {
    let config_str = match env.get_string(&unsafe { JString::from_raw(config_json) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => {
            return match env.new_string(r#"{"password":""}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    let config: PasswordConfig = match serde_json::from_str(&config_str) {
        Ok(c) => c,
        Err(_) => {
            return match env.new_string(r#"{"password":""}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    let result = generate_password(config);
    
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => {
            match env.new_string(r#"{"password":""}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

/// Analyze password strength - JNI entry point
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_password_domain_RustPasswordGenerator_nativeAnalyzePassword(
    mut env: JNIEnv,
    _class: JClass,
    password: jstring,
) -> jstring {
    let password_str = match env.get_string(&unsafe { JString::from_raw(password) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => String::new(),
    };
    
    let result = analyze_password_strength(&password_str);
    
    match serde_json::to_string(&result) {
        Ok(json) => {
            match env.new_string(&json) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => {
                    match env.new_string(r#"{"score":0,"strength":"WEAK"}"#) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string(r#"{"score":0,"strength":"WEAK"}"#) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_generate_password() {
        let config = PasswordConfig {
            length: 16,
            include_lowercase: true,
            include_uppercase: true,
            include_digits: true,
            include_symbols: true,
        };
        let result = generate_password(config);
        assert_eq!(result.password.len(), 16);
    }

    #[test]
    fn test_analyze_password_strength() {
        let result = analyze_password_strength("Test123!");
        assert!(result.score > 0);
        assert!(!result.strength.is_empty());
    }
}

