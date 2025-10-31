use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use connectias_security::{
    rasp::RaspMonitor,
    threat_detection::{ThreatLevel, ThreatDetector},
    SecurityPolicy,
};
use std::time::Duration;
use tokio::runtime::Runtime;

fn benchmark_certificate_transparency_verification(c: &mut Criterion) {
    let mut group = c.benchmark_group("certificate_transparency");
    group.measurement_time(Duration::from_secs(15));
    
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    // Test with invalid certificate (should fail fast)
    let invalid_cert = b"invalid_certificate_data_for_benchmarking";
    
    // Create a runtime for async operations
    let rt = Runtime::new().unwrap();
    
    group.bench_function("verify_invalid_cert", |b| {
        b.iter(|| {
            let _ = black_box(rt.block_on(monitor.verify_certificate_transparency(black_box(invalid_cert))));
        })
    });
    
    group.finish();
}

fn benchmark_certificate_pinning_validation(c: &mut Criterion) {
    let mut group = c.benchmark_group("certificate_pinning");
    group.measurement_time(Duration::from_secs(10));
    
    let policy = SecurityPolicy {
        certificate_pins: vec![
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".to_string(),
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=".to_string(),
        ],
        ..Default::default()
    };
    
    let monitor = RaspMonitor::new(policy);
    let invalid_cert = b"invalid_certificate_data_for_pinning_benchmark";
    
    // Create a runtime for async operations
    let rt = Runtime::new().unwrap();
    
    group.bench_function("validate_pinning", |b| {
        b.iter(|| {
            let _ = black_box(rt.block_on(monitor.validate_certificate_pinning(
                black_box(invalid_cert),
                black_box(&policy.certificate_pins)
            )));
        })
    });
    
    group.finish();
}

fn benchmark_threat_detection(c: &mut Criterion) {
    let mut group = c.benchmark_group("threat_detection");
    group.measurement_time(Duration::from_secs(10));
    
    let detector = ThreatDetector::new();
    
    let threat_levels = vec![
        ThreatLevel::Low,
        ThreatLevel::Medium,
        ThreatLevel::High,
        ThreatLevel::Critical,
    ];
    
    for level in threat_levels {
        group.bench_with_input(
            BenchmarkId::new("detect_threat", format!("{:?}", level)),
            &level,
            |b, level| {
                b.iter(|| {
                    let _ = black_box(detector.detect_threat(
                        black_box(*level),
                        black_box("Benchmark threat description")
                    ));
                })
            }
        );
    }
    
    group.finish();
}

fn benchmark_security_policy_validation(c: &mut Criterion) {
    let mut group = c.benchmark_group("security_policy");
    group.measurement_time(Duration::from_secs(5));
    
    let policies = vec![
        SecurityPolicy::default(),
        SecurityPolicy {
            enforce_https: true,
            allowed_domains: vec!["api.connectias.com".to_string()],
            certificate_pins: vec!["sha256/test=".to_string()],
            enable_certificate_transparency: true,
            timeout: Duration::from_secs(30),
        },
        SecurityPolicy {
            enforce_https: false,
            allowed_domains: vec![],
            certificate_pins: vec![],
            enable_certificate_transparency: false,
            timeout: Duration::from_secs(0),
        },
    ];
    
    for (i, policy) in policies.iter().enumerate() {
        group.bench_with_input(
            BenchmarkId::new("validate_policy", i),
            &policy,
            |b, policy| {
                b.iter(|| {
                    let _ = black_box(policy.enforce_https);
                    let _ = black_box(&policy.allowed_domains);
                    let _ = black_box(&policy.certificate_pins);
                    let _ = black_box(policy.enable_certificate_transparency);
                    let _ = black_box(policy.timeout);
                })
            }
        );
    }
    
    group.finish();
}

fn benchmark_rasp_monitor_operations(c: &mut Criterion) {
    let mut group = c.benchmark_group("rasp_monitor");
    group.measurement_time(Duration::from_secs(10));
    
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    group.bench_function("initialization", |b| {
        b.iter(|| {
            let _ = black_box(RaspMonitor::new(black_box(SecurityPolicy::default())));
        })
    });
    
    group.bench_function("is_initialized", |b| {
        b.iter(|| {
            let _ = black_box(monitor.is_initialized());
        })
    });
    
    group.finish();
}

fn benchmark_concurrent_security_operations(c: &mut Criterion) {
    let mut group = c.benchmark_group("concurrent_security");
    group.measurement_time(Duration::from_secs(15));
    
    let policy = SecurityPolicy::default();
    let monitor = std::sync::Arc::new(RaspMonitor::new(policy));
    
    // Create a runtime for async operations
    let rt = Runtime::new().unwrap();
    
    group.bench_function("concurrent_ct_verification", |b| {
        b.iter(|| {
            let monitor = std::sync::Arc::clone(&monitor);
            let test_cert = b"concurrent_test_certificate";
            let rt_handle = rt.handle().clone();
            
            // Simulate concurrent operations with async runtime
            let handles: Vec<_> = (0..5)
                .map(|_| {
                    let monitor = std::sync::Arc::clone(&monitor);
                    let cert = test_cert.clone();
                    let rt = rt_handle.clone();
                    std::thread::spawn(move || {
                        rt.block_on(monitor.verify_certificate_transparency(&cert))
                    })
                })
                .collect();
            
            for handle in handles {
                let _ = black_box(handle.join().unwrap());
            }
        })
    });
    
    group.finish();
}

fn benchmark_security_error_handling(c: &mut Criterion) {
    let mut group = c.benchmark_group("security_error_handling");
    group.measurement_time(Duration::from_secs(10));
    
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    // Create a runtime for async operations
    let rt = Runtime::new().unwrap();
    
    let invalid_inputs = vec![
        b"".as_slice(),
        b"invalid".as_slice(),
        b"very_long_invalid_certificate_data_that_should_cause_errors".as_slice(),
    ];
    
    for (i, invalid_input) in invalid_inputs.iter().enumerate() {
        group.bench_with_input(
            BenchmarkId::new("handle_invalid_input", i),
            invalid_input,
            |b, input| {
                b.iter(|| {
                    let _ = black_box(rt.block_on(monitor.verify_certificate_transparency(black_box(input))));
                })
            }
        );
    }
    
    group.finish();
}

fn benchmark_memory_usage_under_load(c: &mut Criterion) {
    let mut group = c.benchmark_group("memory_usage");
    group.measurement_time(Duration::from_secs(20));
    
    let policy = SecurityPolicy::default();
    let monitor = RaspMonitor::new(policy);
    
    // Create a runtime for async operations
    let rt = Runtime::new().unwrap();
    
    group.bench_function("memory_under_load", |b| {
        b.iter(|| {
            // Create many operations to test memory usage
            for i in 0..1000 {
                let test_cert = format!("test_certificate_{}", i).into_bytes();
                let _ = black_box(rt.block_on(monitor.verify_certificate_transparency(&test_cert)));
            }
        })
    });
    
    group.finish();
}

fn benchmark_security_policy_scaling(c: &mut Criterion) {
    let mut group = c.benchmark_group("policy_scaling");
    group.measurement_time(Duration::from_secs(10));
    
    let domain_counts = vec![1, 10, 100, 1000];
    let pin_counts = vec![1, 5, 10, 50];
    
    for domain_count in domain_counts {
        for pin_count in pin_counts {
            let domains: Vec<String> = (0..domain_count)
                .map(|i| format!("domain{}.com", i))
                .collect();
            
            let pins: Vec<String> = (0..pin_count)
                .map(|i| format!("sha256/pin{}=", i))
                .collect();
            
            let policy = SecurityPolicy {
                enforce_https: true,
                allowed_domains: domains,
                certificate_pins: pins,
                enable_certificate_transparency: true,
                timeout: Duration::from_secs(30),
            };
            
            group.bench_with_input(
                BenchmarkId::new("policy_validation", format!("{}_domains_{}_pins", domain_count, pin_count)),
                &policy,
                |b, policy| {
                    b.iter(|| {
                        let _ = black_box(&policy.allowed_domains);
                        let _ = black_box(&policy.certificate_pins);
                        let _ = black_box(policy.enforce_https);
                    })
                }
            );
        }
    }
    
    group.finish();
}

criterion_group!(
    benches,
    benchmark_certificate_transparency_verification,
    benchmark_certificate_pinning_validation,
    benchmark_threat_detection,
    benchmark_security_policy_validation,
    benchmark_rasp_monitor_operations,
    benchmark_concurrent_security_operations,
    benchmark_security_error_handling,
    benchmark_memory_usage_under_load,
    benchmark_security_policy_scaling
);

criterion_main!(benches);
