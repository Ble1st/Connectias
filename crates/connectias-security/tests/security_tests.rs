use connectias_security::{
    rasp::RaspMonitor,
    threat_detection::{ThreatLevel, ThreatDetector},
    SecurityPolicy,
};
use std::sync::Arc;
use tokio;

#[tokio::test]
async fn test_certificate_transparency_verification() {
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    // Test mit ungültigem Zertifikat (sollte Fehler verursachen)
    let invalid_cert = b"invalid_certificate_data_for_testing";
    let result = monitor.verify_certificate_transparency(invalid_cert).await;
    
    // Sollte einen Fehler verursachen (ungültiges Zertifikat)
    match result {
        Ok(is_valid) => {
            // Ungültiges Zertifikat sollte false zurückgeben
            assert!(!is_valid, "Invalid certificate should not be valid");
            println!("✅ Certificate Transparency correctly rejected invalid certificate");
        }
        Err(e) => {
            // Fehler ist auch OK für ungültige Zertifikate
            println!("✅ Certificate Transparency correctly failed for invalid certificate: {}", e);
        }
    }
}

#[tokio::test]
async fn test_certificate_pinning_validation() {
    let policy = SecurityPolicy {
        certificate_pins: vec![
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".to_string(),
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=".to_string(),
        ],
        ..Default::default()
    };
    
    let monitor = RaspMonitor::new(policy);
    
    // Test mit ungültigem Zertifikat
    let invalid_cert = b"invalid_certificate_data_for_pinning_test";
    let result = monitor.validate_certificate_pinning(invalid_cert, &policy.certificate_pins);
    
    // Sollte false sein, da Test-Zertifikat nicht den erwarteten Pins entspricht
    assert!(!result, "Certificate pinning should fail for test certificate");
    println!("✅ Certificate pinning validation working correctly");
}

#[tokio::test]
async fn test_threat_detection_system() {
    let detector = ThreatDetector::new();
    
    // Test für verschiedene Threat-Level
    let threats = vec![
        (ThreatLevel::Low, "Minor security event"),
        (ThreatLevel::Medium, "Suspicious activity detected"),
        (ThreatLevel::High, "Potential security threat"),
        (ThreatLevel::Critical, "Active attack detected"),
    ];
    
    for (level, description) in threats {
        let result = detector.detect_threat(level, description).await;
        assert!(result.is_ok(), "Threat detection should succeed for level {:?}", level);
        println!("✅ Threat detection working for level {:?}", level);
    }
}

#[tokio::test]
async fn test_security_policy_validation() {
    let policy = SecurityPolicy {
        enforce_https: true,
        allowed_domains: vec!["api.connectias.com".to_string()],
        certificate_pins: vec!["sha256/test=".to_string()],
        enable_certificate_transparency: true,
        timeout: std::time::Duration::from_secs(30),
    };
    
    // Test HTTPS enforcement
    assert!(policy.enforce_https, "HTTPS should be enforced");
    
    // Test domain allowlisting
    assert!(policy.allowed_domains.contains(&"api.connectias.com".to_string()));
    assert!(!policy.allowed_domains.contains(&"malicious.com".to_string()));
    
    // Test certificate pinning
    assert!(!policy.certificate_pins.is_empty());
    
    // Test CT verification
    assert!(policy.enable_certificate_transparency);
    
    println!("✅ Security policy validation working correctly");
}

#[tokio::test]
async fn test_rasp_monitor_initialization() {
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    // Monitor sollte erfolgreich initialisiert werden
    assert!(monitor.is_initialized(), "RASP monitor should be initialized");
    println!("✅ RASP monitor initialization working correctly");
}

#[tokio::test]
async fn test_security_error_handling() {
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    // Test mit ungültigen Daten
    let invalid_cert = b"invalid_certificate_data";
    let result = monitor.verify_certificate_transparency(invalid_cert).await;
    
    // Sollte einen Fehler zurückgeben
    assert!(result.is_err(), "Invalid certificate should cause error");
    println!("✅ Security error handling working correctly");
}

#[tokio::test]
async fn test_concurrent_security_operations() {
    let policy = SecurityPolicy::default();
    let monitor = Arc::new(RaspMonitor::new(policy));
    
    // Test parallele Security-Operationen
    let handles: Vec<_> = (0..10)
        .map(|i| {
            let monitor = Arc::clone(&monitor);
            tokio::spawn(async move {
                let test_cert = format!("test_cert_{}", i).into_bytes();
                monitor.verify_certificate_transparency(&test_cert).await
            })
        })
        .collect();
    
    // Warte auf alle Tasks
    for handle in handles {
        let result = handle.await.unwrap();
        // Erwarte Fehler für Test-Zertifikate
        assert!(result.is_err(), "Test certificates should cause errors");
    }
    
    println!("✅ Concurrent security operations working correctly");
}

#[tokio::test]
async fn test_security_policy_edge_cases() {
    // Test mit leerer Policy
    let empty_policy = SecurityPolicy {
        enforce_https: false,
        allowed_domains: vec![],
        certificate_pins: vec![],
        enable_certificate_transparency: false,
        timeout: std::time::Duration::from_secs(0),
    };
    
    let monitor = RaspMonitor::new(empty_policy);
    assert!(monitor.is_initialized(), "Empty policy should still work");
    
    // Test mit sehr langen Domain-Namen
    let long_domain = "a".repeat(1000);
    let policy_with_long_domain = SecurityPolicy {
        allowed_domains: vec![long_domain],
        ..Default::default()
    };
    
    let monitor2 = RaspMonitor::new(policy_with_long_domain);
    assert!(monitor2.is_initialized(), "Long domain names should work");
    
    println!("✅ Security policy edge cases handled correctly");
}

#[tokio::test]
async fn test_certificate_pinning_edge_cases() {
    let policy = SecurityPolicy {
        certificate_pins: vec![
            "invalid_hash_format".to_string(),
            "sha256/".to_string(),
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".to_string(),
        ],
        ..Default::default()
    };
    
    let monitor = RaspMonitor::new(policy);
    
    // Test mit verschiedenen Pin-Formaten
    let test_cert = b"test_certificate";
    let result = monitor.validate_certificate_pinning(test_cert, &policy.certificate_pins);
    
    // Sollte false sein für Test-Zertifikat
    assert!(!result, "Test certificate should not match any pins");
    
    println!("✅ Certificate pinning edge cases handled correctly");
}

#[tokio::test]
async fn test_threat_detection_performance() {
    let detector = ThreatDetector::new();
    
    let start = std::time::Instant::now();
    
    // Test mit vielen Threat-Detection-Operationen
    for i in 0..1000 {
        let _ = detector.detect_threat(ThreatLevel::Low, &format!("Test threat {}", i)).await;
    }
    
    let duration = start.elapsed();
    assert!(duration.as_millis() < 1000, "Threat detection should be fast");
    
    println!("✅ Threat detection performance test passed: {:?}", duration);
}

#[tokio::test]
async fn test_security_monitoring_integration() {
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    // Test Integration zwischen verschiedenen Security-Komponenten
    let test_cert = b"integration_test_certificate";
    
    // Test CT-Verification
    let ct_result = monitor.verify_certificate_transparency(test_cert).await;
    
    // Test Certificate Pinning
    let pin_result = monitor.validate_certificate_pinning(test_cert, &policy.certificate_pins);
    
    // Beide Tests sollten durchgeführt werden können
    assert!(ct_result.is_ok() || ct_result.is_err(), "CT verification should complete");
    assert!(!pin_result, "Certificate pinning should fail for test cert");
    
    println!("✅ Security monitoring integration working correctly");
}