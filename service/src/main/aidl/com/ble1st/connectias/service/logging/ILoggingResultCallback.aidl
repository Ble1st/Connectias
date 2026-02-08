// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging;

import com.ble1st.connectias.service.logging.ExternalLogParcel;
import com.ble1st.connectias.service.logging.SecurityAuditParcel;

/**
 * Async callback interface for log query results.
 *
 * Used by getRecentLogsAsync, getLogsByPackageAsync, getLogsByLevelAsync,
 * and getRecentAuditEventsAsync. All methods are oneway so the LoggingService
 * binder thread is never blocked waiting for the main process to handle results.
 *
 * The requestId field is an opaque correlation token chosen by the caller
 * to match responses to the original request.
 */
interface ILoggingResultCallback {

    /**
     * Called when log query results are ready.
     *
     * @param logs    The returned log entries (may be empty)
     * @param requestId Correlates this response to the original request
     */
    oneway void onLogsResult(in List<ExternalLogParcel> logs, int requestId);

    /**
     * Called when audit event query results are ready.
     *
     * @param events  The returned audit events
     * @param requestId Correlates this response to the original request
     */
    oneway void onAuditEventsResult(in List<SecurityAuditParcel> events, int requestId);

    /**
     * Called when a query fails.
     *
     * @param errorMessage Description of the error
     * @param requestId    Correlates this response to the original request
     */
    oneway void onError(String errorMessage, int requestId);
}
