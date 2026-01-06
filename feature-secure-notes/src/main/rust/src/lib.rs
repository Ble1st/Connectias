//! Connectias Secure Notes Encryption - Rust Implementation
//! 
//! High-performance AES-256-GCM encryption/decryption using native Rust.
//! Uses ring for cryptographically secure encryption.

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jstring};
use jni::JNIEnv;
use base64::Engine;
use ring::aead::{Aad, LessSafeKey, Nonce, UnboundKey, AES_256_GCM};
use ring::rand::{SecureRandom, SystemRandom};
use zeroize::Zeroize;

#[cfg(target_os = "android")]
use android_logger::Config;
#[cfg(target_os = "android")]
use log::LevelFilter;

/// Encryption result
#[derive(Debug, Clone)]
pub struct EncryptionResult {
    pub encrypted_data: Vec<u8>, // IV (12 bytes) + encrypted data
}

/// Decryption result
#[derive(Debug, Clone)]
pub struct DecryptionResult {
    pub decrypted_data: Vec<u8>,
}

/// Encrypt data using AES-256-GCM
/// 
/// # Arguments
/// * `plaintext` - Plaintext data to encrypt
/// * `key` - 32-byte AES-256 key
/// 
/// # Returns
/// Encrypted data with IV prepended (12 bytes IV + encrypted data + 16 bytes tag)
pub fn encrypt_aes256_gcm(plaintext: &[u8], key: &[u8]) -> Result<EncryptionResult, String> {
    if key.len() != 32 {
        return Err("Key must be 32 bytes for AES-256".to_string());
    }
    
    // Generate random 12-byte nonce for GCM
    let rng = SystemRandom::new();
    let mut nonce_bytes = [0u8; 12];
    rng.fill(&mut nonce_bytes).map_err(|e| format!("Failed to generate nonce: {:?}", e))?;
    
    let nonce = Nonce::try_assume_unique_for_key(&nonce_bytes)
        .map_err(|e| format!("Failed to create nonce: {:?}", e))?;
    
    // Create unbound key
    let unbound_key = UnboundKey::new(&AES_256_GCM, key)
        .map_err(|e| format!("Failed to create key: {:?}", e))?;
    
    // Create LessSafeKey (allows manual nonce management)
    let key = LessSafeKey::new(unbound_key);
    
    // Encrypt in-place (ring appends tag automatically)
    let mut in_out = plaintext.to_vec();
    // Reserve space for tag (16 bytes)
    in_out.extend_from_slice(&[0u8; 16]);
    
    key.seal_in_place_append_tag(nonce, Aad::empty(), &mut in_out)
        .map_err(|e| format!("Encryption failed: {:?}", e))?;
    
    // Combine: nonce (12 bytes) + encrypted data + tag (16 bytes)
    let mut result = Vec::with_capacity(12 + in_out.len());
    result.extend_from_slice(&nonce_bytes);
    result.extend_from_slice(&in_out);
    
    Ok(EncryptionResult {
        encrypted_data: result,
    })
}

/// Decrypt data using AES-256-GCM
/// 
/// # Arguments
/// * `encrypted_data` - Encrypted data with IV prepended (12 bytes IV + encrypted data + 16 bytes tag)
/// * `key` - 32-byte AES-256 key
/// 
/// # Returns
/// Decrypted plaintext data
pub fn decrypt_aes256_gcm(encrypted_data: &[u8], key: &[u8]) -> Result<DecryptionResult, String> {
    if key.len() != 32 {
        return Err("Key must be 32 bytes for AES-256".to_string());
    }
    
    if encrypted_data.len() < 12 + 16 {
        return Err("Encrypted data too short".to_string());
    }
    
    // Extract nonce (first 12 bytes)
    let nonce_bytes = &encrypted_data[0..12];
    let nonce = Nonce::try_assume_unique_for_key(nonce_bytes)
        .map_err(|e| format!("Failed to create nonce: {:?}", e))?;
    
    // Extract encrypted data + tag (rest)
    let ciphertext_with_tag = &encrypted_data[12..];
    
    // Create unbound key
    let unbound_key = UnboundKey::new(&AES_256_GCM, key)
        .map_err(|e| format!("Failed to create key: {:?}", e))?;
    
    // Create LessSafeKey (allows manual nonce management)
    let key = LessSafeKey::new(unbound_key);
    
    // Decrypt in-place (ring verifies tag automatically)
    let mut in_out = ciphertext_with_tag.to_vec();
    key.open_in_place(nonce, Aad::empty(), &mut in_out)
        .map_err(|e| format!("Decryption failed: {:?}", e))?;
    
    // Remove tag (last 16 bytes)
    let decrypted_len = in_out.len() - 16;
    in_out.truncate(decrypted_len);
    
    Ok(DecryptionResult {
        decrypted_data: in_out,
    })
}

// ============================================================================
// JNI Bindings
// ============================================================================

/// Initialize logging for Android
#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_securenotes_RustEncryption_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(LevelFilter::Info)
            .with_tag("RustEncryption"),
    );
}

#[cfg(not(target_os = "android"))]
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_securenotes_RustEncryption_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) {
    // No-op for non-Android platforms
}

/// Encrypt - JNI entry point
/// 
/// # Arguments
/// * `plaintext` - Plaintext string (UTF-8)
/// * `key` - 32-byte AES-256 key
/// 
/// # Returns
/// Base64-encoded encrypted data (IV + encrypted + tag)
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_securenotes_RustEncryption_nativeEncrypt(
    mut env: JNIEnv,
    _class: JClass,
    plaintext: jstring,
    key: jbyteArray,
) -> jstring {
    // Extract plaintext
    let plaintext_str = match env.get_string(&unsafe { JString::from_raw(plaintext) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => {
            return match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    // Extract key
    let key_array = unsafe { JByteArray::from_raw(key) };
    let key_len = match env.get_array_length(&key_array) {
        Ok(l) => l as usize,
        Err(_) => 0,
    };
    
    if key_len != 32 {
        return match env.new_string("") {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        };
    }
    
    let mut key_bytes = vec![0i8; 32];
    match env.get_byte_array_region(&key_array, 0, &mut key_bytes) {
        Ok(_) => {},
        Err(_) => {
            return match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    }
    
    // Convert i8 to u8
    let key_bytes_u8: Vec<u8> = key_bytes.iter().map(|&b| b as u8).collect();
    
    // Encrypt
    let plaintext_bytes = plaintext_str.as_bytes();
    match encrypt_aes256_gcm(plaintext_bytes, &key_bytes_u8) {
        Ok(result) => {
            // Encode to Base64
            let encoded = base64::engine::general_purpose::STANDARD.encode(&result.encrypted_data);
            match env.new_string(&encoded) {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => {
            match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
    }
}

/// Decrypt - JNI entry point
/// 
/// # Arguments
/// * `encrypted_text` - Base64-encoded encrypted data
/// * `key` - 32-byte AES-256 key
/// 
/// # Returns
/// Decrypted plaintext string (UTF-8)
#[no_mangle]
pub extern "C" fn Java_com_ble1st_connectias_feature_securenotes_RustEncryption_nativeDecrypt(
    mut env: JNIEnv,
    _class: JClass,
    encrypted_text: jstring,
    key: jbyteArray,
) -> jstring {
    // Extract encrypted text
    let encrypted_str = match env.get_string(&unsafe { JString::from_raw(encrypted_text) }) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => {
            return match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    // Decode from Base64
    let encrypted_data: Vec<u8> = match base64::engine::general_purpose::STANDARD.decode(&encrypted_str) {
        Ok(d) => d,
        Err(_) => {
            return match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    };
    
    // Extract key
    let key_array = unsafe { JByteArray::from_raw(key) };
    let key_len = match env.get_array_length(&key_array) {
        Ok(l) => l as usize,
        Err(_) => 0,
    };
    
    if key_len != 32 {
        return match env.new_string("") {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => std::ptr::null_mut(),
        };
    }
    
    let mut key_bytes = vec![0i8; 32];
    match env.get_byte_array_region(&key_array, 0, &mut key_bytes) {
        Ok(_) => {},
        Err(_) => {
            return match env.new_string("") {
                Ok(jstr) => jstr.into_raw(),
                Err(_) => std::ptr::null_mut(),
            };
        }
    }
    
    // Convert i8 to u8
    let key_bytes_u8: Vec<u8> = key_bytes.iter().map(|&b| b as u8).collect();
    
    // Decrypt
    match decrypt_aes256_gcm(&encrypted_data, &key_bytes_u8) {
        Ok(result) => {
            // Convert to UTF-8 string
            match String::from_utf8(result.decrypted_data) {
                Ok(decrypted_str) => {
                    match env.new_string(&decrypted_str) {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
                Err(_) => {
                    match env.new_string("") {
                        Ok(jstr) => jstr.into_raw(),
                        Err(_) => std::ptr::null_mut(),
                    }
                }
            }
        }
        Err(_) => {
            match env.new_string("") {
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
    fn test_encrypt_decrypt() {
        let key = [0u8; 32]; // Test key (all zeros - NOT for production!)
        let plaintext = b"Hello, World!";
        
        let encrypted = encrypt_aes256_gcm(plaintext, &key).unwrap();
        let decrypted = decrypt_aes256_gcm(&encrypted.encrypted_data, &key).unwrap();
        
        assert_eq!(plaintext, decrypted.decrypted_data.as_slice());
    }
}

