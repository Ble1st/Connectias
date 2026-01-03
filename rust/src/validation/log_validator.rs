// Log validation logic

use anyhow::{Result, bail};

/// Valid log levels
pub const VALID_LOG_LEVELS: &[&str] = &["TRACE", "DEBUG", "INFO", "WARN", "ERROR"];

/// Validate log level
pub fn validate_log_level(level: &str) -> Result<()> {
    if !VALID_LOG_LEVELS.contains(&level.to_uppercase().as_str()) {
        bail!("Invalid log level: {}. Must be one of: {:?}", level, VALID_LOG_LEVELS);
    }
    Ok(())
}

/// Validate log message
pub fn validate_log_message(message: &str) -> Result<()> {
    if message.is_empty() {
        bail!("Log message cannot be empty");
    }
    
    if message.len() > 10000 {
        bail!("Log message too long: {} characters (max 10000)", message.len());
    }
    
    Ok(())
}

/// Validate module name
pub fn validate_module_name(module: Option<&String>) -> Result<()> {
    if let Some(module) = module {
        if module.is_empty() {
            bail!("Module name cannot be empty");
        }
        
        if module.len() > 255 {
            bail!("Module name too long: {} characters (max 255)", module.len());
        }
        
        // Check for valid characters (alphanumeric, underscore, dash, dot)
        if !module.chars().all(|c| c.is_alphanumeric() || c == '_' || c == '-' || c == '.') {
            bail!("Module name contains invalid characters. Only alphanumeric, underscore, dash, and dot are allowed");
        }
    }
    
    Ok(())
}

/// Validate complete log entry before database insertion
pub fn validate_log_entry(level: &str, message: &str, module: Option<&String>) -> Result<()> {
    validate_log_level(level)?;
    validate_log_message(message)?;
    validate_module_name(module)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_log_level() {
        assert!(validate_log_level("INFO").is_ok());
        assert!(validate_log_level("DEBUG").is_ok());
        assert!(validate_log_level("ERROR").is_ok());
        assert!(validate_log_level("INVALID").is_err());
    }

    #[test]
    fn test_validate_log_message() {
        assert!(validate_log_message("Test message").is_ok());
        assert!(validate_log_message("").is_err());
        let long_message = "a".repeat(10001);
        assert!(validate_log_message(&long_message).is_err());
    }

    #[test]
    fn test_validate_module_name() {
        assert!(validate_module_name(Some(&"test_module".to_string())).is_ok());
        assert!(validate_module_name(Some(&"test-module".to_string())).is_ok());
        assert!(validate_module_name(Some(&"test.module".to_string())).is_ok());
        assert!(validate_module_name(Some(&"".to_string())).is_err());
        assert!(validate_module_name(None).is_ok());
    }
}

