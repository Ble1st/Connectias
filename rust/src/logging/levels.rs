// Log level definitions

/// Log levels in order of severity
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum LogLevel {
    Trace = 0,
    Debug = 1,
    Info = 2,
    Warn = 3,
    Error = 4,
}

impl LogLevel {
    /// Convert string to LogLevel
    pub fn from_str(level: &str) -> Option<Self> {
        match level.to_uppercase().as_str() {
            "TRACE" => Some(LogLevel::Trace),
            "DEBUG" => Some(LogLevel::Debug),
            "INFO" => Some(LogLevel::Info),
            "WARN" => Some(LogLevel::Warn),
            "ERROR" => Some(LogLevel::Error),
            _ => None,
        }
    }

    /// Convert LogLevel to string
    pub fn to_string(&self) -> String {
        match self {
            LogLevel::Trace => "TRACE".to_string(),
            LogLevel::Debug => "DEBUG".to_string(),
            LogLevel::Info => "INFO".to_string(),
            LogLevel::Warn => "WARN".to_string(),
            LogLevel::Error => "ERROR".to_string(),
        }
    }

    /// Check if a log level should be logged based on current level
    pub fn should_log(&self, current_level: LogLevel) -> bool {
        *self >= current_level
    }
}

impl Default for LogLevel {
    fn default() -> Self {
        LogLevel::Info
    }
}

