package com.ble1st.connectias.service.logging;

import com.ble1st.connectias.service.logging.ExternalLogParcel;

/**
 * Combined AIDL interface for LoggingService.
 * 
 * External apps use submitLog* methods (requires SUBMIT_EXTERNAL_LOGS permission).
 * Main process uses setEnabled/getRecentLogs methods (validated by caller UID).
 * 
 * The service validates caller permissions/UID for each method.
 */
interface ILoggingService {
    
    // ========== Methods for external apps (requires permission) ==========
    
    /**
     * Submit a single log entry.
     */
    void submitLog(String packageName, String level, String tag, String message);
    
    /**
     * Submit a log entry with exception trace.
     */
    void submitLogWithException(String packageName, String level, String tag, String message, String exceptionTrace);
    
    // ========== Methods for main process (validated by UID) ==========
    
    /**
     * Enable or disable the logging service.
     */
    void setEnabled(boolean enabled);
    
    /**
     * Check if the logging service is currently enabled.
     */
    boolean isEnabled();
    
    /**
     * Get recent logs for the Service-Log-Viewer.
     */
    List<ExternalLogParcel> getRecentLogs(int limit);
    
    /**
     * Get logs for a specific package.
     */
    List<ExternalLogParcel> getLogsByPackage(String packageName, int limit);
    
    /**
     * Get logs by level.
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
}
