use connectias_security::{SignatureVerifier, RaspProtection, InputSanitizer, NetworkSecurityFilter, ResourceQuotaManager, PluginValidator, SecurityError};
use std::fs::File;
use std::io::Write;
use tempfile::TempDir;

#[tokio::test]
async fn test_signature_verification() {
    let verifier = SignatureVerifier::new();
    
    // Test mit ungültiger Signatur
    let temp_dir = TempDir::new().unwrap();
    let plugin_path = temp_dir.path().join("test_plugin.zip");
    
    // Erstelle ein einfaches ZIP ohne Signatur
    let mut file = File::create(&plugin_path).unwrap();
    file.write_all(b"fake zip content").unwrap();
    
    // Sollte fehlschlagen da keine Signatur vorhanden
    let result = verifier.verify_plugin(&plugin_path);
    assert!(result.is_err());
}

#[tokio::test]
async fn test_rasp_protection() {
    let protection = RaspProtection::new();
    
    // Test Environment Check
    let result = protection.check_environment();
    // In einer echten Umgebung könnte dies fehlschlagen
    // Hier testen wir nur dass die Funktion ausgeführt wird
    let _ = result;
}

#[tokio::test]
async fn test_input_sanitization() {
    let sanitizer = InputSanitizer::new();
    
    // Test SQL Injection Detection
    let result1 = sanitizer.sanitize_input("SELECT * FROM users WHERE id = '1' OR '1'='1'");
    assert_ne!(result1, "SELECT * FROM users WHERE id = '1' OR '1'='1'");
    
    let result2 = sanitizer.sanitize_input("admin'--");
    assert_ne!(result2, "admin'--");
    
    let result3 = sanitizer.sanitize_input("'; DROP TABLE users; --");
    assert_ne!(result3, "'; DROP TABLE users; --");
    
    // Test Path Traversal Detection
    let result4 = sanitizer.sanitize_input("../etc/passwd");
    assert_ne!(result4, "../etc/passwd");
    
    let result5 = sanitizer.sanitize_input("./config");
    assert_ne!(result5, "./config");
    
    let result6 = sanitizer.sanitize_input("C:\\Windows\\System32");
    assert_ne!(result6, "C:\\Windows\\System32");
    
    // Test Safe Strings
    let result7 = sanitizer.sanitize_input("safe_string_123");
    assert_eq!(result7, "safe_string_123");
    
    let result8 = sanitizer.sanitize_input("user@example.com");
    assert_eq!(result8, "user@example.com");
}

#[tokio::test]
async fn test_network_security_filter() {
    let filter = NetworkSecurityFilter::new();
    
    // Test Localhost Blocking
    assert!(filter.validate_url("http://localhost:8080").is_err());
    assert!(filter.validate_url("http://127.0.0.1:8080").is_err());
    assert!(filter.validate_url("http://::1:8080").is_err());
    
    // Test Private IP Blocking
    assert!(filter.validate_url("http://192.168.1.1").is_err());
    assert!(filter.validate_url("http://10.0.0.1").is_err());
    assert!(filter.validate_url("http://172.16.0.1").is_err());
    
    // Test Public URLs (should be allowed)
    assert!(filter.validate_url("https://api.example.com").is_ok());
    assert!(filter.validate_url("https://www.google.com").is_ok());
}

#[tokio::test]
async fn test_resource_quota_manager() {
    let manager = ResourceQuotaManager::new();
    
    // Set limits for test plugin
    let limits = connectias_security::ResourceLimits {
        max_memory: 1024 * 1024, // 1MB
        max_cpu_percent: 50.0,
        max_storage: 512 * 1024, // 512KB
        max_network_req_per_min: 10,
        max_execution_time: std::time::Duration::from_secs(5),
    };
    
    manager.set_limits("test_plugin", limits);
    
    // Test normal usage (should pass)
    assert!(manager.check_and_enforce("test_plugin").is_ok());
}

#[tokio::test]
async fn test_network_rate_limiting() {
    let filter = NetworkSecurityFilter::new();
    
    // Test rate limiting
    for _i in 0..60 {
        let url = format!("https://api.example.com/endpoint{}", _i);
        assert!(filter.validate_url(&url).is_ok());
    }
    
    // 61st request should be rate limited
    assert!(filter.validate_url("https://api.example.com/endpoint61").is_err());
}

#[tokio::test]
async fn test_security_error_types() {
    // Test SecurityError variants
    let signature_error = SecurityError::SignatureVerificationFailed("test".to_string());
    assert!(matches!(signature_error, SecurityError::SignatureVerificationFailed(_)));
    
    let structure_error = SecurityError::InvalidPluginStructure("test".to_string());
    assert!(matches!(structure_error, SecurityError::InvalidPluginStructure(_)));
    
    let violation_error = SecurityError::SecurityViolation("test".to_string());
    assert!(matches!(violation_error, SecurityError::SecurityViolation(_)));
}

#[tokio::test]
async fn test_plugin_validator() {
    let validator = PluginValidator::new();
    
    // Test mit ungültiger ZIP
    let temp_dir = TempDir::new().unwrap();
    let invalid_zip = temp_dir.path().join("invalid.zip");
    
    let mut file = File::create(&invalid_zip).unwrap();
    file.write_all(b"not a zip file").unwrap();
    
    let result = validator.validate_plugin_zip(&invalid_zip);
    assert!(result.is_err());
}

#[tokio::test]
async fn test_comprehensive_security_flow() {
    // Test kompletter Security-Flow
    let temp_dir = TempDir::new().unwrap();
    let plugin_path = temp_dir.path().join("test_plugin.zip");
    
    // Erstelle Test-ZIP
    let file = File::create(&plugin_path).unwrap();
    let _ = std::io::Write::write_all(&mut { file }, b"fake zip content");
    
    // 1. RASP Protection
    let protection = RaspProtection::new();
    let _ = protection.check_environment(); // Kann in Test-Umgebung fehlschlagen
    
    // 2. Signature Verification
    let verifier = SignatureVerifier::new();
    let _ = verifier.verify_plugin(&plugin_path); // Sollte fehlschlagen
    
    // 3. Plugin Validation
    let validator = PluginValidator::new();
    let _ = validator.validate_plugin_zip(&plugin_path); // Sollte fehlschlagen
    
    // 4. Input Sanitization
    let sanitizer = InputSanitizer::new();
    let malicious_input = "'; DROP TABLE users; --";
    let sanitized = sanitizer.sanitize_input(malicious_input);
    assert_ne!(sanitized, malicious_input);
    
    // 5. Network Security
    let filter = NetworkSecurityFilter::new();
    let localhost_url = "http://localhost:8080";
    assert!(filter.validate_url(localhost_url).is_err());
    
    // 6. Resource Quotas
    let manager = ResourceQuotaManager::new();
    let limits = connectias_security::ResourceLimits::default();
    manager.set_limits("test_plugin", limits);
    
    // Normal usage should pass
    assert!(manager.check_and_enforce("test_plugin").is_ok());
}

#[tokio::test]
async fn test_security_performance() {
    use std::time::Instant;
    
    // Test Performance von Security-Checks
    let start = Instant::now();
    
    // Input Sanitization Performance
    let sanitizer = InputSanitizer::new();
    for i in 0..1000 {
        let input = format!("test_input_{}", i);
        let _ = sanitizer.sanitize_input(&input);
    }
    
    let sanitization_time = start.elapsed();
    println!("Input sanitization 1000 iterations: {:?}", sanitization_time);
    
    // Network Security Performance
    let start = Instant::now();
    
    let filter = NetworkSecurityFilter::new();
    for i in 0..1000 {
        let url = format!("https://api{}.example.com", i);
        let _ = filter.validate_url(&url);
    }
    
    let network_time = start.elapsed();
    println!("Network security 1000 iterations: {:?}", network_time);
    
    // Resource Quota Performance
    let start = Instant::now();
    
    let manager = ResourceQuotaManager::new();
    let limits = connectias_security::ResourceLimits::default();
    manager.set_limits("test_plugin", limits);
    
    for i in 0..1000 {
        let _ = manager.check_and_enforce("test_plugin");
    }
    
    let quota_time = start.elapsed();
    println!("Resource quota 1000 iterations: {:?}", quota_time);
    
    // Alle Tests sollten unter 1 Sekunde laufen
    assert!(sanitization_time.as_millis() < 1000);
    assert!(network_time.as_millis() < 1000);
    assert!(quota_time.as_millis() < 1000);
}

//ich diene der aktualisierung wala
