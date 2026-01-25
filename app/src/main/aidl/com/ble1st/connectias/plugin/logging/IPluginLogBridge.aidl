package com.ble1st.connectias.plugin.logging;

/**
 * One-way log bridge from Sandbox/UI processes to Main process.
 *
 * Purpose:
 * - Persist plugin logs in DB (main process only)
 * - Mirror to Logcat for developer debugging
 *
 * Notes:
 * - Keep payload small; use exception stack trace as String.
 */
interface IPluginLogBridge {
    /**
     * Record a log line for a plugin.
     *
     * @param pluginId Plugin ID (required)
     * @param priority Android Log priority (VERBOSE..ASSERT)
     * @param tag Optional tag (may be null)
     * @param message Message (required)
     * @param threadName Thread name in emitting process (optional, may be null)
     * @param exceptionTrace Optional stack trace string (may be null)
     * @param timestamp Epoch millis
     */
    void log(
        String pluginId,
        int priority,
        String tag,
        String message,
        String threadName,
        String exceptionTrace,
        long timestamp
    );
}

