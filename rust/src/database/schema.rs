// Database schema definitions

use sqlx::sqlite::SqlitePool;
use sqlx::Row;
use chrono::{DateTime, Utc};

/// Log entry structure
#[derive(Debug, Clone)]
pub struct LogEntry {
    pub id: i64,
    pub timestamp: DateTime<Utc>,
    pub level: String,
    pub message: String,
    pub module: Option<String>,
}

impl LogEntry {
    /// Create a new log entry from database row
    pub fn from_row(row: &sqlx::sqlite::SqliteRow) -> Result<Self, sqlx::Error> {
        let timestamp_str: String = row.get("timestamp");
        let timestamp = DateTime::parse_from_rfc3339(&timestamp_str)
            .map_err(|e| sqlx::Error::Decode(Box::new(e)))?
            .with_timezone(&Utc);

        Ok(LogEntry {
            id: row.get("id"),
            timestamp,
            level: row.get("level"),
            message: row.get("message"),
            module: row.get("module"),
        })
    }
}

/// Initialize database schema
pub async fn init_schema(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL,
            level TEXT NOT NULL CHECK(level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR')),
            message TEXT NOT NULL,
            module TEXT
        )
        "#,
    )
    .execute(pool)
    .await?;

    Ok(())
}

