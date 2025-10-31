use std::collections::{HashMap, VecDeque};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::RwLock;
use crate::SecurityError;

use async_trait::async_trait;

/// Advanced Threat Detection System
/// 
/// Implements ML-based anomaly detection, pattern-based threat detection,
/// automated threat response, threat intelligence integration, and rate limiting.
#[derive(Clone)]
pub struct ThreatDetectionSystem {
    anomaly_detector: Arc<AnomalyDetector>,
    pattern_detector: Arc<PatternDetector>,
    threat_intelligence: Arc<ThreatIntelligence>,
    response_automation: Arc<ResponseAutomation>,
    rate_limiter: Arc<RateLimiter>,
    behavioral_analyzer: Arc<BehavioralAnalyzer>,
    threat_history: Arc<RwLock<HashMap<String, Vec<ThreatEvent>>>>,
}

/// Trait für Permission Service Integration
#[async_trait]
pub trait PermissionServiceTrait: Send + Sync {
    async fn restrict_plugin_permissions(&self, plugin_id: &str, allowed: Vec<String>) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;
    async fn revoke_permission(&self, plugin_id: &str, permission: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;
}

/// Trait für Monitoring Service Integration
pub trait MonitoringServiceTrait: Send + Sync {
    fn increase_sampling_rate_sync(&self, plugin_id: &str, factor: f64) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;
}

/// Trait für Alert Service Integration
#[async_trait]
pub trait AlertServiceTrait: Send + Sync {
    async fn send_security_alert(&self, assessment: &ThreatAssessment) -> Result<String, Box<dyn std::error::Error + Send + Sync>>;
    async fn create_permission_alert(&self, plugin_id: &str, permission: &str, action: &str) -> Result<String, Box<dyn std::error::Error + Send + Sync>>;
    async fn create_resource_alert(&self, plugin_id: &str, resource_type: &str, usage: f64, limit: f64) -> Result<String, Box<dyn std::error::Error + Send + Sync>>;
}

/// Trait für Plugin Manager Integration
#[async_trait]
pub trait PluginManagerTrait: Send + Sync {
    async fn suspend_plugin(&self, plugin_id: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;
    async fn block_plugin_network_access(&self, plugin_id: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>>;
}

#[derive(Debug, Clone)]
pub struct ThreatEvent {
    pub timestamp: chrono::DateTime<chrono::Utc>,
    pub threat_type: ThreatType,
    pub severity: ThreatSeverity,
    pub plugin_id: String,
    pub description: String,
    pub indicators: Vec<String>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum ThreatType {
    Malware,
    DataExfiltration,
    PrivilegeEscalation,
    NetworkAttack,
    ResourceAbuse,
    BehavioralAnomaly,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum ThreatSeverity {
    Low,
    Medium,
    High,
    Critical,
}

impl ThreatDetectionSystem {
    /// Erstellt eine neue ThreatDetectionSystem-Instanz
    pub fn new() -> Self {
        Self {
            anomaly_detector: Arc::new(AnomalyDetector::new()),
            pattern_detector: Arc::new(PatternDetector::new()),
            threat_intelligence: Arc::new(ThreatIntelligence::new()),
            response_automation: Arc::new(ResponseAutomation::new()),
            rate_limiter: Arc::new(RateLimiter::new()),
            behavioral_analyzer: Arc::new(BehavioralAnalyzer::new()),
            threat_history: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Startet kontinuierliche Threat-Überwachung
    pub async fn start_continuous_monitoring(&self) {
        let threat_system = self.clone();
        tokio::spawn(async move {
            let mut interval_timer = tokio::time::interval(std::time::Duration::from_secs(30));
            
            loop {
                interval_timer.tick().await;
                
                // Überwache alle aktiven Plugins auf Threats
                let all_history = threat_system.get_all_threat_history().await;
                if !all_history.is_empty() {
                    log::info!("Threat detection active: {} Plugins überwacht", all_history.len());
                }
            }
        });
    }

    /// Gibt alle Threat-History zurück
    pub async fn get_all_threat_history(&self) -> HashMap<String, Vec<ThreatEvent>> {
        self.threat_history.read().await.clone()
    }

    /// Analysiert Plugin-Verhalten auf Bedrohungen
    pub async fn analyze(&self, plugin_id: &str, operation: &str, context: &HashMap<String, String>) -> Result<ThreatAssessment, SecurityError> {
        // Rate-Limiting prüfen
        if !self.rate_limiter.check_rate_limit(plugin_id, operation).await? {
            return Err(SecurityError::SecurityViolation("Rate limit exceeded".to_string()));
        }

        // Sammle alle Detector-Ergebnisse
        let anomaly_score = self.anomaly_detector.detect_anomaly(plugin_id, operation, context).await?;
        let pattern_matches = self.pattern_detector.detect_patterns(operation, context).await?;
        let intelligence_score = self.threat_intelligence.check_indicators(operation, context).await?;
        let behavioral_score = self.behavioral_analyzer.analyze_behavior(plugin_id, operation, context).await?;
        
        // Verwende gemeinsame Logik für Assessment-Erstellung
        self.compute_threat_assessment(plugin_id, operation, context, anomaly_score, pattern_matches, intelligence_score, behavioral_score).await
    }

    /// Analyze plugin behavior for threats
    pub async fn analyze_behavior(&self, plugin_id: &str, operation: &str, context: &HashMap<String, String>) -> Result<ThreatAssessment, SecurityError> {
        // Rate-Limiting prüfen
        if !self.rate_limiter.check_rate_limit(plugin_id, operation).await? {
            return Err(SecurityError::SecurityViolation("Rate limit exceeded".to_string()));
        }

        // Sammle alle Detector-Ergebnisse
        let anomaly_score = self.anomaly_detector.detect_anomaly(plugin_id, operation, context).await?;
        let pattern_matches = self.pattern_detector.detect_patterns(operation, context).await?;
        let intelligence_score = self.threat_intelligence.check_indicators(operation, context).await?;
        let behavioral_score = self.behavioral_analyzer.analyze_behavior(plugin_id, operation, context).await?;
        
        // Verwende gemeinsame Logik für Assessment-Erstellung
        let assessment = self.compute_threat_assessment(plugin_id, operation, context, anomaly_score, pattern_matches, intelligence_score, behavioral_score).await?;
        
        // Zusätzliche Verarbeitung für analyze_behavior
        if assessment.threat_score > 0.4 {
            self.log_threat_event(&assessment).await?;
        }
        
        if assessment.severity == ThreatSeverity::Critical {
            self.response_automation.trigger_response(&assessment).await?;
        }
        
        Ok(assessment)
    }

    /// Gemeinsame Logik für Threat Assessment-Erstellung
    async fn compute_threat_assessment(
        &self,
        plugin_id: &str,
        _operation: &str,
        _context: &HashMap<String, String>,
        anomaly_score: f64,
        pattern_matches: Vec<String>,
        intelligence_score: f64,
        behavioral_score: f64,
    ) -> Result<ThreatAssessment, SecurityError> {
        // Berechne Threat Score mit erweiterten Komponenten
        let pattern_score = pattern_matches.len() as f64 * 0.3;
        let threat_score = (anomaly_score + pattern_score + intelligence_score + behavioral_score) / 4.0;
        
        // Bestimme Severity
        let severity = match threat_score {
            s if s >= 0.8 => ThreatSeverity::Critical,
            s if s >= 0.6 => ThreatSeverity::High,
            s if s >= 0.4 => ThreatSeverity::Medium,
            _ => ThreatSeverity::Low,
        };
        
        // Generiere Empfehlungen
        let recommendations = self.generate_recommendations(threat_score, &pattern_matches);
        
        // Erstelle Assessment
        Ok(ThreatAssessment {
            plugin_id: plugin_id.to_string(),
            threat_score,
            severity,
            detected_threats: pattern_matches,
            recommendations,
        })
    }

    async fn log_threat_event(&self, assessment: &ThreatAssessment) -> Result<(), SecurityError> {
        let event = ThreatEvent {
            timestamp: chrono::Utc::now(),
            threat_type: ThreatType::BehavioralAnomaly,
            severity: assessment.severity.clone(),
            plugin_id: assessment.plugin_id.clone(),
            description: format!("Threat detected with score: {}", assessment.threat_score),
            indicators: assessment.detected_threats.clone(),
        };
        
        let mut history = self.threat_history.write().await;
        history.entry(assessment.plugin_id.clone())
            .or_insert_with(Vec::new)
            .push(event);
        
        Ok(())
    }

    fn generate_recommendations(&self, threat_score: f64, threats: &[String]) -> Vec<String> {
        let mut recommendations = Vec::new();
        
        if threat_score > 0.8 {
            recommendations.push("IMMEDIATE: Suspend plugin and investigate".to_string());
        } else if threat_score > 0.6 {
            recommendations.push("HIGH: Increase monitoring and restrict permissions".to_string());
        } else if threat_score > 0.4 {
            recommendations.push("MEDIUM: Monitor closely and log all activities".to_string());
        }
        
        for threat in threats {
            match threat.as_str() {
                "suspicious_network" => recommendations.push("Block network access temporarily".to_string()),
                "resource_abuse" => recommendations.push("Apply stricter resource limits".to_string()),
                "data_access_anomaly" => recommendations.push("Review data access patterns".to_string()),
                _ => recommendations.push("Investigate suspicious behavior".to_string()),
            }
        }
        
        recommendations
    }
}

#[derive(Debug, Clone)]
pub struct ThreatAssessment {
    pub plugin_id: String,
    pub threat_score: f64,
    pub severity: ThreatSeverity,
    pub detected_threats: Vec<String>,
    pub recommendations: Vec<String>,
}

/// ML-based Anomaly Detector
pub struct AnomalyDetector {
    models: Arc<RwLock<HashMap<String, AnomalyModel>>>,
}

#[derive(Debug, Clone)]
pub struct AnomalyModel {
    baseline: HashMap<String, f64>,
    thresholds: HashMap<String, f64>,
    learning_rate: f64,
}

impl AnomalyDetector {
    pub fn new() -> Self {
        Self {
            models: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn detect_anomaly(&self, plugin_id: &str, operation: &str, _context: &HashMap<String, String>) -> Result<f64, SecurityError> {
        // Simplified ML-based anomaly detection
        // In a real system, this would use actual ML models
        
        let models = self.models.read().await;
        let model = models.get(plugin_id);
        
        let anomaly_score = match model {
            Some(m) => {
                // Check against baseline patterns
                let operation_frequency = m.baseline.get(operation).unwrap_or(&0.0);
                let threshold = m.thresholds.get(operation).unwrap_or(&0.5);
                
                // Use learning rate to adjust anomaly detection
                let adjusted_threshold = threshold * (1.0 + m.learning_rate);
                
                if *operation_frequency > adjusted_threshold {
                    0.8 // High anomaly
                } else if *operation_frequency > adjusted_threshold * 0.7 {
                    0.5 // Medium anomaly
                } else {
                    0.1 // Low anomaly
                }
            },
            None => {
                // No baseline, assume normal for now
                0.1
            }
        };
        
        Ok(anomaly_score)
    }
}

/// Pattern-based Threat Detector
pub struct PatternDetector {
    threat_patterns: Arc<RwLock<Vec<ThreatPattern>>>,
}

#[derive(Debug, Clone)]
pub struct ThreatPattern {
    pub name: String,
    pub pattern: String,
    pub severity: ThreatSeverity,
    pub indicators: Vec<String>,
}

impl PatternDetector {
    pub fn new() -> Self {
        Self {
            threat_patterns: Arc::new(RwLock::new(Vec::new())),
        }
    }

    pub async fn detect_patterns(&self, operation: &str, context: &HashMap<String, String>) -> Result<Vec<String>, SecurityError> {
        let patterns = self.threat_patterns.read().await;
        let mut detected = Vec::new();
        
        for pattern in patterns.iter() {
            if self.matches_pattern(operation, context, pattern) {
                detected.push(pattern.name.clone());
            }
        }
        
        Ok(detected)
    }

    fn matches_pattern(&self, operation: &str, context: &HashMap<String, String>, pattern: &ThreatPattern) -> bool {
        // Simplified pattern matching
        // In a real system, this would use regex or more sophisticated matching
        
        match pattern.name.as_str() {
            "suspicious_network" => {
                operation.contains("network") && context.contains_key("external_url")
            },
            "resource_abuse" => {
                operation.contains("resource") && {
                    match context.get("frequency") {
                        Some(freq_str) => {
                            match freq_str.parse::<f64>() {
                                Ok(frequency) => frequency > 100.0,
                                Err(e) => {
                                    log::warn!("Fehler beim Parsen der Frequenz '{}': {}", freq_str, e);
                                    false // Behandle Parse-Fehler als nicht-matching
                                }
                            }
                        }
                        None => false
                    }
                }
            },
            "data_access_anomaly" => {
                operation.contains("data") && context.get("sensitive").map_or(false, |s| s == "true")
            },
            _ => false,
        }
    }
}

/// Threat Intelligence Integration
pub struct ThreatIntelligence {
    indicators: Arc<RwLock<HashMap<String, ThreatIndicator>>>,
}

#[derive(Debug, Clone)]
pub struct ThreatIndicator {
    pub indicator: String,
    pub threat_type: ThreatType,
    pub confidence: f64,
    pub source: String,
}

impl ThreatIntelligence {
    pub fn new() -> Self {
        Self {
            indicators: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn check_indicators(&self, operation: &str, _context: &HashMap<String, String>) -> Result<f64, SecurityError> {
        let indicators = self.indicators.read().await;
        let mut max_confidence: f64 = 0.0;
        
        for (_, indicator) in indicators.iter() {
            if operation.contains(&indicator.indicator) {
                max_confidence = max_confidence.max(indicator.confidence);
            }
        }
        
        Ok(max_confidence)
    }
}

/// Automated Response System
pub struct ResponseAutomation {
    response_rules: Arc<RwLock<Vec<ResponseRule>>>,
}

#[derive(Debug, Clone)]
pub struct ResponseRule {
    pub condition: String,
    pub action: ResponseAction,
    pub severity_threshold: ThreatSeverity,
}

#[derive(Debug, Clone)]
pub enum ResponseAction {
    SuspendPlugin,
    RestrictPermissions,
    IncreaseMonitoring,
    AlertAdministrator,
    BlockNetworkAccess,
}

impl ResponseAutomation {
    pub fn new() -> Self {
        Self {
            response_rules: Arc::new(RwLock::new(Vec::new())),
        }
    }

    pub async fn trigger_response(&self, assessment: &ThreatAssessment) -> Result<(), SecurityError> {
        let rules = self.response_rules.read().await;
        
        for rule in rules.iter() {
            if self.should_trigger_rule(assessment, rule) {
                self.execute_action(&rule.action, assessment).await?;
            }
        }
        
        Ok(())
    }

    fn should_trigger_rule(&self, assessment: &ThreatAssessment, rule: &ResponseRule) -> bool {
        match rule.severity_threshold {
            ThreatSeverity::Critical => assessment.severity == ThreatSeverity::Critical,
            ThreatSeverity::High => assessment.severity == ThreatSeverity::Critical || assessment.severity == ThreatSeverity::High,
            ThreatSeverity::Medium => assessment.severity != ThreatSeverity::Low,
            ThreatSeverity::Low => true,
        }
    }

    async fn execute_action(&self, action: &ResponseAction, assessment: &ThreatAssessment) -> Result<(), SecurityError> {
        // Diese Methode sollte in ThreatDetectionSystem implementiert werden, nicht in ResponseAutomation
        // Für jetzt loggen wir nur die Aktionen
        match action {
            ResponseAction::SuspendPlugin => {
                log::warn!("Suspending plugin {} due to threat score: {}", assessment.plugin_id, assessment.threat_score);
            },
            ResponseAction::RestrictPermissions => {
                log::warn!("Restricting permissions for plugin: {}", assessment.plugin_id);
            },
            ResponseAction::IncreaseMonitoring => {
                log::info!("Increasing monitoring for plugin: {}", assessment.plugin_id);
            },
            ResponseAction::AlertAdministrator => {
                log::error!("Security alert for plugin {}: threat score {}", assessment.plugin_id, assessment.threat_score);
            },
            ResponseAction::BlockNetworkAccess => {
                log::warn!("Blocking network access for plugin: {}", assessment.plugin_id);
            },
        }
        
        Ok(())
    }
}

/// Threat Report für detaillierte Analyse
#[derive(Debug, Clone)]
pub struct ThreatReport {
    pub assessment: ThreatAssessment,
    pub timeline: Vec<ThreatEvent>,
    pub patterns: Vec<String>,
    pub recommendations: Vec<String>,
}

/// Rate Limiter für API-Call-Limiting
pub struct RateLimiter {
    limits: Arc<RwLock<HashMap<String, RateLimit>>>,
}

#[derive(Debug, Clone)]
pub struct RateLimit {
    pub requests: VecDeque<Instant>,
    pub max_requests: u32,
    pub window_duration: Duration,
}

impl RateLimiter {
    pub fn new() -> Self {
        Self {
            limits: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn check_rate_limit(&self, plugin_id: &str, operation: &str) -> Result<bool, SecurityError> {
        let key = format!("{}:{}", plugin_id, operation);
        let now = Instant::now();
        
        let mut limits = self.limits.write().await;
        let limit = limits.entry(key.clone()).or_insert_with(|| RateLimit {
            requests: VecDeque::new(),
            max_requests: 100, // 100 requests
            window_duration: Duration::from_secs(60), // per minute
        });

        // Entferne alte Requests außerhalb des Zeitfensters
        while let Some(&old_request) = limit.requests.front() {
            if now.duration_since(old_request) > limit.window_duration {
                limit.requests.pop_front();
            } else {
                break;
            }
        }

        // Prüfe ob Limit überschritten
        if limit.requests.len() >= limit.max_requests as usize {
            return Ok(false);
        }

        // Füge neuen Request hinzu
        limit.requests.push_back(now);
        Ok(true)
    }

    pub async fn set_rate_limit(&self, plugin_id: &str, operation: &str, max_requests: u32, window_duration: Duration) {
        let key = format!("{}:{}", plugin_id, operation);
        let mut limits = self.limits.write().await;
        limits.insert(key, RateLimit {
            requests: VecDeque::new(),
            max_requests,
            window_duration,
        });
    }
}

/// Behavioral Analyzer für erweiterte Verhaltensanalyse
pub struct BehavioralAnalyzer {
    behavior_profiles: Arc<RwLock<HashMap<String, BehaviorProfile>>>,
    baseline_metrics: Arc<RwLock<HashMap<String, BaselineMetrics>>>,
}

#[derive(Debug, Clone)]
pub struct BehaviorProfile {
    pub plugin_id: String,
    pub normal_operations: HashMap<String, f64>,
    pub normal_frequency: HashMap<String, f64>,
    pub normal_timing: HashMap<String, Duration>,
    pub suspicious_patterns: Vec<String>,
}

#[derive(Debug, Clone)]
pub struct BaselineMetrics {
    pub avg_execution_time: Duration,
    pub avg_memory_usage: usize,
    pub avg_cpu_usage: f64,
    pub operation_frequency: HashMap<String, f64>,
    pub error_rate: f64,
}

impl BehavioralAnalyzer {
    pub fn new() -> Self {
        Self {
            behavior_profiles: Arc::new(RwLock::new(HashMap::new())),
            baseline_metrics: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn analyze_behavior(&self, plugin_id: &str, operation: &str, context: &HashMap<String, String>) -> Result<f64, SecurityError> {
        let profiles = self.behavior_profiles.read().await;
        let baselines = self.baseline_metrics.read().await;
        
        let profile = profiles.get(plugin_id);
        let baseline = baselines.get(plugin_id);
        
        let mut anomaly_score: f64 = 0.0;
        
        // Prüfe auf ungewöhnliche Operationen
        if let Some(profile) = profile {
            if !profile.normal_operations.contains_key(operation) {
                anomaly_score += 0.3; // Neue Operation
            }
        }
        
        // Prüfe auf ungewöhnliche Frequenz
        if let Some(baseline) = baseline {
            if let Some(expected_freq) = baseline.operation_frequency.get(operation) {
                if let Some(actual_freq_str) = context.get("frequency") {
                    if let Ok(actual_freq) = actual_freq_str.parse::<f64>() {
                        let freq_ratio = actual_freq / expected_freq;
                        if freq_ratio > 2.0 {
                            anomaly_score += 0.4; // Zu hohe Frequenz
                        } else if freq_ratio < 0.1 {
                            anomaly_score += 0.2; // Zu niedrige Frequenz
                        }
                    }
                }
            }
        }
        
        // Prüfe auf verdächtige Patterns
        if let Some(profile) = profile {
            for pattern in &profile.suspicious_patterns {
                if operation.contains(pattern) {
                    anomaly_score += 0.5;
                }
            }
        }
        
        // Prüfe auf ungewöhnliche Timing
        if let Some(timing_str) = context.get("execution_time") {
            if let Ok(execution_time_ms) = timing_str.parse::<u64>() {
                let execution_time = Duration::from_millis(execution_time_ms);
                if let Some(baseline) = baseline {
                    if execution_time > baseline.avg_execution_time * 3 {
                        anomaly_score += 0.3; // Ungewöhnlich lange Ausführung
                    }
                }
            }
        }
        
        Ok(anomaly_score.min(1.0)) // Cap bei 1.0
    }

    pub async fn update_behavior_profile(&self, plugin_id: &str, operation: &str, frequency: f64, execution_time: Duration) {
        let mut profiles = self.behavior_profiles.write().await;
        let profile = profiles.entry(plugin_id.to_string()).or_insert_with(|| BehaviorProfile {
            plugin_id: plugin_id.to_string(),
            normal_operations: HashMap::new(),
            normal_frequency: HashMap::new(),
            normal_timing: HashMap::new(),
            suspicious_patterns: Vec::new(),
        });
        
        profile.normal_operations.insert(operation.to_string(), 1.0);
        profile.normal_frequency.insert(operation.to_string(), frequency);
        profile.normal_timing.insert(operation.to_string(), execution_time);
    }

    pub async fn add_suspicious_pattern(&self, plugin_id: &str, pattern: String) {
        let mut profiles = self.behavior_profiles.write().await;
        if let Some(profile) = profiles.get_mut(plugin_id) {
            profile.suspicious_patterns.push(pattern);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_rate_limiter() {
        let rate_limiter = RateLimiter::new();
        
        // Test normal rate limiting
        assert!(rate_limiter.check_rate_limit("plugin1", "operation1").await.unwrap());
        assert!(rate_limiter.check_rate_limit("plugin1", "operation1").await.unwrap());
        
        // Set very restrictive limit
        rate_limiter.set_rate_limit("plugin1", "operation1", 1, Duration::from_secs(60)).await;
        
        // First request should pass
        assert!(rate_limiter.check_rate_limit("plugin1", "operation1").await.unwrap());
        
        // Second request should fail
        assert!(!rate_limiter.check_rate_limit("plugin1", "operation1").await.unwrap());
    }

    #[tokio::test]
    async fn test_behavioral_analyzer() {
        let analyzer = BehavioralAnalyzer::new();
        
        let mut context = HashMap::new();
        context.insert("frequency".to_string(), "10.0".to_string());
        context.insert("execution_time".to_string(), "1000".to_string());
        
        let score = analyzer.analyze_behavior("plugin1", "test_operation", &context).await.unwrap();
        assert!(score >= 0.0 && score <= 1.0);
    }

    #[tokio::test]
    async fn test_threat_detection_system() {
        let system = ThreatDetectionSystem::new();
        
        let mut context = HashMap::new();
        context.insert("test".to_string(), "value".to_string());
        
        let assessment = system.analyze("plugin1", "test_operation", &context).await.unwrap();
        assert_eq!(assessment.plugin_id, "plugin1");
        assert!(assessment.threat_score >= 0.0 && assessment.threat_score <= 1.0);
    }
}
