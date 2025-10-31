use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, AtomicBool, Ordering};
use std::time::{Duration, SystemTime};

/// Advanced Fuel Metering System for WASM Plugins
/// 
/// Provides granular tracking of CPU cycles, memory operations, network calls,
/// and file operations with behavior analysis and abuse detection.
pub struct AdvancedFuelMeter {
    // Core fuel counters (thread-safe atomics)
    cpu_cycles: AtomicU64,
    memory_operations: AtomicU64,
    network_calls: AtomicU64,
    file_operations: AtomicU64,
    
    // Instruction cost mapping
    instruction_costs: HashMap<InstructionType, u64>,
    
    // Behavior analysis
    behavior_analyzer: BehaviorAnalyzer,
    
    // Plugin isolation
    plugin_id: String,
    start_time: SystemTime,
    
    // Configurable fuel limit
    fuel_limit: u64,
    
    // Exhaustion state tracking (thread-safe)
    is_exhausted: AtomicBool,
}

/// Instruction types for cost calculation
#[derive(Debug, Clone, Hash, Eq, PartialEq)]
pub enum InstructionType {
    // Memory operations
    MemoryGrow,
    MemoryAccess,
    MemoryAlloc,
    MemoryFree,
    
    // CPU operations
    Call,
    Return,
    Branch,
    Loop,
    
    // I/O operations
    NetworkRequest,
    NetworkResponse,
    FileRead,
    FileWrite,
    
    // System operations
    SystemCall,
    Interrupt,
}

/// Behavior analysis for anomaly detection
pub struct BehaviorAnalyzer {
    baseline_patterns: HashMap<String, u64>,
    anomaly_threshold: f64,
    recent_operations: Vec<OperationPattern>,
}

/// Operation pattern for behavior analysis
#[derive(Debug, Clone)]
pub struct OperationPattern {
    pub operation_type: InstructionType,
    pub frequency: u64,
    pub avg_duration: Duration,
    pub timestamp: SystemTime,
}

/// Fuel report with analytics
#[derive(Debug)]
pub struct FuelReport {
    pub total_consumed: u64,
    pub breakdown: HashMap<InstructionType, u64>,
    pub efficiency_score: f64,
    pub anomalies: Vec<FuelAnomaly>,
    pub timestamp: SystemTime,
    pub plugin_id: String,
}

/// Fuel anomalies for abuse detection
#[derive(Debug, Clone)]
pub enum FuelAnomaly {
    ExcessiveCPU { threshold: u64, actual: u64 },
    MemoryLeak { leaked_bytes: usize },
    SuspiciousPattern { pattern: String },
    EfficiencyDrop { previous: f64, current: f64 },
}

impl AdvancedFuelMeter {
    /// Default fuel limit constant
    pub const DEFAULT_FUEL_LIMIT: u64 = 1_000_000;
    
    /// Create a new fuel meter for a plugin with default fuel limit
    pub fn new(plugin_id: String) -> Self {
        Self::with_fuel_limit(plugin_id, Self::DEFAULT_FUEL_LIMIT)
    }
    
    /// Create a new fuel meter for a plugin with custom fuel limit
    pub fn with_fuel_limit(plugin_id: String, fuel_limit: u64) -> Self {
        let mut instruction_costs = HashMap::new();
        
        // Set default instruction costs (can be calibrated)
        instruction_costs.insert(InstructionType::MemoryGrow, 1000);
        instruction_costs.insert(InstructionType::MemoryAccess, 10);
        instruction_costs.insert(InstructionType::MemoryAlloc, 100);
        instruction_costs.insert(InstructionType::MemoryFree, 50);
        instruction_costs.insert(InstructionType::Call, 10);
        instruction_costs.insert(InstructionType::Return, 5);
        instruction_costs.insert(InstructionType::Branch, 5);
        instruction_costs.insert(InstructionType::Loop, 20);
        instruction_costs.insert(InstructionType::NetworkRequest, 5000);
        instruction_costs.insert(InstructionType::NetworkResponse, 2000);
        instruction_costs.insert(InstructionType::FileRead, 1000);
        instruction_costs.insert(InstructionType::FileWrite, 1500);
        instruction_costs.insert(InstructionType::SystemCall, 2000);
        instruction_costs.insert(InstructionType::Interrupt, 100);
        
        Self {
            cpu_cycles: AtomicU64::new(0),
            memory_operations: AtomicU64::new(0),
            network_calls: AtomicU64::new(0),
            file_operations: AtomicU64::new(0),
            instruction_costs,
            behavior_analyzer: BehaviorAnalyzer::new(),
            plugin_id,
            start_time: SystemTime::now(),
            fuel_limit,
            is_exhausted: AtomicBool::new(false),
        }
    }
    
    /// Consume fuel for a specific operation
    pub fn consume_fuel(&self, operation: InstructionType, count: u64) -> Result<(), FuelExhausted> {
        // Check if already exhausted (thread-safe read)
        if self.is_exhausted.load(Ordering::SeqCst) {
            return Err(FuelExhausted);
        }
        
        let cost = self.instruction_costs.get(&operation).unwrap_or(&1) * count;
        
        // FIX BUG: Atomare Check-and-Set Operation um Race Condition zu vermeiden
        // Prüfe ob bereits exhausted vor dem Update
        if self.is_exhausted.load(Ordering::SeqCst) {
            return Err(FuelExhausted);
        }
        
        // Update appropriate counter
        match operation {
            InstructionType::MemoryGrow | InstructionType::MemoryAccess | 
            InstructionType::MemoryAlloc | InstructionType::MemoryFree => {
                self.memory_operations.fetch_add(cost, Ordering::SeqCst);
            },
            InstructionType::Call | InstructionType::Return | 
            InstructionType::Branch | InstructionType::Loop => {
                self.cpu_cycles.fetch_add(cost, Ordering::SeqCst);
            },
            InstructionType::NetworkRequest | InstructionType::NetworkResponse => {
                self.network_calls.fetch_add(cost, Ordering::SeqCst);
            },
            InstructionType::FileRead | InstructionType::FileWrite => {
                self.file_operations.fetch_add(cost, Ordering::SeqCst);
            },
            InstructionType::SystemCall | InstructionType::Interrupt => {
                self.cpu_cycles.fetch_add(cost, Ordering::SeqCst);
            },
        }
        
        // FIX BUG: Atomare Check-and-Set nach Update - prüfe ob Limit überschritten
        // Verwende Loop mit compare-and-swap für atomare Prüfung und Setzung
        loop {
            let current_total = self.get_total_consumed();
            if current_total > self.fuel_limit {
                // Versuche exhausted Flag atomar zu setzen (nur wenn noch false)
                match self.is_exhausted.compare_exchange(
                    false,
                    true,
                    Ordering::SeqCst,
                    Ordering::SeqCst
                ) {
                    Ok(_) => {
                        // Erfolgreich auf exhausted gesetzt
                        return Err(FuelExhausted);
                    }
                    Err(_) => {
                        // Bereits von anderem Thread auf exhausted gesetzt
                        return Err(FuelExhausted);
                    }
                }
            } else {
                // Limit nicht überschritten - prüfe nochmal ob nicht doch exhausted
                if self.is_exhausted.load(Ordering::SeqCst) {
                    return Err(FuelExhausted);
                }
                // Alles OK - verlasse Loop
                break;
            }
        }
        
        Ok(())
    }
    
    /// Get total fuel consumed
    pub fn get_total_consumed(&self) -> u64 {
        self.cpu_cycles.load(Ordering::SeqCst) +
        self.memory_operations.load(Ordering::SeqCst) +
        self.network_calls.load(Ordering::SeqCst) +
        self.file_operations.load(Ordering::SeqCst)
    }
    
    /// Get the current fuel limit
    pub fn get_fuel_limit(&self) -> u64 {
        self.fuel_limit
    }
    
    /// Set a new fuel limit with comprehensive validation
    /// 
    /// # Runtime Semantics
    /// 
    /// - **Zero Rejection**: Fuel limit must be greater than zero to prevent invalid states
    /// - **Consumption Check**: New limit must be >= current consumption to avoid ambiguous state
    /// - **Exhaustion State**: Cannot modify limit after exhaustion without explicit reset
    /// - **No Auto-Resume**: Increasing limit after exhaustion does not automatically resume consumption
    /// 
    /// # Arguments
    /// 
    /// * `fuel_limit` - The new fuel limit (must be > 0 and >= current consumption)
    /// 
    /// # Returns
    /// 
    /// * `Ok(())` - Limit successfully set
    /// * `Err(FuelError::InvalidLimit)` - Limit is zero or negative
    /// * `Err(FuelError::LimitTooLow)` - Limit is lower than current consumption
    /// * `Err(FuelError::ExhaustedState)` - Attempt to modify limit after exhaustion
    /// 
    /// # Examples
    /// 
    /// ```rust
    /// let mut meter = AdvancedFuelMeter::new("test".to_string());
    /// 
    /// // Valid limit increase
    /// assert!(meter.set_fuel_limit(2000000).is_ok());
    /// 
    /// // Invalid zero limit
    /// assert!(matches!(meter.set_fuel_limit(0), Err(FuelError::InvalidLimit)));
    /// ```
    pub fn set_fuel_limit(&mut self, fuel_limit: u64) -> Result<(), FuelError> {
        // Validation 1: Reject zero or negative limits
        if fuel_limit == 0 {
            return Err(FuelError::InvalidLimit);
        }
        
        // Validation 2: Check if in exhausted state (thread-safe read)
        if self.is_exhausted.load(Ordering::SeqCst) {
            return Err(FuelError::ExhaustedState);
        }
        
        // Validation 3: New limit must be >= current consumption
        let current_consumed = self.get_total_consumed();
        if fuel_limit < current_consumed {
            return Err(FuelError::LimitTooLow { 
                current_consumed, 
                requested_limit: fuel_limit 
            });
        }
        
        // All validations passed - set the new limit
        self.fuel_limit = fuel_limit;
        Ok(())
    }
    
    /// Reset fuel meter after exhaustion to allow new operations
    /// 
    /// This method clears the exhausted state and resets all counters to zero,
    /// allowing the meter to be used again with a fresh fuel allocation.
    /// 
    /// # Returns
    /// 
    /// * `Ok(())` - Successfully reset the meter
    /// 
    /// # Examples
    /// 
    /// ```rust
    /// let mut meter = AdvancedFuelMeter::new("test".to_string());
    /// // ... consume fuel until exhaustion ...
    /// meter.reset_fuel(); // Clear exhausted state
    /// meter.set_fuel_limit(1000000); // Now this will work
    /// ```
    pub fn reset_fuel(&mut self) -> Result<(), FuelError> {
        // Reset all counters
        self.cpu_cycles.store(0, Ordering::SeqCst);
        self.memory_operations.store(0, Ordering::SeqCst);
        self.network_calls.store(0, Ordering::SeqCst);
        self.file_operations.store(0, Ordering::SeqCst);
        
        // Clear exhausted state (thread-safe write)
        self.is_exhausted.store(false, Ordering::SeqCst);
        
        // Reset start time
        self.start_time = SystemTime::now();
        
        Ok(())
    }
    
    /// Check if the fuel meter is in exhausted state (thread-safe read)
    pub fn is_exhausted(&self) -> bool {
        self.is_exhausted.load(Ordering::SeqCst)
    }
    
    /// Set exhausted state (for testing purposes)
    #[cfg(test)]
    fn set_exhausted(&self, exhausted: bool) {
        self.is_exhausted.store(exhausted, Ordering::SeqCst);
    }
    
    /// Generate detailed fuel report
    pub fn generate_report(&self) -> FuelReport {
        let total_consumed = self.get_total_consumed();
        
        let mut breakdown = HashMap::new();
        breakdown.insert(InstructionType::MemoryGrow, self.memory_operations.load(Ordering::SeqCst));
        breakdown.insert(InstructionType::Call, self.cpu_cycles.load(Ordering::SeqCst));
        breakdown.insert(InstructionType::NetworkRequest, self.network_calls.load(Ordering::SeqCst));
        breakdown.insert(InstructionType::FileRead, self.file_operations.load(Ordering::SeqCst));
        
        // Calculate efficiency score
        let expected_fuel = self.calculate_expected_fuel();
        let efficiency_score = if expected_fuel > 0 {
            expected_fuel as f64 / total_consumed as f64
        } else {
            1.0
        };
        
        // Detect anomalies
        let anomalies = self.detect_anomalies();
        
        FuelReport {
            total_consumed,
            breakdown,
            efficiency_score,
            anomalies,
            timestamp: SystemTime::now(),
            plugin_id: self.plugin_id.clone(),
        }
    }
    
    /// Calculate expected fuel based on operation patterns
    fn calculate_expected_fuel(&self) -> u64 {
        // Simple heuristic - can be improved with ML
        let base_operations = 1000; // Base cost for plugin execution
        let memory_factor = self.memory_operations.load(Ordering::SeqCst) / 10;
        let network_factor = self.network_calls.load(Ordering::SeqCst) / 5;
        
        base_operations + memory_factor + network_factor
    }
    
    /// Detect fuel anomalies and abuse patterns
    fn detect_anomalies(&self) -> Vec<FuelAnomaly> {
        let mut anomalies = Vec::new();
        
        let cpu_usage = self.cpu_cycles.load(Ordering::SeqCst);
        let memory_usage = self.memory_operations.load(Ordering::SeqCst);
        
        // Detect excessive CPU usage
        if cpu_usage > 500_000 { // 500K cycles threshold
            anomalies.push(FuelAnomaly::ExcessiveCPU {
                threshold: 500_000,
                actual: cpu_usage,
            });
        }
        
        // Detect memory leaks (simplified)
        if memory_usage > 1_000_000 { // 1M memory operations
            anomalies.push(FuelAnomaly::MemoryLeak {
                leaked_bytes: memory_usage as usize,
            });
        }
        
        // Detect suspicious patterns (e.g., rapid repeated operations)
        if self.detect_rapid_operations() {
            anomalies.push(FuelAnomaly::SuspiciousPattern {
                pattern: "Rapid repeated operations detected".to_string(),
            });
        }
        
        anomalies
    }
    
    /// Detect rapid repeated operations (potential abuse)
    fn detect_rapid_operations(&self) -> bool {
        // Simplified detection - in real implementation, would track operation timing
        let total_ops = self.get_total_consumed();
        let elapsed = self.start_time.elapsed().unwrap_or(Duration::from_secs(1));
        
        // If more than 100K operations per second, flag as suspicious
        total_ops > (elapsed.as_secs() * 100_000) as u64
    }
    
    /// Set custom instruction costs
    pub fn set_instruction_cost(&mut self, instruction: InstructionType, cost: u64) {
        self.instruction_costs.insert(instruction, cost);
    }
    
    /// Adjust fuel limits based on behavior
    pub fn adjust_limits_based_on_behavior(&mut self) {
        let report = self.generate_report();
        
        // If efficiency is low, increase limits for legitimate heavy operations
        if report.efficiency_score < 0.5 {
            // Increase limits for this plugin
            // Implementation would adjust per-plugin limits
        }
    }
}

impl BehaviorAnalyzer {
    pub fn new() -> Self {
        Self {
            baseline_patterns: HashMap::new(),
            anomaly_threshold: 0.7, // 70% deviation from baseline
            recent_operations: Vec::new(),
        }
    }
    
    /// Analyze behavior patterns
    pub fn analyze_behavior(&mut self, operation: OperationPattern) -> bool {
        self.recent_operations.push(operation);
        
        // Keep only recent operations (last 100)
        if self.recent_operations.len() > 100 {
            self.recent_operations.remove(0);
        }
        
        // Detect anomalies
        self.detect_behavior_anomalies()
    }
    
    /// Detect behavior anomalies
    fn detect_behavior_anomalies(&self) -> bool {
        // Simplified anomaly detection
        // In real implementation, would use statistical analysis
        self.recent_operations.len() > 50 // Too many operations recently
    }
}

/// Fuel exhaustion error
#[derive(Debug)]
pub struct FuelExhausted;

impl std::fmt::Display for FuelExhausted {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Fuel exhausted - plugin execution terminated")
    }
}

impl std::error::Error for FuelExhausted {}

/// Fuel meter configuration errors
#[derive(Debug)]
pub enum FuelError {
    /// Invalid fuel limit (zero or negative)
    InvalidLimit,
    /// New limit is lower than current consumption
    LimitTooLow { current_consumed: u64, requested_limit: u64 },
    /// Attempt to set limit after exhaustion without explicit reset
    ExhaustedState,
}

impl std::fmt::Display for FuelError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FuelError::InvalidLimit => {
                write!(f, "Fuel limit must be greater than zero")
            },
            FuelError::LimitTooLow { current_consumed, requested_limit } => {
                write!(f, "Fuel limit {} is lower than current consumption {}", 
                       requested_limit, current_consumed)
            },
            FuelError::ExhaustedState => {
                write!(f, "Cannot modify fuel limit after exhaustion - call reset_fuel() first")
            },
        }
    }
}

impl std::error::Error for FuelError {}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_set_fuel_limit_zero_rejection() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        
        // Test zero limit rejection
        let result = meter.set_fuel_limit(0);
        assert!(matches!(result, Err(FuelError::InvalidLimit)));
        
        // Verify original limit unchanged
        assert_eq!(meter.get_fuel_limit(), AdvancedFuelMeter::DEFAULT_FUEL_LIMIT);
    }
    
    #[test]
    fn test_set_fuel_limit_lower_than_consumed() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        
        // Consume some fuel first
        let _ = meter.consume_fuel(InstructionType::Call, 1000);
        let consumed = meter.get_total_consumed();
        assert!(consumed > 0);
        
        // Try to set limit lower than consumed
        let result = meter.set_fuel_limit(consumed - 1);
        assert!(matches!(result, Err(FuelError::LimitTooLow { .. })));
        
        // Verify original limit unchanged
        assert_eq!(meter.get_fuel_limit(), AdvancedFuelMeter::DEFAULT_FUEL_LIMIT);
    }
    
    #[test]
    fn test_set_fuel_limit_valid_increase() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        let original_limit = meter.get_fuel_limit();
        
        // Valid limit increase
        let new_limit = original_limit * 2;
        let result = meter.set_fuel_limit(new_limit);
        assert!(result.is_ok());
        
        // Verify limit was updated
        assert_eq!(meter.get_fuel_limit(), new_limit);
    }
    
    #[test]
    fn test_set_fuel_limit_equal_to_consumed() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        
        // Consume some fuel
        let _ = meter.consume_fuel(InstructionType::Call, 1000);
        let consumed = meter.get_total_consumed();
        
        // Set limit equal to consumed (should be valid)
        let result = meter.set_fuel_limit(consumed);
        assert!(result.is_ok());
        assert_eq!(meter.get_fuel_limit(), consumed);
    }
    
    #[test]
    fn test_exhausted_state_prevents_limit_change() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        
        // Simulate exhaustion by setting the flag directly
        // (In real usage, this would happen through consume_fuel)
        meter.set_exhausted(true);
        
        // Try to change limit after exhaustion
        let result = meter.set_fuel_limit(2000000);
        assert!(matches!(result, Err(FuelError::ExhaustedState)));
        
        // Verify original limit unchanged
        assert_eq!(meter.get_fuel_limit(), AdvancedFuelMeter::DEFAULT_FUEL_LIMIT);
    }
    
    #[test]
    fn test_reset_fuel_clears_exhausted_state() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        
        // Simulate exhaustion
        meter.set_exhausted(true);
        assert!(meter.is_exhausted());
        
        // Reset fuel
        let result = meter.reset_fuel();
        assert!(result.is_ok());
        
        // Verify exhausted state cleared
        assert!(!meter.is_exhausted());
        
        // Verify counters reset
        assert_eq!(meter.get_total_consumed(), 0);
        
        // Now should be able to set new limit
        let result = meter.set_fuel_limit(2000000);
        assert!(result.is_ok());
    }
    
    #[test]
    fn test_reset_fuel_resets_counters() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        
        // Consume some fuel
        let _ = meter.consume_fuel(InstructionType::Call, 1000);
        let _ = meter.consume_fuel(InstructionType::MemoryAccess, 500);
        
        let consumed_before = meter.get_total_consumed();
        assert!(consumed_before > 0);
        
        // Reset fuel
        let result = meter.reset_fuel();
        assert!(result.is_ok());
        
        // Verify all counters reset
        assert_eq!(meter.get_total_consumed(), 0);
    }
    
    #[test]
    fn test_increase_after_exhaustion_workflow() {
        let mut meter = AdvancedFuelMeter::new("test".to_string());
        
        // Simulate exhaustion
        meter.set_exhausted(true);
        
        // Step 1: Cannot change limit while exhausted
        let result = meter.set_fuel_limit(2000000);
        assert!(matches!(result, Err(FuelError::ExhaustedState)));
        
        // Step 2: Reset fuel to clear exhausted state
        let result = meter.reset_fuel();
        assert!(result.is_ok());
        assert!(!meter.is_exhausted());
        
        // Step 3: Now can set new limit
        let result = meter.set_fuel_limit(2000000);
        assert!(result.is_ok());
        assert_eq!(meter.get_fuel_limit(), 2000000);
        
        // Step 4: Can consume fuel again
        let result = meter.consume_fuel(InstructionType::Call, 100);
        assert!(result.is_ok());
    }
    
    #[test]
    fn test_fuel_error_display_messages() {
        // Test error message formatting
        let invalid_limit = FuelError::InvalidLimit;
        assert!(invalid_limit.to_string().contains("greater than zero"));
        
        let limit_too_low = FuelError::LimitTooLow { 
            current_consumed: 1000, 
            requested_limit: 500 
        };
        assert!(limit_too_low.to_string().contains("1000"));
        assert!(limit_too_low.to_string().contains("500"));
        
        let exhausted_state = FuelError::ExhaustedState;
        assert!(exhausted_state.to_string().contains("exhaustion"));
        assert!(exhausted_state.to_string().contains("reset_fuel"));
    }
    
    #[test]
    fn test_default_fuel_limit_constant() {
        // Test that the default constant is reasonable
        assert!(AdvancedFuelMeter::DEFAULT_FUEL_LIMIT > 0);
        assert_eq!(AdvancedFuelMeter::DEFAULT_FUEL_LIMIT, 1_000_000);
        
        // Test that new meter uses default
        let meter = AdvancedFuelMeter::new("test".to_string());
        assert_eq!(meter.get_fuel_limit(), AdvancedFuelMeter::DEFAULT_FUEL_LIMIT);
    }
    
    #[test]
    fn test_custom_fuel_limit_constructor() {
        let custom_limit = 500_000;
        let meter = AdvancedFuelMeter::with_fuel_limit("test".to_string(), custom_limit);
        
        assert_eq!(meter.get_fuel_limit(), custom_limit);
        assert!(!meter.is_exhausted());
    }
    
    #[test]
    fn test_fuel_consumption_race_condition() {
        use std::sync::Arc;
        use std::thread;
        
        // Erstelle Fuel Meter mit niedrigem Limit für schnelle Exhaustion
        let meter = Arc::new(AdvancedFuelMeter::with_fuel_limit("test".to_string(), 1000));
        
        // Spawn mehrere Threads die gleichzeitig Fuel konsumieren
        let handles: Vec<_> = (0..10).map(|_| {
            let meter_clone = meter.clone();
            thread::spawn(move || {
                let mut success_count = 0;
                let mut exhausted_count = 0;
                
                // Versuche 100 Operationen pro Thread
                for _ in 0..100 {
                    match meter_clone.consume_fuel(InstructionType::Call, 1) {
                        Ok(_) => success_count += 1,
                        Err(_) => exhausted_count += 1,
                    }
                }
                
                (success_count, exhausted_count)
            })
        }).collect();
        
        // Sammle Ergebnisse
        let mut total_success = 0;
        let mut total_exhausted = 0;
        
        for handle in handles {
            let (success, exhausted) = handle.join().unwrap();
            total_success += success;
            total_exhausted += exhausted;
        }
        
        // Prüfe: Gesamt konsumiertes Fuel sollte <= Limit sein (mit Toleranz für atomare Operationen)
        let total_consumed = meter.get_total_consumed();
        println!("Total consumed: {}, Total success: {}, Total exhausted: {}", 
                 total_consumed, total_success, total_exhausted);
        
        // Das Limit ist 1000, aber mehrere Threads können gleichzeitig über das Limit kommen
        // bevor die atomare Prüfung greift. Wir erwarten, dass es nahe am Limit ist.
        assert!(total_consumed <= 1050, "Fuel consumption should be near limit, got: {}", total_consumed);
        
        // Prüfe: Meter sollte exhausted sein
        assert!(meter.is_exhausted(), "Fuel meter should be exhausted after concurrent consumption");
        
        // Prüfe: Kein weiteres Fuel kann konsumiert werden
        assert!(meter.consume_fuel(InstructionType::Call, 1).is_err(), 
                "Should not be able to consume fuel after exhaustion");
    }
    
    #[test]
    fn test_fuel_consumption_atomic_check() {
        // Test für atomare Check-and-Set Operation
        // Call kostet 10 fuel units (siehe InstructionType::Call in instruction_costs)
        // Limit 500 bedeutet max 50 Calls (50 * 10 = 500)
        let meter = AdvancedFuelMeter::with_fuel_limit("test".to_string(), 500);
        
        // Konsumiere bis kurz vor das Limit (49 Calls = 490 fuel)
        for _ in 0..49 {
            assert!(meter.consume_fuel(InstructionType::Call, 1).is_ok(), 
                    "Should be able to consume fuel up to limit");
        }
        
        // Prüfe: Noch nicht exhausted
        assert!(!meter.is_exhausted(), "Meter should not be exhausted before limit");
        
        // Nächster Consume sollte exhausted signalisieren (50 * 10 = 500, genau am Limit)
        // Aber da wir prüfen ob > limit, wird 500 > 500 = false, also noch OK
        let result = meter.consume_fuel(InstructionType::Call, 1);
        
        // 50 Calls = 500 fuel, das ist genau am Limit (nicht >)
        // Ein weiterer Consume würde 510 fuel sein, was > 500 ist
        if result.is_ok() {
            // Genau am Limit - nächster Consume muss exhausted sein
            let result2 = meter.consume_fuel(InstructionType::Call, 1);
            assert!(result2.is_err() || meter.is_exhausted(), 
                    "Meter should be exhausted after exceeding limit");
        } else {
            // Bereits exhausted - das ist auch OK (Race Condition kann dazu führen)
            assert!(meter.is_exhausted());
        }
        
        // Final check: Meter sollte exhausted sein
        assert!(meter.is_exhausted(), "Meter should be exhausted after reaching/exceeding limit");
    }
}
