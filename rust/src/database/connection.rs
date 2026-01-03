// Database connection management

use sqlx::sqlite::{SqliteConnectOptions, SqlitePool};
use std::str::FromStr;
use anyhow::Result;
use crate::database::schema::init_schema;

/// Get database path for Android
fn get_database_path() -> String {
    // On Android, we'll use the app's data directory
    // The path will be provided by Flutter via the API
    // For now, use a default path that will be set during initialization
    "/data/data/com.connectias.connectias/databases/connectias.db".to_string()
}

/// Create a new database connection pool
pub async fn create_pool(database_path: &str) -> Result<SqlitePool> {
    let options = SqliteConnectOptions::from_str(&format!("sqlite://{}?mode=rwc", database_path))?
        .create_if_missing(true);

    let pool = SqlitePool::connect_with(options).await?;
    
    // Initialize schema
    init_schema(&pool).await?;
    
    Ok(pool)
}

/// Get or create the global database pool
/// This will be managed by a static or passed from Flutter
pub struct DatabaseManager {
    pool: Option<SqlitePool>,
}

impl DatabaseManager {
    pub fn new() -> Self {
        Self { pool: None }
    }

    pub async fn initialize(&mut self, database_path: &str) -> Result<()> {
        self.pool = Some(create_pool(database_path).await?);
        Ok(())
    }

    pub fn get_pool(&self) -> Option<&SqlitePool> {
        self.pool.as_ref()
    }
}

