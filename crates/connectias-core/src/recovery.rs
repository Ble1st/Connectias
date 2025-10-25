use std::sync::Arc;
use tokio::sync::RwLock;
use connectias_api::PluginError;

/// Recovery Strategy for crashed plugins
#[derive(Debug, Clone)]
pub enum RecoveryStrategy {
    /// Ask user before restarting
    AskUser,
    /// Restart automatically
    AutoRestart,
    /// Disable plugin
    Disable,
    /// Alert and wait
    AlertAndWait,
}

/// Plugin Recovery Manager
pub struct RecoveryManager {
    strategies: Arc<RwLock<std::collections::HashMap<String, RecoveryStrategy>>>,
    crash_history: Arc<RwLock<std::collections::HashMap<String, Vec<CrashRecord>>>>,
}

/// Crash record for tracking plugin crashes
#[derive(Debug, Clone)]
pub struct CrashRecord {
    pub timestamp: chrono::DateTime<chrono::Utc>,
    pub error: String,
    pub recovery_action: RecoveryAction,
    pub success: bool,
}

impl RecoveryManager {
    pub fn new() -> Self {
        Self {
            strategies: Arc::new(RwLock::new(std::collections::HashMap::new())),
            crash_history: Arc::new(RwLock::new(std::collections::HashMap::new())),
        }
    }

    /// Set recovery strategy for a plugin
    pub async fn set_strategy(&self, plugin_id: &str, strategy: RecoveryStrategy) {
        let mut strategies = self.strategies.write().await;
        strategies.insert(plugin_id.to_string(), strategy);
    }

    /// Handle plugin crash
    pub async fn handle_crash(&self, plugin_id: &str, error: &PluginError) -> RecoveryAction {
        let strategies = self.strategies.read().await;
        let strategy = strategies.get(plugin_id).cloned().unwrap_or(RecoveryStrategy::AskUser);

        // Record crash
        self.record_crash(plugin_id, error).await;

        // Check crash frequency
        let crash_count = self.get_crash_count(plugin_id).await;
        if crash_count > 5 {
            return RecoveryAction::Disable;
        }

        match strategy {
            RecoveryStrategy::AskUser => RecoveryAction::PromptUser,
            RecoveryStrategy::AutoRestart => RecoveryAction::Restart,
            RecoveryStrategy::Disable => RecoveryAction::Disable,
            RecoveryStrategy::AlertAndWait => RecoveryAction::Alert,
        }
    }

    /// Record plugin crash
    async fn record_crash(&self, plugin_id: &str, error: &PluginError) {
        let mut history = self.crash_history.write().await;
        let record = CrashRecord {
            timestamp: chrono::Utc::now(),
            error: error.to_string(),
            recovery_action: RecoveryAction::PromptUser, // Default
            success: false,
        };
        
        history.entry(plugin_id.to_string())
            .or_insert_with(Vec::new)
            .push(record);
    }

    /// Get crash count for plugin
    async fn get_crash_count(&self, plugin_id: &str) -> usize {
        let history = self.crash_history.read().await;
        history.get(plugin_id).map(|records| records.len()).unwrap_or(0)
    }

    /// Get crash history for plugin
    pub async fn get_crash_history(&self, plugin_id: &str) -> Vec<CrashRecord> {
        let history = self.crash_history.read().await;
        history.get(plugin_id).cloned().unwrap_or_default()
    }

    /// Clear crash history for plugin
    pub async fn clear_crash_history(&self, plugin_id: &str) {
        let mut history = self.crash_history.write().await;
        history.remove(plugin_id);
    }

    /// Get recovery strategy for plugin
    pub async fn get_strategy(&self, plugin_id: &str) -> RecoveryStrategy {
        let strategies = self.strategies.read().await;
        strategies.get(plugin_id).cloned().unwrap_or(RecoveryStrategy::AskUser)
    }
}

#[derive(Debug, Clone)]
pub enum RecoveryAction {
    /// Prompt user for action
    PromptUser,
    /// Restart plugin immediately
    Restart,
    /// Disable plugin
    Disable,
    /// Show alert and wait
    Alert,
}

/// Recovery Handler for executing recovery actions
pub struct RecoveryHandler {
    recovery_manager: Arc<RecoveryManager>,
}

impl RecoveryHandler {
    pub fn new(recovery_manager: Arc<RecoveryManager>) -> Self {
        Self { recovery_manager }
    }

    /// Execute recovery action
    pub async fn execute_recovery(&self, plugin_id: &str, action: RecoveryAction) -> RecoveryResult {
        match action {
            RecoveryAction::PromptUser => {
                // In a real implementation, this would show a UI dialog
                tracing::info!("Prompting user for plugin {} recovery", plugin_id);
                RecoveryResult::UserPrompted
            },
            RecoveryAction::Restart => {
                tracing::info!("Restarting plugin {}", plugin_id);
                // In a real implementation, this would restart the plugin
                RecoveryResult::Restarted
            },
            RecoveryAction::Disable => {
                tracing::warn!("Disabling plugin {} due to crashes", plugin_id);
                // In a real implementation, this would disable the plugin
                RecoveryResult::Disabled
            },
            RecoveryAction::Alert => {
                tracing::error!("Alert: Plugin {} has crashed", plugin_id);
                // In a real implementation, this would show an alert
                RecoveryResult::Alerted
            },
        }
    }

    /// Handle plugin crash with automatic recovery
    pub async fn handle_crash(&self, plugin_id: &str, error: &PluginError) -> RecoveryResult {
        let action = self.recovery_manager.handle_crash(plugin_id, error).await;
        self.execute_recovery(plugin_id, action).await
    }
}

#[derive(Debug, Clone)]
pub enum RecoveryResult {
    UserPrompted,
    Restarted,
    Disabled,
    Alerted,
    Failed,
}

/// Recovery configuration for different plugin types
pub struct RecoveryConfig {
    pub max_crashes: u32,
    pub auto_restart_delay: std::time::Duration,
    pub disable_after_crashes: u32,
}

impl Default for RecoveryConfig {
    fn default() -> Self {
        Self {
            max_crashes: 5,
            auto_restart_delay: std::time::Duration::from_secs(5),
            disable_after_crashes: 10,
        }
    }
}

/// Recovery statistics
#[derive(Debug, Clone)]
pub struct RecoveryStats {
    pub total_crashes: u32,
    pub successful_recoveries: u32,
    pub failed_recoveries: u32,
    pub disabled_plugins: u32,
}

impl RecoveryManager {
    /// Get recovery statistics
    pub async fn get_stats(&self) -> RecoveryStats {
        let history = self.crash_history.read().await;

        let mut total_crashes = 0;
        let mut successful_recoveries = 0;
        let mut failed_recoveries = 0;
        let mut disabled_plugins = 0;

        for (_, records) in history.iter() {
            total_crashes += records.len() as u32;
            for record in records {
                if record.success {
                    successful_recoveries += 1;
                } else {
                    failed_recoveries += 1;
                }
                if matches!(record.recovery_action, RecoveryAction::Disable) {
                    disabled_plugins += 1;
                }
            }
        }

        RecoveryStats {
            total_crashes,
            successful_recoveries,
            failed_recoveries,
            disabled_plugins,
        }
    }

    /// Reset plugin statistics
    pub async fn reset_plugin_stats(&self, plugin_id: &str) {
        let mut history = self.crash_history.write().await;
        history.remove(plugin_id);
        let mut strategies = self.strategies.write().await;
        strategies.remove(plugin_id);
    }
}

//ich diene der aktualisierung wala
