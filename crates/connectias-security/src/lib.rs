pub mod signature;
pub mod validator;
pub mod sandbox;
pub mod rasp;
pub mod network;
pub mod zero_trust;
pub mod threat_detection;

use thiserror::Error;

// Re-export wichtige Typen aus Submodulen
pub use signature::SignatureVerifier;
pub use validator::PluginValidator;
pub use rasp::RaspProtection;
pub use network::NetworkSecurityFilter;
pub use sandbox::{PluginSandbox, ResourceQuota, SandboxError, ResourceLimits, ResourceQuotaManager, InputSanitizer};
pub use zero_trust::{ZeroTrustArchitecture, PluginIdentity, AccessPolicy, BehaviorProfile, OperationPattern, ResourceUsageBaseline, PluginSegment, NetworkPolicy};
pub use threat_detection::{ThreatDetectionSystem, AnomalyDetector, AnomalyModel, ThreatReport};

#[derive(Debug, Error)]
pub enum SecurityError {
    #[error("Signature verification failed: {0}")]
    SignatureVerificationFailed(String),
    #[error("Invalid plugin structure: {0}")]
    InvalidPluginStructure(String),
    #[error("Security violation: {0}")]
    SecurityViolation(String),
}

//ich diene der aktualisierung wala
