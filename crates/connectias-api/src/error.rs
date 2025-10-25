use thiserror::Error;

/// Comprehensive Plugin Error Types
#[derive(Debug, Error)]
pub enum PluginError {
    // Initialization Errors
    #[error("Plugin initialization failed: {0}")]
    InitializationFailed(String),
    
    #[error("Plugin dependencies not met: {missing:?}")]
    DependencyNotMet { missing: Vec<String> },
    
    #[error("Plugin version incompatible: required {required}, got {actual}")]
    VersionIncompatible { required: String, actual: String },
    
    // Execution Errors
    #[error("Plugin execution failed: {0}")]
    ExecutionFailed(String),
    
    #[error("Command not found: {0}")]
    CommandNotFound(String),
    
    #[error("Invalid arguments: {0}")]
    InvalidArguments(String),
    
    #[error("Execution timeout after {timeout:?}")]
    ExecutionTimeout { timeout: std::time::Duration },
    
    // Resource Errors
    #[error("Memory limit exceeded: {used} bytes used, {limit} bytes limit")]
    MemoryLimitExceeded { used: usize, limit: usize },
    
    #[error("Storage quota exceeded: {used} bytes used, {quota} bytes quota")]
    StorageQuotaExceeded { used: u64, quota: u64 },
    
    #[error("CPU throttling: usage {usage}% exceeds limit {limit}%")]
    CpuThrottling { usage: f64, limit: f64 },
    
    // Permission Errors
    #[error("Permission denied: {permission} not granted")]
    PermissionDenied { permission: String },
    
    #[error("Permission request failed: {0}")]
    PermissionRequestFailed(String),
    
    // Security Errors
    #[error("Security violation: {0}")]
    SecurityViolation(String),
    
    #[error("Signature verification failed: {0}")]
    SignatureVerificationFailed(String),
    
    #[error("Malicious code detected: {0}")]
    MaliciousCodeDetected(String),
    
    // System Errors
    #[error("Plugin not found: {0}")]
    NotFound(String),
    
    #[error("Plugin already loaded: {0}")]
    AlreadyLoaded(String),
    
    #[error("Plugin crashed: {0}")]
    Crashed(String),
}

impl PluginError {
    /// Check if error is recoverable
    pub fn is_recoverable(&self) -> bool {
        matches!(self, 
            PluginError::ExecutionTimeout { .. } |
            PluginError::MemoryLimitExceeded { .. } |
            PluginError::CpuThrottling { .. } |
            PluginError::Crashed { .. }
        )
    }

    /// Check if error is security-related
    pub fn is_security_error(&self) -> bool {
        matches!(self,
            PluginError::SecurityViolation { .. } |
            PluginError::SignatureVerificationFailed { .. } |
            PluginError::MaliciousCodeDetected { .. }
        )
    }

    /// Get error severity level
    pub fn severity(&self) -> ErrorSeverity {
        match self {
            PluginError::SecurityViolation { .. } |
            PluginError::SignatureVerificationFailed { .. } |
            PluginError::MaliciousCodeDetected { .. } => ErrorSeverity::Critical,
            
            PluginError::Crashed { .. } |
            PluginError::MemoryLimitExceeded { .. } => ErrorSeverity::High,
            
            PluginError::ExecutionFailed { .. } |
            PluginError::PermissionDenied { .. } => ErrorSeverity::Medium,
            
            PluginError::CommandNotFound { .. } |
            PluginError::InvalidArguments { .. } => ErrorSeverity::Low,
            
            _ => ErrorSeverity::Medium,
        }
    }
}

/// Error severity levels
#[derive(Debug, Clone, PartialEq)]
pub enum ErrorSeverity {
    Low,
    Medium,
    High,
    Critical,
}

/// Error context for better debugging
#[derive(Debug, Clone)]
pub struct ErrorContext {
    pub plugin_id: String,
    pub operation: String,
    pub timestamp: chrono::DateTime<chrono::Utc>,
    pub user_id: Option<String>,
    pub session_id: Option<String>,
}

impl ErrorContext {
    pub fn new(plugin_id: String, operation: String) -> Self {
        Self {
            plugin_id,
            operation,
            timestamp: chrono::Utc::now(),
            user_id: None,
            session_id: None,
        }
    }

    pub fn with_user(mut self, user_id: String) -> Self {
        self.user_id = Some(user_id);
        self
    }

    pub fn with_session(mut self, session_id: String) -> Self {
        self.session_id = Some(session_id);
        self
    }
}

/// Enhanced error with context
#[derive(Debug)]
pub struct ContextualError {
    pub error: PluginError,
    pub context: ErrorContext,
}

impl ContextualError {
    pub fn new(error: PluginError, context: ErrorContext) -> Self {
        Self { error, context }
    }

    pub fn is_recoverable(&self) -> bool {
        self.error.is_recoverable()
    }

    pub fn severity(&self) -> ErrorSeverity {
        self.error.severity()
    }
}

impl std::fmt::Display for ContextualError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{} (Plugin: {}, Operation: {})", 
            self.error, 
            self.context.plugin_id, 
            self.context.operation
        )
    }
}

impl std::error::Error for ContextualError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        Some(&self.error)
    }
}

