// Logger implementation

use sqlx::sqlite::SqlitePool;
use chrono::Utc;
use std::sync::Arc;
use tokio::sync::RwLock;
use anyhow::Result;
use crate::logging::levels::LogLevel;
use crate::validation::validate_log_entry;

/// Thread-safe logger that writes to database
pub struct Logger {
    pool: Arc<SqlitePool>,
    current_level: Arc<RwLock<LogLevel>>,
}

impl Logger {
    /// Create a new logger instance
    pub fn new(pool: SqlitePool) -> Self {
        Self {
            pool: Arc::new(pool),
            current_level: Arc::new(RwLock::new(LogLevel::Info)),
        }
    }

    /// Get current log level
    pub async fn get_log_level(&self) -> LogLevel {
        *self.current_level.read().await
    }

    /// Set log level
    pub async fn set_log_level(&self, level: LogLevel) {
        *self.current_level.write().await = level;
    }

    /// Log a message (async, non-blocking)
    pub async fn log(
        &self,
        level: LogLevel,
        message: String,
        module: Option<String>,
    ) -> Result<()> {
        // Check if we should log this level
        let current_level = self.get_log_level().await;
        if !level.should_log(current_level) {
            return Ok(());
        }

        // Validate log entry
        let level_str = level.to_string();
        validate_log_entry(&level_str, &message, module.as_ref())?;

        // Insert into database
        let timestamp = Utc::now().to_rfc3339();
        
        sqlx::query(
            r#"
            INSERT INTO logs (timestamp, level, message, module)
            VALUES (?, ?, ?, ?)
            "#,
        )
        .bind(&timestamp)
        .bind(&level_str)
        .bind(&message)
        .bind(&module)
        .execute(&*self.pool)
        .await?;

        Ok(())
    }

    /// Log trace message
    pub async fn trace(&self, message: String, module: Option<String>) -> Result<()> {
        self.log(LogLevel::Trace, message, module).await
    }

    /// Log debug message
    pub async fn debug(&self, message: String, module: Option<String>) -> Result<()> {
        self.log(LogLevel::Debug, message, module).await
    }

    /// Log info message
    pub async fn info(&self, message: String, module: Option<String>) -> Result<()> {
        self.log(LogLevel::Info, message, module).await
    }

    /// Log warn message
    pub async fn warn(&self, message: String, module: Option<String>) -> Result<()> {
        self.log(LogLevel::Warn, message, module).await
    }

    /// Log error message
    pub async fn error(&self, message: String, module: Option<String>) -> Result<()> {
        self.log(LogLevel::Error, message, module).await
    }
}

