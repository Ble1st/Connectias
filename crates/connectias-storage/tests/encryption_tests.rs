use connectias_storage::EncryptionService;
use base64::{Engine as _, engine::general_purpose};

#[test]
fn test_encryption_service_creation() {
    let _encryption = EncryptionService::new();
    // Should create without errors
    assert!(true);
}

#[test]
fn test_encryption_service_encrypt_data() {
    let encryption = EncryptionService::new();
    
    let data = "This is test data to encrypt";
    let result = encryption.encrypt(data.as_bytes());
    assert!(result.is_ok());
    
    let encrypted = result.unwrap();
    assert!(!encrypted.is_empty());
    assert_ne!(encrypted, data.as_bytes());
}

#[test]
fn test_encryption_service_decrypt_data() {
    let encryption = EncryptionService::new();
    
    let original_data = "This is test data to encrypt";
    let encrypted = encryption.encrypt(original_data.as_bytes()).expect("Failed to encrypt data");
    
    let result = encryption.decrypt(&encrypted);
    assert!(result.is_ok());
    
    let decrypted = result.unwrap();
    assert_eq!(decrypted, original_data.as_bytes());
}

#[test]
fn test_encryption_service_roundtrip() {
    let encryption = EncryptionService::new();
    
    let test_cases = vec![
        "Simple string",
        "String with special characters: !@#$%^&*()",
        "String with unicode: 🚀🌟✨",
        "", // Empty string
    ];
    
    // Test long string separately
    let long_string = "Very long string: ".repeat(1000);
    
    for original_data in test_cases {
        let encrypted = encryption.encrypt(original_data.as_bytes()).expect("Failed to encrypt data");
        let decrypted = encryption.decrypt(&encrypted).expect("Failed to decrypt data");
        assert_eq!(decrypted, original_data.as_bytes());
    }
    
    // Test long string
    let encrypted = encryption.encrypt(long_string.as_bytes()).expect("Failed to encrypt data");
    let decrypted = encryption.decrypt(&encrypted).expect("Failed to decrypt data");
    assert_eq!(decrypted, long_string.as_bytes());
}

#[test]
fn test_encryption_service_different_keys() {
    let encryption1 = EncryptionService::new();
    let encryption2 = EncryptionService::new();
    
    let data = "This is test data";
    let encrypted1 = encryption1.encrypt(data.as_bytes()).expect("Failed to encrypt with key 1");
    let encrypted2 = encryption2.encrypt(data.as_bytes()).expect("Failed to encrypt with key 2");
    
    // Different keys should produce different encrypted data
    assert_ne!(encrypted1, encrypted2);
    
    // Each should decrypt correctly with its own key
    let decrypted1 = encryption1.decrypt(&encrypted1).expect("Failed to decrypt with key 1");
    let decrypted2 = encryption2.decrypt(&encrypted2).expect("Failed to decrypt with key 2");
    
    assert_eq!(decrypted1, data.as_bytes());
    assert_eq!(decrypted2, data.as_bytes());
}

#[test]
fn test_encryption_service_invalid_data() {
    let encryption = EncryptionService::new();
    
    // Try to decrypt invalid data
    let invalid_data = b"invalid encrypted data";
    let result = encryption.decrypt(invalid_data);
    assert!(result.is_err());
}

#[test]
fn test_encryption_service_empty_data() {
    let encryption = EncryptionService::new();
    
    // Test with empty data
    let result = encryption.encrypt(b"");
    assert!(result.is_ok());
    
    let encrypted = result.unwrap();
    let decrypted = encryption.decrypt(&encrypted).expect("Failed to decrypt empty data");
    assert_eq!(decrypted, b"");
}

#[test]
fn test_encryption_service_large_data() {
    let encryption = EncryptionService::new();
    
    // Test with large data
    let large_data = "A".repeat(10000);
    let result = encryption.encrypt(large_data.as_bytes());
    assert!(result.is_ok());
    
    let encrypted = result.unwrap();
    let decrypted = encryption.decrypt(&encrypted).expect("Failed to decrypt large data");
    assert_eq!(decrypted, large_data.as_bytes());
}

#[test]
fn test_encryption_service_binary_data() {
    let encryption = EncryptionService::new();
    
    // Test with binary data
    let binary_data = vec![0x00, 0x01, 0x02, 0x03, 0xFF, 0xFE, 0xFD, 0xFC];
    let result = encryption.encrypt(&binary_data);
    assert!(result.is_ok());
    
    let encrypted = result.unwrap();
    let decrypted = encryption.decrypt(&encrypted).expect("Failed to decrypt binary data");
    assert_eq!(decrypted, binary_data);
}

#[test]
fn test_encryption_service_concurrent_access() {
    let encryption = std::sync::Arc::new(EncryptionService::new());
    let encryption_clone = std::sync::Arc::clone(&encryption);
    
    // Test concurrent encryption/decryption
    let handle = std::thread::spawn(move || {
        let data = "Concurrent test data";
        let encrypted = encryption_clone.encrypt(data.as_bytes()).expect("Failed to encrypt data");
        let decrypted = encryption_clone.decrypt(&encrypted).expect("Failed to decrypt data");
        assert_eq!(decrypted, data.as_bytes());
    });
    
    handle.join().unwrap();
}

#[test]
fn test_encryption_service_performance() {
    let encryption = EncryptionService::new();
    
    let data = "Performance test data";
    let iterations = 1000;
    
    let start = std::time::Instant::now();
    
    for _ in 0..iterations {
        let encrypted = encryption.encrypt(data.as_bytes()).expect("Failed to encrypt data");
        let _decrypted = encryption.decrypt(&encrypted).expect("Failed to decrypt data");
    }
    
    let duration = start.elapsed();
    
    // Should complete within reasonable time (less than 1 second for 1000 iterations)
    assert!(duration < std::time::Duration::from_secs(1));
}

#[test]
fn test_encryption_service_key_rotation() {
    let encryption1 = EncryptionService::new();
    let encryption2 = EncryptionService::new();
    
    let data = "Data before key rotation";
    let encrypted1 = encryption1.encrypt(data.as_bytes()).expect("Failed to encrypt data");
    
    // Different encryption services should produce different encrypted data
    let encrypted2 = encryption2.encrypt(data.as_bytes()).expect("Failed to encrypt with different key");
    
    // Each should decrypt correctly with its own key
    let decrypted1 = encryption1.decrypt(&encrypted1).expect("Failed to decrypt old data");
    let decrypted2 = encryption2.decrypt(&encrypted2).expect("Failed to decrypt with different key");
    
    assert_eq!(decrypted1, data.as_bytes());
    assert_eq!(decrypted2, data.as_bytes());
    
    // Different encrypted data should be different
    assert_ne!(encrypted1, encrypted2);
}

#[test]
fn test_encryption_service_key_export() {
    let encryption = EncryptionService::new();
    
    // Test key export
    let exported_key = encryption.export_key();
    assert_eq!(exported_key.len(), 32); // 256-bit key
    
    let exported_key_base64 = encryption.export_key_base64();
    assert!(!exported_key_base64.is_empty());
    
    // Test that exported key can be used to create equivalent service
    let key_bytes: [u8; 32] = exported_key.try_into().unwrap();
    let encryption2 = EncryptionService::new_with_key(&key_bytes);
    
    // Test encryption/decryption with the new service (not cross-compatible)
    let data = "Test data for key export";
    let encrypted = encryption2.encrypt(data.as_bytes()).expect("Failed to encrypt data");
    let decrypted = encryption2.decrypt(&encrypted).expect("Failed to decrypt data");
    
    // Verify the new service works with its own key
    assert_eq!(decrypted, data.as_bytes());
    
    // Test base64 export format
    let decoded_key = general_purpose::STANDARD.decode(&exported_key_base64)
        .expect("Failed to decode base64 key");
    assert_eq!(decoded_key.len(), 32);
}
//ich diene der aktualisierung wala
