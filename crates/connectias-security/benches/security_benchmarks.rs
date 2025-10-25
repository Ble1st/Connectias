use criterion::{black_box, criterion_group, criterion_main, Criterion};
use connectias_security::{SignatureVerifier, PluginValidator, RaspProtection, NetworkSecurityFilter};
use connectias_security::zero_trust::ZeroTrustManager;
use connectias_security::threat_detection::ThreatDetectionSystem;
use std::path::Path;
use std::collections::HashMap;

fn benchmark_signature_verification(c: &mut Criterion) {
    let mut group = c.benchmark_group("signature_verification");
    
    // Erstelle temporäre Test-Plugin-Datei
    let temp_dir = std::env::temp_dir();
    let test_plugin_path = temp_dir.join("test_plugin.zip");
    
    // Erstelle eine minimale ZIP-Datei für Tests
    use std::fs::File;
    use std::io::Write;
    use zip::write::FileOptions;
    
    let file = File::create(&test_plugin_path).unwrap();
    let mut zip = zip::ZipWriter::new(file);
    zip.start_file("plugin.json", FileOptions::default()).unwrap();
    zip.write_all(b"{\"name\": \"test-plugin\"}").unwrap();
    zip.finish().unwrap();
    
    let verifier = SignatureVerifier::new();
    
    group.bench_function("verify_plugin", |b| {
        b.iter(|| {
            let _ = verifier.verify_plugin(black_box(&test_plugin_path));
        });
    });
    
    // Cleanup
    let _ = std::fs::remove_file(&test_plugin_path);
    
    group.finish();
}

fn benchmark_rasp_protection(c: &mut Criterion) {
    let mut group = c.benchmark_group("rasp_protection");
    
    let rasp = RaspProtection::new();
    
    group.bench_function("check_environment", |b| {
        b.iter(|| {
            let _ = rasp.check_environment();
        });
    });
    
    group.finish();
}

fn benchmark_network_security(c: &mut Criterion) {
    let mut group = c.benchmark_group("network_security");
    
    let filter = NetworkSecurityFilter::new();
    
    group.bench_function("validate_url", |b| {
        b.iter(|| {
            let _ = filter.validate_url(black_box("https://example.com"));
        });
    });
    
    group.finish();
}

fn benchmark_zero_trust_verification(c: &mut Criterion) {
    let mut group = c.benchmark_group("zero_trust");
    
    let zero_trust = ZeroTrustManager::new();
    
    group.bench_function("verify_operation", |b| {
        b.iter(|| {
            let operation = connectias_security::zero_trust::Operation {
                operation_type: "storage_read".to_string(),
                resource: "plugin_data".to_string(),
                parameters: HashMap::new(),
            };
            
            // Rufe echte Verifikation auf (falls async, verwende block_on)
            let result = futures::executor::block_on(zero_trust.verify_operation("test-plugin", &operation));
            black_box(result);
        });
    });
    
    group.finish();
}

fn benchmark_threat_detection(c: &mut Criterion) {
    let mut group = c.benchmark_group("threat_detection");
    
    let threat_system = ThreatDetectionSystem::new();
    
    group.bench_function("analyze_behavior", |b| {
        b.iter(|| {
            let mut context = HashMap::new();
            context.insert("frequency".to_string(), "10".to_string());
            context.insert("sensitive".to_string(), "false".to_string());
            
            // Rufe echte Analyse-Methode auf
            let _ = threat_system.analyze("test-plugin", "test_operation", &context);
        });
    });
    
    group.finish();
}

fn benchmark_plugin_validation(c: &mut Criterion) {
    let mut group = c.benchmark_group("plugin_validation");
    
    let validator = PluginValidator::new();
    let test_plugin_path = Path::new("test_plugin.zip");
    
    group.bench_function("validate_plugin_zip", |b| {
        b.iter(|| {
            // Simulate plugin validation
            let _ = validator.validate_plugin_zip(black_box(test_plugin_path));
        });
    });
    
    group.finish();
}

fn benchmark_security_operations(c: &mut Criterion) {
    let mut group = c.benchmark_group("security_operations");
    
    // Erstelle Instanzen außerhalb der gemessenen Schleife
    let verifier = SignatureVerifier::new();
    let rasp = RaspProtection::new();
    let filter = NetworkSecurityFilter::new();
    
    group.bench_function("combined_security_check", |b| {
        b.iter(|| {
            // Nur die eigentlichen Security-Checks messen
            let _ = verifier.verify_plugin(black_box(Path::new("test.zip")));
            let _ = rasp.check_environment();
            let _ = filter.validate_url(black_box("https://example.com"));
        });
    });
    
    group.finish();
}

criterion_group!(
    benches,
    benchmark_signature_verification,
    benchmark_rasp_protection,
    benchmark_network_security,
    benchmark_zero_trust_verification,
    benchmark_threat_detection,
    benchmark_plugin_validation,
    benchmark_security_operations
);
criterion_main!(benches);
