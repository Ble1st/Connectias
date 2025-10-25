CREATE TABLE IF NOT EXISTS plugins (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    version TEXT NOT NULL,
    author TEXT NOT NULL,
    description TEXT NOT NULL,
    install_path TEXT NOT NULL,
    state TEXT NOT NULL,
    install_date INTEGER NOT NULL,
    last_update INTEGER NOT NULL,
    min_core_version TEXT NOT NULL,
    max_core_version TEXT,
    entry_point TEXT NOT NULL,
    dependencies TEXT,
    is_enabled BOOLEAN NOT NULL DEFAULT 1,
    signature TEXT
);

CREATE TABLE IF NOT EXISTS plugin_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plugin_id TEXT NOT NULL,
    key TEXT NOT NULL,
    value BLOB NOT NULL,
    type TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    size INTEGER NOT NULL,
    is_encrypted BOOLEAN NOT NULL DEFAULT 1,
    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plugin_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plugin_id TEXT NOT NULL,
    level TEXT NOT NULL,
    message TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    stack_trace TEXT,
    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plugin_permissions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plugin_id TEXT NOT NULL,
    permission TEXT NOT NULL,
    granted BOOLEAN NOT NULL,
    request_date INTEGER NOT NULL,
    grant_date INTEGER,
    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plugin_crashes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plugin_id TEXT NOT NULL,
    exception TEXT NOT NULL,
    message TEXT NOT NULL,
    stack_trace TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    severity TEXT NOT NULL,
    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plugin_alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plugin_id TEXT NOT NULL,
    alert_type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    severity TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT 0,
    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_plugin_data_plugin_id ON plugin_data(plugin_id);
CREATE INDEX IF NOT EXISTS idx_plugin_logs_plugin_id ON plugin_logs(plugin_id);
CREATE INDEX IF NOT EXISTS idx_plugin_permissions_plugin_id ON plugin_permissions(plugin_id);

-- Add metrics table
CREATE TABLE IF NOT EXISTS plugin_metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plugin_id TEXT NOT NULL,
    metric_type TEXT NOT NULL,
    metric_name TEXT NOT NULL,
    value REAL NOT NULL,
    timestamp INTEGER NOT NULL,
    metadata TEXT,
    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_plugin_metrics_plugin_id ON plugin_metrics(plugin_id);
CREATE INDEX IF NOT EXISTS idx_plugin_metrics_type ON plugin_metrics(metric_type);
CREATE INDEX IF NOT EXISTS idx_plugin_metrics_timestamp ON plugin_metrics(timestamp);
