// API functions exposed to Flutter via flutter_rust_bridge

use flutter_rust_bridge::frb;
use anyhow::Result;
use std::sync::Arc;
use tokio::sync::RwLock;
use sqlx::sqlite::SqlitePool;
use crate::database::create_pool;
use crate::logging::{Logger, LogLevel};

// Global state for database and logger
static DB_POOL: RwLock<Option<Arc<SqlitePool>>> = RwLock::const_new(None);
static LOGGER: RwLock<Option<Arc<Logger>>> = RwLock::const_new(None);

/// Initialize the database
/// This must be called before any other operations
#[frb(sync)]
pub fn init_database(database_path: String) -> Result<String> {
    let rt = tokio::runtime::Runtime::new()?;
    rt.block_on(async {
        let pool = create_pool(&database_path).await?;
        let pool_arc = Arc::new(pool);
        
        let logger = Logger::new((*pool_arc).clone());
        let logger_arc = Arc::new(logger);
        
        *DB_POOL.write().await = Some(pool_arc.clone());
        *LOGGER.write().await = Some(logger_arc.clone());
        
        Ok("Database initialized successfully".to_string())
    })
}

/// Log a message
#[frb(sync)]
pub fn log_message(level: String, message: String, module: Option<String>) -> Result<String> {
    let rt = tokio::runtime::Runtime::new()?;
    rt.block_on(async {
        let logger_guard = LOGGER.read().await;
        let logger = logger_guard.as_ref()
            .ok_or_else(|| anyhow::anyhow!("Logger not initialized. Call init_database first."))?;
        
        let log_level = LogLevel::from_str(&level)
            .ok_or_else(|| anyhow::anyhow!("Invalid log level: {}", level))?;
        
        logger.log(log_level, message, module).await?;
        Ok("Log written successfully".to_string())
    })
}

/// Get logs from database
#[frb(sync)]
pub fn get_logs(level_filter: Option<String>, limit: i32, offset: i32) -> Result<Vec<String>> {
    let rt = tokio::runtime::Runtime::new()?;
    rt.block_on(async {
        let pool_guard = DB_POOL.read().await;
        let pool = pool_guard.as_ref()
            .ok_or_else(|| anyhow::anyhow!("Database not initialized. Call init_database first."))?;
        
        let limit = limit.max(0).min(1000) as i64; // Limit to 1000 max
        let offset = offset.max(0) as i64;
        
        let query = if let Some(level) = level_filter {
            sqlx::query_as::<_, (i64, String, String, String, Option<String>)>(
                r#"
                SELECT id, timestamp, level, message, module
                FROM logs
                WHERE level = ?
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                "#,
            )
            .bind(level)
            .bind(limit)
            .bind(offset)
            .fetch_all(&**pool)
            .await?
        } else {
            sqlx::query_as::<_, (i64, String, String, String, Option<String>)>(
                r#"
                SELECT id, timestamp, level, message, module
                FROM logs
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                "#,
            )
            .bind(limit)
            .bind(offset)
            .fetch_all(&**pool)
            .await?
        };
        
        let mut results = Vec::new();
        for row in query {
            let (id, timestamp, level, message, module) = row;
            let module_str = module.as_ref().map(|m| format!(" [{}]", m)).unwrap_or_default();
            results.push(format!("[{}] {} {}: {}{}", timestamp, id, level, message, module_str));
        }
        
        Ok(results)
    })
}

/// Set the current log level
#[frb(sync)]
pub fn set_log_level(level: String) -> Result<String> {
    let rt = tokio::runtime::Runtime::new()?;
    rt.block_on(async {
        let logger_guard = LOGGER.read().await;
        let logger = logger_guard.as_ref()
            .ok_or_else(|| anyhow::anyhow!("Logger not initialized. Call init_database first."))?;
        
        let log_level = LogLevel::from_str(&level)
            .ok_or_else(|| anyhow::anyhow!("Invalid log level: {}", level))?;
        
        logger.set_log_level(log_level).await;
        Ok(format!("Log level set to {}", level))
    })
}

/// Get the current log level
#[frb(sync)]
pub fn get_log_level() -> Result<String> {
    let rt = tokio::runtime::Runtime::new()?;
    rt.block_on(async {
        let logger_guard = LOGGER.read().await;
        let logger = logger_guard.as_ref()
            .ok_or_else(|| anyhow::anyhow!("Logger not initialized. Call init_database first."))?;
        
        let level = logger.get_log_level().await;
        Ok(level.to_string())
    })
}

/// Test function to verify flutter_rust_bridge is working
#[frb(sync)]
pub fn init_app() -> String {
    "Hello from Rust!".to_string()
}
