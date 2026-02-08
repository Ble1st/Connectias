// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging;

import com.ble1st.connectias.service.logging.ExternalLogParcel;
import com.ble1st.connectias.service.logging.LoggingMetricsParcel;
import com.ble1st.connectias.service.logging.SecurityAuditParcel;
import com.ble1st.connectias.service.logging.ILoggingResultCallback;

/**
 * Combined AIDL interface for LoggingService.
 *
 * External apps use submitLog* methods (requires SUBMIT_EXTERNAL_LOGS permission).
 * Main process uses setEnabled/getRecentLogs and query methods (validated by caller UID).
 *
 * The service validates caller permissions/UID for each method.
 *
 * Versioning:
 *   v1: submitLog, submitLogWithException, setEnabled, isEnabled,
 *       getRecentLogs, getLogsByPackage, getLogsByLevel, getLogCount, clearAllLogs
 *   v2: setDatabaseKey, getSecurityMetrics, getRecentLogsAsync,
 *       getLogsByPackageAsync, getLogsByLevelAsync,
 *       getRecentAuditEvents, getRecentAuditEventsAsync
 */
interface ILoggingService {

    // ========== v1: Methods for external apps (requires permission) ==========

    /**
     * Submit a single log entry.
     */
    void submitLog(String packageName, String level, String tag, String message);

    /**
     * Submit a log entry with exception trace.
     */
    void submitLogWithException(String packageName, String level, String tag,
                                String message, String exceptionTrace);

    // ========== v1: Methods for main process (validated by UID) ==========

    /**
     * Enable or disable the logging service.
     */
    void setEnabled(boolean enabled);

    /**
     * Check if the logging service is currently enabled.
     */
    boolean isEnabled();

    /**
     * Get recent logs for the Service-Log-Viewer (synchronous, use sparingly).
     * For large datasets prefer getRecentLogsAsync.
     */
    List<ExternalLogParcel> getRecentLogs(int limit);

    /**
     * Get logs for a specific package (synchronous).
     */
    List<ExternalLogParcel> getLogsByPackage(String packageName, int limit);

    /**
     * Get logs by level (synchronous).
     */
    List<ExternalLogParcel> getLogsByLevel(String level, int limit);

    /**
     * Get total count of stored logs.
     */
    int getLogCount();

    /**
     * Clear all logs.
     */
    void clearAllLogs();

    // ========== v2: IPC key exchange ==========

    /**
     * Provide the SQLCipher database passphrase from the main process KeyManager.
     * Must be called immediately after onServiceConnected, before any queries.
     * The service defers database initialization until this is received.
     *
     * @param key Raw passphrase bytes from KeyManager.getDatabasePassphrase()
     */
    void setDatabaseKey(in byte[] key);

    // ========== v2: Metrics endpoint ==========

    /**
     * Get a runtime security metrics snapshot. Main process only (validated by UID).
     */
    LoggingMetricsParcel getSecurityMetrics();

    // ========== v2: Async log queries (non-blocking Binder thread) ==========

    /**
     * Async version of getRecentLogs. Safe for large datasets.
     * Result delivered via ILoggingResultCallback.onLogsResult().
     *
     * @param limit     Maximum entries to return (recommend <= 500)
     * @param callback  Callback for result delivery
     * @param requestId Opaque correlation token chosen by caller
     */
    void getRecentLogsAsync(int limit, ILoggingResultCallback callback, int requestId);

    /**
     * Async version of getLogsByPackage.
     */
    void getLogsByPackageAsync(String packageName, int limit,
                               ILoggingResultCallback callback, int requestId);

    /**
     * Async version of getLogsByLevel.
     */
    void getLogsByLevelAsync(String level, int limit,
                             ILoggingResultCallback callback, int requestId);

    // ========== v2: Security audit events ==========

    /**
     * Synchronous retrieval of recent security audit events.
     */
    List<SecurityAuditParcel> getRecentAuditEvents(int limit);

    /**
     * Async version of getRecentAuditEvents.
     * Result delivered via ILoggingResultCallback.onAuditEventsResult().
     */
    void getRecentAuditEventsAsync(int limit, ILoggingResultCallback callback, int requestId);
}
