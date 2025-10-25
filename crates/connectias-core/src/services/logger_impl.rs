use std::sync::Arc;
use connectias_api::Logger;
use connectias_storage::Database;

pub struct LoggerImpl {
    plugin_id: String,
    db: Arc<Database>,
}

impl LoggerImpl {
    pub fn new(plugin_id: String, db: Arc<Database>) -> Self {
        Self {
            plugin_id,
            db,
        }
    }

    fn log_to_db(&self, level: &str, msg: &str) -> Result<(), String> {
        // Store log entry in database
        let log_entry = format!("[{}] {}: {}", level, self.plugin_id, msg);
        self.db.log_plugin_event(&self.plugin_id, &log_entry)
            .map_err(|e| format!("Failed to log to database: {}", e))?;
        Ok(())
    }
}

impl Logger for LoggerImpl {
    fn debug(&self, msg: &str) {
        tracing::debug!(plugin_id = %self.plugin_id, "{}", msg);
        let _ = self.log_to_db("DEBUG", msg);
    }
    
    fn info(&self, msg: &str) {
        tracing::info!(plugin_id = %self.plugin_id, "{}", msg);
        let _ = self.log_to_db("INFO", msg);
    }
    
    fn warn(&self, msg: &str) {
        tracing::warn!(plugin_id = %self.plugin_id, "{}", msg);
        let _ = self.log_to_db("WARN", msg);
    }
    
    fn error(&self, msg: &str) {
        tracing::error!(plugin_id = %self.plugin_id, "{}", msg);
        let _ = self.log_to_db("ERROR", msg);
    }
}

